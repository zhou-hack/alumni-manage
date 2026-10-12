package com.alumni.consents;

import com.alumni.consents.common.BizException;
import com.alumni.consents.common.ErrorCode;
import com.alumni.consents.entity.Archive;
import com.alumni.consents.entity.Consent;
import com.alumni.consents.entity.ConsentText;
import com.alumni.consents.repository.ArchiveRepository;
import com.alumni.consents.repository.ConsentRepository;
import com.alumni.consents.repository.ConsentTextRepository;
import com.alumni.consents.repository.ShowcaseRepository;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.consents.service.ConsentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ConsentService 单元测试（Mockito mock Repository，无 HTTP / 无数据库）。
 *
 * <p>覆盖：创建授权（正常 / 角色 403 / 参数 400 / 档案不存在 404 / 越权 403 /
 * 重复 409 / 唯一约束并发兜底 409）、我的授权倒序列表、
 * 撤回授权（screen 级联下架 / site 不级联 / 非本人 403 / 不存在 404）。</p>
 *
 * <p>[P1/M16] 授权版本化：创建写入 version/consent_text_hash（consent_texts 空表容错）、
 * /my 出参含 version/consent_text_hash/granted_at/withdrawn_at、授权与撤回写审计。</p>
 */
@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    private static final AuthPrincipal ALUMNI = new AuthPrincipal(100L, "2021001", "alumni");
    private static final AuthPrincipal STUDENT = new AuthPrincipal(101L, "2023002", "student");
    private static final AuthPrincipal SCHOOL = new AuthPrincipal(2L, null, "school_admin");

    @Mock
    private ConsentRepository consentRepository;
    @Mock
    private ArchiveRepository archiveRepository;
    @Mock
    private ShowcaseRepository showcaseRepository;
    @Mock
    private ConsentTextRepository consentTextRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ConsentService service;

    @BeforeEach
    void setUp() {
        service = new ConsentService(consentRepository, archiveRepository, showcaseRepository,
                consentTextRepository, entityManager, jdbcTemplate, objectMapper);
    }

    /** 正常创建：返回 201 数据（id / archive_id / scope / created_at 完整），user_id 记为当前用户 */
    @Test
    void create_happyPath() {
        stubOwnArchive(12L);
        when(consentRepository.saveAndFlush(any(Consent.class))).thenAnswer(inv -> {
            Consent c = inv.getArgument(0);
            c.setId(501L);
            c.setCreatedAt(Instant.parse("2026-09-18T02:56:00Z"));
            return c;
        });

        Map<String, Object> data = service.create(ALUMNI, 12L, "screen");

        assertEquals(501L, data.get("id"));
        assertEquals(12L, data.get("archive_id"));
        assertEquals("screen", data.get("scope"));
        assertEquals(Instant.parse("2026-09-18T02:56:00Z"), data.get("created_at"));

        ArgumentCaptor<Consent> captor = ArgumentCaptor.forClass(Consent.class);
        verify(consentRepository).saveAndFlush(captor.capture());
        assertEquals(100L, captor.getValue().getUserId(), "授权归属当前用户");
        assertEquals(12L, captor.getValue().getArchiveId());
        assertEquals("screen", captor.getValue().getScope());
        verify(entityManager).refresh(any(Consent.class));
    }

    /** student 角色同样可创建授权 */
    @Test
    void create_studentRole_ok() {
        Archive archive = archive(20L, STUDENT.userId());
        when(archiveRepository.findById(20L)).thenReturn(Optional.of(archive));
        when(consentRepository.saveAndFlush(any(Consent.class))).thenAnswer(inv -> {
            Consent c = inv.getArgument(0);
            c.setId(502L);
            c.setCreatedAt(Instant.parse("2026-09-18T03:00:00Z"));
            return c;
        });

        Map<String, Object> data = service.create(STUDENT, 20L, "site");

        assertEquals(502L, data.get("id"));
        assertEquals("site", data.get("scope"));
    }

    /** school_admin 等非 student/alumni 角色 → 403，且不触库 */
    @Test
    void create_nonStudentOrAlumni_forbidden() {
        BizException e = assertThrows(BizException.class, () -> service.create(SCHOOL, 12L, "screen"));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(archiveRepository, consentRepository, showcaseRepository);
    }

    /** archive_id 非正整数 → 400 */
    @Test
    void create_invalidArchiveId_badRequest() {
        BizException e = assertThrows(BizException.class, () -> service.create(ALUMNI, 0L, "screen"));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verifyNoInteractions(archiveRepository, consentRepository);
    }

    /** scope 非法（仅允许 screen/site）→ 400 */
    @Test
    void create_invalidScope_badRequest() {
        BizException e = assertThrows(BizException.class, () -> service.create(ALUMNI, 12L, "public"));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        assertTrue(e.getMessage().contains("scope"));
        verifyNoInteractions(archiveRepository, consentRepository);
    }

    /** 档案不存在 → 404 */
    @Test
    void create_archiveNotFound() {
        when(archiveRepository.findById(99L)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class, () -> service.create(ALUMNI, 99L, "screen"));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verify(consentRepository, never()).saveAndFlush(any());
    }

    /** 越权红线：替他人档案授权 → 403 */
    @Test
    void create_otherUsersArchive_forbidden() {
        Archive archive = archive(12L, 200L); // 归属 user 200，非当前用户 100
        when(archiveRepository.findById(12L)).thenReturn(Optional.of(archive));

        BizException e = assertThrows(BizException.class, () -> service.create(ALUMNI, 12L, "screen"));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verify(consentRepository, never()).saveAndFlush(any());
    }

    /** 同一 (archive_id, scope) 已存在授权 → 409 */
    @Test
    void create_duplicate_conflict() {
        stubOwnArchive(12L);
        when(consentRepository.existsByArchiveIdAndScope(12L, "screen")).thenReturn(true);

        BizException e = assertThrows(BizException.class, () -> service.create(ALUMNI, 12L, "screen"));
        assertEquals(ErrorCode.INVALID_STATE, e.getErrorCode());
        verify(consentRepository, never()).saveAndFlush(any());
    }

    /** 并发重复授权：uk_consents_archive_scope 唯一约束冲突 → 409 */
    @Test
    void create_concurrentDuplicate_conflict() {
        stubOwnArchive(12L);
        when(consentRepository.saveAndFlush(any(Consent.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_consents_archive_scope'"));

        BizException e = assertThrows(BizException.class, () -> service.create(ALUMNI, 12L, "screen"));
        assertEquals(ErrorCode.INVALID_STATE, e.getErrorCode());
    }

    /** [P1/M16] 创建授权：快照当前授权文本 version + SHA-256 摘要入库，响应含 version，写 consent_grant 审计 */
    @Test
    void create_writesVersionAndHash() {
        stubOwnArchive(12L);
        ConsentText text = consentText(7, 2, "授权文本V2全文");
        when(consentTextRepository.findTopByOrderByVersionDesc()).thenReturn(Optional.of(text));
        when(consentRepository.saveAndFlush(any(Consent.class))).thenAnswer(inv -> {
            Consent c = inv.getArgument(0);
            c.setId(501L);
            c.setCreatedAt(Instant.parse("2026-09-18T02:56:00Z"));
            return c;
        });

        Map<String, Object> data = service.create(ALUMNI, 12L, "screen");

        assertEquals(2, data.get("version"), "响应含当前授权文本版本号");
        ArgumentCaptor<Consent> captor = ArgumentCaptor.forClass(Consent.class);
        verify(consentRepository).saveAndFlush(captor.capture());
        assertEquals(2, captor.getValue().getVersion(), "授权记录快照当时文本版本");
        assertEquals(sha256Hex("授权文本V2全文"), captor.getValue().getConsentTextHash(),
                "授权记录快照文本 SHA-256 摘要（hex）");

        // 审计：action=consent_grant，biz_type=consent，biz_id=授权 id，detail 含 archive_id/scope/version
        verify(jdbcTemplate).update(
                argThat(sql -> sql != null && sql.contains("audit_logs")),
                eq(100L), eq("alumni"), eq("consent_grant"), eq(501L),
                argThat((String json) -> json.contains("\"archive_id\":12")
                        && json.contains("\"scope\":\"screen\"")
                        && json.contains("\"version\":2")));
    }

    /** [P1/M16] consent_texts 空表容错：version/hash 为 null，不阻断授权，审计仍写入 */
    @Test
    void create_emptyConsentText_versionNullButGranted() {
        stubOwnArchive(12L);
        when(consentRepository.saveAndFlush(any(Consent.class))).thenAnswer(inv -> {
            Consent c = inv.getArgument(0);
            c.setId(503L);
            c.setCreatedAt(Instant.parse("2026-09-18T04:00:00Z"));
            return c;
        });

        Map<String, Object> data = service.create(ALUMNI, 12L, "screen");

        assertEquals(503L, data.get("id"));
        assertNull(data.get("version"), "空表时 version 为 null");
        assertNull(data.get("consent_text_hash"), "空表时 consent_text_hash 为 null");
        ArgumentCaptor<Consent> captor = ArgumentCaptor.forClass(Consent.class);
        verify(consentRepository).saveAndFlush(captor.capture());
        assertNull(captor.getValue().getVersion());
        assertNull(captor.getValue().getConsentTextHash());
        verify(jdbcTemplate).update(anyString(), eq(100L), eq("alumni"), eq("consent_grant"), eq(503L), anyString());
    }

    /** [P1/M16] GET /consents/text：返回 version 最大的一条 */
    @Test
    void currentText_returnsLatest() {
        when(consentTextRepository.findTopByOrderByVersionDesc())
                .thenReturn(Optional.of(consentText(9, 3, "授权文本V3全文")));

        Map<String, Object> data = service.currentText();

        assertEquals(3, data.get("version"));
        assertEquals("授权文本V3全文", data.get("content"));
    }

    /** [P1/M16] GET /consents/text：空表 → 404 */
    @Test
    void currentText_empty_notFound() {
        when(consentTextRepository.findTopByOrderByVersionDesc()).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class, () -> service.currentText());
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }

    /** 我的授权：返回 {id, archive_id, scope, created_at}，按 created_at 倒序（Repository 排序口径） */
    @Test
    void my_listOrderedByCreatedAtDesc() {
        Consent newer = consent(502L, ALUMNI.userId(), 13L, "site",
                Instant.parse("2026-09-18T03:00:00Z"));
        Consent older = consent(501L, ALUMNI.userId(), 12L, "screen",
                Instant.parse("2026-09-17T02:56:00Z"));
        when(consentRepository.findByUserIdOrderByCreatedAtDesc(ALUMNI.userId()))
                .thenReturn(List.of(newer, older));

        List<Map<String, Object>> list = service.my(ALUMNI);

        assertEquals(2, list.size());
        assertEquals(502L, list.get(0).get("id"), "新的在前（created_at 倒序）");
        assertEquals(501L, list.get(1).get("id"));
        assertEquals(13L, list.get(0).get("archive_id"));
        assertEquals("site", list.get(0).get("scope"));
        assertEquals(Instant.parse("2026-09-18T03:00:00Z"), list.get(0).get("created_at"));
    }

    /** [P1/M16] 我的授权：每条含 version / consent_text_hash / granted_at（=created_at）/ withdrawn_at（恒 null） */
    @Test
    void my_includesVersionHashGrantedWithdrawn() {
        Consent c = consent(502L, ALUMNI.userId(), 13L, "site",
                Instant.parse("2026-09-18T03:00:00Z"));
        c.setVersion(2);
        c.setConsentTextHash("9f2c1a7e");
        when(consentRepository.findByUserIdOrderByCreatedAtDesc(ALUMNI.userId()))
                .thenReturn(List.of(c));

        List<Map<String, Object>> list = service.my(ALUMNI);

        assertEquals(1, list.size());
        Map<String, Object> item = list.get(0);
        assertEquals(2, item.get("version"));
        assertEquals("9f2c1a7e", item.get("consent_text_hash"));
        assertEquals(Instant.parse("2026-09-18T03:00:00Z"), item.get("granted_at"), "granted_at 即 created_at");
        assertTrue(item.containsKey("withdrawn_at"));
        assertNull(item.get("withdrawn_at"), "撤回=删行，withdrawn_at 恒 null");
    }

    /** 我的授权：非 student/alumni 角色 → 403 */
    @Test
    void my_nonStudentOrAlumni_forbidden() {
        BizException e = assertThrows(BizException.class, () -> service.my(SCHOOL));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(consentRepository);
    }

    /** 撤回 screen 授权：同事务级联删除 showcase 上架记录，cascade_unpublished=true */
    @Test
    void revoke_screenScope_cascadeUnpublish() {
        Consent consent = consent(501L, ALUMNI.userId(), 12L, "screen",
                Instant.parse("2026-09-17T02:56:00Z"));
        when(consentRepository.findById(501L)).thenReturn(Optional.of(consent));
        when(showcaseRepository.deleteByArchiveId(12L)).thenReturn(1L);

        Map<String, Object> data = service.revoke(ALUMNI, 501L);

        assertEquals(501L, data.get("id"));
        assertEquals(true, data.get("deleted"));
        assertEquals(true, data.get("cascade_unpublished"), "screen 授权撤回应联动下架");
        verify(showcaseRepository).deleteByArchiveId(12L);
        verify(consentRepository).delete(consent);
    }

    /** [P1/M16] 撤回写审计：action=consent_withdraw，biz_id=授权 id，detail 含 archive_id/scope */
    @Test
    void revoke_writesWithdrawAudit() {
        Consent consent = consent(501L, ALUMNI.userId(), 12L, "screen",
                Instant.parse("2026-09-17T02:56:00Z"));
        when(consentRepository.findById(501L)).thenReturn(Optional.of(consent));

        service.revoke(ALUMNI, 501L);

        verify(jdbcTemplate).update(
                argThat(sql -> sql != null && sql.contains("audit_logs")),
                eq(100L), eq("alumni"), eq("consent_withdraw"), eq(501L),
                argThat((String json) -> json.contains("\"archive_id\":12")
                        && json.contains("\"scope\":\"screen\"")));
    }

    /** 撤回 site 授权：不级联 showcase，cascade_unpublished=false */
    @Test
    void revoke_siteScope_noCascade() {
        Consent consent = consent(502L, ALUMNI.userId(), 12L, "site",
                Instant.parse("2026-09-17T02:56:00Z"));
        when(consentRepository.findById(502L)).thenReturn(Optional.of(consent));

        Map<String, Object> data = service.revoke(ALUMNI, 502L);

        assertEquals(true, data.get("deleted"));
        assertEquals(false, data.get("cascade_unpublished"));
        verifyNoInteractions(showcaseRepository);
        verify(consentRepository).delete(consent);
    }

    /** 越权红线：撤回他人授权 → 403，且不删除 */
    @Test
    void revoke_notOwner_forbidden() {
        Consent consent = consent(501L, 200L, 12L, "screen",
                Instant.parse("2026-09-17T02:56:00Z")); // 归属 user 200
        when(consentRepository.findById(501L)).thenReturn(Optional.of(consent));

        BizException e = assertThrows(BizException.class, () -> service.revoke(ALUMNI, 501L));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verify(consentRepository, never()).delete(any(Consent.class));
        verifyNoInteractions(showcaseRepository);
    }

    /** 授权记录不存在 → 404 */
    @Test
    void revoke_notFound() {
        when(consentRepository.findById(999L)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class, () -> service.revoke(ALUMNI, 999L));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verify(consentRepository, never()).delete(any(Consent.class));
        verifyNoInteractions(showcaseRepository);
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    private Archive archive(long id, long userId) {
        Archive a = new Archive();
        a.setId(id);
        a.setUserId(userId);
        a.setStatus("approved");
        return a;
    }

    private Consent consent(long id, long userId, long archiveId, String scope, Instant createdAt) {
        Consent c = new Consent();
        c.setId(id);
        c.setUserId(userId);
        c.setArchiveId(archiveId);
        c.setScope(scope);
        c.setCreatedAt(createdAt);
        return c;
    }

    private ConsentText consentText(long id, int version, String content) {
        ConsentText t = new ConsentText();
        t.setId(id);
        t.setVersion(version);
        t.setContent(content);
        return t;
    }

    /** 打通创建授权前置校验：档案存在且归属 ALUMNI(100)，尚无同范围授权 */
    private void stubOwnArchive(long archiveId) {
        when(archiveRepository.findById(archiveId)).thenReturn(Optional.of(archive(archiveId, ALUMNI.userId())));
        when(consentRepository.existsByArchiveIdAndScope(archiveId, "screen")).thenReturn(false);
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
