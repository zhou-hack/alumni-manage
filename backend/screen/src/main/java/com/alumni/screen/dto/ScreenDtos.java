package com.alumni.screen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 荣誉大屏模块 DTO（snake_case 对齐前端 Jackson 默认）。
 *
 * <p>布局与主题预设白名单（与 Element Plus Radio 同步）：
 * <ul>
 *   <li>layout: grid-3 / grid-4 / waterfall</li>
 *   <li>theme_color: tech / gold / dark / warm</li>
 *   <li>sort_by: awarded_at_desc / level_asc / pinned_first</li>
 *   <li>refresh_sec: 0 / 10 / 30 / 60（0=手动）</li>
 * </ul>
 */
public final class ScreenDtos {

    private ScreenDtos() {
    }

    /** show_fields 默认值（首次安装 / 重置时使用） */
    public static final String DEFAULT_SHOW_FIELDS_JSON =
            "{\"title\":true,\"level\":true,\"category\":true,\"granted_by\":true,\"awarded_at\":true,\"owner\":false}";

    /* ==================== 请求 ==================== */

    /** 创建/更新配置请求（PUT 全量替换关键字段；PATCH 仅做语义化差异更新——MVP 用 PUT 全量） */
    public record UpsertRequest(
            @NotBlank(message = "name 不能为空")
            @Size(max = 64, message = "name 最长 64 字符") String name,

            @NotBlank(message = "layout 不能为空")
            @Pattern(regexp = "^(grid-3|grid-4|waterfall)$",
                    message = "layout 仅允许 grid-3 / grid-4 / waterfall") String layout,

            @NotBlank(message = "themeColor 不能为空")
            @Pattern(regexp = "^(tech|gold|dark|warm)$",
                    message = "themeColor 仅允许 tech / gold / dark / warm") String themeColor,

            @Min(value = 0, message = "refreshSec 须 ≥ 0（0=手动刷新）")
            @Max(value = 600, message = "refreshSec 须 ≤ 600（10 分钟）") Integer refreshSec,

            /** 字段开关 map：key=字段名，value=boolean；后端 null 时使用默认值 */
            Map<String, Boolean> showFields,

            @NotBlank(message = "sortBy 不能为空")
            @Pattern(regexp = "^(awarded_at_desc|level_asc|pinned_first)$",
                    message = "sortBy 仅允许 awarded_at_desc / level_asc / pinned_first") String sortBy,

            @Min(value = 1, message = "maxItems 须 ≥ 1")
            @Max(value = 200, message = "maxItems 须 ≤ 200") Integer maxItems,

            @Pattern(regexp = "^(active|draft|archived)$",
                    message = "status 仅允许 active / draft / archived") String status,

            @Size(max = 255, message = "remark 最长 255 字符") String remark
    ) {
    }

    /* ==================== 响应 ==================== */

    /** 配置详情（GET /admin/screen/config/{id} 或 active 配置） */
    public record ConfigItem(
            Long id,
            String name,
            String layout,
            String themeColor,
            Integer refreshSec,
            Map<String, Boolean> showFields,
            String sortBy,
            Integer maxItems,
            String status,
            String remark,
            Long createdBy,
            Long updatedBy,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    /** 分页包装 */
    public record PageResult<T>(List<T> list, long total, int page, int pageSize) {
    }

    /* ==================== 统计分析 ==================== */

    /** 概览 KPI（前端展示：总荣誉 / 已通过 / 待审 / 驳回 / 获奖人数） */
    public record Overview(
            long totalHonors,
            long approvedHonors,
            long pendingHonors,
            long rejectedHonors,
            long totalOwners,
            // 兼容旧字段命名（前端曾经误读 total_alumni / college_count / grad_year_count）
            long totalAlumni,
            long collegeCount,
            long gradYearCount
    ) {
    }

    /** 单组维度（趋势/分布/排行通用） */
    public record DimensionItem(String key, String name, long count) {
    }

    /** 趋势分析：按年维度 */
    public record TrendItem(Integer year, long count) {
    }

    /** 类别分布（pie） */
    public record CategoryDist(List<DimensionItem> list) {
    }

    /** 级别分布（bar） */
    public record LevelDist(List<DimensionItem> list) {
    }

    /** 年份趋势（line） */
    public record YearTrend(List<TrendItem> list) {
    }

    /** TOP 排行（按用户获奖数） */
    public record TopOwner(String studentId, String name, String college, long honorCount) {
    }

    /** TOP 排行响应 */
    public record TopOwners(List<TopOwner> list) {
    }

    /** 统计聚合接口响应 */
    public record StatsPayload(
            Overview overview,
            YearTrend yearTrend,
            CategoryDist categoryDist,
            LevelDist levelDist,
            TopOwners topOwners,
            Instant generatedAt
    ) {
    }
}
