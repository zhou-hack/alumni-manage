package com.alumni.auth.service;

import com.alumni.auth.common.BizException;
import com.alumni.auth.common.ErrorCode;
import com.alumni.auth.common.MaskingUtils;
import com.alumni.auth.dto.AdminUserDtos.CreateResult;
import com.alumni.auth.dto.AdminUserDtos.CreateUserRequest;
import com.alumni.auth.dto.AdminUserDtos.Item;
import com.alumni.auth.dto.AdminUserDtos.PageResult;
import com.alumni.auth.dto.AdminUserDtos.ResetPasswordRequest;
import com.alumni.auth.dto.AdminUserDtos.UpdateUserRequest;
import com.alumni.auth.entity.AlumniBase;
import com.alumni.auth.entity.User;
import com.alumni.auth.repository.AlumniBaseRepository;
import com.alumni.auth.repository.RefreshTokenRepository;
import com.alumni.auth.repository.UserRepository;
import com.alumni.common.security.AuthPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 人员管理（管理端用户列表）业务实现。
 *
 * <p>职责：角色值校验（仅平台既有角色）、关键词检索（学号/昵称包含匹配）、
 * 分页查询 users 并联 alumni_base 补齐学籍信息（真实姓名/学院/专业/届别）、手机号脱敏出参；
 * 编辑用户信息（昵称/手机号换绑/角色/学籍信息）与管理员重置密码。</p>
 *
 * <p>隐私红线：手机号仅出脱敏形式；身份证号（alumni_base.id_card）任何情况下不出参、不可修改；
 * 密码散列等敏感字段不落 DTO。</p>
 */
@Service
public class AdminUserService {

    /** 平台既有角色（role 筛选参数白名单） */
    private static final Set<String> KNOWN_ROLES =
            Set.of("student", "alumni", "counselor", "college_admin", "school_admin", "system_admin");

    public static final int MAX_PAGE_SIZE = 100;

    /** 批量重置默认密码（超管操作；用户首次登录后应自行修改） */
    public static final String DEFAULT_PASSWORD = "furuanxy123";

    private final UserRepository userRepository;
    private final AlumniBaseRepository alumniBaseRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public AdminUserService(UserRepository userRepository,
                            AlumniBaseRepository alumniBaseRepository,
                            RefreshTokenRepository refreshTokenRepository,
                            PasswordEncoder passwordEncoder,
                            JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.alumniBaseRepository = alumniBaseRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 分页查询用户列表（含数据范围隔离）。
     *
     * @param operator 当前操作人：college_admin 强制本院（本人 alumni_base.college）；
     *                 counselor 要求命中 counselor_scopes；school/system 不限
     * @param role    角色筛选（student/alumni/...；空 = 全部）
     * @param keyword 关键词（学号/昵称包含匹配；空 = 不过滤）
     * @param college 学院（精确匹配 alumni_base.college；空 = 不过滤）
     * @param major   专业（包含匹配 alumni_base.major；空 = 不过滤）
     * @param gradYear 届别（精确匹配 alumni_base.grad_year；空 = 不过滤）
     * @param page    页码（1 起）
     * @param pageSize 页大小（1~100）
     */
    @Transactional(readOnly = true)
    public PageResult<Item> page(AuthPrincipal operator, String role, String keyword, String college, String major,
                                 Integer gradYear, int page, int pageSize) {
        String roleFilter = role == null || role.isBlank() ? null : role.trim().toLowerCase();
        if (roleFilter != null && !KNOWN_ROLES.contains(roleFilter)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "未知的角色筛选值: " + roleFilter);
        }
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page_size 须在 1~" + MAX_PAGE_SIZE + " 之间");
        }
        int safePage = Math.max(page, 1);

        // 数据范围：院级锁本院，导员锁 counselor_scopes（校级/系统不过滤）
        String scopeCollege = null;
        Long counselorId = null;
        if (operator != null) {
            if ("college_admin".equals(operator.role())) {
                scopeCollege = requireOperatorCollege(operator.userId());
            } else if ("counselor".equals(operator.role())) {
                counselorId = operator.userId();
            }
        }

        Page<User> result = userRepository.search(roleFilter, kw,
                college == null || college.isBlank() ? null : college.trim(),
                major == null || major.isBlank() ? null : major.trim(),
                gradYear, scopeCollege, counselorId,
                PageRequest.of(safePage - 1, pageSize, Sort.by(Sort.Direction.DESC, "id")));
        List<User> users = result.getContent();

        // 批量取学籍信息（real_name/college/major/grad_year），避免逐行回表
        Map<Long, AlumniBase> baseById = loadBases(users);

        List<Item> items = users.stream()
                .map(u -> toItem(u, baseById.get(u.getAlumniBaseId())))
                .toList();

        return new PageResult<>(items, result.getTotalElements(), safePage, pageSize);
    }

    /**
     * 管理员单个新增账号（POST /admin/users）。
     *
     * <p>口径：alumni_base + users 一并落库（id_card 桩值为空串，同导员建号先例），
     * 初始密码为 {@value #DEFAULT_PASSWORD}（服务端补 SHA-256 再 BCrypt，与批量重置同口径）；
     * 学号已在校友库但未激活时，直接挂载到既有校友库行（视为管理员代为激活，校友库学籍为准）。</p>
     *
     * <p>范围校验：college_admin 强制本院；counselor 强制其 scope 学院且须命中管辖维度
     * （届别/专业/指定学号任一）；school/system 不限。仅允许 student/alumni 角色（DTO 已约束）。</p>
     */
    @Transactional
    public CreateResult create(AuthPrincipal operator, CreateUserRequest req, String clientIp) {
        String studentId = req.studentId().trim();
        String realName = req.realName().trim();
        String phone = req.phone().trim();
        String role = req.role().trim().toLowerCase();

        if (userRepository.findByStudentId(studentId).isPresent()) {
            throw new BizException(ErrorCode.INVALID_STATE, "该学号已存在账号: " + studentId);
        }
        if (userRepository.existsByPhone(phone)) {
            throw new BizException(ErrorCode.INVALID_STATE, "手机号已被占用");
        }

        // 校友库：有则挂载（未激活的存量导入行），无则新建
        AlumniBase base = alumniBaseRepository.findByStudentId(studentId).orElse(null);
        boolean attach = base != null;

        String college = req.college() == null || req.college().isBlank() ? null : req.college().trim();
        String major = req.major() == null || req.major().isBlank() ? null : req.major().trim();
        Integer gradYear = req.gradYear();
        if (attach) {
            // 挂载场景以校友库学籍为准（身份一致性），范围校验也用库内值
            college = base.getCollege();
            major = base.getMajor();
            gradYear = base.getGradYear();
        }

        // 数据范围校验 + 学院落定（院级/导员强制本院）
        String effectiveCollege = resolveCreateCollege(operator, studentId, college, major, gradYear);

        if (!attach) {
            base = new AlumniBase();
            base.setStudentId(studentId);
            base.setRealName(realName);
            base.setIdCard("");
            base.setPhone(phone);
            base.setCollege(effectiveCollege);
            base.setMajor(major);
            base.setGradYear(gradYear);
            base = alumniBaseRepository.saveAndFlush(base);
        }

        User user = new User();
        user.setUuid(UUID.randomUUID().toString());
        user.setAlumniBaseId(base.getId());
        user.setStudentId(studentId);
        user.setPhone(phone);
        user.setNickname(attach ? base.getRealName() : realName);
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode(sha256Hex(DEFAULT_PASSWORD)));
        user.setStatus("active");
        user = userRepository.saveAndFlush(user);

        auditCreate(operator, user, attach, clientIp);
        return new CreateResult(toItem(user, base), DEFAULT_PASSWORD);
    }

    /** 新增账号的范围校验与学院落定：返回最终入库学院（强制覆盖时以操作人归属为准） */
    private String resolveCreateCollege(AuthPrincipal operator, String studentId,
                                        String college, String major, Integer gradYear) {
        if (operator == null) {
            return college;
        }
        return switch (operator.role()) {
            case "college_admin" -> requireOperatorCollege(operator.userId());
            case "counselor" -> {
                String scopeCollege = requireCounselorCollege(operator.userId());
                if (!counselorCovers(operator.userId(), studentId, major, gradYear)) {
                    throw new BizException(ErrorCode.FORBIDDEN, "该学生不在你的管辖范围内（届别/专业/指定学号均未命中）");
                }
                yield scopeCollege;
            }
            default -> college;
        };
    }

    /** 院级管理员学院归属：本人 alumni_base.college（AdminAccountService.collegeOf 同口径），缺失即越权 */
    private String requireOperatorCollege(Long userId) {
        User operatorUser = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "操作人账号不存在"));
        AlumniBase base = alumniBaseRepository.findById(operatorUser.getAlumniBaseId()).orElse(null);
        String college = base != null ? base.getCollege() : null;
        if (college == null || college.isBlank()) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前账号未维护学院归属，无法执行该操作");
        }
        return college;
    }

    /** 导员所属学院（scope 行的 college_id → dicts.label；无范围配置即越权） */
    private String requireCounselorCollege(Long counselorId) {
        List<String> labels = jdbcTemplate.queryForList(
                "SELECT DISTINCT d.label FROM counselor_scopes cs"
                        + " JOIN dicts d ON d.id = cs.college_id AND d.type = 'college'"
                        + " WHERE cs.counselor_id = ?",
                String.class, counselorId);
        if (labels.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN, "未配置管辖范围，请联系校级管理员");
        }
        return labels.get(0);
    }

    /** 导员管辖命中校验：届别 / 专业（label→dicts.id）/ 指定学号 任一命中（同学院已由 requireCounselorCollege 保证） */
    private boolean counselorCovers(Long counselorId, String studentId, String major, Integer gradYear) {
        Long majorId = null;
        if (major != null && !major.isBlank()) {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM dicts WHERE type = 'major' AND label = ?", Long.class, major.trim());
            majorId = ids.isEmpty() ? null : ids.get(0);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT grad_year, major_id, student_id FROM counselor_scopes WHERE counselor_id = ?",
                counselorId);
        for (Map<String, Object> row : rows) {
            if (row.get("student_id") != null && row.get("student_id").equals(studentId)) {
                return true;
            }
            if (row.get("grad_year") != null && gradYear != null
                    && ((Number) row.get("grad_year")).intValue() == gradYear) {
                return true;
            }
            if (row.get("major_id") != null && majorId != null
                    && ((Number) row.get("major_id")).longValue() == majorId) {
                return true;
            }
        }
        return false;
    }

    /** 新增账号审计（只追加；写失败仅记日志，不影响主流程） */
    private void auditCreate(AuthPrincipal operator, User user, boolean attach, String clientIp) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO audit_logs (user_id, user_role, action, biz_type, biz_id, detail, ip)"
                            + " VALUES (?, ?, 'admin_user_create', 'user', ?, ?, ?)",
                    operator != null ? operator.userId() : null,
                    operator != null ? operator.role() : null,
                    user.getId(),
                    "{\"student_id\":\"" + user.getStudentId() + "\",\"role\":\"" + user.getRole()
                            + "\",\"attach\":" + attach + "}",
                    clientIp);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AdminUserService.class)
                    .error("单个新增账号审计写入失败：operator={}", operator != null ? operator.userId() : null, e);
        }
    }

    /**
     * 编辑用户信息（昵称 / 手机号换绑 / 角色 / 学籍信息）。字段缺省 = 不修改；
     * 学号、身份证号不可修改。手机号换绑须全平台唯一。
     */
    @Transactional
    public Item update(Long id, UpdateUserRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "用户不存在: " + id));

        if (req.nickname() != null) {
            String nickname = req.nickname().trim();
            if (nickname.isEmpty()) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "昵称不能为空白");
            }
            user.setNickname(nickname);
        }
        if (req.phone() != null && !req.phone().equals(user.getPhone())) {
            // 换绑：手机号全平台唯一（与自身相同视为不修改）
            userRepository.findByPhone(req.phone()).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new BizException(ErrorCode.INVALID_STATE, "手机号已被其他账号占用");
                }
            });
            user.setPhone(req.phone());
        }
        if (req.role() != null) {
            user.setRole(req.role().trim().toLowerCase());
        }
        userRepository.save(user);

        AlumniBase base = alumniBaseRepository.findById(user.getAlumniBaseId()).orElse(null);
        if (base != null) {
            if (req.college() != null) {
                base.setCollege(req.college().trim());
            }
            if (req.major() != null) {
                base.setMajor(req.major().trim());
            }
            if (req.gradYear() != null) {
                base.setGradYear(req.gradYear());
            }
            alumniBaseRepository.save(base);
        }
        return toItem(user, base);
    }

    /**
     * 管理员重置密码：BCrypt(SHA-256 散列) 入库，并作废该用户全部 refresh_token
     * （强制所有已登录设备下线，与 /auth/password/reset 同口径）。
     */
    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "用户不存在: " + id));
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(id);
    }

    /**
     * 超管批量重置密码：统一重置为默认密码 {@value #DEFAULT_PASSWORD}（服务端补 SHA-256 再 BCrypt，
     * 与前端散列链路同口径），并作废每个用户全部 refresh_token（强制下线）。写审计。
     *
     * @return 实际重置的账号数（不存在的 id 跳过）
     */
    @Transactional
    public int batchResetToDefault(List<Long> userIds, Long operatorId, String operatorRole, String clientIp) {
        String hash = passwordEncoder.encode(sha256Hex(DEFAULT_PASSWORD));
        int reset = 0;
        for (Long id : userIds) {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                continue;
            }
            user.setPasswordHash(hash);
            userRepository.save(user);
            refreshTokenRepository.revokeAllByUserId(id);
            reset++;
        }
        auditBatchReset(operatorId, operatorRole, userIds, reset, clientIp);
        return reset;
    }

    /** 批量重置审计（只追加；写失败仅记日志，不影响主流程） */
    private void auditBatchReset(Long operatorId, String operatorRole, List<Long> userIds, int reset, String clientIp) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO audit_logs (user_id, user_role, action, biz_type, biz_id, detail, ip)"
                            + " VALUES (?, ?, 'admin_batch_reset_password', 'user', NULL, ?, ?)",
                    operatorId, operatorRole,
                    "{\"user_ids\":" + userIds + ",\"reset\":" + reset + "}", clientIp);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AdminUserService.class)
                    .error("批量重置密码审计写入失败：operator={}", operatorId, e);
        }
    }

    /** 密码链路：服务端为默认密码补 SHA-256（对齐「前端 SHA-256 → 服务端 BCrypt」口径） */
    private static String sha256Hex(String input) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** User + 学籍 → 列表项（手机号脱敏，身份证号永不出参） */
    private Item toItem(User u, AlumniBase base) {
        return new Item(
                u.getId(),
                u.getUuid(),
                u.getStudentId(),
                u.getNickname(),
                base != null ? base.getRealName() : null,
                MaskingUtils.maskPhone(u.getPhone()),
                u.getRole(),
                base != null ? base.getCollege() : null,
                base != null ? base.getMajor() : null,
                base != null ? base.getGradYear() : null,
                u.getCreatedAt());
    }

    private Map<Long, AlumniBase> loadBases(List<User> users) {
        if (users.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = users.stream().map(User::getAlumniBaseId).toList();
        Map<Long, AlumniBase> map = new HashMap<>(ids.size() * 2);
        for (AlumniBase base : alumniBaseRepository.findAllById(ids)) {
            map.put(base.getId(), base);
        }
        return map;
    }
}
