package com.alumni.photo.service;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.photo.common.BizException;
import com.alumni.photo.common.ErrorCode;
import com.alumni.photo.dto.PhotoDtos;
import com.alumni.photo.dto.PhotoDtos.PageResult;
import com.alumni.photo.dto.PhotoDtos.PhotoItem;
import com.alumni.photo.dto.PhotoDtos.PushLogItem;
import com.alumni.photo.dto.PhotoDtos.PushRequest;
import com.alumni.photo.dto.PhotoDtos.PushResult;
import com.alumni.photo.dto.PhotoDtos.ReviewRequest;
import com.alumni.photo.dto.PhotoDtos.SubmitRequest;
import com.alumni.photo.entity.PhotoCollect;
import com.alumni.photo.entity.PhotoPushLog;
import com.alumni.photo.repository.PhotoCollectRepository;
import com.alumni.photo.repository.PhotoPushLogRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校友照片收集 Service：
 * <ul>
 *   <li>submit：用户上传后由前端调附件上传接口（attachments 模块），再调用 submit 关联到 photo_collect</li>
 *   <li>review：辅导员/校级审核（pending → approved/rejected）</li>
 *   <li>push：超管推送 approved 照片到目标群体（all_users/college/role/single_user）</li>
 *   <li>list/mine/detail：列表查询</li>
 * </ul>
 *
 * <p>权限：
 * <ul>
 *   <li>submit/mine：登录用户本人</li>
 *   <li>review：counselor / college_admin / school_admin / system_admin</li>
 *   <li>push：仅 school_admin / system_admin</li>
 *   <li>list：同上</li>
 * </ul>
 */
@Service
public class PhotoCollectService {

    private static final Logger log = LoggerFactory.getLogger(PhotoCollectService.class);

    private static final Set<String> REVIEW_ROLES = Set.of(
            "counselor", "college_admin", "school_admin", "system_admin");
    private static final Set<String> PUSH_ROLES = Set.of("school_admin", "system_admin");

    private final PhotoCollectRepository photoRepo;
    private final PhotoPushLogRepository pushLogRepo;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    @PersistenceContext private EntityManager em;

    public PhotoCollectService(PhotoCollectRepository photoRepo,
                               PhotoPushLogRepository pushLogRepo,
                               ObjectMapper objectMapper,
                               JdbcTemplate jdbcTemplate) {
        this.photoRepo = photoRepo;
        this.pushLogRepo = pushLogRepo;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /* ==================== 提交 ==================== */

    @Transactional
    public PhotoItem submit(AuthPrincipal operator, SubmitRequest req) {
        if (operator == null) throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        // 校验附件归属：本人的 attachment，且 biz_type=photo_collect
        Map<String, Object> att = loadAttachment(req.attachmentId());
        if (att == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        long uploaderId = ((Number) att.get("uploader_id")).longValue();
        if (uploaderId != operator.userId()) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能使用本人上传的附件");
        }
        String bizType = (String) att.get("biz_type");
        if (bizType != null && !"photo_collect".equals(bizType) && !"unreferenced".equals(bizType)) {
            throw new BizException(ErrorCode.INVALID_STATE, "附件已被其他业务占用");
        }

        PhotoCollect p = new PhotoCollect();
        p.setUserId(operator.userId());
        p.setAttachmentId(req.attachmentId());
        p.setTitle(req.title());
        p.setCaption(req.caption());
        p.setTags(serializeTags(req.tags()));
        p.setStatus("pending");
        // 把附件 biz_type + ref_id 关联起来
        jdbcTemplate.update(
                "UPDATE attachments SET biz_type = 'photo_collect', ref_id = ?, status = 'referenced', referenced_at = ? " +
                "WHERE id = ? AND uploader_id = ?",
                p.getId() == null ? -1L : p.getId(), Instant.now(), req.attachmentId(), operator.userId());
        p = photoRepo.save(p);
        // 保存后再回填 ref_id（photo.id）
        jdbcTemplate.update(
                "UPDATE attachments SET ref_id = ? WHERE id = ? AND uploader_id = ?",
                p.getId(), req.attachmentId(), operator.userId());
        writeAudit(operator, "photo_submit", p.getId(),
                Map.of("attachment_id", req.attachmentId(), "title", req.title() == null ? "" : req.title()));
        return toItem(p);
    }

    /* ==================== 我的照片 ==================== */

    @Transactional(readOnly = true)
    public PageResult<PhotoItem> mine(AuthPrincipal operator, String status, int page, int pageSize) {
        requireLogin(operator);
        if (pageSize < 1 || pageSize > 100) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page_size 须在 1~100 之间");
        }
        Page<PhotoCollect> result;
        if (status == null || status.isBlank()) {
            List<PhotoCollect> all = photoRepo.findByUserIdOrderByIdDesc(operator.userId());
            int total = all.size();
            int from = Math.min((page - 1) * pageSize, total);
            int to = Math.min(from + pageSize, total);
            return new PageResult<>(all.subList(from, to).stream().map(this::toItem).toList(),
                    total, page, pageSize);
        }
        result = photoRepo.findByUserIdAndStatusOrderByIdDesc(operator.userId(), status,
                PageRequest.of(Math.max(page - 1, 0), pageSize));
        List<PhotoItem> list = result.getContent().stream().map(this::toItem).toList();
        return new PageResult<>(list, result.getTotalElements(), page, pageSize);
    }

    /* ==================== 后台审核队列 ==================== */

    @Transactional(readOnly = true)
    public PageResult<PhotoItem> adminList(AuthPrincipal operator, String status, int page, int pageSize) {
        requireReviewRole(operator);
        if (pageSize < 1 || pageSize > 100) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page_size 须在 1~100 之间");
        }
        Page<PhotoCollect> result;
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            result = photoRepo.findAll(PageRequest.of(Math.max(page - 1, 0), pageSize,
                    Sort.by(Sort.Direction.DESC, "id")));
        } else {
            result = photoRepo.findByStatusOrderByIdDesc(status,
                    PageRequest.of(Math.max(page - 1, 0), pageSize));
        }
        List<PhotoItem> list = result.getContent().stream().map(this::toItem).toList();
        return new PageResult<>(list, result.getTotalElements(), page, pageSize);
    }

    /* ==================== 审核 ==================== */

    @Transactional
    public PhotoItem review(AuthPrincipal operator, long id, ReviewRequest req) {
        requireReviewRole(operator);
        PhotoCollect p = photoRepo.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "照片不存在"));
        if (!"pending".equals(p.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE, "仅 pending 状态可审核");
        }
        if ("approve".equals(req.action())) {
            p.setStatus("approved");
            p.setRejectReason(null);
        } else {
            if (req.reason() == null || req.reason().isBlank()) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "驳回需填写原因");
            }
            p.setStatus("rejected");
            p.setRejectReason(req.reason().trim());
        }
        p.setReviewedBy(operator.userId());
        p.setReviewedAt(Instant.now());
        photoRepo.save(p);
        writeAudit(operator, "photo_review", id,
                Map.of("action", req.action(), "reason", req.reason() == null ? "" : req.reason()));
        return toItem(p);
    }

    /* ==================== 推送 ==================== */

    @Transactional
    public PushResult push(AuthPrincipal operator, long id, PushRequest req) {
        requirePushRole(operator);
        PhotoCollect p = photoRepo.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "照片不存在"));
        if (!"approved".equals(p.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE, "仅 approved 状态可推送");
        }
        int recipients = countRecipients(req.targetType(), req.targetId());

        PhotoPushLog log = new PhotoPushLog();
        log.setPhotoId(p.getId());
        log.setTargetType(req.targetType());
        log.setTargetId(req.targetId());
        log.setRecipients(recipients);
        log.setOperatorId(operator.userId());
        log.setNote(req.note());
        log = pushLogRepo.save(log);

        // 回填 photo_collect 推送统计
        p.setPushedCount((p.getPushedCount() == null ? 0 : p.getPushedCount()) + recipients);
        p.setLastPushAt(Instant.now());
        // 标记状态为 pushed（不影响继续推送）
        if (!"pushed".equals(p.getStatus())) p.setStatus("pushed");
        photoRepo.save(p);

        writeAudit(operator, "photo_push", id,
                mapOf("target_type", req.targetType(), "target_id",
                        req.targetId() == null ? "" : req.targetId(),
                        "recipients", recipients, "log_id", log.getId()));
        return new PushResult(recipients, log.getId());
    }

    /* ==================== 推送日志 ==================== */

    @Transactional(readOnly = true)
    public PageResult<PushLogItem> pushLog(AuthPrincipal operator, Long photoId, int page, int pageSize) {
        requireLogin(operator);
        if (pageSize < 1 || pageSize > 100) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page_size 须在 1~100 之间");
        }
        Page<PhotoPushLog> result = photoId == null
                ? pushLogRepo.findAllByOrderByIdDesc(PageRequest.of(Math.max(page - 1, 0), pageSize))
                : pushLogRepo.findByPhotoIdOrderByIdDesc(photoId, PageRequest.of(Math.max(page - 1, 0), pageSize));
        List<PushLogItem> list = result.getContent().stream()
                .map(p -> new PushLogItem(p.getId(), p.getPhotoId(), p.getTargetType(),
                        p.getTargetId(), p.getRecipients(), p.getOperatorId(), p.getNote(), p.getCreatedAt()))
                .toList();
        return new PageResult<>(list, result.getTotalElements(), page, pageSize);
    }

    /* ==================== 工具 ==================== */

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private Map<String, Object> loadAttachment(long id) {
        try {
            return jdbcTemplate.query(
                    "SELECT id, biz_type, status, uploader_id FROM attachments WHERE id = ?",
                    rs -> rs.next() ? Map.of(
                            "id", rs.getLong("id"),
                            "biz_type", rs.getString("biz_type"),
                            "status", rs.getString("status"),
                            "uploader_id", rs.getLong("uploader_id")) : null,
                    id);
        } catch (Exception e) {
            log.warn("加载附件失败：id={}", id, e);
            return null;
        }
    }

    private Map<String, Object> loadAttachmentUrlAndName(long id) {
        try {
            return jdbcTemplate.query(
                    "SELECT id, url, name FROM attachments WHERE id = ?",
                    rs -> rs.next() ? Map.of(
                            "id", rs.getLong("id"),
                            "url", rs.getString("url"),
                            "name", rs.getString("name")) : null,
                    id);
        } catch (Exception e) {
            return null;
        }
    }

    private int countRecipients(String targetType, String targetId) {
        try {
            return switch (targetType) {
                case "all_users" -> ((Number) jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE status = 'active'", Integer.class)).intValue();
                case "college" -> ((Number) jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM users u JOIN alumni_base a ON u.alumni_base_id = a.id " +
                        "WHERE a.college = ? AND u.status = 'active'", Integer.class, targetId)).intValue();
                case "role" -> ((Number) jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE role = ? AND status = 'active'", Integer.class, targetId)).intValue();
                case "single_user" -> 1;
                default -> throw new BizException(ErrorCode.INVALID_PARAMETER,
                        "不支持的 target_type: " + targetType);
            };
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("计算推送对象数量失败：type={}, id={}", targetType, targetId, e);
            return 0;
        }
    }

    private String serializeTags(List<String> tags) {
        if (tags == null) return "[]";
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> deserializeTags(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private PhotoItem toItem(PhotoCollect p) {
        Map<String, Object> att = loadAttachmentUrlAndName(p.getAttachmentId());
        return new PhotoItem(
                p.getId(),
                p.getUserId(),
                p.getAttachmentId(),
                att == null ? null : (String) att.get("url"),
                att == null ? null : (String) att.get("name"),
                p.getTitle(),
                p.getCaption(),
                deserializeTags(p.getTags()),
                p.getStatus(),
                p.getRejectReason(),
                p.getReviewedBy(),
                p.getReviewedAt(),
                p.getPushedCount(),
                p.getLastPushAt(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }

    private void writeAudit(AuthPrincipal operator, String action, Long bizId, Map<String, Object> detail) {
        try {
            String detailJson = objectMapper.writeValueAsString(detail);
            jdbcTemplate.update(
                    "INSERT INTO audit_logs (user_id, user_role, action, biz_type, biz_id, detail) " +
                    "VALUES (?, ?, ?, 'photo_collect', ?, ?)",
                    operator.userId(), operator.role(), action, bizId, detailJson);
        } catch (Exception e) {
            log.error("写审计日志失败：action={}", action, e);
        }
    }

    private void requireLogin(AuthPrincipal op) {
        if (op == null) throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
    }

    private void requireReviewRole(AuthPrincipal op) {
        requireLogin(op);
        if (!REVIEW_ROLES.contains(op.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无审核权限");
        }
    }

    private void requirePushRole(AuthPrincipal op) {
        requireLogin(op);
        if (!PUSH_ROLES.contains(op.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅校级 / 系统管理员可推送照片");
        }
    }
}
