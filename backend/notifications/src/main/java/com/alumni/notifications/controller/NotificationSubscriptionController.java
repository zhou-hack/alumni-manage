package com.alumni.notifications.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.notifications.common.ApiResponse;
import com.alumni.notifications.dto.SubscriptionDtos.MyResponse;
import com.alumni.notifications.dto.SubscriptionDtos.UpdateRequest;
import com.alumni.notifications.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部消息订阅控制器（[M14]；路径相对于 /api/v1）：
 *
 * <ul>
 *   <li>GET /notification-subscriptions/my —— 我的订阅配置（无记录默认全关）</li>
 *   <li>PUT /notification-subscriptions    —— 更新订阅（upsert，即时生效）</li>
 * </ul>
 *
 * <p>职责：参数解析 → 调用 Service → 组装统一响应；不含业务规则与 SQL。</p>
 */
@RestController
@RequestMapping("/api/v1/notification-subscriptions")
public class NotificationSubscriptionController {

    private final SubscriptionService subscriptionService;

    public NotificationSubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /** GET /api/v1/notification-subscriptions/my —— 我的订阅配置 */
    @GetMapping("/my")
    public ApiResponse<MyResponse> my() {
        return ApiResponse.ok(subscriptionService.my(currentUser()));
    }

    /** PUT /api/v1/notification-subscriptions —— 更新订阅（sms / email 整体 upsert） */
    @PutMapping
    public ApiResponse<MyResponse> update(@Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(subscriptionService.update(currentUser(), request));
    }

    /** 从 SecurityContext 取已认证主体（SecurityConfig 已保证 /api/v1/notification-subscriptions/** 必须登录） */
    private AuthPrincipal currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        return null;
    }
}
