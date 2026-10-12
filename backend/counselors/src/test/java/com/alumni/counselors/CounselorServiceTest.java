package com.alumni.counselors;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.common.security.SuspendedUserStore;
import com.alumni.counselors.common.BizException;
import com.alumni.counselors.common.ErrorCode;
import com.alumni.counselors.dto.CounselorDtos.CounselorDetail;
import com.alumni.counselors.dto.CounselorDtos.CreateRequest;
import com.alumni.counselors.dto.CounselorDtos.ScopePayload;
import com.alumni.counselors.dto.CounselorDtos.UpdateRequest;
import com.alumni.counselors.entity.AlumniBase;
import com.alumni.counselors.entity.CounselorScope;
import com.alumni.counselors.entity.User;
import com.alumni.counselors.repository.AlumniBaseRepository;
import com.alumni.counselors.repository.CounselorScopeRepository;
import com.alumni.counselors.repository.UserRepository;
import com.alumni.counselors.service.CounselorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CounselorService 单元测试（Mockito mock Repository / JdbcTemplate，无 HTTP / 无数据库）。
 *
 * <p>覆盖：创建（正常 / SCOPE_REQUIRED / 学院非法 / 手机号冲突-查重 / 手机号冲突-并发唯一约束）、
 * scope 替换校验（正常替换 / 空 scope 拒绝）、disable 在途单回退（JdbcTemplate 直写验证）、
 * enable、重置密码吊销 refresh_token、非校级管理员 403、非导员 404。</p>
 */
@ExtendWith(MockitoExtension.class)
class CounselorServiceTest {

    private static final AuthPrincipal SCHOOL = new AuthPrincipal(2L, null, "school_admin");
    private static final AuthPrincipal COLLEGE = new AuthPrincipal(3L, null, "college_admin");

    @Mock
    private UserRepository userRepository;
    @Mock
    private AlumniBaseRepository alumniBaseRepository;
    @Mock
    private CounselorScopeRepository scopeRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SuspendedUserStore suspendedUserStore;

    private CounselorService service;

    @BeforeEach
    void setUp() {
        service = new CounselorService(userRepository, alumniBaseRepository, scopeRepository,
                jdbcTemplate, passwordEncoder, suspendedUserStore, new ObjectMapper());
    }

    // ------------------------------------------------------------------
    // 创建
    // ------------------------------------------------------------------

    /** 正常创建：alumni_base 桩行 + users(counselor/active) + 范围逐值一行 + 审计，手机号脱敏出参 */
    @Test
    void create_happyPath() {
        ScopePayload scope = new ScopePayload(List.of(2024, 2025), List.of(1L), List.of("2021010003"));
        CreateRequest req = new CreateRequest("王导员", "13900002000", 3L, "sha256-hash", scope);
        when(jdbcTemplate.queryForObject(contains("type = 'college'"), eq(String.class), any(Object[].class)))
                .thenReturn("计算机学院");
        when(jdbcTemplate.queryForObject(contains("type = 'major'"), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(userRepository.findByPhone("13900002000")).thenReturn(Optional.empty());
        when(alumniBaseRepository.saveAndFlush(any(AlumniBase.class))).thenAnswer(inv -> {
            AlumniBase b = inv.getArgument(0);
            b.setId(50L);
            return b;
        });
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(9L);
            return u;
        });
        when(passwordEncoder.encode("sha256-hash")).thenReturn("bcrypt-hash");

        CounselorDetail data = service.create(SCHOOL, req);

        assertEquals(9L, data.id());
        assertEquals("王导员", data.realName());
        assertEquals("139****2000", data.phone(), "手机号脱敏出参");
        assertEquals(3L, data.collegeId());
        assertEquals("计算机学院", data.college());
        assertEquals("counselor", data.role());
        assertEquals("active", data.status());
        assertEquals(List.of(2024, 2025), data.scope().gradYears());
        assertEquals(List.of(1L), data.scope().majorIds());
        assertEquals(List.of("2021010003"), data.scope().studentIds());

        ArgumentCaptor<AlumniBase> baseCaptor = ArgumentCaptor.forClass(AlumniBase.class);
        verify(alumniBaseRepository).saveAndFlush(baseCaptor.capture());
        AlumniBase stub = baseCaptor.getValue();
        assertEquals("T13900002000", stub.getStudentId(), "桩行学号 = T+手机号");
        assertEquals("", stub.getIdCard());
        assertEquals(0, stub.getGradYear());
        assertEquals("计算机学院", stub.getCollege());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertEquals(50L, saved.getAlumniBaseId(), "users 关联桩行 id");
        assertEquals("counselor", saved.getRole());
        assertEquals("active", saved.getStatus());
        assertEquals("王导员", saved.getNickname());
        assertEquals("bcrypt-hash", saved.getPasswordHash());
        assertNotNull(saved.getUuid());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CounselorScope>> scopeCaptor = ArgumentCaptor.forClass(List.class);
        verify(scopeRepository).saveAll(scopeCaptor.capture());
        List<CounselorScope> rows = scopeCaptor.getValue();
        assertEquals(4, rows.size(), "每个维度值一行");
        assertTrue(rows.stream().allMatch(r -> r.getCounselorId().equals(9L) && r.getCollegeId().equals(3L)));
        assertEquals(2, rows.stream().filter(r -> r.getGradYear() != null).count());
        assertEquals(1, rows.stream().filter(r -> r.getMajorId() != null).count());
        assertEquals(1, rows.stream().filter(r -> r.getStudentId() != null).count());

        verify(jdbcTemplate).update(contains("INSERT INTO audit_logs"),
                eq(2L), eq("school_admin"), eq("counselor_scope_change"), eq(9L),
                argThat(json -> json.toString().contains("\"counselor_id\":9")));
    }

    /** scope 为空对象（三维度全空）→ 400 SCOPE_REQUIRED，且不触库 */
    @Test
    void create_emptyScope_scopeRequired() {
        CreateRequest req = new CreateRequest("王导员", "13900002000", 3L, "sha256-hash",
                new ScopePayload(null, List.of(), null));

        BizException e = assertThrows(BizException.class, () -> service.create(SCHOOL, req));
        assertEquals(ErrorCode.SCOPE_REQUIRED, e.getErrorCode());
        verifyNoInteractions(userRepository, alumniBaseRepository, scopeRepository, jdbcTemplate);
    }

    /** scope 整体缺省 → 400 SCOPE_REQUIRED */
    @Test
    void create_nullScope_scopeRequired() {
        CreateRequest req = new CreateRequest("王导员", "13900002000", 3L, "sha256-hash", null);

        BizException e = assertThrows(BizException.class, () -> service.create(SCHOOL, req));
        assertEquals(ErrorCode.SCOPE_REQUIRED, e.getErrorCode());
        verifyNoInteractions(userRepository, jdbcTemplate);
    }

    /** college_id 在 dicts(type='college') 不存在 → 400 INVALID_PARAMETER */
    @Test
    void create_invalidCollege_badRequest() {
        CreateRequest req = new CreateRequest("王导员", "13900002000", 99L, "sha256-hash",
                new ScopePayload(List.of(2024), null, null));
        when(jdbcTemplate.queryForObject(contains("type = 'college'"), eq(String.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        BizException e = assertThrows(BizException.class, () -> service.create(SCHOOL, req));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verify(userRepository, never()).saveAndFlush(any());
    }

    /** 手机号已被其他账号占用（查重命中）→ 409 INVALID_STATE「手机号已被占用」 */
    @Test
    void create_phoneTaken_conflict() {
        CreateRequest req = new CreateRequest("王导员", "13900002000", 3L, "sha256-hash",
                new ScopePayload(List.of(2024), null, null));
        when(jdbcTemplate.queryForObject(contains("type = 'college'"), eq(String.class), any(Object[].class)))
                .thenReturn("计算机学院");
        when(userRepository.findByPhone("13900002000")).thenReturn(Optional.of(counselor(7L, "active")));

        BizException e = assertThrows(BizException.class, () -> service.create(SCHOOL, req));
        assertEquals(ErrorCode.INVALID_STATE, e.getErrorCode());
        assertTrue(e.getMessage().contains("手机号已被占用"));
        verify(alumniBaseRepository, never()).saveAndFlush(any());
    }

    /** 并发重复手机号：alumni_base/users 唯一约束冲突 → 409 INVALID_STATE */
    @Test
    void create_concurrentPhone_conflict() {
        CreateRequest req = new CreateRequest("王导员", "13900002000", 3L, "sha256-hash",
                new ScopePayload(List.of(2024), null, null));
        when(jdbcTemplate.queryForObject(contains("type = 'college'"), eq(String.class), any(Object[].class)))
                .thenReturn("计算机学院");
        when(userRepository.findByPhone("13900002000")).thenReturn(Optional.empty());
        when(alumniBaseRepository.saveAndFlush(any(AlumniBase.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_alumni_base_student_id'"));

        BizException e = assertThrows(BizException.class, () -> service.create(SCHOOL, req));
        assertEquals(ErrorCode.INVALID_STATE, e.getErrorCode());
        assertTrue(e.getMessage().contains("手机号已被占用"));
    }

    /** 非 school_admin → 403 */
    @Test
    void create_nonSchoolAdmin_forbidden() {
        CreateRequest req = new CreateRequest("王导员", "13900002000", 3L, "sha256-hash",
                new ScopePayload(List.of(2024), null, null));

        BizException e = assertThrows(BizException.class, () -> service.create(COLLEGE, req));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(userRepository, alumniBaseRepository, scopeRepository, jdbcTemplate);
    }

    // ------------------------------------------------------------------
    // 修改（scope 全量替换）
    // ------------------------------------------------------------------

    /** scope 给了即全量替换：删旧插新（沿用原学院），并写 counselor_scope_change 审计 */
    @Test
    void update_scopeReplace_ok() {
        User existing = counselor(9L, "active");
        when(userRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(alumniBaseRepository.findById(50L)).thenReturn(Optional.of(stubBase()));
        when(scopeRepository.findByCounselorIdOrderById(9L))
                .thenReturn(List.of(scopeRow(9L, 3L, 2024)));

        UpdateRequest req = new UpdateRequest(null, null, null,
                new ScopePayload(List.of(2026), null, List.of("2022010001")));
        service.update(SCHOOL, 9L, req);

        verify(scopeRepository).deleteByCounselorId(9L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CounselorScope>> captor = ArgumentCaptor.forClass(List.class);
        verify(scopeRepository).saveAll(captor.capture());
        List<CounselorScope> rows = captor.getValue();
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(r -> r.getCollegeId().equals(3L)), "沿用账号原学院，防越院");
        assertEquals(1, rows.stream().filter(r -> Integer.valueOf(2026).equals(r.getGradYear())).count());
        assertEquals(1, rows.stream().filter(r -> "2022010001".equals(r.getStudentId())).count());
        verify(jdbcTemplate).update(contains("INSERT INTO audit_logs"),
                eq(2L), eq("school_admin"), eq("counselor_scope_change"), eq(9L), any());
    }

    /** scope 给了但三维度全空 → 400 SCOPE_REQUIRED，且不删旧范围 */
    @Test
    void update_emptyScope_scopeRequired() {
        User existing = counselor(9L, "active");
        when(userRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(alumniBaseRepository.findById(50L)).thenReturn(Optional.of(stubBase()));
        when(scopeRepository.findByCounselorIdOrderById(9L))
                .thenReturn(List.of(scopeRow(9L, 3L, 2024)));

        UpdateRequest req = new UpdateRequest(null, null, null, new ScopePayload(null, null, List.of()));

        BizException e = assertThrows(BizException.class, () -> service.update(SCHOOL, 9L, req));
        assertEquals(ErrorCode.SCOPE_REQUIRED, e.getErrorCode());
        verify(scopeRepository, never()).deleteByCounselorId(any());
        verify(scopeRepository, never()).saveAll(any());
    }

    // ------------------------------------------------------------------
    // 停用 / 启用
    // ------------------------------------------------------------------

    /** 停用：status=suspended + SuspendedUserStore + 吊销 refresh_token +
     *  在途单回退（reviews→first，archives/honors→pending_first）+ 审计（含回退数量）+ 留痕 */
    @Test
    void disable_rollbackInflight() {
        User existing = counselor(9L, "active");
        when(userRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(jdbcTemplate.queryForList(contains("FROM reviews"), any(Object[].class)))
                .thenReturn(List.of(
                        Map.of("id", 1001L, "biz_type", "archive", "biz_id", 101L),
                        Map.of("id", 1002L, "biz_type", "honor", "biz_id", 202L)));

        Map<String, Object> result = service.disable(SCHOOL, 9L);

        assertEquals("suspended", existing.getStatus());
        assertEquals("suspended", result.get("status"));
        assertEquals(2, result.get("reverted_count"));

        verify(userRepository).save(existing);
        verify(suspendedUserStore).suspend(9L);
        verify(jdbcTemplate).update(eq("UPDATE refresh_tokens SET revoked = 1 WHERE user_id = ?"), eq(9L));
        verify(jdbcTemplate).update(contains("UPDATE reviews SET stage = 'first'"), any(Object[].class));
        verify(jdbcTemplate).update(contains("UPDATE archives SET status = 'pending_first'"), any(Object[].class));
        verify(jdbcTemplate).update(contains("UPDATE honors SET status = 'pending_first'"), any(Object[].class));
        verify(jdbcTemplate).update(contains("INSERT INTO audit_logs"),
                eq(2L), eq("school_admin"), eq("user_suspend"), eq(9L),
                argThat(json -> json.toString().contains("\"reverted_count\":2")));
        verify(jdbcTemplate).update(contains("INSERT INTO user_change_logs"), any(Object[].class));
    }

    /** 无在途单：回退数量为 0，不再直写 reviews/archives/honors */
    @Test
    void disable_noInflight() {
        User existing = counselor(9L, "active");
        when(userRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(jdbcTemplate.queryForList(contains("FROM reviews"), any(Object[].class)))
                .thenReturn(List.of());

        Map<String, Object> result = service.disable(SCHOOL, 9L);

        assertEquals(0, result.get("reverted_count"));
        verify(jdbcTemplate, never()).update(contains("UPDATE reviews SET stage"), any(Object[].class));
        verify(jdbcTemplate, never()).update(contains("UPDATE archives"), any(Object[].class));
        verify(jdbcTemplate, never()).update(contains("UPDATE honors"), any(Object[].class));
        verify(suspendedUserStore).suspend(9L);
    }

    /** 已停用账号重复停用 → 409 INVALID_STATE */
    @Test
    void disable_alreadySuspended_conflict() {
        User existing = counselor(9L, "suspended");
        when(userRepository.findById(9L)).thenReturn(Optional.of(existing));

        BizException e = assertThrows(BizException.class, () -> service.disable(SCHOOL, 9L));
        assertEquals(ErrorCode.INVALID_STATE, e.getErrorCode());
        verifyNoInteractions(jdbcTemplate, suspendedUserStore);
    }

    /** 启用：status=active + SuspendedUserStore.resume + 留痕 */
    @Test
    void enable_ok() {
        User existing = counselor(9L, "suspended");
        when(userRepository.findById(9L)).thenReturn(Optional.of(existing));

        Map<String, Object> result = service.enable(SCHOOL, 9L);

        assertEquals("active", existing.getStatus());
        assertEquals("active", result.get("status"));
        verify(userRepository).save(existing);
        verify(suspendedUserStore).resume(9L);
        verify(jdbcTemplate).update(contains("INSERT INTO user_change_logs"), any(Object[].class));
    }

    // ------------------------------------------------------------------
    // 重置密码
    // ------------------------------------------------------------------

    /** 重置密码：BCrypt(散列值) 入库 + 吊销该账号全部 refresh_token */
    @Test
    void resetPassword_revokesTokens() {
        User existing = counselor(9L, "active");
        when(userRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("new-sha256-hash")).thenReturn("bcrypt-new");

        Map<String, Object> result = service.resetPassword(SCHOOL, 9L, "new-sha256-hash");

        assertEquals("bcrypt-new", existing.getPasswordHash());
        assertEquals(true, result.get("password_reset"));
        verify(userRepository).save(existing);
        verify(jdbcTemplate).update(eq("UPDATE refresh_tokens SET revoked = 1 WHERE user_id = ?"), eq(9L));
    }

    // ------------------------------------------------------------------
    // 详情 / 越界
    // ------------------------------------------------------------------

    /** 非导员账号（role != counselor）→ 404 */
    @Test
    void detail_notCounselor_notFound() {
        User student = counselor(9L, "active");
        student.setRole("student");
        when(userRepository.findById(9L)).thenReturn(Optional.of(student));

        BizException e = assertThrows(BizException.class, () -> service.detail(SCHOOL, 9L));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    private User counselor(long id, String status) {
        User u = new User();
        u.setId(id);
        u.setUuid("uuid-" + id);
        u.setAlumniBaseId(50L);
        u.setStudentId("T13900002000");
        u.setPhone("13900002000");
        u.setNickname("王导员");
        u.setRole("counselor");
        u.setPasswordHash("old-hash");
        u.setStatus(status);
        return u;
    }

    private AlumniBase stubBase() {
        AlumniBase b = new AlumniBase();
        b.setId(50L);
        b.setStudentId("T13900002000");
        b.setRealName("王导员");
        b.setIdCard("");
        b.setPhone("13900002000");
        b.setCollege("计算机学院");
        b.setGradYear(0);
        return b;
    }

    private CounselorScope scopeRow(long counselorId, long collegeId, Integer gradYear) {
        CounselorScope row = new CounselorScope();
        row.setCounselorId(counselorId);
        row.setCollegeId(collegeId);
        row.setGradYear(gradYear);
        return row;
    }
}
