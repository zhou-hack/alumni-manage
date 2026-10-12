package com.alumni.auth.controller;

import com.alumni.auth.common.ApiResponse;
import com.alumni.auth.dto.AdminUserDtos.BatchResetRequest;
import com.alumni.auth.dto.AdminUserDtos.BatchResetResult;
import com.alumni.auth.dto.AdminUserDtos.CreateResult;
import com.alumni.auth.dto.AdminUserDtos.CreateUserRequest;
import com.alumni.auth.dto.AdminUserDtos.Item;
import com.alumni.auth.dto.AdminUserDtos.PageResult;
import com.alumni.auth.dto.AdminUserDtos.ResetPasswordRequest;
import com.alumni.auth.dto.AdminUserDtos.UpdateUserRequest;
import com.alumni.auth.service.AdminUserService;
import com.alumni.common.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人员管理控制器（路径相对于 /api/v1，school_admin / system_admin）：
 *
 * <ul>
 *   <li>GET    /admin/users?role=alumni —— 校友列表</li>
 *   <li>GET    /admin/users?role=student —— 学生列表</li>
 *   <li>GET    /admin/users —— 全部人员（role 缺省）</li>
 *   <li>PATCH  /admin/users/{id} —— 编辑信息（昵称/手机号换绑/角色/学籍；缺省不改）</li>
 *   <li>POST   /admin/users/{id}/reset-password —— 重置密码（作废全部登录态）</li>
 * </ul>
 *
 * <p>统一查询参数：keyword（学号/昵称包含匹配）、page、page_size（上限 100）。
 * URL 级角色限制见 SecurityConfig（/admin/users/** → SCHOOL_ADMIN / SYSTEM_ADMIN）。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /** GET /api/v1/admin/users?role=&keyword=&college=&major=&grad_year=&page=&page_size= —— 人员列表（按角色/学籍筛选；院级锁本院，导员锁管辖范围） */
    @GetMapping
    public ApiResponse<PageResult<Item>> page(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "college", required = false) String college,
            @RequestParam(name = "major", required = false) String major,
            @RequestParam(name = "grad_year", required = false) Integer gradYear,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(adminUserService.page(principal, role, keyword, college, major, gradYear, page, pageSize));
    }

    /**
     * POST /api/v1/admin/users —— 管理员单个新增账号（student / alumni）。
     * 初始密码为默认密码（出参 initial_password 告知）；学号已在校友库未激活时挂载代为激活；
     * 院级强制本院、导员强制管辖范围内（越权 403）。
     */
    @PostMapping
    public ApiResponse<CreateResult> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateUserRequest req,
            HttpServletRequest request) {
        return ApiResponse.ok(adminUserService.create(principal, req, clientIp(request)));
    }

    /** PATCH /api/v1/admin/users/{id} —— 编辑用户信息（字段缺省 = 不修改；学号/身份证不可改） */
    @PatchMapping("/{id}")
    public ApiResponse<Item> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody UpdateUserRequest req) {
        return ApiResponse.ok(adminUserService.update(id, req));
    }

    /** POST /api/v1/admin/users/{id}/reset-password —— 重置密码（new_password 为 SHA-256 散列值） */
    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable("id") Long id,
                                           @Valid @RequestBody ResetPasswordRequest req) {
        adminUserService.resetPassword(id, req);
        return ApiResponse.ok(null);
    }

    /**
     * POST /api/v1/admin/users/batch-reset-password —— 批量重置为默认密码（仅 system_admin）。
     * 重置后各用户全部登录态作废；默认密码见 AdminUserService.DEFAULT_PASSWORD。
     */
    @PostMapping("/batch-reset-password")
    public ApiResponse<BatchResetResult> batchResetPassword(
            @Valid @RequestBody BatchResetRequest req,
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest request) {
        int reset = adminUserService.batchResetToDefault(req.userIds(),
                principal.userId(), principal.role(), clientIp(request));
        return ApiResponse.ok(new BatchResetResult(req.userIds().size(), reset));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
