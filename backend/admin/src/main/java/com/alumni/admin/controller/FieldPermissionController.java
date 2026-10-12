package com.alumni.admin.controller;

import com.alumni.admin.common.ApiResponse;
import com.alumni.admin.dto.AdminDtos.FieldPermissionUpdateRequest;
import com.alumni.admin.dto.AdminDtos.MatrixResult;
import com.alumni.admin.service.FieldPermissionService;
import com.alumni.common.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 字段级权限控制器（[M15]，路径相对于 /api/v1）：
 *
 * <ul>
 *   <li>GET /admin/field-permissions        —— 角色—字段可见性矩阵（school_admin / system_admin）</li>
 *   <li>PUT /admin/field-permissions/{role} —— 更新某角色字段可见性（仅 system_admin）</li>
 * </ul>
 *
 * <p>URL 级角色限制见 SecurityConfig；职责：参数解析 → 调用 Service → 组装统一响应。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/field-permissions")
public class FieldPermissionController {

    private final FieldPermissionService fieldPermissionService;

    public FieldPermissionController(FieldPermissionService fieldPermissionService) {
        this.fieldPermissionService = fieldPermissionService;
    }

    /** GET /api/v1/admin/field-permissions —— 全量矩阵（未配置项按默认值补齐） */
    @GetMapping
    public ApiResponse<MatrixResult> matrix() {
        return ApiResponse.ok(fieldPermissionService.matrix());
    }

    /** PUT /api/v1/admin/field-permissions/{role} —— 按 (role, field) upsert，写审计 */
    @PutMapping("/{role}")
    public ApiResponse<Map<String, String>> update(@PathVariable("role") String role,
                                                   @Valid @RequestBody FieldPermissionUpdateRequest request) {
        return ApiResponse.ok(fieldPermissionService.update(currentUser(), role, request.fields()));
    }

    /** 从 SecurityContext 取已认证主体（SecurityConfig 已保证该路径必须登录） */
    private AuthPrincipal currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        return null;
    }
}
