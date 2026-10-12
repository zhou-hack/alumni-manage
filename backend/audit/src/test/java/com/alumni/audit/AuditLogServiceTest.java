package com.alumni.audit;

import com.alumni.audit.common.BizException;
import com.alumni.audit.common.ErrorCode;
import com.alumni.audit.dto.AuditLogDtos.ActionItem;
import com.alumni.audit.dto.AuditLogDtos.PageResult;
import com.alumni.audit.entity.AuditLog;
import com.alumni.audit.repository.AuditLogRepository;
import com.alumni.audit.service.AuditLogService;
import com.alumni.audit.service.ExportRateLimiter;
import com.alumni.common.security.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * AuditLogService 单元测试（Mockito mock Repository / RateLimiter，无 HTTP / 无数据库）。
 *
 * <p>覆盖：筛选参数校验（非法日期 400 / from 晚于 to 400）、非 system_admin 403、
 * 分页查询组装、actions 字典完整性（P1 登记 15 枚举）、
 * 导出限流（同账号第 6 次/小时 429 EXPORT_LIMIT_EXCEEDED）、导出成功写审计日志。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    private static final AuthPrincipal ADMIN = new AuthPrincipal(1L, null, "system_admin");
    private static final AuthPrincipal SCHOOL = new AuthPrincipal(2L, null, "school_admin");

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private ExportRateLimiter exportRateLimiter;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(auditLogRepository, exportRateLimiter);
    }

    /** date_from 非法格式 → 400 INVALID_PARAMETER，且不触库 */
    @Test
    void page_invalidDateFrom_badRequest() {
        BizException e = assertThrows(BizException.class, () -> service.page(ADMIN,
                null, null, null, "2026/09/01", null, 1, 20));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        assertTrue(e.getMessage().contains("date_from"));
        verifyNoInteractions(auditLogRepository);
    }

    /** date_to 非法格式（非零 padding）→ 400 INVALID_PARAMETER */
    @Test
    void page_invalidDateTo_badRequest() {
        BizException e = assertThrows(BizException.class, () -> service.page(ADMIN,
                null, null, null, null, "2026-9-18", 1, 20));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        assertTrue(e.getMessage().contains("date_to"));
        verifyNoInteractions(auditLogRepository);
    }

    /** date_from 晚于 date_to → 400 INVALID_PARAMETER */
    @Test
    void page_dateFromAfterDateTo_badRequest() {
        BizException e = assertThrows(BizException.class, () -> service.page(ADMIN,
                null, null, null, "2026-09-18", "2026-09-17", 1, 20));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verifyNoInteractions(auditLogRepository);
    }

    /** 非 system_admin 查询 → 403，且不触库 */
    @Test
    void page_nonSystemAdmin_forbidden() {
        BizException e = assertThrows(BizException.class, () -> service.page(SCHOOL,
                null, null, null, null, null, 1, 20));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(auditLogRepository);
    }

    /** 正常查询：返回 {list,total,page,page_size}，记录结构字段完整 */
    @Test
    void page_happyPath() {
        AuditLog log = audit(1001L, 6L, "college_admin", "attachment_delete",
                "attachment", 101L, Map.of("file_name", "证书.jpg"), "10.2.3.4",
                Instant.parse("2026-09-17T02:56:00Z"));
        when(auditLogRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        PageResult result = service.page(ADMIN, 6L, "attachment_delete", "attachment",
                "2026-09-17", "2026-09-17", 1, 20);

        assertEquals(1, result.total());
        assertEquals(1, result.page());
        assertEquals(20, result.pageSize());
        assertEquals(1, result.list().size());
        assertEquals(1001L, result.list().get(0).id());
        assertEquals(6L, result.list().get(0).userId());
        assertEquals("attachment_delete", result.list().get(0).action());
        assertEquals(Map.of("file_name", "证书.jpg"), result.list().get(0).detail());
        assertEquals(Instant.parse("2026-09-17T02:56:00Z"), result.list().get(0).createdAt());
    }

    /** 动作字典：覆盖 P1 登记全部 15 个枚举且均带中文 label */
    @Test
    @SuppressWarnings("unchecked")
    void actions_coversAllP1Enums() {
        Map<String, Object> data = service.actions(ADMIN);

        List<ActionItem> list = (List<ActionItem>) data.get("list");
        assertNotNull(list);
        List<String> expected = List.of(
                "login", "login_failed", "logout", "export", "attachment_delete",
                "field_permission_change", "user_suspend", "user_resume", "user_handover",
                "consent_grant", "consent_withdraw", "dict_change", "tag_change",
                "counselor_scope_change", "todo_remind");
        assertEquals(expected.size(), list.size());
        for (String action : expected) {
            ActionItem item = list.stream()
                    .filter(i -> action.equals(i.action()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("缺少动作枚举: " + action));
            assertNotNull(item.label(), action + " 应有中文 label");
            assertFalse(item.label().isBlank());
        }
    }

    /** 动作字典：非 system_admin → 403 */
    @Test
    void actions_nonSystemAdmin_forbidden() {
        BizException e = assertThrows(BizException.class, () -> service.actions(SCHOOL));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
    }

    /** 导出限流：同账号一小时内第 6 次 → 429 EXPORT_LIMIT_EXCEEDED，且不查询、不写审计 */
    @Test
    void export_rateLimited_tooManyRequests() {
        when(exportRateLimiter.tryAcquire(anyLong())).thenReturn(false);

        BizException e = assertThrows(BizException.class, () -> service.export(ADMIN,
                null, null, null, null, null, "10.0.0.1"));
        assertEquals(ErrorCode.EXPORT_LIMIT_EXCEEDED, e.getErrorCode());
        assertEquals(429, e.getErrorCode().httpStatus().value());
        verifyNoInteractions(auditLogRepository);
    }

    /** 导出成功：返回 xlsx 字节流（ZIP 魔数 PK），并写一条 action=export / biz_type=audit_log 审计 */
    @Test
    void export_success_writesAuditLog() {
        when(exportRateLimiter.tryAcquire(ADMIN.userId())).thenReturn(true);
        List<AuditLog> rows = List.of(
                audit(1002L, 6L, "college_admin", "consent_grant", "consent", 501L,
                        Map.of("scope", "screen"), "10.2.3.4", Instant.parse("2026-09-18T03:00:00Z")),
                audit(1001L, null, null, "login", null, null, null, "10.2.3.5",
                        Instant.parse("2026-09-17T02:56:00Z")));
        when(auditLogRepository.findAll(anySpecification(), any(Sort.class))).thenReturn(rows);

        byte[] bytes = service.export(ADMIN, 6L, null, null,
                "2026-09-17", "2026-09-18", "10.0.0.1");

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        assertEquals('P', bytes[0], "xlsx 应为 ZIP 容器（PK 魔数）");
        assertEquals('K', bytes[1]);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog audit = captor.getValue();
        assertEquals(ADMIN.userId(), audit.getUserId(), "导出审计归属当前操作人");
        assertEquals("system_admin", audit.getUserRole());
        assertEquals("export", audit.getAction());
        assertEquals("audit_log", audit.getBizType());
        assertEquals("10.0.0.1", audit.getIp());
        assertEquals(2, audit.getDetail().get("rows"), "detail 记录导出行数");
        assertEquals("2026-09-17", audit.getDetail().get("date_from"), "detail 记录筛选参数");
        assertEquals("2026-09-18", audit.getDetail().get("date_to"));
        assertEquals(6L, audit.getDetail().get("user_id"));
    }

    /** 导出：非 system_admin → 403，且不消耗限流配额 */
    @Test
    void export_nonSystemAdmin_forbidden() {
        BizException e = assertThrows(BizException.class, () -> service.export(SCHOOL,
                null, null, null, null, null, "10.0.0.1"));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(exportRateLimiter, auditLogRepository);
    }

    /** 导出：非法日期 → 400（限流放行后仍校验筛选参数） */
    @Test
    void export_invalidDate_badRequest() {
        when(exportRateLimiter.tryAcquire(ADMIN.userId())).thenReturn(true);

        BizException e = assertThrows(BizException.class, () -> service.export(ADMIN,
                null, null, null, "20260917", null, "10.0.0.1"));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verify(auditLogRepository, never()).findAll(anySpecification(), any(Sort.class));
    }

    /** 真实滑动窗口：同一账号 5 次放行，第 6 次/小时拒绝 */
    @Test
    void rateLimiter_slidingWindow_sixthCallRejected() {
        ExportRateLimiter limiter = new ExportRateLimiter();
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(1L), "第 " + (i + 1) + " 次应放行");
        }
        assertFalse(limiter.tryAcquire(1L), "同账号第 6 次/小时应限流");
        assertTrue(limiter.tryAcquire(2L), "其他账号不受影响");
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Specification<AuditLog> anySpecification() {
        return any(Specification.class);
    }

    private AuditLog audit(Long id, Long userId, String userRole, String action, String bizType,
                           Long bizId, Map<String, Object> detail, String ip, Instant createdAt) {
        AuditLog log = new AuditLog();
        log.setId(id);
        log.setUserId(userId);
        log.setUserRole(userRole);
        log.setAction(action);
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setDetail(detail);
        log.setIp(ip);
        log.setCreatedAt(createdAt);
        return log;
    }
}
