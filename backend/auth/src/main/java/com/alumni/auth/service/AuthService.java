package com.alumni.auth.service;

import com.alumni.auth.common.BizException;
import com.alumni.auth.common.ErrorCode;
import com.alumni.auth.common.MaskingUtils;
import com.alumni.auth.dto.AuthDtos.*;
import com.alumni.auth.entity.AlumniBase;
import com.alumni.auth.entity.RefreshToken;
import com.alumni.auth.entity.User;
import com.alumni.auth.repository.AlumniBaseRepository;
import com.alumni.auth.repository.RefreshTokenRepository;
import com.alumni.auth.repository.UserRepository;
import com.alumni.auth.security.JwtService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.Year;
import java.util.HexFormat;
import java.util.Map;

/**
 * 认证核心业务：激活注册、登录、重置密码、令牌管理。
 * 密码全程以「前端 SHA-256 散列值」为输入，服务端 BCrypt 二次散列入库（API 规范 1.7）。
 *
 * <p>[P1] 停权账号登录 403（users.status='suspended'，规范 5.2）；
 * 认证事件写审计 audit_logs（login/login_failed/logout，规范 6.1，共享库直写 JdbcTemplate）。</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String SCENE_ACTIVATE = "activate";
    private static final String SCENE_LOGIN = "login";
    private static final String SCENE_RESET = "reset_password";
    private static final String SCENE_REGISTER = "register";
    /** [P1/M15] 账号状态：停权 */
    private static final String STATUS_SUSPENDED = "suspended";
    /** 注册申请制账号状态：待人工审核（登录 403） */
    private static final String STATUS_PENDING = "pending";
    /** 注册申请制账号状态：审核驳回（可重新提交注册申请） */
    private static final String STATUS_REJECTED = "rejected";

    private final AlumniBaseRepository alumniBaseRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SmsCodeService smsCodeService;
    private final TokenStoreService tokenStore;
    private final RateLimitService rateLimit;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RegistrationAdvisor registrationAdvisor;
    private final long refreshExpireSeconds;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AlumniBaseRepository alumniBaseRepository,
                       UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       SmsCodeService smsCodeService,
                       TokenStoreService tokenStore,
                       RateLimitService rateLimit,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       JdbcTemplate jdbcTemplate,
                       ObjectMapper objectMapper,
                       RegistrationAdvisor registrationAdvisor,
                       @Value("${app.refresh-expire-seconds}") long refreshExpireSeconds) {
        this.alumniBaseRepository = alumniBaseRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.smsCodeService = smsCodeService;
        this.tokenStore = tokenStore;
        this.rateLimit = rateLimit;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.registrationAdvisor = registrationAdvisor;
        this.refreshExpireSeconds = refreshExpireSeconds;
    }

    // ---------- 2.1 注册（申报制 + 人工审核） ----------

    /**
     * 注册申请：学校未提供校友库数据，注册改为申报制 —— 手机验证码 + 申报学籍信息，
     * 提交后账号 pending（登录 403 提示审核中），reviews(activate_manual) 单级人工审核：
     * 通过 → active；驳回 → rejected（允许修改资料后重新提交）。
     *
     * <p>事务内：alumni_base（申报学籍，id_card 恒空）+ users(pending) +
     * activate_manual_requests（申报单）+ reviews（审核单）。</p>
     *
     * <p>RegistrationAdvisor 为 AI 审核预留 SPI：当前 Mock 恒转人工，
     * 建议写入申报单 advisor_note + 审计日志，供审核人参考。</p>
     */
    @Transactional
    public RegisterData register(RegisterRequest req, String clientIp) {
        if (!rateLimit.tryAcquire("rl:register:ip:" + clientIp, 10, 3600)) {
            throw new BizException(ErrorCode.RATE_LIMITED, "请求过于频繁，请稍后再试");
        }
        String studentId = req.studentId().trim();
        String realName = req.realName().trim();
        String college = req.college().trim();
        String major = req.major().trim();
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();

        if (!smsCodeService.verify(req.phone(), SCENE_REGISTER, req.smsCode())) {
            throw new BizException(ErrorCode.AUTH_CODE_INVALID, "验证码错误或已过期");
        }

        User user = userRepository.findByStudentId(studentId).orElse(null);
        boolean resubmit = false;
        if (user != null) {
            if (STATUS_PENDING.equals(user.getStatus())) {
                throw new BizException(ErrorCode.INVALID_STATE, "注册申请正在审核中，请耐心等待");
            }
            if (STATUS_REJECTED.equals(user.getStatus())) {
                resubmit = true; // 驳回后重新提交：复用账号，更新申报资料后重新进入审核
            } else {
                throw new BizException(ErrorCode.AUTH_ALREADY_ACTIVATED, "该学号已注册，请直接登录");
            }
        }
        // 手机号全平台唯一（重新提交时与自身相同视为不冲突）
        User finalUser = user;
        userRepository.findByPhone(req.phone()).ifPresent(other -> {
            if (finalUser == null || !other.getId().equals(finalUser.getId())) {
                throw new BizException(ErrorCode.INVALID_STATE, "该手机号已被其他账号绑定");
            }
        });

        AlumniBase base;
        if (resubmit) {
            base = alumniBaseRepository.findById(user.getAlumniBaseId())
                    .orElseThrow(() -> new BizException(ErrorCode.INTERNAL_ERROR, "账号学籍数据缺失"));
            base.setRealName(realName);
            base.setPhone(req.phone());
            base.setCollege(college);
            base.setMajor(major);
            base.setGradYear(req.gradYear());
            alumniBaseRepository.save(base);

            user.setPhone(req.phone());
            user.setNickname(realName);
            user.setRole(req.gradYear() <= Year.now().getValue() ? "alumni" : "student");
            user.setStatus(STATUS_PENDING);
            user.setPasswordHash(passwordEncoder.encode(req.password()));
            userRepository.save(user);
            // 重新提交 = 重置全部登录态（密码已换）
            refreshTokenRepository.revokeAllByUserId(user.getId());
        } else {
            base = new AlumniBase();
            base.setStudentId(studentId);
            base.setRealName(realName);
            base.setIdCard(null); // 申报制不收集身份证号
            base.setPhone(req.phone());
            base.setCollege(college);
            base.setMajor(major);
            base.setGradYear(req.gradYear());
            alumniBaseRepository.save(base);

            user = new User();
            user.setUuid(java.util.UUID.randomUUID().toString());
            user.setAlumniBaseId(base.getId());
            user.setStudentId(studentId);
            user.setPhone(req.phone());
            user.setNickname(realName);
            user.setRole(req.gradYear() <= Year.now().getValue() ? "alumni" : "student");
            user.setStatus(STATUS_PENDING);
            user.setPasswordHash(passwordEncoder.encode(req.password()));
            userRepository.save(user);
        }

        // 智能审核建议（AI 预留；Mock 恒转人工）——实现异常不阻断申报主流程
        String advisorNote;
        try {
            advisorNote = registrationAdvisor.advise(new RegistrationAdvisor.RegistrationApplication(
                    studentId, realName, req.phone(), college, major, req.gradYear(), note)).note();
        } catch (Exception e) {
            log.warn("注册审核建议器执行异常（按转人工处理）：studentId={}", studentId, e);
            advisorNote = "建议器异常，转人工审核";
        }

        // 申报单（reviews.biz_id 指向它；request_id 即审核进度凭证）
        KeyHolder keyHolder = new GeneratedKeyHolder();
        final Long userId = user.getId();
        final String fStudentId = studentId;
        final String fRealName = realName;
        final String fNote = note;
        final String fAdvisorNote = advisorNote;
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO activate_manual_requests"
                            + " (user_id, student_id, real_name, contact_phone, note, advisor_note)"
                            + " VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, fStudentId);
            ps.setString(3, fRealName);
            ps.setString(4, req.phone());
            ps.setString(5, fNote);
            ps.setString(6, fAdvisorNote);
            return ps;
        }, keyHolder);
        long requestId = keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();

        // 审核单：activate_manual 校级单级（记录置于 second 阶段，仅 school_admin/system_admin 待办可见）
        jdbcTemplate.update(
                "INSERT INTO reviews (biz_type, biz_id, submitter_id, stage, status)"
                        + " VALUES ('activate_manual', ?, ?, 'second', 'pending')",
                requestId, userId);

        audit(userId, user.getRole(), resubmit ? "register_resubmit" : "register", requestId,
                Map.of("student_id", studentId, "advisor", advisorNote), clientIp);
        return new RegisterData(requestId, resubmit);
    }

    // ---------- 2.1 激活 ----------

    /** 激活①：三要素核验（限流：单 IP 10 次/小时 + 单学号 5 次/天） */
    public VerifyTokenData activateVerify(ActivateVerifyRequest req, String clientIp) {
        if (!rateLimit.tryAcquire("rl:verify:ip:" + clientIp, 10, 3600)
                || !rateLimit.tryAcquire("rl:verify:sid:" + req.studentId(), 5, 86400)) {
            throw new BizException(ErrorCode.RATE_LIMITED, "请求过于频繁，请稍后再试");
        }
        AlumniBase base = alumniBaseRepository
                .findByStudentIdAndRealNameAndIdCard(req.studentId(), req.realName(), req.idCard())
                // 不指明具体字段，防探测
                .orElseThrow(() -> new BizException(ErrorCode.AUTH_VERIFY_FAILED,
                        "信息不匹配，如确认本人信息无误，可联系管理员人工核验"));
        if (userRepository.existsByAlumniBaseId(base.getId())) {
            throw new BizException(ErrorCode.AUTH_ALREADY_ACTIVATED, "该学号已激活，请直接登录");
        }
        String token = tokenStore.createVerifyToken(base.getStudentId(), base.getPhone());
        return new VerifyTokenData(token, tokenStore.getVerifyExpireSeconds(),
                MaskingUtils.maskPhone(base.getPhone()));
    }

    /** 激活②：发验证码（可换绑手机号，换绑以验证码通过为准） */
    public SmsData activateSms(ActivateSmsRequest req, String clientIp) {
        TokenStoreService.VerifyContext ctx = tokenStore.getVerifyContext(req.verifyToken())
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "verify_token 无效或已过期，请重新核验"));
        String targetPhone = req.newPhone() != null ? req.newPhone() : ctx.registeredPhone();
        checkSmsIpLimit(clientIp, targetPhone);
        checkSmsResendInterval(targetPhone);
        tokenStore.updateTargetPhone(req.verifyToken(),
                new TokenStoreService.VerifyContext(ctx.studentId(), ctx.registeredPhone(), targetPhone));
        String devCode = smsCodeService.send(targetPhone, SCENE_ACTIVATE);
        return new SmsData(MaskingUtils.maskPhone(targetPhone), smsCodeService.getExpireSeconds(), devCode);
    }

    /** 激活③：验证码 + 设置密码，完成激活并直接签发令牌 */
    @Transactional
    public TokenData activateConfirm(ActivateConfirmRequest req) {
        TokenStoreService.VerifyContext ctx = tokenStore.getVerifyContext(req.verifyToken())
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "verify_token 无效或已过期，请重新核验"));
        if (!smsCodeService.verify(ctx.targetPhone(), SCENE_ACTIVATE, req.smsCode())) {
            throw new BizException(ErrorCode.AUTH_CODE_INVALID, "验证码错误或已过期");
        }
        AlumniBase base = alumniBaseRepository.findByStudentId(ctx.studentId())
                .orElseThrow(() -> new BizException(ErrorCode.AUTH_VERIFY_FAILED, "信息不匹配"));
        if (userRepository.existsByAlumniBaseId(base.getId())) {
            throw new BizException(ErrorCode.AUTH_ALREADY_ACTIVATED, "该学号已激活，请直接登录");
        }
        if (userRepository.existsByPhone(ctx.targetPhone())) {
            throw new BizException(ErrorCode.INVALID_STATE, "该手机号已被其他账号绑定");
        }
        User user = new User();
        user.setUuid(java.util.UUID.randomUUID().toString());
        user.setAlumniBaseId(base.getId());
        user.setStudentId(base.getStudentId());
        user.setPhone(ctx.targetPhone());
        user.setNickname(base.getRealName());
        // 激活时按校友库毕业信息自动标记：已毕业 alumni，未毕业 student
        user.setRole(base.getGradYear() <= Year.now().getValue() ? "alumni" : "student");
        user.setStatus("active");
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        userRepository.save(user);

        tokenStore.deleteVerifyToken(req.verifyToken());
        return issueTokens(user);
    }

    // ---------- 2.2 登录 ----------

    /** 密码登录（失败不指明字段，防账号枚举）；账号支持手机号或用户名；[P1] 停权账号 403，认证事件写审计 */
    public TokenData login(LoginRequest req, String clientIp) {
        if (req.account() == null || req.account().isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "账号不能为空");
        }
        User user = userRepository.findByPhone(req.account()).orElse(null);
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            audit(user == null ? null : user.getId(), user == null ? null : user.getRole(),
                    "login_failed", user == null ? null : user.getId(),
                    Map.of("reason", "bad_credentials"), clientIp);
            throw new BizException(ErrorCode.AUTH_VERIFY_FAILED, "手机号或密码错误");
        }
        requireLoginAllowed(user, clientIp);
        TokenData data = issueTokens(user);
        audit(user.getId(), user.getRole(), "login", user.getId(), null, clientIp);
        return data;
    }

    /** 验证码登录；[P1] 停权账号 403，认证事件写审计 */
    public TokenData loginSms(SmsLoginRequest req, String clientIp) {
        User user = userRepository.findByPhone(req.phone())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "该手机号未注册"));
        if (!smsCodeService.verify(req.phone(), SCENE_LOGIN, req.smsCode())) {
            audit(user.getId(), user.getRole(), "login_failed", user.getId(),
                    Map.of("reason", "bad_sms_code"), clientIp);
            throw new BizException(ErrorCode.AUTH_CODE_INVALID, "验证码错误或已过期");
        }
        requireLoginAllowed(user, clientIp);
        TokenData data = issueTokens(user);
        audit(user.getId(), user.getRole(), "login", user.getId(), null, clientIp);
        return data;
    }

    /** 通用发码：scene = login / reset_password / register（单 IP 10 个不同手机号/小时 + 同号 60 秒内仅允许请求一次） */
    public SmsData sendSms(SendSmsRequest req, String clientIp) {
        checkSmsIpLimit(clientIp, req.phone());
        checkSmsResendInterval(req.phone());
        String devCode = smsCodeService.send(req.phone(), req.scene());
        return new SmsData(MaskingUtils.maskPhone(req.phone()), smsCodeService.getExpireSeconds(), devCode);
    }

    // ---------- 2.3 忘记密码 ----------

    /** 重置②：校验验证码，签发 5 分钟一次性 reset_token */
    public ResetTokenData issueResetToken(ResetTokenRequest req) {
        userRepository.findByPhone(req.phone())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "该手机号未注册"));
        if (!smsCodeService.verify(req.phone(), SCENE_RESET, req.smsCode())) {
            throw new BizException(ErrorCode.AUTH_CODE_INVALID, "验证码错误或已过期");
        }
        return new ResetTokenData(tokenStore.createResetToken(req.phone()), tokenStore.getResetExpireSeconds());
    }

    /** 重置③：凭 reset_token 重置密码，成功后该账号全部 Token 作废（强制重新登录） */
    @Transactional
    public void resetPassword(String authorization, ResetPasswordRequest req) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "reset_token 缺失，请重新验证");
        }
        String phone = tokenStore.consumeResetToken(authorization.substring(7))
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "reset_token 无效或已过期，请重新验证"));
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "reset_token 无效或已过期，请重新验证"));
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    /**
     * 当前用户主动改密（[P0 健康审计 2026-09-25]：补 admin ChangePasswordPage 调用的端点）。
     * 校验旧密码正确 → 写入新密码 → 作废该用户全部 refresh_token（其他设备被迫重新登录）。
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req, String clientIp) {
        if (req.isSame()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "新密码不能与旧密码相同");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "账号不存在或已注销"));
        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            audit(userId, user.getRole(), "password_change_failed", userId,
                    Map.of("reason", "old_password_mismatch"), clientIp);
            throw new BizException(ErrorCode.AUTH_VERIFY_FAILED, "旧密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(userId);
        audit(userId, user.getRole(), "password_change", userId, null, clientIp);
    }

    // ---------- 2.4 令牌与当前用户 ----------

    /** 刷新令牌：旧 refresh_token 即刻作废（轮换制） */
    @Transactional
    public TokenData refresh(RefreshRequest req) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256Hex(req.refreshToken()))
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "refresh_token 无效或已过期"));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "refresh_token 无效或已过期");
        }
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "账号不存在或已注销"));
        return issueTokens(user);
    }

    /** 登出：该账号全部 refresh_token 作废；[P1] 写审计 logout */
    @Transactional
    public void logout(Long userId, String userRole, String clientIp) {
        refreshTokenRepository.revokeAllByUserId(userId);
        audit(userId, userRole, "logout", userId, null, clientIp);
    }

    /** 当前用户信息（手机号脱敏） */
    public UserVO me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "账号不存在或已注销"));
        return toVO(user);
    }

    // ---------- 内部 ----------

    /**
     * 登录前置状态校验（403 + 审计 login_failed）：
     * suspended 停权（规范 5.2）；pending 注册申请审核中；rejected 审核驳回（引导重新注册）。
     */
    private void requireLoginAllowed(User user, String clientIp) {
        String status = user.getStatus();
        String reason;
        String message;
        if (STATUS_SUSPENDED.equals(status)) {
            reason = "suspended";
            message = "账号已停权";
        } else if (STATUS_PENDING.equals(status)) {
            reason = "pending_review";
            message = "注册申请正在审核中，审核通过后即可登录";
        } else if (STATUS_REJECTED.equals(status)) {
            reason = "review_rejected";
            message = "注册申请未通过审核，请前往注册页修改资料后重新提交";
        } else {
            return;
        }
        audit(user.getId(), user.getRole(), "login_failed", user.getId(),
                Map.of("reason", reason), clientIp);
        throw new BizException(ErrorCode.FORBIDDEN, message);
    }

    /** [P1/M16] 认证事件审计（只追加；共享库直写）：写失败仅记日志，不影响认证主流程 */
    private void audit(Long userId, String userRole, String action, Long bizId,
                       Map<String, Object> detail, String clientIp) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO audit_logs (user_id, user_role, action, biz_type, biz_id, detail, ip)"
                            + " VALUES (?, ?, ?, 'user', ?, ?, ?)",
                    userId, userRole, action, bizId, detail == null ? null : toJson(detail), clientIp);
        } catch (Exception e) {
            log.error("认证审计日志写入失败：action={}, userId={}", action, userId, e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("审计明细序列化失败", e);
        }
    }

    /** IP 限流：单 IP 每小时最多 10 个不同手机号（同一手机号重复发码不受此限，只防换号轰炸） */
    private void checkSmsIpLimit(String clientIp, String phone) {
        if (!rateLimit.tryAcquireDistinct("rl:sms:ip:" + clientIp, phone, 10, 3600)) {
            throw new BizException(ErrorCode.RATE_LIMITED, "请求过于频繁，请稍后再试");
        }
    }

    /** 60 秒重发间隔：同一手机号 60 秒内仅允许请求一次 */
    private void checkSmsResendInterval(String phone) {
        if (!rateLimit.tryAcquire("rl:sms:interval:" + phone, 1, 60)) {
            throw new BizException(ErrorCode.RATE_LIMITED, "一分钟内请求过，请稍后再试");
        }
    }

    /** 签发 access_token（JWT 2h）+ refresh_token（不透明随机串，MySQL 存散列，7d） */
    private TokenData issueTokens(User user) {
        String accessToken = jwtService.generate(user.getId(), user.getStudentId(), user.getRole(), user.getAlumniBaseId());

        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String refreshToken = "r_tok_" + HexFormat.of().formatHex(bytes);

        RefreshToken entity = new RefreshToken();
        entity.setUserId(user.getId());
        entity.setTokenHash(sha256Hex(refreshToken));
        entity.setExpiresAt(Instant.now().plusSeconds(refreshExpireSeconds));
        refreshTokenRepository.save(entity);

        return new TokenData(accessToken, refreshToken, jwtService.getAccessExpireSeconds(), toVO(user));
    }

    private UserVO toVO(User user) {
        return new UserVO(user.getId(), user.getUuid(), user.getStudentId(), user.getNickname(), user.getRole(),
                MaskingUtils.maskPhone(user.getPhone()), user.getCreatedAt());
    }

    private static String sha256Hex(String input) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
