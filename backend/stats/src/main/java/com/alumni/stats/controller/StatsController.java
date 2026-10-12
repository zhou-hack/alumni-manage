package com.alumni.stats.controller;

import com.alumni.stats.common.ApiResponse;
import com.alumni.stats.common.BizException;
import com.alumni.stats.common.ErrorCode;
import com.alumni.stats.repository.CollegeRankProjection;
import com.alumni.stats.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计模块控制器（Controller 层）。
 *
 * <p>实现《[定稿]API规范》第十章三个接口（路径相对于 /api/v1）：</p>
 * <ul>
 *   <li>GET /stats/overview —— 总量看板：校友数/档案数/荣誉数/待审数</li>
 *   <li>GET /stats/honors —— 荣誉分布（仅统计 Honor 表，按类别/级别聚合）</li>
 *   <li>GET /stats/colleges?limit=8 —— 学院排行（档案数 Top N）</li>
 * </ul>
 *
 * <p>职责：参数解析 → 调用 Service → 组装统一响应（出参 snake_case）。
 * 不含任何业务规则与 SQL。</p>
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /** GET /api/v1/stats/overview —— 总量看板 */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(statsService.overview());
    }

    /** GET /api/v1/stats/honors —— 荣誉分布 */
    @GetMapping("/honors")
    public ApiResponse<Map<String, Object>> honors() {
        return ApiResponse.ok(statsService.honors());
    }

    /** GET /api/v1/stats/honors/distribution?dimension=college|year|category|level —— [M03] 荣誉四维分布 */
    @GetMapping("/honors/distribution")
    public ApiResponse<Map<String, Object>> honorDistribution(
            @RequestParam(name = "dimension", required = false) String dimension,
            @RequestParam(name = "college_id", required = false) Long collegeId,
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return ApiResponse.ok(statsService.honorDistribution(dimension, collegeId, year, limit));
    }

    /** GET /api/v1/stats/colleges?limit=8 —— 学院排行（档案数 Top N） */
    @GetMapping("/colleges")
    public ApiResponse<Map<String, Object>> colleges(
            @RequestParam(name = "limit", required = false) String limitRaw) {
        List<CollegeRankProjection> ranks = statsService.colleges(parseLimit(limitRaw));
        List<Map<String, Object>> list = new ArrayList<>(ranks.size());
        for (CollegeRankProjection rank : ranks) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("college_id", rank.getCollegeId());
            map.put("college_name", rank.getCollegeName());
            map.put("archive_count", rank.getArchiveCount());
            list.add(map);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        return ApiResponse.ok(data);
    }

    /**
     * 解析 limit query 参数。
     *
     * @param raw 原始字符串，null / 空白表示未传（使用默认值）
     * @return 整数值；非数字直接抛出 INVALID_PARAMETER
     */
    private Integer parseLimit(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "limit 必须为整数");
        }
    }
}
