package com.alumni.consents.service;

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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 隐私授权业务实现（Service 层）。
 *
 * <p>职责：角色校验、参数二次校验、档案存在性与归属校验、重复授权兜底、
 * 撤回授权与 showcase 级联下架编排；不含 SQL 与 HTTP 细节，全部通过 Repository 取数。</p>
 *
 * <p>[P1/M16] 授权版本化：创建授权时快照当前 consent_texts 最新版本号与文本 SHA-256 摘要
 * （留证可追溯当时版本）；授权/撤回写 audit_logs（共享库直写 JdbcTemplate，不依赖其他模块 jar）。</p>
 */
@Service
public class ConsentService {

    private static final Logger log = LoggerFactory.getLogger(ConsentService.class);

    /** 大屏授权范围（撤回时级联下架 showcase） */
    private static final String SCOPE_SCREEN = "screen";
    /** 站点授权范围（预留，撤回不级联） */
    private static final String SCOPE_SITE = "site";
    /** 授权操作限定角色 */
    private static final String ROLE_STUDENT = "student";
    private static final String ROLE_ALUMNI = "alumni";

    private final ConsentRepository consentRepository;
    private final ArchiveRepository archiveRepository;
    private final ShowcaseRepository showcaseRepository;
    private final ConsentTextRepository consentTextRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ConsentService(ConsentRepository consentRepository,
                          ArchiveRepository archiveRepository,
                          ShowcaseRepository showcaseRepository,
                          ConsentTextRepository consentTextRepository,
                          EntityManager entityManager,
                          JdbcTemplate jdbcTemplate,
                          ObjectMapper objectMapper) {
        this.consentRepository = consentRepository;
        this.archiveRepository = archiveRepository;
        this.showcaseRepository = showcaseRepository;
        this.consentTextRepository = consentTextRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建授权：仅 student/alumni；档案必须存在且归属当前用户（越权红线）；
     * 同一 (archive_id, scope) 唯一（uk_consents_archive_scope 约束兜底并发）。
     * [P1/M16] 快照当前授权文本 version + SHA-256 摘要入库（consent_texts 空表时容错为 null），
     * 并写审计 consent_grant。
     */
    @Transactional
    public Map<String, Object> create(AuthPrincipal operator, Long archiveId, String scope) {
        requireStudentOrAlumni(operator);
        requirePositiveId(archiveId, "archive_id");
        requireValidScope(scope);

        Archive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "档案不存在"));
        if (!archive.getUserId().equals(operator.userId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅能为本人档案授权");
        }
        if (consentRepository.existsByArchiveIdAndScope(archiveId, scope)) {
            throw new BizException(ErrorCode.INVALID_STATE, "该档案已存在相同范围的授权");
        }

        // 授权文本版本化留证：取当前最新版本；空表容错（version/hash 为 null，不阻断授权）
        ConsentText currentText = consentTextRepository.findTopByOrderByVersionDesc().orElse(null);

        Consent consent = new Consent();
        consent.setUserId(operator.userId());
        consent.setArchiveId(archiveId);
        consent.setScope(scope);
        if (currentText != null) {
            consent.setVersion(currentText.getVersion());
            consent.setConsentTextHash(sha256Hex(currentText.getContent()));
        }
        try {
            consent = consentRepository.saveAndFlush(consent);
        } catch (DataIntegrityViolationException e) {
            // uk_consents_archive_scope 唯一约束冲突 → 并发重复授权，按业务冲突抛出
            throw new BizException(ErrorCode.INVALID_STATE, "该档案已存在相同范围的授权");
        }
        // created_at 由数据库默认值生成，回读以返回精确时间
        entityManager.refresh(consent);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("archive_id", archiveId);
        detail.put("scope", scope);
        detail.put("version", consent.getVersion());
        insertAudit(operator, "consent_grant", consent.getId(), detail);

        return toData(consent);
    }

    /** GET /consents/text：当前生效授权文本（version 最大的一条），前端授权弹窗展示 */
    @Transactional(readOnly = true)
    public Map<String, Object> currentText() {
        ConsentText text = consentTextRepository.findTopByOrderByVersionDesc()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "授权文本未配置"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", text.getVersion());
        data.put("content", text.getContent());
        return data;
    }

    /** 我的授权记录：按 created_at 倒序；[P1/M16] 每条含 version/consent_text_hash/granted_at/withdrawn_at */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> my(AuthPrincipal operator) {
        requireStudentOrAlumni(operator);
        return consentRepository.findByUserIdOrderByCreatedAtDesc(operator.userId())
                .stream().map(this::toData).toList();
    }

    /**
     * 撤回授权：仅授权归属人本人；scope='screen' 时同事务级联删除
     * showcase 中该 archive_id 的上架记录（大屏立即下架），scope='site' 不级联。
     * [P1/M16] 撤回即时生效（删行）并写审计 consent_withdraw。
     */
    @Transactional
    public Map<String, Object> revoke(AuthPrincipal operator, long id) {
        Consent consent = consentRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "授权记录不存在"));
        if (operator == null || !consent.getUserId().equals(operator.userId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅本人可撤回授权");
        }

        boolean cascadeUnpublished = false;
        if (SCOPE_SCREEN.equals(consent.getScope())) {
            cascadeUnpublished = showcaseRepository.deleteByArchiveId(consent.getArchiveId()) > 0;
        }
        consentRepository.delete(consent);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("archive_id", consent.getArchiveId());
        detail.put("scope", consent.getScope());
        insertAudit(operator, "consent_withdraw", id, detail);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("deleted", true);
        data.put("cascade_unpublished", cascadeUnpublished);
        return data;
    }

    /** 仅 student / alumni 可创建与查看授权（SecurityConfig 已拦截未登录，此处二次校验角色） */
    private void requireStudentOrAlumni(AuthPrincipal operator) {
        if (operator == null || (!ROLE_STUDENT.equals(operator.role()) && !ROLE_ALUMNI.equals(operator.role()))) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅在校学生或校友可执行该操作");
        }
    }

    /** archive_id 必须为正整数 */
    private void requirePositiveId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, name + " 必须为正整数");
        }
    }

    /** scope 仅允许 screen / site */
    private void requireValidScope(String scope) {
        if (!SCOPE_SCREEN.equals(scope) && !SCOPE_SITE.equals(scope)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "scope 仅支持 screen/site");
        }
    }

    /** 审计日志（只追加）：授权/撤回；写失败仅记日志，不影响授权主流程 */
    private void insertAudit(AuthPrincipal operator, String action, Long bizId, Map<String, Object> detail) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO audit_logs (user_id, user_role, action, biz_type, biz_id, detail)"
                            + " VALUES (?, ?, ?, 'consent', ?, ?)",
                    operator.userId(), operator.role(), action, bizId, toJson(detail));
        } catch (Exception e) {
            log.error("授权审计日志写入失败：action={}, bizId={}", action, bizId, e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("审计明细序列化失败", e);
        }
    }

    private Map<String, Object> toData(Consent consent) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", consent.getId());
        map.put("archive_id", consent.getArchiveId());
        map.put("scope", consent.getScope());
        map.put("version", consent.getVersion());
        map.put("consent_text_hash", consent.getConsentTextHash());
        map.put("created_at", consent.getCreatedAt());
        // [P1/M16] granted_at 即 created_at（按文档字段名出参）；撤回=删行，故 withdrawn_at 恒 null
        map.put("granted_at", consent.getCreatedAt());
        map.put("withdrawn_at", null);
        return map;
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
