package com.alumni.directory.service;

import com.alumni.directory.common.BizException;
import com.alumni.directory.common.ErrorCode;
import com.alumni.directory.dto.DirectoryDtos.DirectoryDetail;
import com.alumni.directory.dto.DirectoryDtos.DirectoryItem;
import com.alumni.directory.dto.DirectoryDtos.PageResult;
import com.alumni.directory.dto.DirectoryDtos.PublicArchive;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 校友档案索引业务实现（Service 层，扩展模块 [M15]，纯聚合检索不建表）。
 *
 * <p>隐私红线（与 /screen/feed 同一口径，单条 SQL 天然过滤）：
 * 仅收录存在 <b>approved 档案 + showcase 上架行 + consents(screen) 有效授权</b> 的校友；
 * 未授权资料绝不出现在公开接口。出参仅含 学号/姓名/学院/届次/公开档案/已审荣誉，
 * 绝不返回手机号、身份证号等字段。</p>
 *
 * <p>聚合口径：users ⋈ alumni_base（学籍）+ archives/showcase/consents（公开性）
 * + honors（approved 荣誉）+ outstanding_alumni（published 精选位的展示增强）。</p>
 */
@Service
public class AlumniDirectoryService {

    /** 单页上限（文档 4.1 ②：默认 20 / 最大 100） */
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 荣誉级别英文 → 展示中文（与字典/前端展示对齐；未映射级别原样返回） */
    private static final Map<String, String> LEVEL_LABELS = Map.of(
            "national", "国家级", "provincial", "省级", "city", "市级", "school", "校级");

    /**
     * 公开口径 FROM 子句（红线三表 JOIN，与 ShowcaseRepository.feedAll 完全一致）：
     * showcase ⋈ archives(status='approved') ⋈ consents(scope='screen')。
     */
    private static final String PUBLIC_FROM = """
            FROM users u
            JOIN alumni_base ab ON ab.id = u.alumni_base_id
            JOIN archives a ON a.user_id = u.id AND a.status = 'approved'
            JOIN showcase s ON s.archive_id = a.id
            JOIN consents c ON c.archive_id = a.id AND c.scope = 'screen'
            """;

    private final EntityManager em;
    private final ObjectMapper objectMapper;

    public AlumniDirectoryService(EntityManager em, ObjectMapper objectMapper) {
        this.em = em;
        this.objectMapper = objectMapper;
    }

    /* ==================== 列表检索 ==================== */

    /**
     * 公开名录检索：keyword/name/grad_year/college_id/student_id 组合过滤 + 分页。
     * keyword 综合命中：姓名 / 学号 / 学院 / 当前职位 / 荣誉标题（五类）。
     */
    @Transactional(readOnly = true)
    public PageResult search(String keyword, String name, Integer gradYear, Long collegeId,
                             String studentId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            // 文档 5.3：单页上限 100，page_size 超限返回 400 + INVALID_PARAMETER
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "page_size 须在 1~" + MAX_PAGE_SIZE + " 之间");
        }
        if (gradYear != null) {
            requireValidGradYear(gradYear);
        }
        if (collegeId != null && collegeId <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "college_id 必须为正整数");
        }

        // 动态条件（全部参数化，杜绝拼接注入；每条以换行结尾，避免与后续 SQL 粘连）
        StringBuilder where = new StringBuilder(" WHERE 1 = 1\n");
        Map<String, Object> params = new LinkedHashMap<>();
        if (hasText(name)) {
            where.append(" AND ab.real_name LIKE :name\n");
            params.put("name", like(name));
        }
        if (gradYear != null) {
            where.append(" AND ab.grad_year = :gradYear\n");
            params.put("gradYear", gradYear);
        }
        if (collegeId != null) {
            // 学院筛选：dicts.id → label 文本对齐 alumni_base.college（stats/showcase 先例）
            where.append(" AND ab.college = (SELECT label FROM dicts WHERE id = :collegeId AND type = 'college')\n");
            params.put("collegeId", collegeId);
        }
        if (hasText(studentId)) {
            where.append(" AND u.student_id LIKE :studentId\n");
            params.put("studentId", like(studentId));
        }
        if (hasText(keyword)) {
            where.append("""
                     AND (ab.real_name LIKE :kw
                       OR u.student_id LIKE :kw
                       OR ab.college LIKE :kw
                       OR EXISTS (SELECT 1 FROM honors h
                                   WHERE h.user_id = u.id AND h.status = 'approved' AND h.title LIKE :kw)
                       OR EXISTS (SELECT 1 FROM outstanding_alumni oax
                                   WHERE oax.user_id = u.id AND oax.status = 'published'
                                     AND (oax.current_title LIKE :kw OR oax.display_name LIKE :kw)))
                    """);
            params.put("kw", like(keyword));
        }
        String whereSql = where.toString();

        // COUNT（COUNT 与明细两条 SQL，避免 GROUP BY + COUNT 双重扫描）
        Query countQuery = em.createNativeQuery(
                "SELECT COUNT(DISTINCT u.id)\n" + PUBLIC_FROM + whereSql);
        bind(countQuery, params);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        // 明细（page 超界返回空 list，不报错）
        int offset = (safePage - 1) * pageSize;
        if (offset >= total) {
            return new PageResult(List.of(), total, safePage, pageSize);
        }

        String listSql = """
                SELECT u.student_id, ab.real_name, ab.college, ab.grad_year,
                       oa.display_name, oa.photo_url, oa.current_title, oa.current_city,
                       (SELECT h.title FROM honors h
                         WHERE h.user_id = u.id AND h.status = 'approved'
                         ORDER BY FIELD(h.level, 'national', 'provincial', 'city', 'school'), h.awarded_at DESC
                         LIMIT 1) AS top_honor,
                       COUNT(DISTINCT a.id) AS archive_count
                """ + PUBLIC_FROM
                + " LEFT JOIN outstanding_alumni oa ON oa.user_id = u.id AND oa.status = 'published'\n"
                + whereSql
                + " GROUP BY u.id, u.student_id, ab.real_name, ab.college, ab.grad_year,\n"
                + "          oa.display_name, oa.photo_url, oa.current_title, oa.current_city\n"
                + " ORDER BY ab.grad_year DESC, u.student_id ASC\n"
                + " LIMIT :__limit OFFSET :__offset";
        Query listQuery = em.createNativeQuery(listSql);
        bind(listQuery, params);
        listQuery.setParameter("__limit", pageSize);
        listQuery.setParameter("__offset", offset);

        List<DirectoryItem> list = new ArrayList<>();
        for (Object rowObj : listQuery.getResultList()) {
            Object[] row = (Object[]) rowObj;
            String displayName = (String) row[4];
            list.add(new DirectoryItem(
                    (String) row[0],
                    displayName != null ? displayName : (String) row[1],
                    (String) row[5],
                    (String) row[2],
                    row[3] == null ? null : ((Number) row[3]).intValue(),
                    joinCurrent((String) row[6], (String) row[7]),
                    (String) row[8],
                    ((Number) row[9]).longValue()));
        }
        return new PageResult(list, total, safePage, pageSize);
    }

    /* ==================== 详情（两模块共用弹窗） ==================== */

    /**
     * 校友公开档案详情：学号不存在或无任何公开档案 → 404 NOT_FOUND。
     * 精选校友（outstanding published）返回编辑的寄语/时间线/代表荣誉；
     * 非精选校友：timeline 由公开档案按年份聚合、awards 取 approved 荣誉、message 为 null。
     */
    @Transactional(readOnly = true)
    public DirectoryDetail detail(String studentId) {
        if (!hasText(studentId)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "student_id 不能为空");
        }

        // ① 基本信息 + 公开性校验（单行）
        Object[] row = (Object[]) em.createNativeQuery("""
                SELECT u.id, u.student_id, ab.real_name, ab.college, ab.grad_year,
                       oa.display_name, oa.photo_url, oa.message, oa.current_title, oa.current_city,
                       oa.timeline, oa.awards,
                       EXISTS (SELECT 1 FROM archives a
                                JOIN showcase s2 ON s2.archive_id = a.id
                                JOIN consents c2 ON c2.archive_id = a.id AND c2.scope = 'screen'
                                WHERE a.user_id = u.id AND a.status = 'approved') AS has_public
                FROM users u
                JOIN alumni_base ab ON ab.id = u.alumni_base_id
                LEFT JOIN outstanding_alumni oa ON oa.user_id = u.id AND oa.status = 'published'
                WHERE u.student_id = :studentId
                """).setParameter("studentId", studentId.trim())
                .getResultStream().findFirst().orElse(null);
        if (row == null || ((Number) row[12]).intValue() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "校友不存在或无公开档案");
        }

        long userId = ((Number) row[0]).longValue();
        String displayName = (String) row[5];
        String name = displayName != null ? displayName : (String) row[2];

        // ② 公开档案列表（published_at = showcase 上架时间）
        List<PublicArchive> publicArchives = new ArrayList<>();
        for (Object rowObj : em.createNativeQuery("""
                SELECT a.id, a.title, a.category, s.published_at
                FROM archives a
                JOIN showcase s ON s.archive_id = a.id
                JOIN consents c ON c.archive_id = a.id AND c.scope = 'screen'
                WHERE a.user_id = :userId AND a.status = 'approved'
                ORDER BY s.published_at DESC, a.id DESC
                """).setParameter("userId", userId).getResultList()) {
            Object[] ar = (Object[]) rowObj;
            publicArchives.add(new PublicArchive(((Number) ar[0]).longValue(), (String) ar[1],
                    (String) ar[2], toInstant(ar[3])));
        }

        // ③ approved 荣誉（按级别 → 授予时间倒序）
        List<Object[]> honorRows = em.createNativeQuery("""
                SELECT h.title, h.awarded_at, h.level
                FROM honors h
                WHERE h.user_id = :userId AND h.status = 'approved'
                ORDER BY FIELD(h.level, 'national', 'provincial', 'city', 'school'), h.awarded_at DESC
                """).setParameter("userId", userId).getResultStream()
                .map(r -> (Object[]) r).toList();

        // ④ timeline / awards：精选校友用编辑内容，非精选由公开数据聚合
        List<Map<String, String>> timeline = parseJsonArray((String) row[10]);
        List<Map<String, String>> awards = parseJsonArray((String) row[11]);
        if (timeline == null) {
            timeline = aggregateTimeline(publicArchives);
        }
        if (awards == null) {
            awards = new ArrayList<>();
            for (Object[] hr : honorRows) {
                awards.add(awardEntry((String) hr[0], hr[1], (String) hr[2]));
            }
        }

        return new DirectoryDetail((String) row[1], name, (String) row[6], (String) row[3],
                row[4] == null ? null : ((Number) row[4]).intValue(),
                joinCurrent((String) row[8], (String) row[9]),
                (String) row[7], timeline, awards, publicArchives);
    }

    /* ---------------- 私有工具 ---------------- */

    private static boolean hasText(String raw) {
        return raw != null && !raw.isBlank();
    }

    private static String like(String raw) {
        return "%" + raw.trim() + "%";
    }

    /** 毕业年份须在 2000 ~ 当前年+4（对齐 dicts grad_year 虚拟口径） */
    private void requireValidGradYear(int gradYear) {
        int max = Year.now().getValue() + 4;
        if (gradYear < 2000 || gradYear > max) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "grad_year 须在 2000~" + max + " 之间");
        }
    }

    /** current 拼装："职位 / 城市"（服务端拼好，与前端展示格式对齐；双空返回 null） */
    private static String joinCurrent(String title, String city) {
        if (title != null && city != null) {
            return title + " / " + city;
        }
        return title != null ? title : city;
    }

    private static void bind(Query query, Map<String, Object> params) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
    }

    private static Instant toInstant(Object raw) {
        return raw instanceof Timestamp ts ? ts.toInstant() : null;
    }

    /** JSON 列字符串 → List<Map>（空/非法返回 null，交由调用方走聚合兜底） */
    private List<Map<String, String>> parseJsonArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Map<String, String>>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    /** 非精选校友的 timeline 兜底：公开档案按上架年份聚合 [{year, event}] */
    private static List<Map<String, String>> aggregateTimeline(List<PublicArchive> archives) {
        Map<String, List<String>> byYear = new LinkedHashMap<>();
        for (PublicArchive archive : archives) {
            if (archive.publishedAt() == null) {
                continue;
            }
            String year = String.valueOf(archive.publishedAt().atZone(java.time.ZoneOffset.UTC).getYear());
            byYear.computeIfAbsent(year, k -> new ArrayList<>()).add(archive.title());
        }
        List<Map<String, String>> timeline = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : byYear.entrySet()) {
            timeline.add(Map.of("year", entry.getKey(), "event", String.join("、", entry.getValue())));
        }
        return timeline;
    }

    /** 荣誉行 → {title, year, level}（级别英文转中文展示） */
    private static Map<String, String> awardEntry(String title, Object awardedAt, String level) {
        String year = "";
        if (awardedAt instanceof java.sql.Date date) {
            year = String.valueOf(date.toLocalDate().getYear());
        } else if (awardedAt instanceof String raw && raw.length() >= 4) {
            year = raw.substring(0, 4);
        }
        return Map.of("title", title, "year", year, "level", LEVEL_LABELS.getOrDefault(level, level));
    }
}
