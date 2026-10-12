package com.alumni.archives.service;

import com.alumni.archives.common.BizException;
import com.alumni.archives.common.ErrorCode;
import com.alumni.archives.dto.ArchiveDtos.AggGroupRow;
import com.alumni.archives.dto.ArchiveDtos.AggPersonRow;
import com.alumni.archives.dto.ArchiveDtos.ArchiveDetail;
import com.alumni.archives.dto.ArchiveDtos.ArchiveItem;
import com.alumni.archives.dto.ArchiveDtos.AttachmentItem;
import com.alumni.archives.dto.ArchiveDtos.CreateRequest;
import com.alumni.archives.dto.ArchiveDtos.PageResult;
import com.alumni.archives.dto.ArchiveDtos.UpdateRequest;
import com.alumni.archives.entity.Archive;
import com.alumni.archives.repository.ArchiveRepository;
import com.alumni.common.pinyin.PinyinUtils;
import com.alumni.common.security.AuthPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 档案核心业务（API 规范第四章）：
 * 状态机 draft → pending_first → pending_second → approved；驳回 → rejected（可 resubmit version+1）；
 * 任意状态 → deleting → deleted（archive_delete 审核通道）。
 * P1 三级审核（规范 2.1）：提交/重提先做导员覆盖检查，命中 → pending_counselor（reviews stage=counselor），
 * 未命中任何 active 导员范围 → 兜底 pending_first（stage=first），流程不卡死。
 *
 * 跨模块契约（共享库直写，不走 HTTP）：
 * - 提交/重提/删除申请：直写 reviews + review_logs（biz_type=archive / archive_delete）
 * - 附件关联：直写 attachments.ref_id/status/referenced_at（biz_type=archive）
 * - 正式提交成功：直写删除 drafts 行（API 规范 12.1，biz_type=archive）
 * - reviews 模块审核后直写 archives.status；驳回原因与删除结果由本模块读取时懒同步
 */
@Service
public class ArchiveService {

    /** 可编辑状态（PATCH 允许） */
    private static final Set<String> EDITABLE = Set.of("draft", "rejected");
    /** 附件业务归属 */
    private static final String BIZ_TYPE = "archive";
    /** 删除审核 biz_type */
    private static final String BIZ_DELETE = "archive_delete";
    /** 管理端筛选 status 白名单（防注入，非法值 400） */
    private static final Set<String> STATUS_FILTER = Set.of(
            "draft", "pending_counselor", "pending_first", "pending_second",
            "approved", "rejected", "deleting", "deleted");
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 导员覆盖检查：提交人（users⋈alumni_base）是否命中任一 active 导员的 counselor_scopes
     * （同学院 + 届别∪专业∪学号任一命中；major_id 经 dicts 与 alumni_base.major 文本对齐）。
     */
    private static final String COVERAGE_SQL = """
            SELECT COUNT(*) FROM users su
            JOIN alumni_base ab ON ab.id = su.alumni_base_id
            WHERE su.id = ?
              AND EXISTS (
                SELECT 1 FROM counselor_scopes cs
                JOIN users cu ON cu.id = cs.counselor_id AND cu.status = 'active'
                WHERE ab.college = (SELECT d.label FROM dicts d WHERE d.id = cs.college_id AND d.type = 'college')
                  AND (cs.grad_year = ab.grad_year
                       OR (cs.major_id IS NOT NULL
                           AND cs.major_id = (SELECT m.id FROM dicts m WHERE m.type = 'major' AND m.label = ab.major))
                       OR cs.student_id = ab.student_id))
            """;

    /**
     * 管理端数据范围（PRD M06-01）：导员锁 counselor_scopes（同学院 + 届别∪专业∪学号任一命中），
     * 院级锁本学院（本人 alumni_base.college）；校级/系统不限。表别名：ab=alumni_base。
     */
    private static final String SCOPE_MATCH = """
            EXISTS (
              SELECT 1 FROM counselor_scopes cs
              WHERE cs.counselor_id = ?
                AND ab.college = (SELECT d.label FROM dicts d WHERE d.id = cs.college_id AND d.type = 'college')
                AND (cs.grad_year = ab.grad_year
                     OR (cs.major_id IS NOT NULL
                         AND cs.major_id = (SELECT m.id FROM dicts m WHERE m.type = 'major' AND m.label = ab.major))
                     OR cs.student_id = ab.student_id))
            """;

    private final ArchiveRepository archiveRepository;
    private final JdbcTemplate jdbcTemplate;

    public ArchiveService(ArchiveRepository archiveRepository, JdbcTemplate jdbcTemplate) {
        this.archiveRepository = archiveRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 数据范围 SQL 片段（拼进 WHERE，ab=alumni_base）：返回追加的 SQL 与占位参数。
     * 院级管理员学院缺失时返回恒假条件（看不到任何数据，防越权）。
     */
    private String scopeWhere(AuthPrincipal user, List<Object> params) {
        if ("college_admin".equals(user.role())) {
            String college = jdbcTemplate.query(
                    "SELECT ab.college FROM users u JOIN alumni_base ab ON ab.id = u.alumni_base_id WHERE u.id = ?",
                    rs -> rs.next() ? rs.getString(1) : null, user.userId());
            if (college == null || college.isBlank()) {
                return " AND 1 = 0";
            }
            params.add(college);
            return " AND ab.college = ?";
        }
        if ("counselor".equals(user.role())) {
            params.add(user.userId());
            return " AND " + SCOPE_MATCH;
        }
        return "";
    }

    // ---------- 创建 ----------

    /** POST /archives：submit=true 进入审核流（有导员覆盖 → pending_counselor，无覆盖兜底 pending_first）并生成审核记录；false 存草稿 */
    @Transactional
    public ArchiveDetail create(CreateRequest req, AuthPrincipal user) {
        Archive archive = new Archive();
        archive.setUserId(user.userId());
        archive.setTitle(req.title());
        archive.setTitlePinyin(PinyinUtils.toFullPinyin(req.title()));
        archive.setTitlePinyinAbbr(PinyinUtils.toAbbr(req.title()));
        archive.setCategory(req.category());
        archive.setContent(req.content());
        archive.setRemark(req.remark());
        boolean submit = Boolean.TRUE.equals(req.submit());
        boolean counselorCovered = submit && coveredByCounselor(user.userId());
        archive.setStatus(!submit ? "draft" : counselorCovered ? "pending_counselor" : "pending_first");
        archiveRepository.save(archive);

        bindAttachments(archive.getId(), req.attachmentIds(), user.userId());
        if (submit) {
            createReview(BIZ_TYPE, archive.getId(), user.userId(), counselorCovered ? "counselor" : "first");
            // 正式提交成功：服务端草稿自动删除（API 规范 12.1，跨模块直写 drafts 表，幂等）
            jdbcTemplate.update("DELETE FROM drafts WHERE user_id = ? AND biz_type = ?", user.userId(), BIZ_TYPE);
        }
        return detail(archive.getId(), user);
    }

    // ---------- 我的列表 ----------

    /** GET /archives/my：本人记录（含状态+驳回原因），分页 */
    @Transactional
    public PageResult<ArchiveItem> my(AuthPrincipal user, int page, int pageSize) {
        checkPage(page, pageSize);
        var pageable = PageRequest.of(page - 1, pageSize);
        var records = archiveRepository.findByUserIdOrderByCreatedAtDescIdDesc(user.userId(), pageable);
        List<ArchiveItem> list = records.getContent().stream()
                .map(a -> {
                    syncReviewOutcome(a);
                    return toItem(a, null);
                })
                .toList();
        return new PageResult<>(list, records.getTotalElements(), page, pageSize);
    }

    // ---------- 管理端列表 ----------

    /** GET /archives：审核角色组合筛选（keyword 学号/姓名、college_id、grad_year、category、status、date 区间、user_id 按人）；
     *  数据范围（PRD M06-01）：院级锁本院、导员锁 counselor_scopes（与 /archives/agg 同口径） */
    @Transactional
    public PageResult<ArchiveItem> adminList(AuthPrincipal user, Map<String, String> filters,
                                             int page, int pageSize) {
        if (!user.isReviewer()) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
        checkPage(page, pageSize);

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        where.append(scopeWhere(user, params));

        Long userId = parseLong(filters.get("user_id"), "user_id");
        if (userId != null) {
            where.append(" AND a.user_id = ?");
            params.add(userId);
        }
        String keyword = trim(filters.get("keyword"));
        if (keyword != null) {
            // 拼音容错（规范 3.3）：学号/姓名/姓名拼音列 + 标题/标题拼音列（拼音列 NULL 兼容，LIKE 天然不匹配 NULL）
            where.append(" AND (u.student_id LIKE ? OR ab.real_name LIKE ?"
                    + " OR ab.real_name_pinyin LIKE ? OR ab.real_name_pinyin_abbr LIKE ?"
                    + " OR a.title LIKE ? OR a.title_pinyin LIKE ? OR a.title_pinyin_abbr LIKE ?)");
            for (int i = 0; i < 7; i++) {
                params.add("%" + keyword + "%");
            }
        }
        Long collegeId = parseLong(filters.get("college_id"), "college_id");
        if (collegeId != null) {
            // dicts college 为文本对齐：college_id → dicts.label → alumni_base.college
            where.append(" AND ab.college = (SELECT d.label FROM dicts d WHERE d.id = ? AND d.type = 'college')");
            params.add(collegeId);
        }
        Long gradYear = parseLong(filters.get("grad_year"), "grad_year");
        if (gradYear != null) {
            where.append(" AND ab.grad_year = ?");
            params.add(gradYear.intValue());
        }
        String category = trim(filters.get("category"));
        if (category != null) {
            where.append(" AND a.category = ?");
            params.add(category);
        }
        String status = trim(filters.get("status"));
        if (status != null) {
            if (!STATUS_FILTER.contains(status)) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "status 取值不合法");
            }
            where.append(" AND a.status = ?");
            params.add(status);
        }
        Instant dateFrom = parseDate(filters.get("date_from"), "date_from");
        if (dateFrom != null) {
            where.append(" AND a.created_at >= ?");
            params.add(Timestamp.from(dateFrom));
        }
        Instant dateTo = parseDate(filters.get("date_to"), "date_to");
        if (dateTo != null) {
            where.append(" AND a.created_at < ?");
            params.add(Timestamp.from(dateTo.plusSeconds(86400)));
        }

        String from = " FROM archives a JOIN users u ON u.id = a.user_id"
                + " LEFT JOIN alumni_base ab ON ab.id = u.alumni_base_id"
                + " LEFT JOIN showcase s ON s.archive_id = a.id" + where;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + from, Long.class, params.toArray());

        String sql = "SELECT a.id, a.title, a.category, a.status, a.reject_reason, a.version, a.created_at,"
                + " u.student_id, ab.real_name, ab.college, ab.grad_year,"
                + " (s.archive_id IS NOT NULL) AS published" + from
                + " ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?";
        params.add(pageSize);
        params.add((page - 1) * pageSize);
        List<ArchiveItem> rawList = jdbcTemplate.query(sql, (rs, i) -> new ArchiveItem(
                rs.getLong("id"), rs.getString("title"), rs.getString("category"), rs.getString("status"),
                rs.getString("reject_reason"), rs.getInt("version"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("student_id"), rs.getString("real_name"), rs.getString("college"),
                rs.getObject("grad_year") != null ? rs.getInt("grad_year") : null,
                rs.getBoolean("published")), params.toArray());

        // 懒同步：先对删除/驳回相关行落库同步，再用同步后的实体字段重建 item（JOIN 出的 owner / published 字段保留）
        List<ArchiveItem> list = rawList.stream()
                .map(item -> {
                    if ("deleting".equals(item.status()) || "deleted".equals(item.status()) || "rejected".equals(item.status())) {
                        return archiveRepository.findById(item.id())
                                .map(a -> {
                                    syncReviewOutcome(a);
                                    return new ArchiveItem(a.getId(), a.getTitle(), a.getCategory(), a.getStatus(),
                                            a.getRejectReason(), a.getVersion(), a.getCreatedAt(),
                                            item.studentId(), item.realName(), item.college(), item.gradYear(),
                                            item.published());
                                })
                                .orElse(item);
                    }
                    return item;
                })
                .toList();
        return new PageResult<>(list, total != null ? total : 0, page, pageSize);
    }

    // ---------- 按人聚合（院系 → 专业 → 个人 层层递进） ----------

    /**
     * GET /archives/agg?level=college|major —— 组级聚合行。
     * level=college：按学院分组；level=major：须带 college（文本精确），按专业分组。
     * 空学院/专业归并为「（未填写）」；deleted 不计；数据范围同 adminList。
     */
    @Transactional(readOnly = true)
    public List<AggGroupRow> aggGroups(AuthPrincipal user, String level, String college) {
        requireReviewer(user);
        List<Object> params = new ArrayList<>();
        String scope = scopeWhere(user, params);

        String grpExpr;
        if ("college".equals(level)) {
            grpExpr = "IFNULL(NULLIF(ab.college, ''), '（未填写）')";
        } else if ("major".equals(level)) {
            if (college == null || college.isBlank()) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "level=major 须带 college 参数");
            }
            grpExpr = "IFNULL(NULLIF(ab.major, ''), '（未填写）')";
            params.add(college.trim());
            scope += " AND IFNULL(NULLIF(ab.college, ''), '（未填写）') = ?";
        } else {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "level 仅支持 college / major");
        }

        String sql = "SELECT " + grpExpr + " AS grp_key, COUNT(DISTINCT a.user_id) AS people, COUNT(*) AS archives"
                + " FROM archives a JOIN users u ON u.id = a.user_id"
                + " LEFT JOIN alumni_base ab ON ab.id = u.alumni_base_id"
                + " WHERE a.status <> 'deleted'" + scope
                + " GROUP BY grp_key ORDER BY archives DESC, grp_key";
        return jdbcTemplate.query(sql, (rs, i) -> new AggGroupRow(
                rs.getString("grp_key"), rs.getLong("people"), rs.getLong("archives")), params.toArray());
    }

    /**
     * GET /archives/agg?level=person —— 个人聚合行（须带 college + major 文本精确）。
     * 档案数按状态拆分；按最近提交倒序；数据范围同 adminList。
     */
    @Transactional(readOnly = true)
    public List<AggPersonRow> aggPersons(AuthPrincipal user, String college, String major) {
        requireReviewer(user);
        if (college == null || college.isBlank() || major == null || major.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "level=person 须带 college 与 major 参数");
        }
        List<Object> params = new ArrayList<>();
        String scope = scopeWhere(user, params);
        params.add(college.trim());
        params.add(major.trim());
        scope += " AND IFNULL(NULLIF(ab.college, ''), '（未填写）') = ?"
                + " AND IFNULL(NULLIF(ab.major, ''), '（未填写）') = ?";

        String sql = "SELECT a.user_id, u.student_id, ab.real_name, ab.college, ab.major, ab.grad_year,"
                + " COUNT(*) AS total,"
                + " SUM(a.status = 'approved') AS approved,"
                + " SUM(a.status IN ('pending_counselor', 'pending_first', 'pending_second', 'deleting')) AS pending,"
                + " SUM(a.status = 'rejected') AS rejected,"
                + " SUM(a.status = 'draft') AS draft,"
                + " MAX(a.created_at) AS latest_at"
                + " FROM archives a JOIN users u ON u.id = a.user_id"
                + " LEFT JOIN alumni_base ab ON ab.id = u.alumni_base_id"
                + " WHERE a.status <> 'deleted'" + scope
                + " GROUP BY a.user_id, u.student_id, ab.real_name, ab.college, ab.major, ab.grad_year"
                + " ORDER BY latest_at DESC";
        return jdbcTemplate.query(sql, (rs, i) -> new AggPersonRow(
                rs.getLong("user_id"), rs.getString("student_id"), rs.getString("real_name"),
                rs.getString("college"), rs.getString("major"),
                rs.getObject("grad_year") != null ? rs.getInt("grad_year") : null,
                rs.getLong("total"), rs.getLong("approved"), rs.getLong("pending"),
                rs.getLong("rejected"), rs.getLong("draft"),
                rs.getTimestamp("latest_at").toInstant()), params.toArray());
    }

    private void requireReviewer(AuthPrincipal user) {
        if (!user.isReviewer()) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
    }

    // ---------- 详情 ----------

    /** GET /archives/{id}：本人或审核角色 */
    @Transactional
    public ArchiveDetail detail(Long id, AuthPrincipal user) {
        Archive archive = findAndSync(id);
        if (!archive.getUserId().equals(user.userId()) && !user.isReviewer()) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
        List<AttachmentItem> attachments = jdbcTemplate.query(
                "SELECT id, name, url, mime, size FROM attachments"
                        + " WHERE biz_type = ? AND ref_id = ? AND status = 'referenced' ORDER BY id",
                (rs, i) -> new AttachmentItem(rs.getLong("id"), rs.getString("name"),
                        rs.getString("url"), rs.getString("mime"), rs.getLong("size")),
                BIZ_TYPE, id);
        return new ArchiveDetail(archive.getId(), archive.getTitle(), archive.getCategory(),
                archive.getContent(), archive.getRemark(), archive.getStatus(), archive.getRejectReason(),
                archive.getVersion(), archive.getCreatedAt(), archive.getUpdatedAt(), attachments,
                isPublished(id), hasScreenConsent(id));
    }

    // ---------- 修改 ----------

    /** PATCH /archives/{id}：仅本人 + draft/rejected 可改；attachment_ids 给了则整体换绑 */
    @Transactional
    public ArchiveDetail update(Long id, UpdateRequest req, AuthPrincipal user) {
        Archive archive = mustOwn(id, user);
        if (!EDITABLE.contains(archive.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE, "当前状态不可修改（仅 draft / rejected 可编辑）");
        }
        if (req.title() != null) {
            if (req.title().isBlank() || req.title().length() > 128) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "title 不能为空且最长 128 字符");
            }
            archive.setTitle(req.title());
            archive.setTitlePinyin(PinyinUtils.toFullPinyin(req.title()));
            archive.setTitlePinyinAbbr(PinyinUtils.toAbbr(req.title()));
        }
        if (req.category() != null) {
            archive.setCategory(req.category());
        }
        if (req.content() != null) {
            archive.setContent(req.content());
        }
        if (req.remark() != null) {
            archive.setRemark(req.remark());
        }
        archiveRepository.save(archive);

        if (req.attachmentIds() != null) {
            // 释放不在新列表中的原关联，再绑定新列表
            jdbcTemplate.update("UPDATE attachments SET ref_id = NULL, status = 'unreferenced', referenced_at = NULL"
                            + " WHERE biz_type = ? AND ref_id = ? AND status = 'referenced'"
                            + (req.attachmentIds().isEmpty() ? "" : " AND id NOT IN (" + placeholders(req.attachmentIds().size()) + ")"),
                    concat(BIZ_TYPE, id, req.attachmentIds().toArray()));
            bindAttachments(id, req.attachmentIds(), user.userId());
        }
        return detail(id, user);
    }

    // ---------- 驳回重提 ----------

    /** POST /archives/{id}/resubmit：仅本人 + rejected；version+1 并回审核流（P1 起有导员覆盖 → pending_counselor，无覆盖 → pending_first） */
    @Transactional
    public ArchiveDetail resubmit(Long id, AuthPrincipal user) {
        Archive archive = mustOwn(id, user);
        if (!"rejected".equals(archive.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE, "仅 rejected 状态可重提");
        }
        boolean counselorCovered = coveredByCounselor(user.userId());
        archive.setVersion(archive.getVersion() + 1);
        archive.setStatus(counselorCovered ? "pending_counselor" : "pending_first");
        archive.setRejectReason(null);
        archiveRepository.save(archive);
        createReview(BIZ_TYPE, archive.getId(), user.userId(), counselorCovered ? "counselor" : "first");
        return detail(id, user);
    }

    // ---------- 删除申请 ----------

    /** POST /archives/{id}/delete-request：本人发起删除审核（非立即删除）；deleting/deleted 重复发起 409 */
    @Transactional
    public ArchiveDetail deleteRequest(Long id, AuthPrincipal user) {
        Archive archive = mustOwn(id, user);
        if ("deleting".equals(archive.getStatus()) || "deleted".equals(archive.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE, "删除审核进行中或已删除，不可重复发起");
        }
        archive.setPrevStatus(archive.getStatus());
        archive.setStatus("deleting");
        archiveRepository.save(archive);
        createReview(BIZ_DELETE, archive.getId(), user.userId(), "first");
        return detail(id, user);
    }

    // ---------- 内部：审核结果懒同步 ----------

    /**
     * reviews 模块审核后只直写 archives.status，本模块读取时补齐：
     * - rejected：回显最近一次驳回原因
     * - deleting + 删除审核被驳回：恢复 prev_status 并回显原因
     * - deleted（删除审核通过）：释放关联附件为 unreferenced（幂等）
     */
    private void syncReviewOutcome(Archive archive) {
        switch (archive.getStatus()) {
            case "rejected" -> {
                Map<String, Object> review = latestReview(BIZ_TYPE, archive.getId());
                if (review != null && "rejected".equals(review.get("status"))
                        && !Objects.equals(archive.getRejectReason(), review.get("reason"))) {
                    archive.setRejectReason((String) review.get("reason"));
                    archiveRepository.save(archive);
                }
            }
            case "deleting" -> {
                Map<String, Object> review = latestReview(BIZ_DELETE, archive.getId());
                if (review != null && "rejected".equals(review.get("status"))) {
                    archive.setStatus(archive.getPrevStatus() != null ? archive.getPrevStatus() : "draft");
                    archive.setRejectReason((String) review.get("reason"));
                    archive.setPrevStatus(null);
                    archiveRepository.save(archive);
                }
            }
            case "deleted" -> jdbcTemplate.update(
                    "UPDATE attachments SET ref_id = NULL, status = 'unreferenced', referenced_at = NULL"
                            + " WHERE biz_type = ? AND ref_id = ? AND status = 'referenced'",
                    BIZ_TYPE, archive.getId());
            default -> { }
        }
    }

    /** 查某档案某 biz_type 的最新审核记录（id 最大即最新） */
    private Map<String, Object> latestReview(String bizType, Long bizId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status, reason FROM reviews WHERE biz_type = ? AND biz_id = ? ORDER BY id DESC LIMIT 1",
                bizType, bizId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ---------- 内部：大屏上架 / 授权标记（跨模块共享库直查） ----------

    /** 指定档案是否已上架大屏（showcase 存在记录即上架） */
    private boolean isPublished(Long archiveId) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM showcase WHERE archive_id = ?", Integer.class, archiveId);
        return cnt != null && cnt > 0;
    }

    /** 指定档案是否存在有效大屏授权（consents scope=screen） */
    private boolean hasScreenConsent(Long archiveId) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consents WHERE archive_id = ? AND scope = 'screen'", Integer.class, archiveId);
        return cnt != null && cnt > 0;
    }

    // ---------- 内部：reviews 直写 ----------

    /** 生成审核记录（stage 由调用方按导员覆盖检查结果给定, status=pending）+ create 日志（operator NULL=系统动作） */
    private void createReview(String bizType, Long bizId, Long submitterId, String stage) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO reviews (biz_type, biz_id, submitter_id, stage, status) VALUES (?, ?, ?, ?, 'pending')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, bizType);
            ps.setLong(2, bizId);
            ps.setLong(3, submitterId);
            ps.setString(4, stage);
            return ps;
        }, keyHolder);
        jdbcTemplate.update("INSERT INTO review_logs (review_id, operator_id, action, reason) VALUES (?, NULL, 'create', NULL)",
                Objects.requireNonNull(keyHolder.getKey()).longValue());
    }

    /** 无导员覆盖兜底：提交人未命中任何 active 导员管辖范围时返回 false，审核流自动跳过 counselor 环节 */
    private boolean coveredByCounselor(Long userId) {
        Integer cnt = jdbcTemplate.queryForObject(COVERAGE_SQL, Integer.class, userId);
        return cnt != null && cnt > 0;
    }

    // ---------- 内部：附件直写 ----------

    /** 绑定附件：存在 + 本人上传 + biz_type=archive + 未被其他业务引用；直写 ref_id/status/referenced_at */
    private void bindAttachments(Long archiveId, List<Long> attachmentIds, Long userId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        for (Long attachmentId : attachmentIds) {
            Map<String, Object> row;
            try {
                row = jdbcTemplate.queryForMap(
                        "SELECT uploader_id, biz_type, status, ref_id FROM attachments WHERE id = ?", attachmentId);
            } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "附件不存在：" + attachmentId);
            }
            if (!Objects.equals(((Number) row.get("uploader_id")).longValue(), userId)) {
                throw new BizException(ErrorCode.FORBIDDEN, "附件非本人上传：" + attachmentId);
            }
            if (!BIZ_TYPE.equals(row.get("biz_type"))) {
                throw new BizException(ErrorCode.INVALID_PARAMETER,
                        "附件 biz_type 非 archive，不可关联：" + attachmentId);
            }
            Object refId = row.get("ref_id");
            if ("referenced".equals(row.get("status")) && refId != null
                    && !Objects.equals(((Number) refId).longValue(), archiveId)) {
                throw new BizException(ErrorCode.INVALID_STATE, "附件已被其他业务引用：" + attachmentId);
            }
            jdbcTemplate.update(
                    "UPDATE attachments SET ref_id = ?, status = 'referenced', referenced_at = UTC_TIMESTAMP(6)"
                            + " WHERE id = ? AND status = 'unreferenced'",
                    archiveId, attachmentId);
        }
    }

    // ---------- 内部：通用 ----------

    private Archive mustOwn(Long id, AuthPrincipal user) {
        Archive archive = archiveRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "档案不存在"));
        if (!archive.getUserId().equals(user.userId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
        return archive;
    }

    private Archive findAndSync(Long id) {
        Archive archive = archiveRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "档案不存在"));
        syncReviewOutcome(archive);
        return archive;
    }

    private ArchiveItem toItem(Archive a, Void unused) {
        return new ArchiveItem(a.getId(), a.getTitle(), a.getCategory(), a.getStatus(),
                a.getRejectReason(), a.getVersion(), a.getCreatedAt(), null, null, null, null,
                isPublished(a.getId()));
    }

    private void checkPage(int page, int pageSize) {
        if (page < 1) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page 必须从 1 开始");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page_size 必须在 1~" + MAX_PAGE_SIZE + " 之间");
        }
    }

    private Long parseLong(String raw, String name) {
        String v = trim(raw);
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, name + " 必须为数字");
        }
    }

    private Instant parseDate(String raw, String name) {
        String v = trim(raw);
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(v).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, name + " 格式错误（YYYY-MM-DD）");
        }
    }

    private String trim(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private String placeholders(int n) {
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }

    private Object[] concat(Object head1, Object head2, Object[] tail) {
        Object[] result = new Object[tail.length + 2];
        result[0] = head1;
        result[1] = head2;
        System.arraycopy(tail, 0, result, 2, tail.length);
        return result;
    }
}
