package com.alumni.auth.config;

import com.alumni.auth.entity.AlumniBase;
import com.alumni.auth.entity.User;
import com.alumni.auth.repository.AlumniBaseRepository;
import com.alumni.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 种子数据（幂等）。
 * 仅保留系统管理员 admin / admin（本地测试环境）；不再生成演示校友/测试账号等人工数据，
 * 校友库与业务数据通过管理后台批量导入或激活流程产生。
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AlumniBaseRepository alumniBaseRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(AlumniBaseRepository alumniBaseRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.alumniBaseRepository = alumniBaseRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 系统管理员种子（幂等）：账号 admin，密码 admin（仅本地测试环境）
        seedSystemAdmin();
        // 各角色演示账号（幂等）：密码与 backend smoke.ps1 对齐，覆盖登录页快捷登录全部角色
        // 注意：smoke.ps1 用的是 13900010xxx 系（Alumni@123 等），这里是另一种命名
        // DataInitializer 的 password 实际不被前端快捷登录使用——仅作为 SQL 修复入口
        // 真正登录用的快捷账号见 smoke.ps1 + 前端 LoginPage 的 QUICK_ACCOUNTS
        seedRoleUser("T_STUDENT",  "学生演示",   "13900000001", "student",      2027, "计算机学院", "软件工程");
        seedRoleUser("T_ALUMNI",   "校友演示",   "13900000002", "alumni",       2022, "计算机学院", "软件工程");
        seedRoleUser("T_COUNSEL",  "导员演示",   "13900000003", "counselor",    2018, "计算机学院", "软件工程");
        seedRoleUser("T_COLLEGE",  "院系管理员", "13900000004", "college_admin", 2015, "计算机学院", "软件工程");
        seedRoleUser("T_SCHOOL",   "校级管理员", "13900000005", "school_admin",  2012, "信息中心", "平台管理");
        // [P1/DEMO] 修复演示学生账号（学号 2050001xxx 系）的密码 hash 与 status
        // 历史脚本直接 SQL 写入的 hash 与 Spring Security 的 BCrypt 校验可能不匹配，
        // 这里用 DataInitializer 启动期一次性重置这些演示账号的 hash 为「123456 的正确 BCrypt」，
        // 避免种子散列值在 Spring 升级后校验失败的兼容性陷阱。
        fixupDemoStudentAccounts();
    }

    /** [P1/DEMO] 重置学号 2050001xxx 系学生的密码 hash 为 123456（演示账号统一密码）。幂等。 */
    private void fixupDemoStudentAccounts() {
        String expectedHash = passwordEncoder.encode(sha256Hex("123456"));
        var all = userRepository.findAll();
        int fixed = 0;
        for (User u : all) {
            if (u.getStudentId() == null || !u.getStudentId().startsWith("205000")) continue;
            boolean dirty = false;
            if (!"active".equals(u.getStatus())) { u.setStatus("active"); dirty = true; }
            if (!expectedHash.equals(u.getPasswordHash())) { u.setPasswordHash(expectedHash); dirty = true; }
            if (dirty) { userRepository.save(u); fixed++; }
        }
        if (fixed > 0) {
            log.info("[DEMO] 修复 {} 个学生演示账号的密码 hash / status", fixed);
        }
    }

    /** 角色演示账号：手机号登录，密码 123456（仅本地测试环境）。
     *
     * 幂等逻辑（修复 #3：测试账号无法正常使用）：
     *   - phone 不存在 → 新建 alumni_base + user（与之前一致）
     *   - phone 存在但 role 不对（如先前版本只 seed 了 admin，新版补充缺失角色）→
     *     强制更新 role/nickname/password_hash/status = active，确保测试账号能登录
     *   - phone + role 都对 → 跳过（已 seed 完成）
     *
     * 这样无论之前跑过哪一版 DataInitializer，重启都会自动补齐缺失的 5 个测试账号
     * （学生/校友/导员/院系管理员/校级管理员 + system_admin），密码统一 123456。 */
    private void seedRoleUser(String studentId, String name, String phone, String role,
                              int gradYear, String college, String major) {
        String expectedHash = passwordEncoder.encode(sha256Hex("123456"));
        var existingByPhone = userRepository.findByPhone(phone).orElse(null);
        if (existingByPhone != null) {
            // phone 已存在 → 检查角色/状态是否正确，否则原地修补（不动校友库，避免重复插入）
            boolean dirty = false;
            if (!role.equals(existingByPhone.getRole())) {
                existingByPhone.setRole(role);
                dirty = true;
            }
            if (!"active".equals(existingByPhone.getStatus())) {
                existingByPhone.setStatus("active");
                dirty = true;
            }
            if (!expectedHash.equals(existingByPhone.getPasswordHash())) {
                existingByPhone.setPasswordHash(expectedHash);
                dirty = true;
            }
            if (dirty) {
                userRepository.save(existingByPhone);
                log.info("测试账号 {} {} 已修复（role/status/password）", role, phone);
            }
            return;
        }
        if (userRepository.existsByStudentId(studentId)) {
            // 学号已占用但 phone 不一致（说明用户通过激活流程改过 phone），不动他
            return;
        }
        AlumniBase base = alumniBaseRepository.findByStudentId(studentId).orElseGet(() -> {
            AlumniBase b = new AlumniBase();
            b.setStudentId(studentId);
            b.setRealName(name);
            b.setIdCard("SEED_" + studentId);
            b.setPhone(phone);
            b.setCollege(college);
            b.setMajor(major);
            b.setGradYear(gradYear);
            return alumniBaseRepository.save(b);
        });

        User user = new User();
        user.setUuid(java.util.UUID.randomUUID().toString());
        user.setAlumniBaseId(base.getId());
        user.setStudentId(studentId);
        user.setPhone(phone);
        user.setNickname(name);
        user.setRole(role);
        user.setStatus("active");
        user.setPasswordHash(expectedHash);
        userRepository.save(user);
        log.info("角色演示账号已初始化：{} {} / 123456", role, phone);
    }

    /**
     * 系统管理员种子：账号 admin / 密码 admin（前端 SHA-256 后传输，服务端 BCrypt 入库）。
     * 复用 users.phone 列存储用户名，alumni_base 占位行满足外键约束。幂等：已存在则跳过。
     */
    private void seedSystemAdmin() {
        if (userRepository.existsByPhone("admin")) {
            return;
        }
        AlumniBase base = alumniBaseRepository.findByStudentId("ADMIN").orElseGet(() -> {
            AlumniBase b = new AlumniBase();
            b.setStudentId("ADMIN");
            b.setRealName("系统管理员");
            b.setIdCard("ADMIN");
            b.setPhone("admin");
            b.setCollege("信息中心");
            b.setMajor("平台管理");
            b.setGradYear(2000);
            return alumniBaseRepository.save(b);
        });

        User user = new User();
        user.setUuid(java.util.UUID.randomUUID().toString());
        user.setAlumniBaseId(base.getId());
        user.setStudentId(base.getStudentId());
        user.setPhone("admin");
        user.setNickname("系统管理员");
        user.setRole("system_admin");
        user.setStatus("active");
        user.setPasswordHash(passwordEncoder.encode(sha256Hex("admin")));
        userRepository.save(user);
        log.info("系统管理员种子已初始化：admin / admin（仅本地测试环境，生产请立即改密）");
    }

    private static String sha256Hex(String input) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
