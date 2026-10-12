package com.alumni.showcase.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.showcase.common.ApiResponse;
import com.alumni.showcase.dto.AlumniProfileDtos.ProfileRequest;
import com.alumni.showcase.dto.AlumniProfileDtos.ProfileResponse;
import com.alumni.showcase.service.AlumniProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 校友大屏展示字段（V18）。
 *
 * 端点：
 * <ul>
 *   <li>GET    /api/v1/alumni/{id}/profile            —— 公开（门户大屏 / 校友通讯录消费）</li>
 *   <li>GET    /api/v1/admin/alumni/{id}/profile     —— 校级/系统管理员读</li>
 *   <li>PATCH  /api/v1/admin/alumni/{id}/profile     —— 校级/系统管理员写</li>
 *   <li>GET    /api/v1/me/profile                    —— 校友本人读</li>
 *   <li>PATCH  /api/v1/me/profile                    —— 校友本人写</li>
 * </ul>
 */
@RestController
public class AlumniProfileController {

    private final AlumniProfileService service;

    public AlumniProfileController(AlumniProfileService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/alumni/{id}/profile")
    public ApiResponse<ProfileResponse> getPublic(@PathVariable long id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/api/v1/admin/alumni/{id}/profile")
    public ApiResponse<ProfileResponse> getAdmin(@PathVariable long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PatchMapping("/api/v1/admin/alumni/{id}/profile")
    public ApiResponse<ProfileResponse> updateAdmin(
            @AuthenticationPrincipal AuthPrincipal operator,
            @PathVariable long id,
            @Valid @RequestBody ProfileRequest req
    ) {
        return ApiResponse.ok(service.updateByAdmin(operator, id, req));
    }

    @GetMapping("/api/v1/me/profile")
    public ApiResponse<ProfileResponse> getSelf(@AuthenticationPrincipal AuthPrincipal operator) {
        if (operator == null || operator.alumniBaseId() == null) {
            return ApiResponse.ok(null);
        }
        return ApiResponse.ok(service.get(operator.alumniBaseId()));
    }

    @PatchMapping("/api/v1/me/profile")
    public ApiResponse<ProfileResponse> updateSelf(
            @AuthenticationPrincipal AuthPrincipal operator,
            @Valid @RequestBody ProfileRequest req
    ) {
        return ApiResponse.ok(service.updateBySelf(operator, req));
    }
}
