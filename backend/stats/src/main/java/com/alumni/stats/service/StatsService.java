package com.alumni.stats.service;

import com.alumni.stats.common.BizException;
import com.alumni.stats.common.ErrorCode;
import com.alumni.stats.repository.ArchiveRepository;
import com.alumni.stats.repository.CollegeRankProjection;
import com.alumni.stats.repository.CollegeRankRepository;
import com.alumni.stats.repository.HonorDistributionRepository;
import com.alumni.stats.repository.HonorRepository;
import com.alumni.stats.repository.KeyCountProjection;
import com.alumni.stats.repository.ReviewRepository;
import com.alumni.stats.repository.ShowcaseCountRepository;
import com.alumni.stats.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统计业务服务（Service 层）。
 *
 * <p>实现《[定稿]API规范》第十章三个口径：总量看板 / 荣誉分布 / 学院排行。
 * 职责：参数业务校验 + 调用 Repository + 结果组装；
 * 不含任何 HTTP 与 SQL 细节，全部通过 Repository 取数。</p>
 */
@Service
public class StatsService {

    /** 学院排行默认返回条数（规范第十章示例 ?limit=8） */
    public static final int DEFAULT_LIMIT = 8;
    /** 学院排行最大返回条数（与分页 page_size 上限 100 保持一致） */
    public static final int MAX_LIMIT = 100;
    /** [M03] 荣誉分布维度合法集 */
    private static final Set<String> HONOR_DISTRIBUTION_DIMENSIONS = Set.of("college", "year", "category", "level");
    /** [M03] 荣誉分布默认条数 */
    private static final int HONOR_DISTRIBUTION_DEFAULT_LIMIT = 20;

    /** 待审状态（reviews.status='pending'） */
    private static final String REVIEW_STATUS_PENDING = "pending";

    private final UserRepository userRepository;
    private final ArchiveRepository archiveRepository;
    private final HonorRepository honorRepository;
    private final ReviewRepository reviewRepository;
    private final CollegeRankRepository collegeRankRepository;
    private final ShowcaseCountRepository showcaseCountRepository;
    private final HonorDistributionRepository honorDistributionRepository;

    public StatsService(UserRepository userRepository,
                        ArchiveRepository archiveRepository,
                        HonorRepository honorRepository,
                        ReviewRepository reviewRepository,
                        CollegeRankRepository collegeRankRepository,
                        ShowcaseCountRepository showcaseCountRepository,
                        HonorDistributionRepository honorDistributionRepository) {
        this.userRepository = userRepository;
        this.archiveRepository = archiveRepository;
        this.honorRepository = honorRepository;
        this.reviewRepository = reviewRepository;
        this.collegeRankRepository = collegeRankRepository;
        this.showcaseCountRepository = showcaseCountRepository;
        this.honorDistributionRepository = honorDistributionRepository;
    }

    /** 总量看板：校友数 / 档案数 / 荣誉数 / 待审数 / 已上架数（schema.sql 第十章口径 + 看板扩展） */
    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total_alumni", userRepository.count());
        data.put("total_archives", archiveRepository.count());
        data.put("total_honors", honorRepository.count());
        data.put("pending_reviews", reviewRepository.countByStatus(REVIEW_STATUS_PENDING));
        data.put("published_count", showcaseCountRepository.countAll());
        return data;
    }

    /** 荣誉分布：仅统计 Honor 表，按类别 / 级别两个维度聚合 */
    @Transactional(readOnly = true)
    public Map<String, Object> honors() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("by_category", toKeyCountList(honorRepository.countGroupByCategory()));
        data.put("by_level", toKeyCountList(honorRepository.countGroupByLevel()));
        return data;
    }

    /**
     * 学院排行：档案数 Top N。
     *
     * @param limit 未传默认 8，合法区间 1~100，越界抛 INVALID_PARAMETER
     */
    @Transactional(readOnly = true)
    public List<CollegeRankProjection> colleges(Integer limit) {
        int effective = (limit == null) ? DEFAULT_LIMIT : limit;
        if (effective < 1 || effective > MAX_LIMIT) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "limit 必须在 1~" + MAX_LIMIT + " 之间");
        }
        return collegeRankRepository.findCollegeRanks(effective);
    }

    /**
     * [M03] 荣誉分布：按维度（college/year/category/level）聚合已通过荣誉数，
     * 可选 college_id / year 过滤；limit 默认 20 最大 100。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> honorDistribution(String dimension, Long collegeId, Integer year,
                                                 Integer limit) {
        String dim = dimension == null || dimension.isBlank() ? "category" : dimension.trim();
        if (!HONOR_DISTRIBUTION_DIMENSIONS.contains(dim)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "dimension 仅支持 college / year / category / level");
        }
        int effective = (limit == null) ? HONOR_DISTRIBUTION_DEFAULT_LIMIT : limit;
        if (effective < 1 || effective > MAX_LIMIT) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "limit 必须在 1~" + MAX_LIMIT + " 之间");
        }
        List<KeyCountProjection> rows = switch (dim) {
            case "college" -> honorDistributionRepository.byCollege(collegeId, year, effective);
            case "year" -> honorDistributionRepository.byYear(collegeId, year, effective);
            case "level" -> honorDistributionRepository.byLevel(collegeId, year, effective);
            default -> honorDistributionRepository.byCategory(collegeId, year, effective);
        };
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dimension", dim);
        data.put("college_id", collegeId);
        data.put("year", year);
        data.put("list", toKeyCountList(rows));
        return data;
    }

    /** 分组计数投影 → [{key, count}] 列表 */
    private static List<Map<String, Object>> toKeyCountList(List<KeyCountProjection> rows) {
        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (KeyCountProjection row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("key", row.getK());
            map.put("count", row.getCnt());
            list.add(map);
        }
        return list;
    }
}
