package com.alumni.honors.service;

import com.alumni.honors.dto.HonorScreenDtos;
import com.alumni.honors.dto.HonorScreenDtos.Stats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 校友荣誉大屏聚合（/api/v1/dashboard-honor · GET · 公开免认证）。
 *
 * <p>一次返回大屏渲染所需的全部数据，避免前端 6 次轮询。后端用单库 SQL 直接聚合，约束：
 * <ul>
 *   <li>仅统计 honors.status='approved' 的已通过荣誉（与公开门户口径一致）</li>
 *   <li>honor_by_type：按 level 聚合（national/provincial/city/school），按数量降序</li>
 *   <li>industry_top：按 alumni_base.college 聚合，Top 8</li>
 *   <li>province_dist：按 alumni_base.grad_year 聚合，最近 12 年 Top 12</li>
 *   <li>honors：荣誉榜单 Top 20（按级别优先级 + 年份倒序）</li>
 *   <li>alumni：杰出校友 Top 8（按累计 approved 荣誉数排序，取最新一条荣誉做 highlight）</li>
 *   <li>screen_config：当前生效大屏配置（由 admin 端配置页维护，portal 端据此渲染）
 *       —— 直接读 honor_screen_config 表（同库跨模块读，避免跨模块依赖）</li>
 * </ul>
 */
@Service
public class HonorScreenService {

    /** screen 模块的 honor_screen_config 表名（跨模块读，避免反向依赖） */
    private static final String SCREEN_CONFIG_TABLE = "honor_screen_config";

    /** 加载失败/不存在时返回的默认配置（与 admin ScreenDtos.DEFAULT_* 对齐） */
    private static final HonorScreenDtos.ScreenConfig DEFAULT_SCREEN_CONFIG = new HonorScreenDtos.ScreenConfig(
            "default",
            "grid-3",
            "tech",
            30,
            java.util.Map.of(
                    "title", true,
                    "level", true,
                    "category", true,
                    "granted_by", true,
                    "awarded_at", true,
                    "owner", false),
            "awarded_at_desc",
            50);

    /** level 中文映射（与 honors.level 字典对齐，前端展示用） */
    private static final Map<String, String> LEVEL_LABEL = Map.of(
            "international", "国际级",
            "national", "国家级",
            "provincial", "省部级",
            "city", "市级",
            "school", "校级");

    /** 级别排序权重（数值越小越靠前） */
    private static final Map<String, Integer> LEVEL_ORDER = Map.of(
            "international", 0,
            "national", 1,
            "provincial", 2,
            "city", 3,
            "school", 4);

    /** 杰出校友 Top N */
    private static final int ALUMNI_TOP = 8;
    /** 荣誉榜单 Top N */
    private static final int HONORS_TOP = 20;
    /** 学院 Top N */
    private static final int INDUSTRY_TOP = 8;
    /** 届别 Top N */
    private static final int PROVINCE_TOP = 12;

    private final JdbcTemplate jdbc;

    public HonorScreenService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 一次性聚合大屏数据。readOnly 事务避免脏读。 */
    @Transactional(readOnly = true)
    public HonorScreenDtos load() {
        return new HonorScreenDtos(
                loadStats(),
                loadHonorByType(),
                loadIndustryTop(),
                loadProvinceDist(),
                loadHonors(),
                loadAlumni(),
                loadScreenConfig(),
                Instant.now());
    }

    /* ----------------------- 当前生效大屏配置（与 admin 配置页同步）----------------------- */

    /**
     * 直接读 honor_screen_config 表，取 status='active' 的最新一条；
     * 表/字段读取失败（如未初始化）时返回默认配置，保证 portal 端永不报错。
     */
    private HonorScreenDtos.ScreenConfig loadScreenConfig() {
        try {
            // 列名按 entity 字段推断（snake_case）；与 screen 模块 HonorScreenConfig 实体保持一致
            // 防御性查询：表或列不存在时回退默认
            java.util.List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT name, layout, theme_color, refresh_sec, show_fields, sort_by, max_items " +
                            "FROM " + SCREEN_CONFIG_TABLE + " WHERE status = 'active' " +
                            "ORDER BY updated_at DESC LIMIT 1");
            if (rows.isEmpty()) {
                return DEFAULT_SCREEN_CONFIG;
            }
            java.util.Map<String, Object> r = rows.get(0);
            java.util.Map<String, Boolean> showFields = parseShowFields(asString(r.get("show_fields")));
            return new HonorScreenDtos.ScreenConfig(
                    asString(r.get("name")),
                    asString(r.get("layout")),
                    asString(r.get("theme_color")),
                    asInt(r.get("refresh_sec")),
                    showFields,
                    asString(r.get("sort_by")),
                    asInt(r.get("max_items")));
        } catch (Exception e) {
            // 兜底：跨模块读表异常时返回默认配置（不影响门户大屏主链路）
            return DEFAULT_SCREEN_CONFIG;
        }
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static int asInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** show_fields 是 JSON 字符串（Map<String,Boolean>），失败回退空 map（前端按默认值兜底） */
    private static java.util.Map<String, Boolean> parseShowFields(String json) {
        if (json == null || json.isBlank()) return new java.util.LinkedHashMap<>();
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            return om.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, Boolean>>() {});
        } catch (Exception e) {
            return new java.util.LinkedHashMap<>();
        }
    }

    /* ----------------------- 4 张 KPI ----------------------- */

    private Stats loadStats() {
        Long totalAlumni = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role IN ('alumni','student') AND status = 'active'",
                Long.class);
        Long totalAwards = jdbc.queryForObject(
                "SELECT COUNT(*) FROM honors WHERE status = 'approved' AND screen_status = 'online'",
                Long.class);
        Long collegeCount = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT college) FROM alumni_base WHERE college IS NOT NULL AND college <> ''",
                Long.class);
        Long gradYearCount = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT grad_year) FROM alumni_base", Long.class);
        return new Stats(
                nz(totalAlumni), nz(totalAwards), nz(collegeCount), nz(gradYearCount));
    }

    /* ----------------------- 4 个分组聚合 ----------------------- */

    /** 荣誉类型分布：按 level 聚合，已通过 + 上架，按级别优先级排序 */
    private List<Map<String, Object>> loadHonorByType() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT level AS k, COUNT(*) AS cnt FROM honors " +
                "WHERE status = 'approved' AND screen_status = 'online' " +
                "GROUP BY level ORDER BY cnt DESC");
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            String level = String.valueOf(r.get("k"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", level);
            m.put("name", LEVEL_LABEL.getOrDefault(level, level));
            m.put("value", ((Number) r.get("cnt")).longValue());
            out.add(m);
        }
        // 按 LEVEL_ORDER 排序后输出，保证"国家级"始终在"校级"前
        out.sort((a, b) -> Integer.compare(
                LEVEL_ORDER.getOrDefault(String.valueOf(a.get("type")), 99),
                LEVEL_ORDER.getOrDefault(String.valueOf(b.get("type")), 99)));
        return out;
    }

    /** 学院 Top 8：按 alumni_base.college 聚合荣誉数（仅已上架） */
    private List<Map<String, Object>> loadIndustryTop() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT ab.college AS name, COUNT(h.id) AS cnt " +
                "FROM honors h JOIN alumni_base ab ON ab.id = h.user_id " +
                "WHERE h.status = 'approved' AND h.screen_status = 'online' " +
                "  AND ab.college IS NOT NULL AND ab.college <> '' " +
                "GROUP BY ab.college ORDER BY cnt DESC LIMIT ?", INDUSTRY_TOP);
        return rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.get("name"));
            m.put("value", ((Number) r.get("cnt")).longValue());
            return m;
        }).toList();
    }

    /** 届别分布 Top 12：按 alumni_base.grad_year 聚合（最近年份优先，仅已上架） */
    private List<Map<String, Object>> loadProvinceDist() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT ab.grad_year AS year, COUNT(h.id) AS cnt " +
                "FROM honors h JOIN alumni_base ab ON ab.id = h.user_id " +
                "WHERE h.status = 'approved' AND h.screen_status = 'online' " +
                "GROUP BY ab.grad_year ORDER BY ab.grad_year DESC LIMIT ?", PROVINCE_TOP);
        return rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.get("year") + " 届");
            m.put("value", ((Number) r.get("cnt")).longValue());
            return m;
        }).toList();
    }

    /* ----------------------- 荣誉榜单 + 杰出校友 ----------------------- */

    /**
     * 荣誉榜单 Top 20：取已通过 + 上架荣誉，按级别优先级 + 年份倒序排序。
     * 关联 alumni_base 取校友姓名（real_name）。
     */
    private List<Map<String, Object>> loadHonors() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT h.id, h.title, h.level, YEAR(h.awarded_at) AS year, ab.real_name AS alumnus_name " +
                "FROM honors h JOIN alumni_base ab ON ab.id = h.user_id " +
                "WHERE h.status = 'approved' AND h.screen_status = 'online' " +
                "ORDER BY FIELD(h.level,'international','national','provincial','city','school'), " +
                "         h.awarded_at DESC LIMIT ?", HONORS_TOP);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            String level = String.valueOf(r.get("level"));
            Integer year = r.get("year") == null ? null : ((Number) r.get("year")).intValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("title", r.get("title"));
            m.put("level", LEVEL_LABEL.getOrDefault(level, level));
            m.put("levelKey", level);
            m.put("year", year);
            m.put("alumnusName", r.get("alumnus_name"));
            out.add(m);
        }
        return out;
    }

    /**
     * 杰出校友 Top 8：按"该用户累计 approved 荣誉数"降序取前 8。
     * 每位校友附加：累计获奖数、最近一条 approved 荣誉标题作为 highlight。
     * V18 扩展：同时读取 photo_url / industry / intro / quote / motto（前端不再降级为空）。
     */
    private List<Map<String, Object>> loadAlumni() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT ab.id AS ab_id, ab.real_name AS name, ab.college, ab.grad_year, " +
                "       ab.photo_url, ab.industry, ab.intro, ab.quote, ab.motto, " +
                "       COUNT(h.id) AS award_count, " +
                "       (SELECT title FROM honors h2 " +
                "        WHERE h2.user_id = ab.id AND h2.status = 'approved' AND h2.screen_status = 'online' " +
                "        ORDER BY h2.awarded_at DESC LIMIT 1) AS highlight " +
                "FROM honors h JOIN alumni_base ab ON ab.id = h.user_id " +
                "WHERE h.status = 'approved' AND h.screen_status = 'online' " +
                "GROUP BY ab.id, ab.real_name, ab.college, ab.grad_year, " +
                "         ab.photo_url, ab.industry, ab.intro, ab.quote, ab.motto " +
                "ORDER BY award_count DESC, ab.grad_year DESC LIMIT ?", ALUMNI_TOP);

        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        int idx = 0;
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", "a" + (++idx));
            // 新增：让管理端 Tab 2「校友风采」每张卡可直接拿 alumniBaseId 编辑座右铭/头像
            m.put("alumniBaseId", ((Number) r.get("ab_id")).longValue());
            m.put("name", r.get("name"));
            m.put("college", r.get("college"));
            m.put("graduationYear", r.get("grad_year"));
            // V18：从 alumni_base 直接读取
            m.put("photo", strOrEmpty(r.get("photo_url")));
            m.put("industry", strOrEmpty(r.get("industry")));
            m.put("intro", strOrEmpty(r.get("intro")));
            m.put("quote", strOrEmpty(r.get("quote")));
            m.put("motto", strOrEmpty(r.get("motto")));
            m.put("highlight", r.get("highlight"));
            m.put("awardCount", ((Number) r.get("award_count")).intValue());
            // 渐变色按姓名 hash 取两段暖色（mock 中原本写死的）
            int hash = Math.abs(String.valueOf(r.get("name")).hashCode());
            String[] palette = {"#6b3a44","#7a4452","#54633f","#8a4a3a","#3f5570","#5c4a6e","#44615e","#70504a"};
            String a = palette[hash % palette.length];
            String b = palette[(hash / 7) % palette.length];
            m.put("colorA", a);
            m.put("colorB", b);
            out.add(m);
        }
        return out;
    }

    private static String strOrEmpty(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static long nz(Long v) { return v == null ? 0L : v; }

    /** 仅供 Service 层内部单测使用的辅助：将 LocalDate 转 year（保留扩展点） */
    @SuppressWarnings("unused")
    private static Integer yearOf(LocalDate d) { return d == null ? null : d.getYear(); }
}
