package com.alumni.admin.service;

import com.alumni.admin.common.BizException;
import com.alumni.admin.common.ErrorCode;
import com.alumni.admin.dto.AdminDtos.ChangeLogPage;
import com.alumni.admin.dto.AdminDtos.HandoverResult;
import com.alumni.admin.entity.AuditLog;
import com.alumni.admin.entity.UserChangeLog;
import com.alumni.admin.repository.AuditLogRepository;
import com.alumni.admin.repository.UserChangeLogRepository;
import com.alumni.admin.service.AdminAccountService.UserRow;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.common.security.SuspendedUserStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AdminAccountService 单元测试（Mockito：JdbcTemplate / SuspendedUserStore / Repository 全 mock，
 * 无 HTTP / 无数据库 / 无 Redis）。
 *
 * <p>覆盖：suspend（reason 必填 400 / 不存在 404 / 自停权 409 / 非系统管理员 403 / 正常停权三件套+留痕）、
 * resume、handover 各 409 分支（目标不存在 / 同人 / 角色不同 / 目标非正常 / 导员跨学院）、
 * 正常交接（在途单改派 + 导员范围转移 + 原账号停权 + 留痕）、change-logs 分页映射。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {

    private static final AuthPrincipal SYS = new AuthPrincipal(1L, null, "system_admin");
    private static final AuthPrincipal SCHOOL = new AuthPrincipal(2L, null, "school_admin");

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SuspendedUserStore suspendedUserStore;
    @Mock
    private UserChangeLogRepository userChangeLogRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    private AdminAccountService service;

    @BeforeEach
    void setUp() {
        service = new AdminAccountService(jdbcTemplate, suspendedUserStore,
                userChangeLogRepository, auditLogRepository);
    }

    // ------------------------------------------------------------------
    // suspend
    // ------------------------------------------------------------------

    /** reason 空白 → 400，且不触库 */
    @Test
    void suspend_blankReason_badRequest() {
        BizException e = assertThrows(BizException.class, () -> service.suspend(SYS, 10L, "  "));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verifyNoInteractions(jdbcTemplate, suspendedUserStore, userChangeLogRepository, auditLogRepository);
    }

    /** 非 system_admin → 403（SecurityConfig URL 层之外的 Service 兜底） */
    @Test
    void suspend_notSystemAdmin_forbidden() {
        BizException e = assertThrows(BizException.class, () -> service.suspend(SCHOOL, 10L, "离岗"));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(jdbcTemplate, suspendedUserStore);
    }

    /** 目标用户不存在 → 404 */
    @Test
    void suspend_userNotFound() {
        stubUsers(Map.of(), Map.of());

        BizException e = assertThrows(BizException.class, () -> service.suspend(SYS, 99L, "离岗"));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verify(suspendedUserStore, never()).suspend(anyLong());
    }

    /** 不能停权自己 → 409 INVALID_STATE */
    @Test
    void suspend_self_conflict() {
        stubUsers(Map.of(1L, new UserRow(1L, "system_admin", "active")), Map.of());

        BizException e = assertThrows(BizException.class, () -> service.suspend(SYS, 1L, "离岗"));
        assertEquals(ErrorCode.INVALID_STATE, e.getErrorCode());
        assertTrue(e.getMessage().contains("自己"));
        verify(suspendedUserStore, never()).suspend(anyLong());
        verify(jdbcTemplate, never()).update(contains("UPDATE users"), any(Object[].class));
    }

    /** 正常停权：users.status=suspended + SuspendedUserStore 标记 + refresh_token 吊销 + 双留痕 */
    @Test
    void suspend_happyPath() {
        stubUsers(Map.of(10L, new UserRow(10L, "alumni", "active")), Map.of());
        stubUpdates();

        service.suspend(SYS, 10L, "离岗");

        verify(jdbcTemplate).update(contains("UPDATE users SET status = 'suspended'"), eq(10L));
        verify(suspendedUserStore).suspend(10L);
        verify(jdbcTemplate).update(contains("UPDATE refresh_tokens SET revoked = 1"), eq(10L));

        ArgumentCaptor<UserChangeLog> logCaptor = ArgumentCaptor.forClass(UserChangeLog.class);
        verify(userChangeLogRepository).save(logCaptor.capture());
        UserChangeLog log = logCaptor.getValue();
        assertEquals(10L, log.getUserId(), "留痕记被操作账号");
        assertEquals("suspend", log.getAction());
        assertEquals(1L, log.getOperatorId(), "operator_id 记当前操作人");
        assertEquals("离岗", log.getDetail().get("reason"), "detail 含 reason");

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertEquals("user_suspend", auditCaptor.getValue().getAction());
        assertEquals(10L, auditCaptor.getValue().getBizId());
    }

    // ------------------------------------------------------------------
    // resume
    // ------------------------------------------------------------------

    /** 正常恢复：status=active + SuspendedUserStore 解除 + 双留痕 */
    @Test
    void resume_happyPath() {
        stubUsers(Map.of(10L, new UserRow(10L, "alumni", "suspended")), Map.of());
        stubUpdates();

        service.resume(SYS, 10L);

        verify(jdbcTemplate).update(contains("UPDATE users SET status = 'active'"), eq(10L));
        verify(suspendedUserStore).resume(10L);

        ArgumentCaptor<UserChangeLog> logCaptor = ArgumentCaptor.forClass(UserChangeLog.class);
        verify(userChangeLogRepository).save(logCaptor.capture());
        assertEquals("resume", logCaptor.getValue().getAction());

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertEquals("user_resume", auditCaptor.getValue().getAction());
    }

    // ------------------------------------------------------------------
    // handover
    // ------------------------------------------------------------------

    /** 目标账号不存在 → 409 HANDOVER_INVALID */
    @Test
    void handover_targetNotFound() {
        stubUsers(Map.of(10L, new UserRow(10L, "counselor", "active")), Map.of());

        BizException e = assertThrows(BizException.class,
                () -> service.handover(SYS, 10L, 99L, "离岗移交"));
        assertEquals(ErrorCode.HANDOVER_INVALID, e.getErrorCode());
        verify(jdbcTemplate, never()).update(contains("UPDATE reviews"), any(), any());
    }

    /** 目标与原账号相同 → 409 HANDOVER_INVALID */
    @Test
    void handover_sameUser() {
        stubUsers(Map.of(10L, new UserRow(10L, "counselor", "active")), Map.of());

        BizException e = assertThrows(BizException.class,
                () -> service.handover(SYS, 10L, 10L, "离岗移交"));
        assertEquals(ErrorCode.HANDOVER_INVALID, e.getErrorCode());
    }

    /** 目标与原账号角色不同 → 409 HANDOVER_INVALID */
    @Test
    void handover_roleMismatch() {
        stubUsers(Map.of(
                10L, new UserRow(10L, "counselor", "active"),
                15L, new UserRow(15L, "school_admin", "active")), Map.of());

        BizException e = assertThrows(BizException.class,
                () -> service.handover(SYS, 10L, 15L, "离岗移交"));
        assertEquals(ErrorCode.HANDOVER_INVALID, e.getErrorCode());
    }

    /** 目标账号非正常状态（suspended）→ 409 HANDOVER_INVALID */
    @Test
    void handover_targetNotActive() {
        stubUsers(Map.of(
                10L, new UserRow(10L, "counselor", "active"),
                15L, new UserRow(15L, "counselor", "suspended")), Map.of());

        BizException e = assertThrows(BizException.class,
                () -> service.handover(SYS, 10L, 15L, "离岗移交"));
        assertEquals(ErrorCode.HANDOVER_INVALID, e.getErrorCode());
    }

    /** 导员跨学院交接 → 409 HANDOVER_INVALID（提示"仅限本院"），且不发生任何转移 */
    @Test
    void handover_counselorCrossCollege() {
        stubUsers(Map.of(
                        10L, new UserRow(10L, "counselor", "active"),
                        15L, new UserRow(15L, "counselor", "active")),
                Map.of(10L, "计算机学院", 15L, "外国语学院"));

        BizException e = assertThrows(BizException.class,
                () -> service.handover(SYS, 10L, 15L, "离岗移交"));
        assertEquals(ErrorCode.HANDOVER_INVALID, e.getErrorCode());
        assertTrue(e.getMessage().contains("仅限本院"));
        verify(jdbcTemplate, never()).update(contains("UPDATE reviews"), any(), any());
        verify(suspendedUserStore, never()).suspend(anyLong());
    }

    /** college_admin 跨学院交接同样被拦截 */
    @Test
    void handover_collegeAdminCrossCollege() {
        stubUsers(Map.of(
                        10L, new UserRow(10L, "college_admin", "active"),
                        15L, new UserRow(15L, "college_admin", "active")),
                Map.of(10L, "计算机学院", 15L, "外国语学院"));

        BizException e = assertThrows(BizException.class,
                () -> service.handover(SYS, 10L, 15L, "离岗移交"));
        assertEquals(ErrorCode.HANDOVER_INVALID, e.getErrorCode());
    }

    /** 正常交接（导员同学院）：在途 pending 单改派 + counselor_scopes 转移 + 原账号停权 + 双留痕 */
    @Test
    void handover_counselor_happyPath() {
        stubUsers(Map.of(
                        10L, new UserRow(10L, "counselor", "active"),
                        15L, new UserRow(15L, "counselor", "active")),
                Map.of(10L, "计算机学院", 15L, "计算机学院"));
        stubUpdates();
        when(jdbcTemplate.update(contains("UPDATE reviews SET reviewer_id"), eq(15L), eq(10L))).thenReturn(3);

        HandoverResult result = service.handover(SYS, 10L, 15L, "原管理员毕业离岗，移交给继任者");

        assertEquals(3, result.transferredReviews(), "返回改派单数");
        assertEquals("suspended", result.originStatus());

        verify(jdbcTemplate).update(contains("UPDATE reviews SET reviewer_id"), eq(15L), eq(10L));
        verify(jdbcTemplate).update(contains("UPDATE counselor_scopes SET counselor_id"), eq(15L), eq(10L));
        verify(jdbcTemplate).update(contains("UPDATE users SET status = 'suspended'"), eq(10L));
        verify(suspendedUserStore).suspend(10L);
        verify(jdbcTemplate).update(contains("UPDATE refresh_tokens SET revoked = 1"), eq(10L));

        ArgumentCaptor<UserChangeLog> logCaptor = ArgumentCaptor.forClass(UserChangeLog.class);
        verify(userChangeLogRepository).save(logCaptor.capture());
        UserChangeLog log = logCaptor.getValue();
        assertEquals("handover", log.getAction());
        assertEquals(10L, log.getUserId());
        assertEquals(1L, log.getOperatorId());
        assertEquals(15L, log.getDetail().get("target_user_id"), "detail 含 target_user_id");
        assertEquals(3, log.getDetail().get("transferred_reviews"), "detail 含 transferred_reviews");

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertEquals("user_handover", auditCaptor.getValue().getAction());
    }

    /** school_admin 跨学院交接不受限；非导员不触碰 counselor_scopes */
    @Test
    void handover_schoolAdmin_crossCollegeAllowed() {
        stubUsers(Map.of(
                        10L, new UserRow(10L, "school_admin", "active"),
                        15L, new UserRow(15L, "school_admin", "active")),
                Map.of(10L, "计算机学院", 15L, "外国语学院"));
        stubUpdates();
        when(jdbcTemplate.update(contains("UPDATE reviews SET reviewer_id"), eq(15L), eq(10L))).thenReturn(0);

        HandoverResult result = service.handover(SYS, 10L, 15L, "轮岗");

        assertEquals(0, result.transferredReviews());
        assertEquals("suspended", result.originStatus());
        verify(jdbcTemplate, never()).update(contains("UPDATE counselor_scopes"), any(), any());
        verify(suspendedUserStore).suspend(10L);
    }

    // ------------------------------------------------------------------
    // change-logs
    // ------------------------------------------------------------------

    /** 变更留痕：created_at 倒序分页，映射 {list,total,page,page_size} */
    @Test
    void changeLogs_paged() {
        UserChangeLog log = new UserChangeLog();
        log.setId(7L);
        log.setUserId(10L);
        log.setAction("handover");
        log.setOperatorId(1L);
        log.setDetail(Map.of("target_user_id", 15L, "reason", "离岗移交"));
        log.setCreatedAt(Instant.parse("2026-10-08T02:56:00Z"));
        when(userChangeLogRepository.findByUserIdOrderByCreatedAtDesc(eq(10L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));

        ChangeLogPage page = service.changeLogs(SYS, 10L, 1, 20);

        assertEquals(1, page.total());
        assertEquals(1, page.page());
        assertEquals(20, page.pageSize());
        assertEquals(1, page.list().size());
        assertEquals(7L, page.list().get(0).id());
        assertEquals("handover", page.list().get(0).action());
        assertEquals(1L, page.list().get(0).operatorId());
        assertEquals(15L, page.list().get(0).detail().get("target_user_id"));
        assertEquals(Instant.parse("2026-10-08T02:56:00Z"), page.list().get(0).createdAt());
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    /**
     * 打通账号查询：SELECT id,role,status FROM users → users 表快照；
     * SELECT ab.college ... JOIN alumni_base → 学院文本（以 alumni_base.college 为准）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubUsers(Map<Long, UserRow> users, Map<Long, String> colleges) {
        Map<Long, UserRow> usersCopy = new HashMap<>(users);
        Map<Long, String> collegesCopy = new HashMap<>(colleges);
        lenient().when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyLong()))
                .thenAnswer(inv -> {
                    String sql = inv.getArgument(0);
                    Long id = inv.getArgument(2);
                    if (sql.contains("alumni_base")) {
                        String college = collegesCopy.get(id);
                        return college == null ? List.of() : List.of(college);
                    }
                    UserRow row = usersCopy.get(id);
                    return row == null ? List.of() : List.of(row);
                });
    }

    /** 打通各类 UPDATE（具体改派行数由用例自行覆盖 reviews 的返回值） */
    private void stubUpdates() {
        lenient().when(jdbcTemplate.update(contains("UPDATE reviews"), any(), any())).thenReturn(0);
        lenient().when(jdbcTemplate.update(contains("UPDATE counselor_scopes"), any(), any())).thenReturn(1);
        lenient().when(jdbcTemplate.update(contains("UPDATE users"), any(Object[].class))).thenReturn(1);
        lenient().when(jdbcTemplate.update(contains("UPDATE refresh_tokens"), any(Object[].class))).thenReturn(1);
    }
}
