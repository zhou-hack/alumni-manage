package com.alumni.reviews.service;

import com.alumni.reviews.common.BizException;
import com.alumni.reviews.common.ErrorCode;
import com.alumni.reviews.entity.Review;
import com.alumni.reviews.entity.ReviewLog;
import com.alumni.reviews.repository.ArchiveRepository;
import com.alumni.reviews.repository.HonorRepository;
import com.alumni.reviews.repository.ReviewLogRepository;
import com.alumni.reviews.repository.ReviewRepository;
import com.alumni.common.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审核业务服务：审核状态机 + 角色-阶段分工 + 业务状态同步。
 *
 * <p>状态机（P0 规范第四章 / 第七章，P1 规范 2.1 三级审核扩展）：</p>
 * <pre>
 *   三级（archive / honor，P1 起新提交）：
 *     counselor 通过 → stage=first（业务 pending_first）
 *     first 通过 → stage=second（业务 pending_second）
 *     second 通过 → status=approved（业务 approved）
 *     任意阶段驳回 → status=rejected（业务 rejected）
 *   存量在途两级单（archive / honor / archive_delete）：first → second → approved，按当前 stage 推进
 *   单级（activate_manual）：first 通过 → approved（无业务表），仍 college_admin/school_admin 处理
 * </pre>
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    /** 合法状态过滤值 */
    private static final Set<String> STATUSES = Set.of("pending", "approved", "rejected");
    /** 审核角色集合（P1 起 counselor 参与审核流转） */
    private static final Set<String> REVIEW_ROLES = Set.of("college_admin", "school_admin", "counselor", "system_admin");
    /** 合法 stage 过滤值（P1 三级：counselor/first/second） */
    private static final Set<String> STAGES = Set.of("counselor", "first", "second");
    /** 单级审核业务类型（仅 first 一级，通过即完成） */
    public static final String BIZ_ACTIVATE_MANUAL = "activate_manual";
    /** 分页上限（规范 1.4 节 page_size 最大 100） */
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 导员管辖范围命中条件（counselor_scopes：同学院 + 届别∪专业∪学号任一命中；
     * major_id 经 dicts 与 alumni_base.major 文本对齐）。表别名：ab=alumni_base。
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

    /** 导员待办列表：stage=counselor 且提交人在其管辖范围内（越范围不可见） */
    private static final String COUNSELOR_LIST_FROM = """
             FROM reviews r
             JOIN users su ON su.id = r.submitter_id
             JOIN alumni_base ab ON ab.id = su.alumni_base_id
             WHERE r.stage = 'counselor' AND r.status = ?
            """ + " AND " + SCOPE_MATCH;

    /** 导员越权校验：指定提交人是否在其管辖范围内 */
    private static final String SCOPE_CHECK_SQL = """
            SELECT COUNT(*) FROM users su
            JOIN alumni_base ab ON ab.id = su.alumni_base_id
            WHERE su.id = ?
            """ + " AND " + SCOPE_MATCH;

    private final ReviewRepository reviewRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final ArchiveRepository archiveRepository;
    private final HonorRepository honorRepository;
    private final JdbcTemplate jdbcTemplate;

    public ReviewService(ReviewRepository reviewRepository,
                         ReviewLogRepository reviewLogRepository,
                         ArchiveRepository archiveRepository,
                         HonorRepository honorRepository,
                         JdbcTemplate jdbcTemplate) {
        this.reviewRepository = reviewRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.archiveRepository = archiveRepository;
        this.honorRepository = honorRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 待办列表：导员强制 counselor 阶段 + 管辖范围过滤；学院/校级默认本角色阶段，
     * 可选 stage 筛选（counselor/first/second，非法 400）；可选 biz_type 过滤；status 默认 pending
     */
    @Transactional(readOnly = true)
    public Map<String, Object> listFor(AuthPrincipal user, String bizType, String status, String stage,
                                       int page, int pageSize) {
        requireReviewer(user);
        String effectiveStatus = (status == null || status.isBlank()) ? "pending" : status;
        if (!STATUSES.contains(effectiveStatus)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "status 仅支持 pending / approved / rejected");
        }
        if (page < 1) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page 必须从 1 开始");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page_size 必须在 1~" + MAX_PAGE_SIZE + " 之间");
        }
        String stageFilter = (stage == null || stage.isBlank()) ? null : stage.trim();
        if (stageFilter != null && !STAGES.contains(stageFilter)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "stage 仅支持 counselor / first / second");
        }
        boolean filterBizType = bizType != null && !bizType.isBlank();

        // 导员：强制 counselor 阶段 + 管辖范围过滤（越范围数据不可见）
        if ("counselor".equals(user.role())) {
            return listForCounselor(user, filterBizType ? bizType : null, effectiveStatus, page, pageSize);
        }

        String effectiveStage = stageFilter != null ? stageFilter : stageOf(user.role());
        Pageable pageable = PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Review> records = filterBizType
                ? reviewRepository.findByStageAndStatusAndBizType(effectiveStage, effectiveStatus, bizType, pageable)
                : reviewRepository.findByStageAndStatus(effectiveStage, effectiveStatus, pageable);
        long total = filterBizType
                ? reviewRepository.countByStageAndStatusAndBizType(effectiveStage, effectiveStatus, bizType)
                : reviewRepository.countByStageAndStatus(effectiveStage, effectiveStatus);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", records.getContent());
        data.put("total", total);
        data.put("page", page);
        data.put("page_size", pageSize);
        return data;
    }

    /** 审核详情：仅审核角色；不存在 404；activate_manual 附带申报单信息（manual_request） */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(AuthPrincipal user, long reviewId) {
        requireReviewer(user);
        Review review = mustFind(reviewId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", review.getId());
        data.put("biz_type", review.getBizType());
        data.put("biz_id", review.getBizId());
        data.put("submitter_id", review.getSubmitterId());
        data.put("stage", review.getStage());
        data.put("status", review.getStatus());
        data.put("reviewer_id", review.getReviewerId());
        data.put("reason", review.getReason());
        data.put("created_at", review.getCreatedAt());
        data.put("updated_at", review.getUpdatedAt());
        if (BIZ_ACTIVATE_MANUAL.equals(review.getBizType()) && review.getBizId() != null) {
            data.put("manual_request", loadManualRequest(review.getBizId()));
        }
        return data;
    }

    /** 注册申报单 + 申报学籍（alumni_base 经 users 关联），供人工审核比对 */
    private Map<String, Object> loadManualRequest(long requestId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.id AS request_id_no, r.real_name, r.student_id, r.contact_phone,
                       r.note AS remark, r.advisor_note, r.created_at,
                       ab.college, ab.major, ab.grad_year
                FROM activate_manual_requests r
                JOIN users u ON u.id = r.user_id
                LEFT JOIN alumni_base ab ON ab.id = u.alumni_base_id
                WHERE r.id = ?
                """, requestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 通过：仅当前阶段对应角色（counselor=导员，first=college_admin，second=school_admin），
     * activate_manual 单级学院/校级均可。导员操作前校验提交人在其管辖范围内（越权 403）。
     * 记录更新、业务状态同步、日志写入在同一事务；审核结果通知失败不影响主流程。
     */
    @Transactional
    public Review approve(AuthPrincipal user, long reviewId) {
        requireReviewer(user);
        Review review = mustFind(reviewId);
        mustBePending(review);
        mustMatchStage(user, review);
        mustCoverSubmitter(user, review);

        if (isFinalStage(review)) {
            // 终审通过：记录置 approved，同步业务终态
            review.setStatus("approved");
            syncBusiness(review, finalBusinessStatus(review));
        } else if ("counselor".equals(review.getStage())) {
            // 导员初审通过：推进到院系复审，业务状态 pending_first
            review.setStage("first");
            syncBusiness(review, "pending_first");
        } else {
            // 院系复审通过：推进到校级终审，业务状态 pending_second
            review.setStage("second");
            syncBusiness(review, "pending_second");
        }
        review.setReviewerId(user.userId());
        reviewRepository.save(review);
        addLog(review, user.userId(), ReviewLog.ACTION_APPROVE, null);
        notifyReviewResult(review, true, null);
        return review;
    }

    /**
     * 驳回：reason 必填；记录置 rejected，同步业务 rejected（archive_delete 除外），
     * 三级流程中任一层级均可驳回。导员操作前校验提交人在其管辖范围内（越权 403）。
     * 记录更新、业务状态同步、日志写入在同一事务；审核结果通知失败不影响主流程。
     */
    @Transactional
    public Review reject(AuthPrincipal user, long reviewId, String reason) {
        requireReviewer(user);
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "驳回原因 reason 必填");
        }
        Review review = mustFind(reviewId);
        mustBePending(review);
        mustMatchStage(user, review);
        mustCoverSubmitter(user, review);

        review.setStatus("rejected");
        review.setReason(reason);
        review.setReviewerId(user.userId());
        reviewRepository.save(review);

        // archive_delete 驳回应恢复业务原状态（原状态未存于本表，业务模块自行恢复）；
        // archive / honor 驳回直接置 rejected
        if (!"archive_delete".equals(review.getBizType()) && review.getBizId() != null) {
            syncBusiness(review, "rejected");
        }
        addLog(review, user.userId(), ReviewLog.ACTION_REJECT, reason);
        notifyReviewResult(review, false, reason);
        return review;
    }

    /** 审核日志：审核角色或业务提交人本人可见，否则 403 */
    @Transactional(readOnly = true)
    public List<ReviewLog> logs(AuthPrincipal user, long reviewId) {
        Review review = mustFind(reviewId);
        boolean allowed = isReviewer(user.role())
                || (review.getSubmitterId() != null && review.getSubmitterId().equals(user.userId()));
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
        return reviewLogRepository.findByReviewIdOrderByCreatedAtAscIdAsc(reviewId);
    }

    // ---------- 内部规则 ----------

    /** 是否为审核角色 */
    public static boolean isReviewer(String role) {
        return REVIEW_ROLES.contains(role);
    }

    /** 要求当前用户为审核角色（列表 / 详情 / 审批 / 驳回入口） */
    private static void requireReviewer(AuthPrincipal user) {
        if (user == null || !isReviewer(user.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
    }

    /** 角色对应的负责阶段：导员初审 / 学院复审 / 校级终审 */
    private static String stageOf(String role) {
        return switch (role) {
            case "counselor" -> "counselor";
            case "college_admin" -> "first";
            default -> "second";
        };
    }

    /** 查询记录，不存在抛 404 */
    private Review mustFind(long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "审核记录不存在"));
    }

    /** 仅 pending 状态可执行审批操作，否则 409 */
    private static void mustBePending(Review review) {
        if (!"pending".equals(review.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE, "该审核已处理，不能重复操作");
        }
    }

    /** 角色与阶段 / 业务类型匹配校验，不匹配抛 403 */
    private static void mustMatchStage(AuthPrincipal user, Review review) {
        if (!canHandle(user.role(), review.getBizType(), review.getStage())) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前角色无权处理该阶段的审核");
        }
    }

    /** 判断角色是否有权处理指定阶段 / 业务类型的审核 */
    public static boolean canHandle(String role, String bizType, String stage) {
        if (!isReviewer(role)) {
            return false;
        }
        // 系统管理员为全量超集：可处理任意阶段 / 业务类型
        if ("system_admin".equals(role)) {
            return true;
        }
        // 人工核验为校级单级审核（记录置于 second 阶段），仅校级可处理（导员/院系不参与）
        if (BIZ_ACTIVATE_MANUAL.equals(bizType)) {
            return "school_admin".equals(role);
        }
        // 三级审核：导员管 counselor，学院管 first，校级管 second
        return ("counselor".equals(role) && "counselor".equals(stage))
                || ("college_admin".equals(role) && "first".equals(stage))
                || ("school_admin".equals(role) && "second".equals(stage));
    }

    /** 导员越权校验：提交人不在其管辖范围内抛 403（其他角色跳过） */
    private void mustCoverSubmitter(AuthPrincipal user, Review review) {
        if (!"counselor".equals(user.role())) {
            return;
        }
        Long submitterId = review.getSubmitterId();
        Integer covered = submitterId == null ? 0
                : jdbcTemplate.queryForObject(SCOPE_CHECK_SQL, Integer.class, submitterId, user.userId());
        if (covered == null || covered == 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "该单据不在您的管辖范围内");
        }
    }

    /** 是否为终审阶段：second 阶段，或单级审核（activate_manual） */
    private static boolean isFinalStage(Review review) {
        return "second".equals(review.getStage())
                || BIZ_ACTIVATE_MANUAL.equals(review.getBizType());
    }

    /** 终审通过后的业务状态：删除审核 → deleted，其余 → approved */
    private static String finalBusinessStatus(Review review) {
        return "archive_delete".equals(review.getBizType()) ? "deleted" : "approved";
    }

    /** 同步业务表状态（无业务 ID 时跳过；activate_manual 联动 users.status：通过 active / 驳回 rejected） */
    private void syncBusiness(Review review, String status) {
        if (review.getBizId() == null) {
            return;
        }
        switch (review.getBizType()) {
            case "archive", "archive_delete" -> archiveRepository.updateStatus(review.getBizId(), status);
            case "honor" -> honorRepository.updateStatus(review.getBizId(), status);
            case BIZ_ACTIVATE_MANUAL -> jdbcTemplate.update(
                    "UPDATE users u JOIN activate_manual_requests r ON r.user_id = u.id"
                            + " SET u.status = ? WHERE r.id = ?",
                    "approved".equals(status) ? "active" : "rejected", review.getBizId());
            default -> {
                // 未知业务类型不同步
            }
        }
    }

    /** 写审核日志 */
    private void addLog(Review review, long operatorId, String action, String reason) {
        ReviewLog log = new ReviewLog();
        log.setReviewId(review.getId());
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setReason(reason);
        reviewLogRepository.save(log);
    }

    // ---------- 内部：导员管辖范围待办 ----------

    /** 导员待办：stage=counselor + 提交人在其 counselor_scopes 覆盖范围内（共享库直查） */
    private Map<String, Object> listForCounselor(AuthPrincipal user, String bizType, String status,
                                                 int page, int pageSize) {
        List<Object> params = new ArrayList<>();
        params.add(status);
        params.add(user.userId());
        String from = COUNSELOR_LIST_FROM;
        if (bizType != null) {
            from += " AND r.biz_type = ?";
            params.add(bizType);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + from, Long.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((page - 1) * pageSize);
        List<Review> list = jdbcTemplate.query(
                "SELECT r.id, r.biz_type, r.biz_id, r.submitter_id, r.stage, r.status,"
                        + " r.reviewer_id, r.reason, r.created_at, r.updated_at" + from
                        + " ORDER BY r.created_at DESC, r.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Review r = new Review();
                    r.setId(rs.getLong("id"));
                    r.setBizType(rs.getString("biz_type"));
                    long bizId = rs.getLong("biz_id");
                    r.setBizId(rs.wasNull() ? null : bizId);
                    long submitterId = rs.getLong("submitter_id");
                    r.setSubmitterId(rs.wasNull() ? null : submitterId);
                    r.setStage(rs.getString("stage"));
                    r.setStatus(rs.getString("status"));
                    long reviewerId = rs.getLong("reviewer_id");
                    r.setReviewerId(rs.wasNull() ? null : reviewerId);
                    r.setReason(rs.getString("reason"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    r.setCreatedAt(createdAt != null ? createdAt.toInstant() : null);
                    Timestamp updatedAt = rs.getTimestamp("updated_at");
                    r.setUpdatedAt(updatedAt != null ? updatedAt.toInstant() : null);
                    return r;
                }, pageParams.toArray());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total != null ? total : 0L);
        data.put("page", page);
        data.put("page_size", pageSize);
        return data;
    }

    // ---------- 内部：审核结果站内通知（P1 规范 4.1 事件源） ----------

    /**
     * 审核通过/驳回落库后生成站内通知（type=review，接收人=提交人）；
     * content=昵称+业务标题（驳回含 reason），严禁包含学号；查不到昵称/标题用通用文案。
     * 订阅了 review_result 外部渠道的打日志模拟发送。任何失败仅记日志，不影响审核主流程。
     */
    private void notifyReviewResult(Review review, boolean approved, String reason) {
        try {
            if (review.getSubmitterId() == null) {
                return;
            }
            Long submitterId = review.getSubmitterId();
            String nickname = queryOne("SELECT nickname FROM users WHERE id = ?", "nickname", submitterId);
            String bizTitle = bizTitleOf(review);
            String bizName = switch (review.getBizType()) {
                case "archive" -> "档案";
                case "archive_delete" -> "档案删除申请";
                case "honor" -> "荣誉";
                case BIZ_ACTIVATE_MANUAL -> "注册申请";
                default -> "提交的内容";
            };
            String target = bizTitle != null ? bizName + "《" + bizTitle + "》" : bizName;
            String prefix = nickname != null ? nickname + "，您的" : "您的";
            String content = approved
                    ? prefix + target + "审核已通过"
                    : prefix + target + "审核被驳回，原因：" + reason;
            if (content.length() > 500) {
                content = content.substring(0, 500);
            }
            jdbcTemplate.update(
                    "INSERT INTO notifications (user_id, type, title, content, biz_type, biz_id)"
                            + " VALUES (?, 'review', ?, ?, ?, ?)",
                    submitterId, approved ? "审核通过" : "审核驳回", content,
                    review.getBizType(), review.getBizId());

            List<Map<String, Object>> subscriptions = jdbcTemplate.queryForList(
                    "SELECT channel FROM notification_subscriptions"
                            + " WHERE user_id = ? AND enabled = 1"
                            + " AND JSON_CONTAINS(scenes, '\"review_result\"')",
                    submitterId);
            for (Map<String, Object> subscription : subscriptions) {
                log.info("模拟外部发送审核结果通知：userId={}, channel={}, reviewId={}, approved={}",
                        submitterId, subscription.get("channel"), review.getId(), approved);
            }
        } catch (Exception e) {
            log.warn("审核结果通知写入失败（不影响审核主流程）：reviewId={}", review.getId(), e);
        }
    }

    /** 业务标题：archive/archive_delete 查 archives，honor 查 honors；查不到返回 null（通用文案兜底） */
    private String bizTitleOf(Review review) {
        if (review.getBizId() == null) {
            return null;
        }
        return switch (review.getBizType()) {
            case "archive", "archive_delete" ->
                    queryOne("SELECT title FROM archives WHERE id = ?", "title", review.getBizId());
            case "honor" -> queryOne("SELECT title FROM honors WHERE id = ?", "title", review.getBizId());
            default -> null;
        };
    }

    private String queryOne(String sql, String column, Object... params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        if (rows.isEmpty()) {
            return null;
        }
        Object value = rows.get(0).get(column);
        return value != null ? value.toString() : null;
    }
}
