package com.alumni.auth.controller;

import com.alumni.auth.common.ApiResponse;
import com.alumni.auth.dto.AuthDtos.*;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口（Base URL: /api/v1，路径为相对路径，见 API 规范第二章）。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // ---------- 注册（申报制 + 人工审核） ----------

    /** 注册申请：验证码 + 申报学籍信息 → 账号 pending，reviews(activate_manual) 人工审核 */
    @PostMapping("/register")
    public ApiResponse<RegisterData> register(@Valid @RequestBody RegisterRequest req,
                                              HttpServletRequest request) {
        return ApiResponse.ok(authService.register(req, clientIp(request)));
    }

    // ---------- 激活 ----------

    /** 激活① 三要素核验 */
    @PostMapping("/activate/verify")
    public ApiResponse<VerifyTokenData> activateVerify(@Valid @RequestBody ActivateVerifyRequest req,
                                                       HttpServletRequest request) {
        return ApiResponse.ok(authService.activateVerify(req, clientIp(request)));
    }

    /** 激活② 发验证码（可换绑手机号） */
    @PostMapping("/activate/sms")
    public ApiResponse<SmsData> activateSms(@Valid @RequestBody ActivateSmsRequest req,
                                            HttpServletRequest request) {
        return ApiResponse.ok(authService.activateSms(req, clientIp(request)));
    }

    /** 激活③ 验证码 + 设置密码，完成激活并签发令牌 */
    @PostMapping("/activate/confirm")
    public ApiResponse<TokenData> activateConfirm(@Valid @RequestBody ActivateConfirmRequest req) {
        return ApiResponse.ok(authService.activateConfirm(req));
    }

    // ---------- 登录 ----------

    /** 密码登录 */
    @PostMapping("/login")
    public ApiResponse<TokenData> login(@Valid @RequestBody LoginRequest req,
                                        HttpServletRequest request) {
        org.slf4j.LoggerFactory.getLogger(AuthController.class).info("[debug] login req: phone={}, username={}, pwLen={}",
            req == null ? "null" : req.phone(),
            req == null ? "null" : req.username(),
            req == null ? -1 : req.password() == null ? -1 : req.password().length());
        return ApiResponse.ok(authService.login(req, clientIp(request)));
    }

    /** 验证码登录 */
    @PostMapping("/login/sms")
    public ApiResponse<TokenData> loginSms(@Valid @RequestBody SmsLoginRequest req,
                                           HttpServletRequest request) {
        return ApiResponse.ok(authService.loginSms(req, clientIp(request)));
    }

    /** 通用发码：{ "phone", "scene": "login" | "reset_password" } */
    @PostMapping("/sms")
    public ApiResponse<SmsData> sendSms(@Valid @RequestBody SendSmsRequest req,
                                        HttpServletRequest request) {
        return ApiResponse.ok(authService.sendSms(req, clientIp(request)));
    }

    // ---------- 忘记密码 ----------

    /** 重置② 校验验证码，签发 5 分钟一次性 reset_token */
    @PostMapping("/password/reset-token")
    public ApiResponse<ResetTokenData> resetToken(@Valid @RequestBody ResetTokenRequest req) {
        return ApiResponse.ok(authService.issueResetToken(req));
    }

    /** 重置③ 凭 reset_token（Bearer）设置新密码，成功后全部 Token 作废 */
    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(authorization, req);
        return ApiResponse.ok(null);
    }

    /**
     * 当前用户主动改密（需登录）：
     *   - 校验旧密码（前端 SHA-256 散列后传输）
     *   - 写入新密码
     *   - 作废该用户全部 refresh_token（其他设备被迫重新登录）
     * [P0 健康审计 2026-09-25]：补前端 ChangePasswordPage 调用的端点
     */
    @PostMapping("/password/change")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthPrincipal principal,
                                            @Valid @RequestBody ChangePasswordRequest req,
                                            HttpServletRequest request) {
        authService.changePassword(principal.userId(), req, clientIp(request));
        return ApiResponse.ok(null);
    }

    // ---------- 令牌与当前用户 ----------

    /** 刷新令牌（旧 refresh_token 即刻作废） */
    @PostMapping("/refresh")
    public ApiResponse<TokenData> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.ok(authService.refresh(req));
    }

    /** 登出（需登录）：refresh_token 作废 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthPrincipal principal,
                                    HttpServletRequest request) {
        authService.logout(principal.userId(), principal.role(), clientIp(request));
        return ApiResponse.ok(null);
    }

    /** 当前用户信息（需登录） */
    @GetMapping("/me")
    public ApiResponse<UserVO> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(authService.me(principal.userId()));
    }

    // ---------- 内部 ----------

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
