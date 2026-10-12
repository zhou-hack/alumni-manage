package com.alumni.counselors.service;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.common.security.SuspendedUserStore;
import com.alumni.counselors.common.BizException;
import com.alumni.counselors.common.ErrorCode;
import com.alumni.counselors.dto.CounselorDtos.CounselorDetail;
import com.alumni.counselors.dto.CounselorDtos.CounselorItem;
import com.alumni.counselors.dto.CounselorDtos.CreateRequest;
import com.alumni.counselors.dto.CounselorDtos.PageResult;
import com.alumni.counselors.dto.CounselorDtos.ScopePayload;
import com.alumni.counselors.dto.CounselorDtos.UpdateRequest;
import com.alumni.counselors.entity.AlumniBase;
import com.alumni.counselors.entity.CounselorScope;
import com.alumni.counselors.entity.User;
import com.alumni.counselors.repository.AlumniBaseRepository;
import com.alumni.counselors.repository.CounselorScopeRepository;
import com.alumni.counselors.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 导员管理业务实现（《[定稿]API规范P1》第二章 M34）。
 *
 * <p>职责：导员账号生命周期（创建/修改/停用/启用/重置密码）与管辖范围维护。
 * 导员账号 = users(role='counselor') + alumni_base 桩行（users.alumni_base_id NOT NULL 有 FK）；
 * 管辖范围 = counselor_scopes（本学院内 届别∪专业∪学号 并集，每行一个维度值）。</p>
 *
 * <p>跨模块契约（共享库直写，JdbcTemplate，不依赖其他业务模块 jar）：
 * dicts 字典校验、audit_logs 审计、user_change_logs 留痕、refresh_tokens 吊销、
 * 停用时 reviews/archives/honors 在途单回退。</p>
 */
@Service
public class CounselorService {

    private static final String ROLE = "counselor";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_SUSPENDED = "suspended";
    private static final Set<String> STATUS_FILTER = Set.of(STATUS_ACTIVE, STATUS_SUSPENDED);
    private static final int MAX_PAGE_SIZE = 100;

    /** 在途单回退：提交人被该导员范围覆盖（届别/专业/学号任一命中且同学院）的 counselor 环节待审单 */
    private static final String INFLIGHT_SQL = """
            SELECT r.id AS id, r.biz_type AS biz_type, r.biz_id AS biz_id
            FROM reviews r
            JOIN users su ON su.id = r.submitter_id
            JOIN alumni_base ab ON ab.id = su.alumni_base_id
            WHERE r.stage = 'counselor' AND r.status = 'pending'
              AND EXISTS (
                SELECT 1 FROM counselor_scopes cs
                WHERE cs.counselor_id = ?
                  AND ab.college = (SELECT d.label FROM dicts d WHERE d.id = cs.college_id AND d.type = 'college')
                  AND (cs.grad_year = ab.grad_year
                       OR (cs.major_id IS NOT NULL
                           AND cs.major_id = (SELECT m.id FROM dicts m WHERE m.type = 'major' AND m.label = ab.major))
                       OR cs.student_id = ab.student_id))
            """;

    private final UserRepository userRepository;
    private final AlumniBaseRepository alumniBaseRepository;
    private final CounselorScopeRepository scopeRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SuspendedUserStore suspendedUserStore;
    private final ObjectMapper objectMapper;

    public CounselorService(UserRepository userRepository,
                            AlumniBaseRepository alumniBaseRepository,
                            CounselorScopeRepository scopeRepository,
                            JdbcTemplate jdbcTemplate,
                            PasswordEncoder passwordEncoder,
                            SuspendedUserStore suspendedUserStore,
                            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.alumniBaseRepository = alumniBaseRepository;
        this.scopeRepository = scopeRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.suspendedUserStore = suspendedUserStore;
        this.objectMapper = objectMapper;
    }

    // ---------- 列表 ----------

    /** GET /counselors：导员列表（school_admin；college_id/keyword/status 筛选，手机号脱敏） */
    @Transactional(readOnly = true)
    public PageResult<CounselorItem> page(AuthPrincipal operator, Long collegeId, String keyword,
                                          String status, int page, int pageSize) {
        requireSchoolAdmin(operator);
        String statusFilter = (status == null || status.isBlank()) ? null : status.trim();
        if (statusFilter != null && !STATUS_FILTER.contains(statusFilter)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "status 仅允许 active/suspended");
        }
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        StringBuilder from = new StringBuilder(
                "FROM users u JOIN alumni_base ab ON ab.id = u.alumni_base_id WHERE u.role = 'counselor'");
        List<Object> params = new ArrayList<>();
        if (collegeId != null) {
            from.append(" AND ab.college = (SELECT label FROM dicts WHERE id = ? AND type = 'college')");
            params.add(collegeId);
        }
        if (kw != null) {
            from.append(" AND (ab.real_name LIKE ? OR u.phone LIKE ?)");
            params.add("%" + kw + "%");
            params.add("%" + kw + "%");
        }
        if (statusFilter != null) {
            from.append(" AND u.status = ?");
            params.add(statusFilter);
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + from, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safeSize);
        pageParams.add((safePage - 1) * safeSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT u.id AS id, u.phone AS phone, u.role AS role, u.status AS status, u.created_at AS created_at,"
                        + " ab.real_name AS real_name, ab.college AS college,"
                        + " (SELECT cs.college_id FROM counselor_scopes cs WHERE cs.counselor_id = u.id LIMIT 1) AS college_id "
                        + from + " ORDER BY u.id DESC LIMIT ? OFFSET ?",
                pageParams.toArray());

        List<CounselorItem> list = rows.stream()
                .map(row -> new CounselorItem(
                        ((Number) row.get("id")).longValue(),
                        (String) row.get("real_name"),
                        maskPhone((String) row.get("phone")),
                        row.get("college_id") == null ? null : ((Number) row.get("college_id")).longValue(),
                        (String) row.get("college"),
                        (String) row.get("role"),
                        (String) row.get("status"),
                        toInstant(row.get("created_at"))))
                .toList();
        return new PageResult<>(list, total == null ? 0 : total, safePage, safeSize);
    }

    // ---------- 创建 ----------

    /** POST /counselors：创建导员（alumni_base 桩行 + users + counselor_scopes + 审计） */
    @Transactional
    public CounselorDetail create(AuthPrincipal operator, CreateRequest req) {
        requireSchoolAdmin(operator);
        String realName = req.realName() == null ? "" : req.realName().trim();
        if (realName.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "real_name 不能为空");
        }
        String phone = req.phone() == null ? "" : req.phone().trim();
        if (phone.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "phone 不能为空");
        }
        if (req.collegeId() == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "college_id 不能为空");
        }
        if (req.password() == null || req.password().isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "password 不能为空");
        }
        requireScope(req.scope());
        String collegeLabel = loadCollegeLabel(req.collegeId());
        validateMajors(req.scope().majorIds());
        if (userRepository.findByPhone(phone).isPresent()) {
            throw new BizException(ErrorCode.INVALID_STATE, "手机号已被占用");
        }

        // users.alumni_base_id NOT NULL 有 FK：先落 alumni_base 桩行（工号式学号 "T"+手机号）
        String stubStudentId = "T" + phone;
        AlumniBase base = new AlumniBase();
        base.setStudentId(stubStudentId);
        base.setRealName(realName);
        base.setIdCard("");
        base.setPhone(phone);
        base.setCollege(collegeLabel);
        base.setGradYear(0);
        try {
            base = alumniBaseRepository.saveAndFlush(base);
        } catch (DataIntegrityViolationException e) {
            throw new BizException(ErrorCode.INVALID_STATE, "手机号已被占用");
        }

        User user = new User();
        user.setUuid(UUID.randomUUID().toString());
        user.setAlumniBaseId(base.getId());
        user.setStudentId(stubStudentId);
        user.setPhone(phone);
        user.setNickname(realName);
        user.setRole(ROLE);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setStatus(STATUS_ACTIVE);
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BizException(ErrorCode.INVALID_STATE, "手机号已被占用");
        }

        insertScopes(user.getId(), req.collegeId(), req.scope());
        insertAudit(operator, "counselor_scope_change", user.getId(), scopeDetail(user.getId(), req.scope()));

        return new CounselorDetail(user.getId(), realName, maskPhone(phone), req.collegeId(), collegeLabel,
                ROLE, user.getStatus(), req.scope(), user.getCreatedAt());
    }

    // ---------- 详情 ----------

    /** GET /counselors/{id}：账号信息 + 管辖范围聚合（非导员/不存在 → 404） */
    @Transactional(readOnly = true)
    public CounselorDetail detail(AuthPrincipal operator, long id) {
        requireSchoolAdmin(operator);
        return buildDetail(findCounselor(id));
    }

    // ---------- 修改 ----------

    /** PATCH /counselors/{id}：改 real_name/phone/college_id/scope；scope 给了即全量替换并写审计 */
    @Transactional
    public CounselorDetail update(AuthPrincipal operator, long id, UpdateRequest req) {
        requireSchoolAdmin(operator);
        User user = findCounselor(id);
        AlumniBase base = alumniBaseRepository.findById(user.getAlumniBaseId())
                .orElseThrow(() -> new BizException(ErrorCode.INTERNAL_ERROR, "导员配套学籍数据缺失"));

        Long effectiveCollegeId = currentCollegeId(id);
        boolean collegeChanged = false;
        if (req.collegeId() != null) {
            String label = loadCollegeLabel(req.collegeId());
            base.setCollege(label);
            effectiveCollegeId = req.collegeId();
            collegeChanged = true;
        }
        if (req.realName() != null) {
            String realName = req.realName().trim();
            if (realName.isEmpty()) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "real_name 不能为空白");
            }
            user.setNickname(realName);
            base.setRealName(realName);
        }
        if (req.phone() != null && !req.phone().equals(user.getPhone())) {
            String phone = req.phone().trim();
            if (phone.isEmpty()) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "phone 不能为空白");
            }
            userRepository.findByPhone(phone).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new BizException(ErrorCode.INVALID_STATE, "手机号已被占用");
                }
            });
            user.setPhone(phone);
            base.setPhone(phone);
        }
        userRepository.save(user);
        alumniBaseRepository.save(base);

        if (req.scope() != null) {
            requireScope(req.scope());
            validateMajors(req.scope().majorIds());
            scopeRepository.deleteByCounselorId(id);
            insertScopes(id, effectiveCollegeId, req.scope());
            insertAudit(operator, "counselor_scope_change", id, scopeDetail(id, req.scope()));
        } else if (collegeChanged) {
            // 仅调整学院：既有范围行同学院同步（防越院）
            scopeRepository.updateCollegeIdByCounselorId(id, req.collegeId());
        }
        return buildDetail(user);
    }

    // ---------- 停用 / 启用 ----------

    /**
     * POST /counselors/{id}/disable：停权即刻生效（status=suspended + SuspendedUserStore +
     * 吊销全部 refresh_token）；名下 counselor 环节在途单回退至院系初审（reviews.stage→first，
     * archives/honors.status→pending_first），回退动作写审计与账号变更留痕。
     */
    @Transactional
    public Map<String, Object> disable(AuthPrincipal operator, long id) {
        requireSchoolAdmin(operator);
        User user = findCounselor(id);
        if (STATUS_SUSPENDED.equals(user.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE, "账号已处于停用状态");
        }
        user.setStatus(STATUS_SUSPENDED);
        userRepository.save(user);
        suspendedUserStore.suspend(id);
        jdbcTemplate.update("UPDATE refresh_tokens SET revoked = 1 WHERE user_id = ?", id);

        int reverted = rollbackInflight(id);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("counselor_id", id);
        detail.put("reverted_count", reverted);
        insertAudit(operator, "user_suspend", id, detail);
        insertChangeLog(id, "suspend", operator.userId(), Map.of("target_user_id", id));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", STATUS_SUSPENDED);
        result.put("reverted_count", reverted);
        return result;
    }

    /** POST /counselors/{id}/enable：恢复启用（status=active + SuspendedUserStore.resume + 留痕） */
    @Transactional
    public Map<String, Object> enable(AuthPrincipal operator, long id) {
        requireSchoolAdmin(operator);
        User user = findCounselor(id);
        if (STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE, "账号已处于启用状态");
        }
        user.setStatus(STATUS_ACTIVE);
        userRepository.save(user);
        suspendedUserStore.resume(id);
        insertChangeLog(id, "resume", operator.userId(), Map.of("target_user_id", id));
        return Map.of("id", id, "status", STATUS_ACTIVE);
    }

    // ---------- 重置密码 ----------

    /** POST /counselors/{id}/password/reset：BCrypt(SHA-256 散列值) 入库 + 吊销全部 refresh_token（同 auth resetPassword 口径） */
    @Transactional
    public Map<String, Object> resetPassword(AuthPrincipal operator, long id, String newPassword) {
        requireSchoolAdmin(operator);
        User user = findCounselor(id);
        if (newPassword == null || newPassword.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "new_password 不能为空");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        jdbcTemplate.update("UPDATE refresh_tokens SET revoked = 1 WHERE user_id = ?", id);
        return Map.of("id", id, "password_reset", true);
    }

    /* ---------------- 私有工具 ---------------- */

    private void requireSchoolAdmin(AuthPrincipal operator) {
        // 与前端路由 /counselors 的 roles 配置（school_admin / system_admin）和 SecurityConfig 一致
        if (operator == null
                || !("school_admin".equals(operator.role()) || "system_admin".equals(operator.role()))) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅校级管理员或系统管理员可管理导员");
        }
    }

    private User findCounselor(long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "导员不存在"));
        if (!ROLE.equals(user.getRole())) {
            throw new BizException(ErrorCode.NOT_FOUND, "导员不存在");
        }
        return user;
    }

    /** scope 必填：三维度至少一项非空，否则 400 SCOPE_REQUIRED（防无范围导员越权看到全院数据） */
    private void requireScope(ScopePayload scope) {
        if (scope == null
                || (isEmpty(scope.gradYears()) && isEmpty(scope.majorIds()) && isEmpty(scope.studentIds()))) {
            throw new BizException(ErrorCode.SCOPE_REQUIRED, "管辖范围必填：届别/专业/指定学号至少一项");
        }
    }

    /** 学院必须存在于 dicts(type='college')，返回其 label */
    private String loadCollegeLabel(Long collegeId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT label FROM dicts WHERE id = ? AND type = 'college'", String.class, collegeId);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "学院不存在: " + collegeId);
        }
    }

    /** major_ids 必须全部存在于 dicts(type='major') */
    private void validateMajors(List<Long> majorIds) {
        if (majorIds == null || majorIds.isEmpty()) {
            return;
        }
        List<Long> distinct = majorIds.stream().distinct().toList();
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dicts WHERE type = 'major' AND id IN (" + placeholders(distinct.size()) + ")",
                Long.class, distinct.toArray());
        if (count == null || count != distinct.size()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "专业不存在");
        }
    }

    /** 插入管辖范围：college_id 固定为所属学院，grad_years/major_ids/student_ids 每个值一行 */
    private void insertScopes(Long counselorId, Long collegeId, ScopePayload scope) {
        List<CounselorScope> rows = new ArrayList<>();
        if (scope.gradYears() != null) {
            for (Integer gradYear : scope.gradYears()) {
                CounselorScope row = newScope(counselorId, collegeId);
                row.setGradYear(gradYear);
                rows.add(row);
            }
        }
        if (scope.majorIds() != null) {
            for (Long majorId : scope.majorIds()) {
                CounselorScope row = newScope(counselorId, collegeId);
                row.setMajorId(majorId);
                rows.add(row);
            }
        }
        if (scope.studentIds() != null) {
            for (String studentId : scope.studentIds()) {
                CounselorScope row = newScope(counselorId, collegeId);
                row.setStudentId(studentId);
                rows.add(row);
            }
        }
        scopeRepository.saveAll(rows);
    }

    private CounselorScope newScope(Long counselorId, Long collegeId) {
        CounselorScope row = new CounselorScope();
        row.setCounselorId(counselorId);
        row.setCollegeId(collegeId);
        return row;
    }

    /** 在途单回退：返回回退的 reviews 数量 */
    private int rollbackInflight(long counselorId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(INFLIGHT_SQL, counselorId);
        if (rows.isEmpty()) {
            return 0;
        }
        List<Long> reviewIds = new ArrayList<>();
        List<Long> archiveIds = new ArrayList<>();
        List<Long> honorIds = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            reviewIds.add(((Number) row.get("id")).longValue());
            Object bizId = row.get("biz_id");
            if (bizId == null) {
                continue;
            }
            if ("archive".equals(row.get("biz_type"))) {
                archiveIds.add(((Number) bizId).longValue());
            } else if ("honor".equals(row.get("biz_type"))) {
                honorIds.add(((Number) bizId).longValue());
            }
        }
        jdbcTemplate.update("UPDATE reviews SET stage = 'first' WHERE id IN (" + placeholders(reviewIds.size()) + ")",
                reviewIds.toArray());
        if (!archiveIds.isEmpty()) {
            jdbcTemplate.update("UPDATE archives SET status = 'pending_first'"
                            + " WHERE status = 'pending_counselor' AND id IN (" + placeholders(archiveIds.size()) + ")",
                    archiveIds.toArray());
        }
        if (!honorIds.isEmpty()) {
            jdbcTemplate.update("UPDATE honors SET status = 'pending_first'"
                            + " WHERE status = 'pending_counselor' AND id IN (" + placeholders(honorIds.size()) + ")",
                    honorIds.toArray());
        }
        return reviewIds.size();
    }

    private CounselorDetail buildDetail(User user) {
        AlumniBase base = alumniBaseRepository.findById(user.getAlumniBaseId()).orElse(null);
        List<CounselorScope> rows = scopeRepository.findByCounselorIdOrderById(user.getId());
        return new CounselorDetail(
                user.getId(),
                base != null ? base.getRealName() : user.getNickname(),
                maskPhone(user.getPhone()),
                rows.isEmpty() ? null : rows.get(0).getCollegeId(),
                base != null ? base.getCollege() : null,
                user.getRole(),
                user.getStatus(),
                aggregateScope(rows),
                user.getCreatedAt());
    }

    private ScopePayload aggregateScope(List<CounselorScope> rows) {
        return new ScopePayload(
                rows.stream().filter(s -> s.getGradYear() != null).map(CounselorScope::getGradYear).toList(),
                rows.stream().filter(s -> s.getMajorId() != null).map(CounselorScope::getMajorId).toList(),
                rows.stream().filter(s -> s.getStudentId() != null).map(CounselorScope::getStudentId).toList());
    }

    private Long currentCollegeId(long counselorId) {
        List<CounselorScope> rows = scopeRepository.findByCounselorIdOrderById(counselorId);
        return rows.isEmpty() ? null : rows.get(0).getCollegeId();
    }

    /** 审计日志（只追加）：action 取值见 audit_logs.action 注释 */
    private void insertAudit(AuthPrincipal operator, String action, Long bizId, Map<String, Object> detail) {
        jdbcTemplate.update(
                "INSERT INTO audit_logs (user_id, user_role, action, biz_type, biz_id, detail)"
                        + " VALUES (?, ?, ?, 'counselor', ?, ?)",
                operator.userId(), operator.role(), action, bizId, toJson(detail));
    }

    /** 账号变更留痕 [M15]：suspend/resume */
    private void insertChangeLog(Long userId, String action, Long operatorId, Map<String, Object> detail) {
        jdbcTemplate.update(
                "INSERT INTO user_change_logs (user_id, action, operator_id, detail) VALUES (?, ?, ?, ?)",
                userId, action, operatorId, toJson(detail));
    }

    private Map<String, Object> scopeDetail(Long counselorId, ScopePayload scope) {
        Map<String, Object> scopeMap = new LinkedHashMap<>();
        scopeMap.put("grad_years", scope.gradYears() == null ? List.of() : scope.gradYears());
        scopeMap.put("major_ids", scope.majorIds() == null ? List.of() : scope.majorIds());
        scopeMap.put("student_ids", scope.studentIds() == null ? List.of() : scope.studentIds());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("counselor_id", counselorId);
        detail.put("scope", scopeMap);
        return detail;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("审计明细序列化失败", e);
        }
    }

    /** 手机号脱敏：138****1234 */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String placeholders(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    private static Instant toInstant(Object value) {
        return value instanceof Timestamp ts ? ts.toInstant() : null;
    }
}
