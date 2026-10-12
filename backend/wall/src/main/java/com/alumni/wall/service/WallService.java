package com.alumni.wall.service;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.wall.common.BizException;
import com.alumni.wall.common.ErrorCode;
import com.alumni.wall.dto.WallDtos.AdminItem;
import com.alumni.wall.dto.WallDtos.MyItem;
import com.alumni.wall.dto.WallDtos.PageResult;
import com.alumni.wall.dto.WallDtos.PublicItem;
import com.alumni.wall.entity.WallMessage;
import com.alumni.wall.repository.WallMessageRepository;
import com.alumni.wall.repository.WallMessageRepository.PublicMessageProjection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 留言墙业务实现（Service 层，扩展模块 [M16]）。
 *
 * <p>职责：登录/角色校验、内容校验（去空白 1~120 字）、提交频率限制（同用户 60s 一条 / 每天 5 条，
 * 内存计数，重启即清，与 auth 限流同策略）、审核编排（pending → approved/rejected）、
 * 越权防护（撤回/编辑/重发仅限本人）。</p>
 *
 * <p>软删除 + 重新发送策略：
 * 用户撤回留言走软删除（deleted_at = now），保留原行供「重新发送」按 content 重建一条 pending；
 * 管理端删除走物理删除（不可恢复），彻底清除脏数据。</p>
 *
 * <p>隐私红线：公开列表仅出 approved 且不含 ip_addr / 手机号等任何个人信息；
 * ip_addr 仅在管理端（school_admin）可见，用于风控。</p>
 */
@Service
public class WallService {

    /** 留言墙维护角色（院系 / 校级 / 系统管理员；与档案/荣誉审核保持一致） */
    private static final Set<String> MANAGER_ROLES = Set.of("college_admin", "school_admin", "system_admin");

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_HIDDEN = "hidden";

    /** 正文长度（去空白后 1~120，文档 §2） */
    private static final int CONTENT_MAX = 120;
    /** 公开列表分页（文档 §3.1：默认 50，上限 100） */
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;
    /** 提交频率限制（文档 §4：60 秒 1 条 / 每天 5 条） */
    private static final long SUBMIT_INTERVAL_MS = 60_000L;
    private static final int DAILY_LIMIT = 5;

    private final WallMessageRepository repository;
    private final EntityManager em;

    /** 提交限流状态（userId → 最近提交时间/当日计数；内存态，重启即清） */
    private final Map<Long, RateState> rateStates = new ConcurrentHashMap<>();

    public WallService(WallMessageRepository repository, EntityManager em) {
        this.repository = repository;
        this.em = em;
    }

    /* ==================== 公开端点 ==================== */

    /** 公开列表（免登录）：仅 approved，置顶优先、时间倒序 */
    @Transactional(readOnly = true)
    public PageResult<PublicItem> listPublic(int page, int pageSize) {
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "page_size 须在 1~" + MAX_PAGE_SIZE + " 之间");
        }
        int safePage = Math.max(page, 1);
        long total = repository.countApproved();
        int offset = (safePage - 1) * pageSize;
        List<PublicItem> list = offset >= total
                ? List.of()
                : repository.findApproved(pageSize, offset).stream().map(this::toPublicItem).toList();
        return new PageResult<>(list, total, safePage, pageSize);
    }

    /* ==================== 用户端点（登录） ==================== */

    /**
     * 我的留言（全部状态，含 pending/rejected/hidden 及驳回原因，含软删除记录以便「重新发送」）。
     * 公开墙 admin 进入本菜单也能看到自己发送的全部留言。
     */
    @Transactional(readOnly = true)
    public List<MyItem> mine(AuthPrincipal operator) {
        requireLogin(operator);
        return repository.findByUserIdOrderByIdDesc(operator.userId()).stream()
                .map(this::toMyItem).toList();
    }

    /** 提交留言：新留言一律 pending，审核通过后公开展示；showName=true 才在公开墙显示真实姓名 */
    @Transactional
    public MyItem create(AuthPrincipal operator, String content, String ip, Boolean showName) {
        requireLogin(operator);
        String text = normalizeContent(content);
        checkRateLimit(operator.userId());

        WallMessage message = new WallMessage();
        message.setUserId(operator.userId());
        message.setContent(text);
        message.setShowName(Boolean.TRUE.equals(showName));
        message.setStatus(STATUS_PENDING);
        message.setIsPinned(Boolean.FALSE);
        message.setIpAddr(ip);
        message = repository.save(message);

        recordSubmit(operator.userId());
        return toMyItem(message);
    }

    /**
     * 撤回自己的留言（软删除：deleted_at = now，保留原行供「重新发送」）。
     * 仅本人留言、未软删、可撤回；已通过留言需走管理端「取消展示」/删除。
     */
    @Transactional
    public void remove(AuthPrincipal operator, long id) {
        requireLogin(operator);
        requirePositiveId(id);
        WallMessage message = repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "留言不存在"));
        if (!message.getUserId().equals(operator.userId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能撤回自己的留言");
        }
        if (message.getDeletedAt() != null) {
            // 幂等：已经软删过的不抛错，避免前端二次撤销时报 500
            return;
        }
        if (STATUS_APPROVED.equals(message.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE,
                    "已通过展示的留言不可直接撤回，请联系管理员取消展示");
        }
        message.setDeletedAt(Instant.now());
        repository.save(message);
    }

    /**
     * 编辑自己处于 pending / rejected 的留言（重置 review 字段，下次按 pending 走审核）。
     * 限本人 + 未软删；approved/hidden 不允许编辑（approved 内容需管理员 hide 后再编辑）。
     */
    @Transactional
    public MyItem update(AuthPrincipal operator, long id, String content, Boolean showName) {
        requireLogin(operator);
        requirePositiveId(id);
        String text = normalizeContent(content);
        WallMessage message = repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "留言不存在"));
        if (!message.getUserId().equals(operator.userId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能编辑自己的留言");
        }
        if (message.getDeletedAt() != null) {
            throw new BizException(ErrorCode.INVALID_STATE, "已撤回的留言不可编辑，请使用「重新发送」");
        }
        if (STATUS_APPROVED.equals(message.getStatus()) || STATUS_HIDDEN.equals(message.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE,
                    "已通过或已取消展示的留言不可编辑，请联系管理员");
        }
        message.setContent(text);
        if (showName != null) {
            message.setShowName(showName);
        }
        // 内容变更后重置为 pending，让审核员重新过一遍
        message.setStatus(STATUS_PENDING);
        message.setReviewNote(null);
        message.setReviewedAt(null);
        message.setReviewedBy(null);
        return toMyItem(repository.save(message));
    }

    /**
     * 重新发送：基于一条已软删除的留言，复制 content / show_name 创建一条新的 pending 留言。
     * 限本人；满足频率限制；原行保留（仍可见于「我的留言」，deleted_at 不变）。
     */
    @Transactional
    public MyItem resubmit(AuthPrincipal operator, long id, String ip) {
        requireLogin(operator);
        requirePositiveId(id);
        WallMessage origin = repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "留言不存在"));
        if (!origin.getUserId().equals(operator.userId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能重新发送自己的留言");
        }
        if (origin.getDeletedAt() == null) {
            throw new BizException(ErrorCode.INVALID_STATE, "仅已撤回的留言支持「重新发送」");
        }
        checkRateLimit(operator.userId());

        WallMessage fresh = new WallMessage();
        fresh.setUserId(operator.userId());
        fresh.setContent(origin.getContent());
        fresh.setShowName(Boolean.TRUE.equals(origin.getShowName()));
        fresh.setStatus(STATUS_PENDING);
        fresh.setIsPinned(Boolean.FALSE);
        fresh.setIpAddr(ip);
        fresh = repository.save(fresh);

        recordSubmit(operator.userId());
        return toMyItem(fresh);
    }

    /* ==================== 管理端点（school_admin） ==================== */

    /** 审核队列：status 筛选（pending/approved/rejected/hidden）+ keyword 模糊搜正文/留言人/学号 + 分页 */
    @Transactional(readOnly = true)
    public PageResult<AdminItem> adminPage(AuthPrincipal operator, String status, String keyword,
                                           int page, int pageSize) {
        requireManager(operator);
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "page_size 须在 1~" + MAX_PAGE_SIZE + " 之间");
        }
        int safePage = Math.max(page, 1);
        String effectiveStatus = normalizeStatus(status);

        // 动态条件（全部参数化，杜绝拼接注入；每条以换行结尾，避免与后续 SQL 粘连）
        StringBuilder where = new StringBuilder(" WHERE 1 = 1\n");
        Map<String, Object> params = new LinkedHashMap<>();
        if (effectiveStatus != null) {
            where.append(" AND m.status = :status\n");
            params.put("status", effectiveStatus);
        }
        if (hasText(keyword)) {
            where.append(" AND (m.content LIKE :kw OR ab.real_name LIKE :kw OR u.student_id LIKE :kw)\n");
            params.put("kw", "%" + keyword.trim() + "%");
        }
        String whereSql = where.toString();

        String fromSql = """
                FROM wall_message m
                JOIN users u ON u.id = m.user_id
                JOIN alumni_base ab ON ab.id = u.alumni_base_id
                """;

        Query countQuery = em.createNativeQuery("SELECT COUNT(*)\n" + fromSql + whereSql);
        bind(countQuery, params);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        int offset = (safePage - 1) * pageSize;
        if (offset >= total) {
            return new PageResult<>(List.of(), total, safePage, pageSize);
        }

        Query listQuery = em.createNativeQuery("""
                SELECT m.id, m.user_id, ab.real_name AS name, u.student_id, m.content, m.status,
                       m.review_note, m.is_pinned, m.ip_addr, m.created_at, m.reviewed_at, m.deleted_at
                """ + fromSql + whereSql
                + " ORDER BY m.created_at DESC, m.id DESC\n"
                + " LIMIT :__limit OFFSET :__offset");
        bind(listQuery, params);
        listQuery.setParameter("__limit", pageSize);
        listQuery.setParameter("__offset", offset);

        List<AdminItem> list = new ArrayList<>();
        for (Object rowObj : listQuery.getResultList()) {
            Object[] row = (Object[]) rowObj;
            list.add(new AdminItem(
                    ((Number) row[0]).longValue(),
                    ((Number) row[1]).longValue(),
                    (String) row[2],
                    (String) row[3],
                    (String) row[4],
                    (String) row[5],
                    (String) row[6],
                    toBool(row[7]),
                    (String) row[8],
                    toInstant(row[9]),
                    toInstant(row[10]),
                    toInstant(row[11])));
        }
        return new PageResult<>(list, total, safePage, pageSize);
    }

    /** 审核（school_admin）：approve 通过；reject 驳回（必填原因，展示给留言人）；hide 取消展示（已通过 → 下墙，原因选填） */
    @Transactional
    public MyItem review(AuthPrincipal operator, long id, String action, String note) {
        requireManager(operator);
        requirePositiveId(id);
        WallMessage message = repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "留言不存在"));

        String trimmedNote = note == null ? null : note.trim();
        switch (action) {
            case "approve" -> {
                message.setStatus(STATUS_APPROVED);
                message.setReviewNote(null); // 通过时清空历史驳回原因
            }
            case "hide" -> {
                if (!STATUS_APPROVED.equals(message.getStatus())) {
                    throw new BizException(ErrorCode.INVALID_STATE, "仅已通过的留言可取消展示");
                }
                message.setStatus(STATUS_HIDDEN);
                message.setReviewNote(trimmedNote); // 选填：记录取消原因
            }
            default -> {
                if (trimmedNote == null || trimmedNote.isEmpty()) {
                    throw new BizException(ErrorCode.INVALID_PARAMETER, "驳回需填写原因");
                }
                message.setStatus(STATUS_REJECTED);
                message.setReviewNote(trimmedNote);
            }
        }
        message.setReviewedBy(operator.userId());
        message.setReviewedAt(Instant.now());
        message = repository.save(message);
        return toMyItem(message);
    }

    /** 删除留言（school_admin / system_admin；物理删除，不可恢复） */
    @Transactional
    public void adminDelete(AuthPrincipal operator, long id) {
        requireManager(operator);
        requirePositiveId(id);
        if (!repository.existsById(id)) {
            throw new BizException(ErrorCode.NOT_FOUND, "留言不存在");
        }
        repository.deleteById(id);
    }

    /** PUT /admin/wall/messages/{id}/pin —— 切换置顶（仅 approved 留言可置顶） */
    @Transactional
    public void setPinned(AuthPrincipal operator, long id, boolean pinned) {
        requireManager(operator);
        requirePositiveId(id);
        WallMessage msg = repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "留言不存在"));
        if (pinned && !STATUS_APPROVED.equals(msg.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE, "仅已通过的留言可置顶");
        }
        msg.setIsPinned(pinned);
        repository.save(msg);
    }

    /** POST /admin/wall/users/{userId}/mute —— 禁言用户（durationDays = null 表示永久） */
    @Transactional
    public void mute(AuthPrincipal operator, long userId, Integer durationDays, String reason) {
        requireManager(operator);
        if (userId <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "user_id 必须为正整数");
        }
        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "reason 不能为空");
        }
        if (durationDays != null && durationDays <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "duration_days 必须为正整数");
        }
        // 内存级禁言：与现有 rate-limit 一致的 ConcurrentHashMap；重启失效 → 仅作临时风控
        Instant until = durationDays == null
                ? Instant.MAX
                : Instant.now().plusSeconds(durationDays * 86400L);
        MUTED_USERS.put(userId, new MuteRecord(until, operator.userId(), trimmedReason));
    }

    /** 内存禁言表（M16 风控） */
    private static final Map<Long, MuteRecord> MUTED_USERS = new ConcurrentHashMap<>();
    private record MuteRecord(Instant until, Long operatorId, String reason) {}

    /** 校验当前操作者是否被禁言 */
    public boolean isMuted(long userId) {
        MuteRecord rec = MUTED_USERS.get(userId);
        if (rec == null) return false;
        if (rec.until.isBefore(Instant.now())) {
            MUTED_USERS.remove(userId);
            return false;
        }
        return true;
    }

    /* ---------------- 私有工具 ---------------- */

    private void requireLogin(AuthPrincipal operator) {
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
    }

    private void requireManager(AuthPrincipal operator) {
        if (operator == null || !MANAGER_ROLES.contains(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅 school_admin / system_admin 可管理留言墙");
        }
    }

    private void requirePositiveId(long id) {
        if (id <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "id 必须为正整数");
        }
    }

    /** null/空串 → null（全部）；否则校验取值，非法抛 400 */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status) {
            case STATUS_PENDING, STATUS_APPROVED, STATUS_REJECTED, STATUS_HIDDEN -> status;
            default -> throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "status 仅允许 pending/approved/rejected/hidden");
        };
    }

    /** 校验正文长度（去首尾空白后 1~120 字），返回 trimmed 文本 */
    private String normalizeContent(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty() || text.length() > CONTENT_MAX) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "留言内容须为 1~" + CONTENT_MAX + " 字（去首尾空白）");
        }
        return text;
    }

    private static boolean hasText(String raw) {
        return raw != null && !raw.isBlank();
    }

    private static void bind(Query query, Map<String, Object> params) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
    }

    private static Instant toInstant(Object raw) {
        return raw instanceof java.sql.Timestamp ts ? ts.toInstant() : null;
    }

    /** TINYINT(1) 兼容转换：MySQL 驱动默认 tinyInt1isBit=true 返回 Boolean，关闭时返回 Number */
    private static boolean toBool(Object raw) {
        if (raw == null) {
            return false;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        return ((Number) raw).intValue() == 1;
    }

    /** 提交频率校验：同用户 60 秒 1 条、每天 5 条（内存计数，重启即清） */
    private void checkRateLimit(long userId) {
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        synchronized (rateStates) {
            RateState state = rateStates.computeIfAbsent(userId, k -> new RateState());
            if (!today.equals(state.day)) {
                state.day = today;
                state.count = 0;
            }
            if (now - state.lastAt < SUBMIT_INTERVAL_MS) {
                throw new BizException(ErrorCode.RATE_LIMITED, "提交过于频繁，每 60 秒最多留言 1 条");
            }
            if (state.count >= DAILY_LIMIT) {
                throw new BizException(ErrorCode.RATE_LIMITED, "今日留言已达上限（" + DAILY_LIMIT + " 条）");
            }
        }
    }

    /** 校验通过后记录一次提交（占用频率额度） */
    private void recordSubmit(long userId) {
        String today = LocalDate.now().toString();
        synchronized (rateStates) {
            RateState state = rateStates.computeIfAbsent(userId, k -> new RateState());
            if (!today.equals(state.day)) {
                state.day = today;
                state.count = 0;
            }
            state.lastAt = System.currentTimeMillis();
            state.count += 1;
        }
    }

    /** 限流状态（单机内存态） */
    private static final class RateState {
        long lastAt;
        String day = "";
        int count;
    }

    private PublicItem toPublicItem(PublicMessageProjection row) {
        // grad_year 拼好"届"（文档 §3.1 示例："2023届"），null 保持 null
        Integer year = row.getGradYear();
        // 姓名脱敏：提交人未勾选显示姓名 → 匿名（届次/学院照常展示）
        String name = Boolean.TRUE.equals(row.getShowName()) ? row.getName() : "匿名";
        return new PublicItem(row.getId(), row.getUserId(), name,
                year == null ? null : year + "届",
                row.getCollege(), row.getContent(), Boolean.TRUE.equals(row.getIsPinned()),
                toInstant(row.getCreatedAt()));
    }

    private MyItem toMyItem(WallMessage m) {
        return new MyItem(m.getId(), m.getContent(), m.getShowName(), m.getStatus(), m.getReviewNote(),
                m.getCreatedAt(), m.getReviewedAt(), m.getDeletedAt());
    }
}
