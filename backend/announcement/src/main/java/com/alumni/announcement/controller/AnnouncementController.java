package com.alumni.announcement.controller;

import com.alumni.announcement.common.ApiResponse;
import com.alumni.announcement.dto.AnnouncementDtos.AnnouncementItem;
import com.alumni.announcement.dto.AnnouncementDtos.CreateRequest;
import com.alumni.announcement.dto.AnnouncementDtos.PageResult;
import com.alumni.announcement.dto.AnnouncementDtos.UpdateRequest;
import com.alumni.announcement.service.AnnouncementService;
import com.alumni.common.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公告控制器（Controller 层，扩展模块 [M13]；路径相对于 /api/v1）：
 *
 * <ul>
 *   <li>GET    /announcements      —— 列表（登录；管理员可筛 status，非管理员仅见 published）</li>
 *   <li>GET    /announcements/{id} —— 详情（登录；非管理员仅可见 published）</li>
 *   <li>POST   /announcements      —— 创建（college_admin / school_admin，初始草稿，201）</li>
 *   <li>PATCH  /announcements/{id} —— 修改/发布/撤下（status 变更即发布或撤下）</li>
 *   <li>DELETE /announcements/{id} —— 删除</li>
 * </ul>
 *
 * <p>职责：参数解析 → 调用 Service → 组装统一响应；不含业务规则与 SQL。</p>
 */
@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    /** POST /api/v1/announcements —— 创建公告（草稿），成功返回 201 */
    @PostMapping
    public ResponseEntity<ApiResponse<AnnouncementItem>> create(
            @Valid @RequestBody CreateRequest request) {
        AnnouncementItem data = announcementService.create(currentUser(), request.title(), request.content(),
                request.coverImage());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
    }

    /** GET /api/v1/announcements —— 列表（?status=&page=&page_size=） */
    @GetMapping
    public ApiResponse<PageResult> page(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(announcementService.page(currentUser(), status, page, pageSize));
    }

    /** GET /api/v1/announcements/{id} —— 详情 */
    @GetMapping("/{id}")
    public ApiResponse<AnnouncementItem> detail(@PathVariable long id) {
        return ApiResponse.ok(announcementService.detail(currentUser(), id));
    }

    /** PATCH /api/v1/announcements/{id} —— 修改（title/content/status 均可选） */
    @PatchMapping("/{id}")
    public ApiResponse<AnnouncementItem> update(@PathVariable long id,
                                                @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(announcementService.update(currentUser(), id,
                request.title(), request.content(), request.coverImage(), request.status()));
    }

    /** DELETE /api/v1/announcements/{id} —— 删除 */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable long id) {
        announcementService.delete(currentUser(), id);
        return ApiResponse.ok(Map.of("id", id, "deleted", true));
    }

    /** 从 SecurityContext 取已认证主体（SecurityConfig 已保证 /api/v1/announcements/** 必须登录） */
    private AuthPrincipal currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        return null;
    }
}
