package com.alumni.consents.controller;

import com.alumni.consents.common.ApiResponse;
import com.alumni.consents.common.BizException;
import com.alumni.consents.common.ErrorCode;
import com.alumni.consents.dto.ConsentDtos.CreateConsentRequest;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.consents.service.ConsentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 隐私授权控制器（Controller 层）。
 *
 * <p>实现《[定稿]API规范》第九章接口（路径相对于 /api/v1）：</p>
 * <ul>
 *   <li>POST   /consents      —— 创建授权（student / alumni，仅本人档案）</li>
 *   <li>GET    /consents/text —— 当前生效授权文本（登录，[P1/M16] 授权版本化）</li>
 *   <li>GET    /consents/my   —— 我的授权记录（student / alumni）</li>
 *   <li>DELETE /consents/{id} —— 撤回授权（本人，screen 授权级联下架）</li>
 * </ul>
 *
 * <p>职责：参数解析 → 调用 Service → 组装统一响应。
 * 不含任何业务规则与 SQL。</p>
 */
@RestController
@RequestMapping("/api/v1/consents")
public class ConsentController {

    private final ConsentService consentService;

    public ConsentController(ConsentService consentService) {
        this.consentService = consentService;
    }

    /** POST /api/v1/consents —— 创建授权，成功返回 201 */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody CreateConsentRequest request) {
        Map<String, Object> data = consentService.create(currentUser(), request.archiveId(), request.scope());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
    }

    /** GET /api/v1/consents/text —— 当前生效授权文本（登录，version + 全文，前端授权弹窗展示） */
    @GetMapping("/text")
    public ApiResponse<Map<String, Object>> text() {
        return ApiResponse.ok(consentService.currentText());
    }

    /** GET /api/v1/consents/my —— 我的授权记录（created_at 倒序） */
    @GetMapping("/my")
    public ApiResponse<Map<String, Object>> my() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", consentService.my(currentUser()));
        return ApiResponse.ok(data);
    }

    /** DELETE /api/v1/consents/{id} —— 撤回授权（screen 授权同步级联下架） */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> revoke(@PathVariable long id) {
        if (id <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "id 必须为正整数");
        }
        return ApiResponse.ok(consentService.revoke(currentUser(), id));
    }

    /** 从 SecurityContext 取已认证主体（SecurityConfig 已保证 /api/v1/consents/** 必须登录） */
    private AuthPrincipal currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        return null;
    }
}
