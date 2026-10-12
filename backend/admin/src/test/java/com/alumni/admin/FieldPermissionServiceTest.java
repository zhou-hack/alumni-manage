package com.alumni.admin;

import com.alumni.admin.common.BizException;
import com.alumni.admin.common.ErrorCode;
import com.alumni.admin.dto.AdminDtos.MatrixResult;
import com.alumni.admin.entity.AuditLog;
import com.alumni.admin.entity.FieldPermission;
import com.alumni.admin.repository.AuditLogRepository;
import com.alumni.admin.repository.FieldPermissionRepository;
import com.alumni.admin.service.FieldPermissionService;
import com.alumni.common.security.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FieldPermissionService 单元测试（Mockito mock Repository，无 HTTP / 无数据库）。
 *
 * <p>覆盖：矩阵默认值补齐（6 角色 × 6 字段，phone→masked / id_card 对 student、alumni→hidden）、
 * 库配置覆盖默认值、PUT 的 role / field / visibility 白名单校验、按 (role,field) upsert 与审计写入。</p>
 */
@ExtendWith(MockitoExtension.class)
class FieldPermissionServiceTest {

    private static final AuthPrincipal SYS = new AuthPrincipal(1L, null, "system_admin");

    @Mock
    private FieldPermissionRepository fieldPermissionRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    private FieldPermissionService service;

    @BeforeEach
    void setUp() {
        service = new FieldPermissionService(fieldPermissionRepository, auditLogRepository);
    }

    /** 无库配置时按默认值补齐：6 角色 × 6 字段，phone 全角色 masked，id_card 对 student/alumni hidden */
    @Test
    void matrix_defaultsFilled() {
        when(fieldPermissionRepository.findAll()).thenReturn(List.of());

        MatrixResult matrix = service.matrix();

        assertEquals(FieldPermissionService.ROLES.size(), matrix.roles().size(), "覆盖全部 6 角色");
        for (String role : FieldPermissionService.ROLES) {
            Map<String, String> fields = matrix.roles().get(role);
            assertNotNull(fields, "角色缺失：" + role);
            assertEquals(FieldPermissionService.FIELDS.size(), fields.size(), "每角色覆盖全部字段");
            assertEquals("masked", fields.get("phone"), "phone 默认 masked");
        }
        assertEquals("hidden", matrix.roles().get("student").get("id_card"), "student 默认不可见 id_card");
        assertEquals("hidden", matrix.roles().get("alumni").get("id_card"), "alumni 默认不可见 id_card");
        assertEquals("visible", matrix.roles().get("counselor").get("id_card"));
        assertEquals("visible", matrix.roles().get("system_admin").get("id_card"));
        assertEquals("visible", matrix.roles().get("student").get("email"));
    }

    /** 库中配置覆盖默认值，未配置项仍按默认补齐 */
    @Test
    void matrix_dbOverridesDefaults() {
        when(fieldPermissionRepository.findAll()).thenReturn(List.of(
                fp(11L, "counselor", "phone", "visible"),
                fp(12L, "alumni", "id_card", "masked")));

        MatrixResult matrix = service.matrix();

        assertEquals("visible", matrix.roles().get("counselor").get("phone"), "库配置覆盖默认 masked");
        assertEquals("masked", matrix.roles().get("alumni").get("id_card"), "库配置覆盖默认 hidden");
        assertEquals("masked", matrix.roles().get("student").get("phone"), "未配置仍走默认");
        assertEquals("visible", matrix.roles().get("counselor").get("email"), "未配置仍走默认");
    }

    /** role 越界（非 6 角色白名单）→ 400，且不触库 */
    @Test
    void update_invalidRole_badRequest() {
        BizException e = assertThrows(BizException.class,
                () -> service.update(SYS, "super_admin", Map.of("phone", "masked")));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verifyNoInteractions(fieldPermissionRepository, auditLogRepository);
    }

    /** field 越界（非白名单）→ 400 */
    @Test
    void update_invalidField_badRequest() {
        BizException e = assertThrows(BizException.class,
                () -> service.update(SYS, "counselor", Map.of("honor_title", "visible")));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verify(fieldPermissionRepository, never()).save(any());
        verifyNoInteractions(auditLogRepository);
    }

    /** visibility 非法（仅 visible/masked/hidden）→ 400 */
    @Test
    void update_invalidVisibility_badRequest() {
        BizException e = assertThrows(BizException.class,
                () -> service.update(SYS, "counselor", Map.of("phone", "blur")));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verify(fieldPermissionRepository, never()).save(any());
        verifyNoInteractions(auditLogRepository);
    }

    /** 正常更新：已有行改值 + 缺失行新增（按 (role,field) upsert），updated_by 记操作人，写审计 */
    @Test
    void update_upsertAndAudit() {
        FieldPermission existing = fp(11L, "counselor", "phone", "masked");
        when(fieldPermissionRepository.findByRoleAndField("counselor", "phone"))
                .thenReturn(Optional.of(existing));
        when(fieldPermissionRepository.findByRoleAndField("counselor", "id_card"))
                .thenReturn(Optional.empty());
        when(fieldPermissionRepository.save(any(FieldPermission.class))).thenAnswer(inv -> inv.getArgument(0));
        // update 末尾返回该角色最新矩阵（重新查全量）
        FieldPermission created = fp(13L, "counselor", "id_card", "hidden");
        when(fieldPermissionRepository.findAll()).thenAnswer(inv -> List.of(existing, created));

        Map<String, String> result = service.update(SYS, "counselor",
                Map.of("phone", "visible", "id_card", "hidden"));

        ArgumentCaptor<FieldPermission> captor = ArgumentCaptor.forClass(FieldPermission.class);
        verify(fieldPermissionRepository, times(2)).save(captor.capture());
        List<FieldPermission> saved = captor.getAllValues();
        FieldPermission savedPhone = saved.stream().filter(f -> "phone".equals(f.getField())).findFirst().orElseThrow();
        assertEquals(11L, savedPhone.getId(), "已有行原地更新");
        assertEquals("visible", savedPhone.getVisibility());
        assertEquals(1L, savedPhone.getUpdatedBy(), "updated_by 记当前操作人");
        FieldPermission savedIdCard = saved.stream().filter(f -> "id_card".equals(f.getField())).findFirst().orElseThrow();
        assertNull(savedIdCard.getId(), "缺失行新增");
        assertEquals("counselor", savedIdCard.getRole());
        assertEquals("hidden", savedIdCard.getVisibility());

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertEquals("field_permission_change", audit.getAction());
        assertEquals(1L, audit.getUserId());
        assertEquals("system_admin", audit.getUserRole());
        assertEquals("counselor", audit.getDetail().get("role"), "审计 detail 含 role");
        assertEquals(Map.of("phone", "visible", "id_card", "hidden"), audit.getDetail().get("fields"),
                "审计 detail 含变更字段集");

        assertEquals("visible", result.get("phone"), "返回该角色最新矩阵");
        assertEquals("hidden", result.get("id_card"));
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    private FieldPermission fp(Long id, String role, String field, String visibility) {
        FieldPermission fp = new FieldPermission();
        fp.setId(id);
        fp.setRole(role);
        fp.setField(field);
        fp.setVisibility(visibility);
        return fp;
    }
}
