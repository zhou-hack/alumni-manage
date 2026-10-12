package com.alumni.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/**
 * 认证模块全部出入参 DTO。
 * 全局 Jackson 已配置 snake_case，Java 字段保持 camelCase 即可自动映射。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    private static final String PHONE = "^1\\d{10}$";
    // 登录账号：11 位手机号，或字母开头的用户名（如系统管理员 admin）
    private static final String ACCOUNT = "^(1\\d{10}|[A-Za-z][A-Za-z0-9_.-]{2,31})$";
    private static final String SMS_CODE = "^\\d{6}$";
    private static final String SHA256_HEX = "^[0-9a-f]{64}$";
    private static final String ID_CARD = "^\\d{15}(\\d{2}[0-9Xx])?$";

    // ---------- 请求 ----------

    /** 激活① 三要素核验 */
    public record ActivateVerifyRequest(
            @NotBlank(message = "学号不能为空") String studentId,
            @NotBlank(message = "姓名不能为空") String realName,
            @NotBlank(message = "身份证号不能为空")
            @Pattern(regexp = ID_CARD, message = "身份证号格式错误") String idCard) {
    }

    /** 激活② 发验证码（可换绑手机号） */
    public record ActivateSmsRequest(
            @NotBlank(message = "verify_token 不能为空") String verifyToken,
            @Pattern(regexp = PHONE, message = "手机号格式错误") String newPhone) {
    }

    /** 激活③ 验证码 + 设置密码完成激活 */
    public record ActivateConfirmRequest(
            @NotBlank(message = "verify_token 不能为空") String verifyToken,
            @NotBlank(message = "验证码不能为空")
            @Pattern(regexp = SMS_CODE, message = "验证码格式错误") String smsCode,
            @NotBlank(message = "密码不能为空")
            @Pattern(regexp = SHA256_HEX, message = "密码必须为 SHA-256 散列值") String password) {
    }

    /** 密码登录（phone / username 二选一，统一按账号匹配 users.phone 列） */
    public record LoginRequest(
            @Pattern(regexp = ACCOUNT, message = "账号格式错误") String phone,
            @Pattern(regexp = ACCOUNT, message = "账号格式错误") String username,
            @NotBlank(message = "密码不能为空")
            @Pattern(regexp = SHA256_HEX, message = "密码必须为 SHA-256 散列值") String password) {

        /** 实际登录账号：优先 phone，兼容管理端 username 字段 */
        public String account() {
            return phone != null && !phone.isBlank() ? phone : username;
        }
    }

    /** 验证码登录 */
    public record SmsLoginRequest(
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = PHONE, message = "手机号格式错误") String phone,
            @NotBlank(message = "验证码不能为空")
            @Pattern(regexp = SMS_CODE, message = "验证码格式错误") String smsCode) {
    }

    /** 通用发码：scene = login / reset_password / register */
    public record SendSmsRequest(
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = PHONE, message = "手机号格式错误") String phone,
            @NotBlank(message = "scene 不能为空")
            @Pattern(regexp = "^(login|reset_password|register)$",
                    message = "scene 仅支持 login / reset_password / register") String scene) {
    }

    /** 注册申请（申报制：无校友库核验，提交后账号 pending，人工审核通过方可登录） */
    public record RegisterRequest(
            @NotBlank(message = "学号不能为空")
            @Pattern(regexp = "^[A-Za-z0-9_-]{4,32}$", message = "学号格式错误（4~32 位字母/数字）") String studentId,
            @NotBlank(message = "姓名不能为空") String realName,
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = PHONE, message = "手机号格式错误") String phone,
            @NotBlank(message = "验证码不能为空")
            @Pattern(regexp = SMS_CODE, message = "验证码格式错误") String smsCode,
            @NotBlank(message = "密码不能为空")
            @Pattern(regexp = SHA256_HEX, message = "密码必须为 SHA-256 散列值") String password,
            @NotBlank(message = "学院不能为空") String college,
            @NotBlank(message = "专业不能为空") String major,
            @jakarta.validation.constraints.NotNull(message = "毕业年份不能为空")
            @jakarta.validation.constraints.Min(value = 1950, message = "毕业年份须 ≥1950")
            @jakarta.validation.constraints.Max(value = 2100, message = "毕业年份须 ≤2100") Integer gradYear,
            String note) {
    }

    /** 重置密码② 用验证码换 reset_token */
    public record ResetTokenRequest(
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = PHONE, message = "手机号格式错误") String phone,
            @NotBlank(message = "验证码不能为空")
            @Pattern(regexp = SMS_CODE, message = "验证码格式错误") String smsCode) {
    }

    /** 重置密码③ 凭 reset_token 设置新密码 */
    public record ResetPasswordRequest(
            @NotBlank(message = "新密码不能为空")
            @Pattern(regexp = SHA256_HEX, message = "密码必须为 SHA-256 散列值") String newPassword) {
    }

    /**
     * 当前用户主动改密（需登录）。验证旧密码后写入新密码，并作废该用户全部
     * refresh_token（强制其他设备重新登录）。前后端都已 SHA-256 散列传输。
     */
    public record ChangePasswordRequest(
            @NotBlank(message = "旧密码不能为空")
            @Pattern(regexp = SHA256_HEX, message = "密码必须为 SHA-256 散列值") String oldPassword,
            @NotBlank(message = "新密码不能为空")
            @Pattern(regexp = SHA256_HEX, message = "密码必须为 SHA-256 散列值") String newPassword) {

        /** 旧密码与新密码不能相同（防止误操作） */
        public boolean isSame() {
            return oldPassword != null && oldPassword.equals(newPassword);
        }
    }

    /** 刷新令牌 */
    public record RefreshRequest(
            @NotBlank(message = "refresh_token 不能为空") String refreshToken) {
    }

    // ---------- 响应 ----------

    /** 激活① 响应 */
    public record VerifyTokenData(String verifyToken, long expiresIn, String maskedPhone) {
    }

    /** 发码响应（dev_code 仅 Mock 模式回显） */
    public record SmsData(String maskedPhone, long expiresIn, String devCode) {
    }

    /** 当前用户信息（手机号已脱敏；uuid 供外部平台对接） */
    public record UserVO(Long id, String uuid, String studentId, String nickname, String role,
                         String phone, Instant createdAt) {
    }

    /** 登录 / 激活完成 / 刷新 响应 */
    public record TokenData(String accessToken, String refreshToken, long expiresIn, UserVO user) {
    }

    /** 重置密码② 响应 */
    public record ResetTokenData(String resetToken, long expiresIn) {
    }

    /** 注册申请响应：request_id 为申报单号（审核进度凭证） */
    public record RegisterData(long requestId, boolean resubmit) {
    }
}
