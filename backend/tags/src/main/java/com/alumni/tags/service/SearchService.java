package com.alumni.tags.service;

import com.alumni.common.pinyin.PinyinUtils;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.tags.common.BizException;
import com.alumni.tags.common.ErrorCode;
import com.alumni.tags.dto.SearchDtos.SearchItem;
import com.alumni.tags.dto.SearchDtos.SearchResult;
import com.alumni.tags.dto.SearchDtos.SuggestItem;
import com.alumni.tags.dto.SearchDtos.SuggestResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 增强检索业务实现（Service 层，P1 [M13]，纯聚合检索不建表）。
 *
 * <p>跨模块数据一律 JdbcTemplate 直读共享库（honors/archives/users/alumni_base/dicts/tag_bindings），
 * 全部参数化绑定，杜绝拼接注入。</p>
 *
 * <p>拼音容错（文档 3.3）：keyword 同时匹配原列与预生成拼音列（全拼 + 首字母）；
 * 拼音列为 NULL 的存量行仅靠原列 LIKE 命中，自然兼容。</p>
 *
 * <p>数据范围红线：student/alumni 仅返回 approved 数据；
 * college_admin/school_admin/counselor/system_admin 传 include_all=true 可含在审数据（pending_*）。</p>
 */
@Service
public class SearchService {

    /** 检索类型取值（与标签域一一对应） */
    public static final Set<String> TYPES = Set.of("honor", "archive", "alumni");
    /** include_all=true 生效角色（审核 / 管理侧） */
    private static final Set<String> INCLUDE_ALL_ROLES =
            Set.of("college_admin", "school_admin", "counselor", "system_admin");
    /** 姓名联想可见角色（脱敏规则：student/alumni/counselor 不出姓名联想） */
    private static final Set<String> NAME_SUGGEST_ROLES =
            Set.of("college_admin", "school_admin", "system_admin");

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_SUGGEST_LIMIT = 10;
    private static final int MAX_SUGGEST_LIMIT = 20;

    private final JdbcTemplate jdbcTemplate;

    public SearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /* ==================== 聚合检索 ==================== */

    /**
     * GET /search：keyword/types/college_id/grad_year/tag_ids 组合过滤，
     * 三型结果统一 {type,id,title,owner,college,grad_year,tags,status,created_at}，
     * 按 created_at 倒序内存合并分页（各类型分别 count 求 total）。
     */
    public SearchResult search(AuthPrincipal viewer, String keyword, String types, Long collegeId,
                               Integer gradYear, String tagIds, boolean includeAll, int page, int pageSize) {
        requireLogin(viewer);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        List<String> typeList = parseTypes(types);
        List<Long> tagIdList = parseTagIds(tagIds);
        if (collegeId != null && collegeId <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "college_id 必须为正整数");
        }
        if (gradYear != null) {
            requireValidGradYear(gradYear);
        }
        // 红线：非审核/管理角色传 include_all 一律忽略，仍只见 approved
        boolean approvedOnly = !(includeAll && INCLUDE_ALL_ROLES.contains(viewer.role()));
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();

        long total = 0;
        List<SearchItem> merged = new ArrayList<>();
        for (String type : typeList) {
            TypeQuery query = buildTypeQuery(type, kw, collegeId, gradYear, tagIdList, approvedOnly);
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) " + query.fromWhere, Long.class, query.params.toArray());
            total += count != null ? count : 0;
            merged.addAll(jdbcTemplate.query(query.select + query.fromWhere + query.orderBy,
                    (rs, rowNum) -> mapRow(type, rs), query.params.toArray()));
        }
        merged.sort(Comparator.comparing(SearchItem::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        int offset = (safePage - 1) * safeSize;
        List<SearchItem> pageList = offset >= merged.size() ? List.of()
                : merged.subList(offset, Math.min(offset + safeSize, merged.size()));
        return new SearchResult(pageList, total, safePage, safeSize);
    }

    /* ==================== 关键词联想 ==================== */

    /**
     * GET /search/suggest：联想词来源 honors.title / tags.name / dicts(college).label
     * （匹配同样支持拼音列；tags 无拼音列仅原词匹配）；
     * 校友姓名仅审核角色（college_admin/school_admin/system_admin）出现在联想中。
     */
    public SuggestResult suggest(AuthPrincipal viewer, String keyword, Integer limit) {
        requireLogin(viewer);
        if (keyword == null || keyword.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "keyword 不能为空");
        }
        int safeLimit = limit == null ? DEFAULT_SUGGEST_LIMIT
                : Math.min(Math.max(limit, 1), MAX_SUGGEST_LIMIT);
        String kw = keyword.trim();
        String likeRaw = like(kw);
        String likeFull = like(PinyinUtils.toFullPinyin(kw));
        String likeAbbr = like(PinyinUtils.toAbbr(kw));

        List<SuggestItem> items = new ArrayList<>();
        // ① 荣誉标题（数据范围同检索红线：student/alumni 仅 approved）
        boolean honorApprovedOnly = !INCLUDE_ALL_ROLES.contains(viewer.role());
        String honorSql = "SELECT h.title AS text, COUNT(*) AS cnt FROM honors h"
                + " WHERE (h.title LIKE ? OR h.title_pinyin LIKE ? OR h.title_pinyin_abbr LIKE ?)"
                + (honorApprovedOnly ? " AND h.status = 'approved'" : "")
                + " GROUP BY h.title ORDER BY cnt DESC, h.title ASC LIMIT ?";
        items.addAll(jdbcTemplate.query(honorSql, this::mapSuggestHonor,
                likeRaw, likeFull, likeAbbr, safeLimit));
        // ② 标签名（tags 表无拼音列，仅原词模糊）
        String tagSql = "SELECT t.name AS text, COUNT(*) AS cnt FROM tags t"
                + " WHERE t.name LIKE ? GROUP BY t.name ORDER BY cnt DESC, t.name ASC LIMIT ?";
        items.addAll(jdbcTemplate.query(tagSql, this::mapSuggestTag, likeRaw, safeLimit));
        // ③ 学院名（dicts type='college'，含拼音列）
        String collegeSql = "SELECT d.label AS text, COUNT(*) AS cnt FROM dicts d"
                + " WHERE d.type = 'college'"
                + " AND (d.label LIKE ? OR d.label_pinyin LIKE ? OR d.label_pinyin_abbr LIKE ?)"
                + " GROUP BY d.label ORDER BY cnt DESC, d.label ASC LIMIT ?";
        items.addAll(jdbcTemplate.query(collegeSql, this::mapSuggestCollege,
                likeRaw, likeFull, likeAbbr, safeLimit));
        // ④ 校友姓名（仅审核角色；脱敏规则：student/alumni/counselor 不出姓名联想）
        if (NAME_SUGGEST_ROLES.contains(viewer.role())) {
            String alumniSql = "SELECT ab.real_name AS text, COUNT(*) AS cnt"
                    + " FROM alumni_base ab JOIN users u ON u.alumni_base_id = ab.id"
                    + " WHERE u.role IN ('student','alumni')"
                    + " AND (ab.real_name LIKE ? OR ab.real_name_pinyin LIKE ? OR ab.real_name_pinyin_abbr LIKE ?)"
                    + " GROUP BY ab.real_name ORDER BY cnt DESC, ab.real_name ASC LIMIT ?";
            items.addAll(jdbcTemplate.query(alumniSql, this::mapSuggestAlumni,
                    likeRaw, likeFull, likeAbbr, safeLimit));
        }
        return new SuggestResult(items.stream().limit(safeLimit).toList());
    }

    /* ---------------- 检索 SQL 组装 ---------------- */

    /** 单类型的查询片段（select / fromWhere / orderBy / 绑定参数，count 与明细共用 fromWhere + params） */
    private record TypeQuery(String select, String fromWhere, String orderBy, List<Object> params) {
    }

    private TypeQuery buildTypeQuery(String type, String kw, Long collegeId, Integer gradYear,
                                     List<Long> tagIds, boolean approvedOnly) {
        String tableAlias;
        String select;
        String fromWhere;
        String idColumn;
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        switch (type) {
            case "honor" -> {
                tableAlias = "h";
                idColumn = "h.id";
                select = honorArchiveSelect("h", "honor");
                where.append(" WHERE 1 = 1\n");
                if (approvedOnly) {
                    where.append(" AND h.status = 'approved'\n");
                }
                if (kw != null) {
                    where.append(" AND (h.title LIKE ? OR h.title_pinyin LIKE ? OR h.title_pinyin_abbr LIKE ?)\n");
                    addKeywordParams(params, kw);
                }
            }
            case "archive" -> {
                tableAlias = "a";
                idColumn = "a.id";
                select = honorArchiveSelect("a", "archive");
                where.append(" WHERE 1 = 1\n");
                if (approvedOnly) {
                    where.append(" AND a.status = 'approved'\n");
                }
                if (kw != null) {
                    where.append(" AND (a.title LIKE ? OR a.title_pinyin LIKE ? OR a.title_pinyin_abbr LIKE ?)\n");
                    addKeywordParams(params, kw);
                }
            }
            default -> { // alumni
                tableAlias = "u";
                idColumn = "u.id";
                select = "SELECT u.id AS id, ab.real_name AS title, ab.real_name AS owner,"
                        + " ab.college AS college, ab.grad_year AS grad_year, u.status AS status,"
                        + " u.created_at AS created_at,"
                        + " (SELECT GROUP_CONCAT(t.name ORDER BY t.sort, t.id SEPARATOR ',')"
                        + "   FROM tag_bindings tb JOIN tags t ON t.id = tb.tag_id"
                        + "  WHERE tb.biz_type = 'alumni' AND tb.biz_id = u.id) AS tag_names\n";
                where.append(" WHERE u.role IN ('student','alumni')\n");
                if (approvedOnly) {
                    where.append(" AND u.status = 'active'\n");
                }
                if (kw != null) {
                    where.append(" AND (ab.real_name LIKE ? OR ab.real_name_pinyin LIKE ?"
                            + " OR ab.real_name_pinyin_abbr LIKE ?)\n");
                    addKeywordParams(params, kw);
                }
            }
        }
        if (collegeId != null) {
            // 学院筛选：dicts.id → label 文本对齐 alumni_base.college（directory/stats 先例）
            where.append(" AND ab.college = (SELECT label FROM dicts WHERE id = ? AND type = 'college')\n");
            params.add(collegeId);
        }
        if (gradYear != null) {
            where.append(" AND ab.grad_year = ?\n");
            params.add(gradYear);
        }
        if (!tagIds.isEmpty()) {
            String placeholders = String.join(", ", java.util.Collections.nCopies(tagIds.size(), "?"));
            where.append(" AND EXISTS (SELECT 1 FROM tag_bindings tbx"
                    + " WHERE tbx.biz_type = '").append(type).append("'")
                    .append(" AND tbx.biz_id = ").append(idColumn)
                    .append(" AND tbx.tag_id IN (").append(placeholders).append("))\n");
            params.addAll(tagIds);
        }

        String from = switch (tableAlias) {
            case "h" -> "FROM honors h JOIN users u ON u.id = h.user_id"
                    + " JOIN alumni_base ab ON ab.id = u.alumni_base_id\n";
            case "a" -> "FROM archives a JOIN users u ON u.id = a.user_id"
                    + " JOIN alumni_base ab ON ab.id = u.alumni_base_id\n";
            default -> "FROM users u JOIN alumni_base ab ON ab.id = u.alumni_base_id\n";
        };
        String orderBy = " ORDER BY " + tableAlias + ".created_at DESC, " + idColumn + " DESC";
        return new TypeQuery(select, from + where, orderBy, params);
    }

    private static String honorArchiveSelect(String alias, String bizType) {
        return "SELECT " + alias + ".id AS id, " + alias + ".title AS title, ab.real_name AS owner,"
                + " ab.college AS college, ab.grad_year AS grad_year, " + alias + ".status AS status,"
                + " " + alias + ".created_at AS created_at,"
                + " (SELECT GROUP_CONCAT(t.name ORDER BY t.sort, t.id SEPARATOR ',')"
                + "   FROM tag_bindings tb JOIN tags t ON t.id = tb.tag_id"
                + "  WHERE tb.biz_type = '" + bizType + "' AND tb.biz_id = " + alias + ".id) AS tag_names\n";
    }

    /** keyword 三形态绑定：原词 / 全拼 / 首字母（拼音列 NULL 的存量行仅靠原列 LIKE 命中） */
    private static void addKeywordParams(List<Object> params, String kw) {
        params.add(like(kw));
        params.add(like(PinyinUtils.toFullPinyin(kw)));
        params.add(like(PinyinUtils.toAbbr(kw)));
    }

    /* ---------------- 行映射 ---------------- */

    private SearchItem mapRow(String type, ResultSet rs) throws SQLException {
        String tagNames = rs.getString("tag_names");
        List<String> tags = tagNames == null || tagNames.isEmpty() ? List.of()
                : Arrays.asList(tagNames.split(","));
        int gradYear = rs.getInt("grad_year");
        Integer gradYearBoxed = rs.wasNull() ? null : gradYear;
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new SearchItem(type, rs.getLong("id"), rs.getString("title"), rs.getString("owner"),
                rs.getString("college"), gradYearBoxed, tags,
                rs.getString("status"), createdAt == null ? null : createdAt.toInstant());
    }

    private SuggestItem mapSuggestHonor(ResultSet rs, int rowNum) throws SQLException {
        return new SuggestItem(rs.getString("text"), "honor", rs.getLong("cnt"));
    }

    private SuggestItem mapSuggestTag(ResultSet rs, int rowNum) throws SQLException {
        return new SuggestItem(rs.getString("text"), "tag", rs.getLong("cnt"));
    }

    private SuggestItem mapSuggestCollege(ResultSet rs, int rowNum) throws SQLException {
        return new SuggestItem(rs.getString("text"), "college", rs.getLong("cnt"));
    }

    private SuggestItem mapSuggestAlumni(ResultSet rs, int rowNum) throws SQLException {
        return new SuggestItem(rs.getString("text"), "alumni", rs.getLong("cnt"));
    }

    /* ---------------- 参数解析与校验 ---------------- */

    private List<String> parseTypes(String types) {
        if (types == null || types.isBlank()) {
            return List.of("honor", "archive", "alumni");
        }
        List<String> parsed = new ArrayList<>();
        for (String raw : types.split(",")) {
            String type = raw.trim();
            if (!TYPES.contains(type)) {
                throw new BizException(ErrorCode.INVALID_PARAMETER,
                        "types 仅允许 honor/archive/alumni 逗号组合：" + type);
            }
            parsed.add(type);
        }
        return List.copyOf(new LinkedHashSet<>(parsed));
    }

    private List<Long> parseTagIds(String tagIds) {
        if (tagIds == null || tagIds.isBlank()) {
            return List.of();
        }
        List<Long> parsed = new ArrayList<>();
        for (String raw : tagIds.split(",")) {
            try {
                parsed.add(Long.parseLong(raw.trim()));
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "tag_ids 须为逗号分隔的整数：" + raw.trim());
            }
        }
        return List.copyOf(new LinkedHashSet<>(parsed));
    }

    /** 毕业年份须在 2000 ~ 当前年+4（对齐 directory / dicts grad_year 口径） */
    private void requireValidGradYear(int gradYear) {
        int max = Year.now().getValue() + 4;
        if (gradYear < 2000 || gradYear > max) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "grad_year 须在 2000~" + max + " 之间");
        }
    }

    private void requireLogin(AuthPrincipal viewer) {
        if (viewer == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或 Token 已过期");
        }
    }

    private static String like(String raw) {
        return "%" + raw.trim() + "%";
    }
}
