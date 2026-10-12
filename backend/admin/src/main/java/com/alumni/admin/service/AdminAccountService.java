package com.alumni.admin.service;

import com.alumni.admin.common.BizException;
import com.alumni.admin.common.ErrorCode;
import com.alumni.admin.dto.AdminDtos.ChangeLogItem;
import com.alumni.admin.dto.AdminDtos.ChangeLogPage;
import com.alumni.admin.dto.AdminDtos.HandoverResult;
import com.alumni.admin.entity.AuditLog;
import com.alumni.admin.entity.UserChangeLog;
import com.alumni.admin.repository.AuditLogRepository;
import com.alumni.admin.repository.UserChangeLogRepository;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.common.security.SuspendedUserStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 账号管理（[M15] 停权 / 恢复 / 交接 / 变更留痕，仅 system_admin）。
 *
 * <p>停权即时生效 = users.status='suspended' + SuspendedUserStore 标记（已签发 Token 即刻拦截）
 * + refresh_tokens 全量吊销。存量跨模块表（users/alumni_base/reviews/counselor_scopes/refresh_tokens）
 * 按共享库惯例用 JdbcTemplate 直读直写，不依赖其他业务模块 jar。</p>
 */
@Service
public class AdminAccountService {

    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final SuspendedUserStore suspendedUserStore;
    private final UserChangeLogRepository userChangeLogRepository;
    private final AuditLogRepository auditLogRepository;

    public AdminAccountService(JdbcTemplate jdbcTemplate,
                               SuspendedUserStore suspendedUserStore,
                               UserChangeLogRepository userChangeLogRepository,
                               AuditLogRepository auditLogRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.suspendedUserStore = suspendedUserStore;
        this.userChangeLogRepository = userChangeLogRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /** POST /admin/users/{id}/suspend —— 停权（reason 必填；不可停权自己） */
    @Transactional
    public void suspend(AuthPrincipal operator, long userId, String reason) {
        requireSystemAdmin(operator);
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "reason 必填");
        }
        UserRow target = loadUser(userId);
        if (target == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (Objects.equals(target.id(), operator.userId())) {
            throw new BizException(ErrorCode.INVALID_STATE, "不能停权自己");
        }
        suspendUser(userId);
        writeChangeLog(userId, "suspend", operator.userId(), Map.<String, Object>of("reason", reason));
        writeAudit(operator, "user_suspend", userId, Map.<String, Object>of("reason", reason));
    }

    /** POST /admin/users/{id}/resume —— 恢复 */
    @Transactional
    public void resume(AuthPrincipal operator, long userId) {
        requireSystemAdmin(operator);
        UserRow target = loadUser(userId);
        if (target == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        jdbcTemplate.update("UPDATE users SET status = 'active' WHERE id = ?", userId);
        suspendedUserStore.resume(userId);
        writeChangeLog(userId, "resume", operator.userId(), null);
        writeAudit(operator, "user_resume", userId, null);
    }

    /**
     * POST /admin/users/{id}/handover —— 账号交接（原账号停权，数据范围与在途待办转移给目标账号）。
     * counselor/college_admin 不得跨学院交接（以 alumni_base.college 文本为准）。
     */
    @Transactional
    public HandoverResult handover(AuthPrincipal operator, long userId, Long targetUserId, String reason) {
        requireSystemAdmin(operator);
        if (targetUserId == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "target_user_id 必填");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "reason 必填");
        }
        UserRow origin = loadUser(userId);
        if (origin == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (Objects.equals(targetUserId, userId)) {
            throw new BizException(ErrorCode.HANDOVER_INVALID, "目标账号不能与原账号相同");
        }
        UserRow target = loadUser(targetUserId);
        if (target == null) {
            throw new BizException(ErrorCode.HANDOVER_INVALID, "目标账号不存在");
        }
        if (!Objects.equals(origin.role(), target.role())) {
            throw new BizException(ErrorCode.HANDOVER_INVALID, "目标账号须与原账号同角色");
        }
        if (!"active".equals(target.status())) {
            throw new BizException(ErrorCode.HANDOVER_INVALID, "目标账号状态非正常");
        }
        if ("counselor".equals(origin.role()) || "college_admin".equals(origin.role())) {
            String originCollege = collegeOf(userId);
            String targetCollege = collegeOf(targetUserId);
            if (!Objects.equals(originCollege, targetCollege)) {
                throw new BizException(ErrorCode.HANDOVER_INVALID, "导员/院系管理员交接仅限本院");
            }
        }

        // 在途待办改派（reviewer_id 指向原账号的 pending 单据）
        int transferred = jdbcTemplate.update(
                "UPDATE reviews SET reviewer_id = ? WHERE reviewer_id = ? AND status = 'pending'",
                targetUserId, userId);
        // 数据范围转移：导员管辖范围改挂目标账号
        if ("counselor".equals(origin.role())) {
            jdbcTemplate.update("UPDATE counselor_scopes SET counselor_id = ? WHERE counselor_id = ?",
                    targetUserId, userId);
        }

        // 原账号停权（登录能力停用，历史记录归属不变）
        suspendUser(userId);

        Map<String, Object> detail = Map.of(
                "target_user_id", targetUserId,
                "reason", reason,
                "transferred_reviews", transferred);
        writeChangeLog(userId, "handover", operator.userId(), detail);
        writeAudit(operator, "user_handover", userId, detail);

        return new HandoverResult(transferred, "suspended");
    }

    /** GET /admin/users/{id}/change-logs —— 变更留痕（created_at 倒序分页） */
    @Transactional(readOnly = true)
    public ChangeLogPage changeLogs(AuthPrincipal operator, long userId, int page, int pageSize) {
        requireSystemAdmin(operator);
        int p = Math.max(page, 1);
        int size = pageSize < 1 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        Page<UserChangeLog> result = userChangeLogRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(p - 1, size));
        List<ChangeLogItem> list = result.getContent().stream()
                .map(log -> new ChangeLogItem(log.getId(), log.getAction(), log.getOperatorId(),
                        log.getDetail(), log.getCreatedAt()))
                .toList();
        return new ChangeLogPage(list, result.getTotalElements(), p, size);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /** 停权即时生效三件套：状态置 suspended + SuspendedUserStore 标记 + refresh_token 全量吊销 */
    private void suspendUser(long userId) {
        jdbcTemplate.update("UPDATE users SET status = 'suspended' WHERE id = ?", userId);
        suspendedUserStore.suspend(userId);
        jdbcTemplate.update("UPDATE refresh_tokens SET revoked = 1 WHERE user_id = ?", userId);
    }

    private void requireSystemAdmin(AuthPrincipal operator) {
        if (operator == null || !"system_admin".equals(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅系统管理员可执行该操作");
        }
    }

    /** 账号快照（id/角色/状态），package-private 便于同包单测构造 */
    record UserRow(Long id, String role, String status) {
    }

    private UserRow loadUser(long userId) {
        List<UserRow> rows = jdbcTemplate.query(
                "SELECT id, role, status FROM users WHERE id = ?",
                (rs, rowNum) -> new UserRow(rs.getLong("id"), rs.getString("role"), rs.getString("status")),
                userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 账号所属学院文本（以 alumni_base.college 为准；无记录返回 null） */
    private String collegeOf(long userId) {
        List<String> rows = jdbcTemplate.query(
                "SELECT ab.college FROM users u JOIN alumni_base ab ON u.alumni_base_id = ab.id WHERE u.id = ?",
                (rs, rowNum) -> rs.getString("college"),
                userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void writeChangeLog(long userId, String action, Long operatorId, Map<String, Object> detail) {
        UserChangeLog log = new UserChangeLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setOperatorId(operatorId);
        log.setDetail(detail);
        userChangeLogRepository.save(log);
    }

    private void writeAudit(AuthPrincipal operator, String action, long bizId, Map<String, Object> detail) {
        AuditLog audit = new AuditLog();
        audit.setUserId(operator.userId());
        audit.setUserRole(operator.role());
        audit.setAction(action);
        audit.setBizType("user");
        audit.setBizId(bizId);
        audit.setDetail(detail);
        auditLogRepository.save(audit);
    }
}
