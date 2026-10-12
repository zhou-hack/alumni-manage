package com.alumni.screen.service;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.screen.common.BizException;
import com.alumni.screen.common.ErrorCode;
import com.alumni.screen.dto.ScreenDtos;
import com.alumni.screen.dto.ScreenDtos.CategoryDist;
import com.alumni.screen.dto.ScreenDtos.DimensionItem;
import com.alumni.screen.dto.ScreenDtos.LevelDist;
import com.alumni.screen.dto.ScreenDtos.Overview;
import com.alumni.screen.dto.ScreenDtos.StatsPayload;
import com.alumni.screen.dto.ScreenDtos.TopOwner;
import com.alumni.screen.dto.ScreenDtos.TopOwners;
import com.alumni.screen.dto.ScreenDtos.YearTrend;
import com.alumni.screen.dto.ScreenDtos.TrendItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 荣誉统计分析 Service（[M03 大屏管理]）：
 *
 * <p>聚合查询来源：honors 表（approved 状态为主，all-time）。
 * 所有聚合 SQL 走 EntityManager + 参数化，杜绝拼接注入。
 *
 * <p>维度：
 * <ul>
 *   <li>overview：总数 / 审批态 / 获奖人数（去重 user_id）</li>
 *   <li>year_trend：按 awarded_at 年份分组（line）</li>
 *   <li>category_dist：按 category 分组（pie）</li>
 *   <li>level_dist：按 level 分组（bar）</li>
 *   <li>top_owners：按 user_id 聚合获数 + JOIN users/alumni_base 取姓名（TOP N，默认 10）</li>
 * </ul>
 *
 * <p>权限：仅 school_admin / system_admin（前台大屏 H5 不调此接口，由 stats 公共接口 /dashboard-honor 提供）。</p>
 */
@Service
public class HonorStatsService {

    @PersistenceContext
    private EntityManager em;

    private static final Set<String> ALLOWED_ROLES = Set.of("school_admin", "system_admin");

    /** 荣誉级别 key → 中文标签（与 api/honor.js HONOR_LEVELS 对齐） */
    private static final Map<String, String> LEVEL_LABEL = Map.of(
            "national", "国家级",
            "provincial", "省级",
            "city", "市级",
            "school", "校级",
            "international", "国际级"
    );

    /** 荣誉类别 key → 中文标签（与 dicts honor_category 约定一致） */
    private static final Map<String, String> CATEGORY_LABEL = new LinkedHashMap<>(Map.of(
            "scholarship", "奖学金",
            "competition", "学科竞赛",
            "research", "科研项目",
            "social", "社会实践",
            "arts", "文体活动",
            "honor_title", "荣誉称号",
            "other", "其他"
    ));

    /* ==================== 主入口 ==================== */

    @Transactional(readOnly = true)
    public StatsPayload stats(AuthPrincipal operator, int topN) {
        requireManager(operator);
        int limit = Math.max(1, Math.min(topN, 50));

        Overview overview = overview();
        YearTrend yearTrend = yearTrend();
        CategoryDist categoryDist = categoryDist();
        LevelDist levelDist = levelDist();
        TopOwners topOwners = topOwners(limit);

        return new StatsPayload(overview, yearTrend, categoryDist, levelDist, topOwners, Instant.now());
    }

    /* ==================== 概览 ==================== */

    public Overview overview() {
        Map<String, Object> totalRow = single("SELECT COUNT(*) c FROM honors");
        Map<String, Object> approvedRow = single("SELECT COUNT(*) c FROM honors WHERE status = 'approved'");
        Map<String, Object> pendingRow = single("SELECT COUNT(*) c FROM honors WHERE status LIKE 'pending%'");
        Map<String, Object> rejectedRow = single("SELECT COUNT(*) c FROM honors WHERE status = 'rejected'");
        Map<String, Object> ownersRow = single("SELECT COUNT(DISTINCT user_id) c FROM honors");
        // 兼容字段（前端 KPI 卡片使用）：
        //   total_alumni：所有 alumni_base（含同名异态已合并到同一毕业表的去重数）
        //   college_count：荣誉数据所覆盖的去重学院数（经 honors → users → alumni_base JOIN 聚合）
        //   grad_year_count：荣誉数据所覆盖的去重届别数（同上路径）
        Map<String, Object> alumniRow = single("SELECT COUNT(*) c FROM alumni_base");
        Map<String, Object> collegeRow = single(
                "SELECT COUNT(DISTINCT ab.college) c " +
                "FROM honors h JOIN users u ON u.id = h.user_id " +
                "LEFT JOIN alumni_base ab ON ab.id = u.alumni_base_id " +
                "WHERE ab.college IS NOT NULL AND ab.college <> ''");
        Map<String, Object> gradYearRow = single(
                "SELECT COUNT(DISTINCT ab.grad_year) c " +
                "FROM honors h JOIN users u ON u.id = h.user_id " +
                "LEFT JOIN alumni_base ab ON ab.id = u.alumni_base_id " +
                "WHERE ab.grad_year IS NOT NULL AND ab.grad_year > 0");
        return new Overview(
                toLong(totalRow.get("c")),
                toLong(approvedRow.get("c")),
                toLong(pendingRow.get("c")),
                toLong(rejectedRow.get("c")),
                toLong(ownersRow.get("c")),
                toLong(alumniRow.get("c")),
                toLong(collegeRow.get("c")),
                toLong(gradYearRow.get("c")));
    }

    /* ==================== 趋势（按年）================== */

    public YearTrend yearTrend() {
        // 按 awarded_at 年份分组；YEAR() 在 awarded_at 为 NULL 时返回 NULL，过滤掉
        Query q = em.createNativeQuery(
                "SELECT YEAR(awarded_at) AS k, COUNT(*) AS c " +
                "FROM honors " +
                "WHERE awarded_at IS NOT NULL " +
                "GROUP BY YEAR(awarded_at) " +
                "ORDER BY YEAR(awarded_at) ASC");
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<TrendItem> list = new ArrayList<>();
        for (Object[] r : rows) {
            int year = ((Number) r[0]).intValue();
            long count = toLong(r[1]);
            list.add(new TrendItem(year, count));
        }
        return new YearTrend(list);
    }

    /* ==================== 类别分布 ==================== */

    public CategoryDist categoryDist() {
        Query q = em.createNativeQuery(
                "SELECT category AS k, COUNT(*) AS c " +
                "FROM honors " +
                "WHERE category IS NOT NULL AND category <> '' " +
                "GROUP BY category " +
                "ORDER BY c DESC");
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<DimensionItem> list = new ArrayList<>();
        for (Object[] r : rows) {
            String key = (String) r[0];
            list.add(new DimensionItem(key, CATEGORY_LABEL.getOrDefault(key, key), toLong(r[1])));
        }
        return new CategoryDist(list);
    }

    /* ==================== 级别分布 ==================== */

    public LevelDist levelDist() {
        Query q = em.createNativeQuery(
                "SELECT level AS k, COUNT(*) AS c " +
                "FROM honors " +
                "WHERE level IS NOT NULL AND level <> '' " +
                "GROUP BY level " +
                "ORDER BY c DESC");
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<DimensionItem> list = new ArrayList<>();
        for (Object[] r : rows) {
            String key = (String) r[0];
            list.add(new DimensionItem(key, LEVEL_LABEL.getOrDefault(key, key), toLong(r[1])));
        }
        return new LevelDist(list);
    }

    /* ==================== TOP 排行 ==================== */

    public TopOwners topOwners(int limit) {
        // 优先展示已通过审批的荣誉（approved）
        // 关联 users + alumni_base（alumni_base 可能在其他 schema 用别名 ab）
        Query q = em.createNativeQuery(
                "SELECT u.student_id AS sid, COALESCE(ab.real_name, u.nickname, '') AS name, " +
                "       COALESCE(ab.college, '') AS college, COUNT(*) AS c " +
                "FROM honors h " +
                "JOIN users u ON u.id = h.user_id " +
                "LEFT JOIN alumni_base ab ON ab.id = u.alumni_base_id " +
                "WHERE h.status = 'approved' " +
                "GROUP BY u.id, u.student_id, ab.real_name, u.nickname, ab.college " +
                "ORDER BY c DESC, u.id ASC " +
                "LIMIT :n");
        q.setParameter("n", limit);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<TopOwner> list = new ArrayList<>();
        for (Object[] r : rows) {
            list.add(new TopOwner(
                    (String) r[0],
                    (String) r[1],
                    (String) r[2],
                    toLong(r[3])));
        }
        return new TopOwners(list);
    }

    /* ==================== 工具 ==================== */

    private Map<String, Object> single(String sql) {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery(sql).getResultList();
        if (rows.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object first = rows.get(0);
        // 单列聚合查询（如 SELECT COUNT(*) c FROM ...）返回的是 scalar (Long/Number/...)，
        // 多列才返回 Object[]；这里兼容两种情况，避免 ClassCastException
        Object value = (first instanceof Object[]) ? ((Object[]) first)[0] : first;
        // 列名未知：用 Map.of() 不可行，改用索引 map：col0/col1/...
        // 为简化：约定上层用 "c" 别名（如 SELECT COUNT(*) c FROM ...），直接读 column "c"
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("c", value);
        return m;
    }

    private static long toLong(Object raw) {
        return raw == null ? 0L : ((Number) raw).longValue();
    }

    private void requireManager(AuthPrincipal operator) {
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        if (!ALLOWED_ROLES.contains(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅 school_admin / system_admin 可查看统计分析");
        }
    }

    // 单测注入用（允许测试替换 EntityManager 行为）
    EntityManager em() { return em; }
}
