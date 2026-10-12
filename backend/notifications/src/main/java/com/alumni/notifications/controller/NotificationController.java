package com.alumni.notifications.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.notifications.common.ApiResponse;
import com.alumni.notifications.dto.NotificationDtos.PageResult;
import com.alumni.notifications.dto.NotificationDtos.UnreadCount;
import com.alumni.notifications.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站内消息控制器（[M14]；路径相对于 /api/v1）：
 *
 * <ul>
 *   <li>GET  /notifications              —— 我的消息（?type=&unread=true&page=&page_size=，仅本人）</li>
 *   <li>GET  /notifications/unread-count —— 未读数（红点）</li>
 *   <li>POST /notifications/{id}/read    —— 标记已读（仅本人，幂等）</li>
 *   <li>POST /notifications/read-all     —— 全部已读（可带 ?type=）</li>
 * </ul>
 *
 * <p>职责：参数解析 → 调用 Service → 组装统一响应；不含业务规则与 SQL。
 * 本模块不提供手动发送接口，消息由事件源模块共享库直写生成。</p>
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** GET /api/v1/notifications —— 我的消息列表 */
    @GetMapping
    public ApiResponse<PageResult> page(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "unread", defaultValue = "false") boolean unread,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(notificationService.page(currentUser(), type, unread, page, pageSize));
    }

    /** GET /api/v1/notifications/unread-count —— 未读数（by_type 仅含有未读的类型） */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCount> unreadCount() {
        return ApiResponse.ok(notificationService.unreadCount(currentUser()));
    }

    /** POST /api/v1/notifications/{id}/read —— 标记已读（仅本人，幂等） */
    @PostMapping("/{id}/read")
    public ApiResponse<Map<String, Object>> markRead(@PathVariable long id) {
        return ApiResponse.ok(notificationService.markRead(currentUser(), id));
    }

    /** POST /api/v1/notifications/read-all —— 全部已读（可带 ?type=），返回 {updated: n} */
    @PostMapping("/read-all")
    public ApiResponse<Map<String, Object>> markAllRead(
            @RequestParam(name = "type", required = false) String type) {
        return ApiResponse.ok(notificationService.markAllRead(currentUser(), type));
    }

    /** 从 SecurityContext 取已认证主体（SecurityConfig 已保证 /api/v1/notifications/** 必须登录） */
    private AuthPrincipal currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        return null;
    }
}
