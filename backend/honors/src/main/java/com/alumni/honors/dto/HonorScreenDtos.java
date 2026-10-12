package com.alumni.honors.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 校友荣誉大屏聚合 DTO（[P1] honor-screen 模块 · /api/v1/dashboard-honor）
 *
 * <p>全部 snake_case 输出，供 portal 荣誉大屏（/dashboard）一次性渲染：
 * <ul>
 *   <li>stats：4 张 KPI（校友总人数 / 累计获奖人次 / 覆盖学院 / 覆盖届别）</li>
 *   <li>honor_by_type：荣誉按级别聚合（national/provincial/city/school → 国际级/国家级/省部级/校级）</li>
 *   <li>industry_top：荣誉按学院聚合（Top 8）</li>
 *   <li>province_dist：荣誉按届别聚合（最近 12 年 Top 12）</li>
 *   <li>honors：荣誉榜单（按级别+年份排序，最多 20 条）</li>
 *   <li>alumni：杰出校友轮播（按累计荣誉数 Top 8，仅 approved 状态）</li>
 *   <li>updated_at：数据更新时间戳</li>
 * </ul>
 */
public record HonorScreenDtos(
        Stats stats,
        List<Map<String, Object>> honorByType,
        List<Map<String, Object>> industryTop,
        List<Map<String, Object>> provinceDist,
        List<Map<String, Object>> honors,
        List<Map<String, Object>> alumni,
        ScreenConfig screenConfig,
        Instant updatedAt) {

    /** KPI 卡片四元组（label / display 前端组件直接渲染） */
    public record Stats(long totalAlumni, long totalAwards, long collegeCount, long gradYearCount) {}

    /**
     * 当前生效大屏配置（来自 screen 模块；portal 大屏据此渲染布局/主题/字段开关）。
     * <p>字段含义与 {@code ScreenDtos.ConfigItem} 对齐；这里仅保留 portal 端实际用到的字段，
     * 避免直接跨模块引用造成反向依赖。</p>
     */
    public record ScreenConfig(
            String name,
            String layout,        // grid-3 / grid-4 / waterfall
            String themeColor,    // tech / gold / dark / warm
            Integer refreshSec,   // 自动刷新间隔（秒；0=不自动）
            Map<String, Boolean> showFields,  // 字段开关：title/level/category/granted_by/awarded_at/owner
            String sortBy,        // awarded_at_desc / level_asc / pinned_first
            Integer maxItems      // 榜单最大条数
    ) {}
}
