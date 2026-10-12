package com.alumni.admin.controller;

import com.alumni.admin.common.ApiResponse;
import com.alumni.admin.dto.AdminDtos.ChangeLogPage;
import com.alumni.admin.dto.AdminDtos.HandoverRequest;
import com.alumni.admin.dto.AdminDtos.HandoverResult;
import com.alumni.admin.dto.AdminDtos.SuspendRequest;
import com.alumni.admin.service.AdminAccountService;
import com.alumni.common.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号管理控制器（[M15]，路径相对于 /api/v1，仅 system_admin）：
 *
 * <ul>
 *   <li>POST /admin/users/{id}/suspend     —— 停权（Token 即刻作废）</li>
 *   <li>POST /admin/users/{id}/resume      —— 恢复</li>
 *   <li>POST /admin/users/{id}/handover    —— 账号交接（原账号停权，范围与在途待办转移）</li>
 *   <li>GET  /admin/users/{id}/change-logs —— 变更留痕（分页）</li>
 * </ul>
 *
 * <p>用户列表端点由 auth 模块 AdminUserController 提供，本控制器只声明 4 个动作端点。
 * URL 级角色限制见 SecurityConfig；Service 层仍校验 system_admin。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    /** POST /api/v1/admin/users/{id}/suspend —— 停权（reason 必填，不可停权自己） */
    @PostMapping("/{id}/suspend")
    public ApiResponse<Void> suspend(@PathVariable("id") long id,
                                     @Valid @RequestBody SuspendRequest request) {
        adminAccountService.suspend(currentUser(), id, request.reason());
        return ApiResponse.ok(null);
    }

    /** POST /api/v1/admin/users/{id}/resume —— 恢复 */
    @PostMapping("/{id}/resume")
    public ApiResponse<Void> resume(@PathVariable("id") long id) {
        adminAccountService.resume(currentUser(), id);
        return ApiResponse.ok(null);
    }

    /** POST /api/v1/admin/users/{id}/handover —— 交接（同角色同状态；导员/院系管理员仅限本院） */
    @PostMapping("/{id}/handover")
    public ApiResponse<HandoverResult> handover(@PathVariable("id") long id,
                                                @Valid @RequestBody HandoverRequest request) {
        return ApiResponse.ok(adminAccountService.handover(
                currentUser(), id, request.targetUserId(), request.reason()));
    }

    /** GET /api/v1/admin/users/{id}/change-logs?page=&page_size= —— 变更留痕（created_at 倒序） */
    @GetMapping("/{id}/change-logs")
    public ApiResponse<ChangeLogPage> changeLogs(@PathVariable("id") long id,
                                                 @RequestParam(name = "page", defaultValue = "1") int page,
                                                 @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(adminAccountService.changeLogs(currentUser(), id, page, pageSize));
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
