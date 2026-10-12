package com.alumni.announcement.service;

import com.alumni.announcement.common.BizException;
import com.alumni.announcement.common.ErrorCode;
import com.alumni.announcement.dto.AnnouncementDtos.AnnouncementItem;
import com.alumni.announcement.dto.AnnouncementDtos.PageResult;
import com.alumni.announcement.entity.Announcement;
import com.alumni.announcement.repository.AnnouncementRepository;
import com.alumni.common.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 公告业务实现（Service 层）。
 *
 * <p>职责：角色校验、参数二次校验、生命周期编排（draft → published ↔ withdrawn）；
 * 不含 SQL 与 HTTP 细节，全部通过 Repository 取数。</p>
 *
 * <p>[P1/M14] 事件源「公告发布」：首次进入 published 时给全体用户生成站内通知
 * （notifications，共享库直写 JdbcTemplate，content=公告标题不含学号），
 * 并按 notification_subscriptions 订阅模拟外部发送；通知失败不影响公告主流程。</p>
 */
@Service
public class AnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementService.class);

    /** 公告写操作（发布/编辑/撤下/删除）限定角色 */
    private static final Set<String> MANAGER_ROLES = Set.of("college_admin", "school_admin", "system_admin");
    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_WITHDRAWN = "withdrawn";
    /** 站内通知标题（content 为公告标题） */
    private static final String NOTIFY_TITLE = "公告发布";

    private final AnnouncementRepository announcementRepository;
    private final JdbcTemplate jdbcTemplate;

    public AnnouncementService(AnnouncementRepository announcementRepository, JdbcTemplate jdbcTemplate) {
        this.announcementRepository = announcementRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 创建公告（管理员；初始草稿） */
    @Transactional
    public AnnouncementItem create(AuthPrincipal operator, String title, String content, String coverImage) {
        requireManager(operator);
        Announcement announcement = new Announcement();
        announcement.setTitle(title);
        announcement.setContent(content);
        announcement.setCoverImage(normalizeCover(coverImage));
        announcement.setStatus(STATUS_DRAFT);
        announcement.setCreatedBy(operator.userId());
        announcement = announcementRepository.save(announcement);
        return toItem(announcement);
    }

    /**
     * 列表（公开可读）：管理员可按 status 筛选（缺省全部）；
     * 非管理员/匿名访客强制只读 published（门户视图）。
     */
    @Transactional(readOnly = true)
    public PageResult page(AuthPrincipal operator, String status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        boolean manager = operator != null && MANAGER_ROLES.contains(operator.role());
        String effectiveStatus = manager ? normalizeStatus(status) : STATUS_PUBLISHED;

        Page<Announcement> result = effectiveStatus == null
                ? announcementRepository.findAllByOrderByIdDesc(PageRequest.of(safePage - 1, safeSize))
                : announcementRepository.findByStatusOrderByIdDesc(effectiveStatus, PageRequest.of(safePage - 1, safeSize));

        return new PageResult(result.map(this::toItem).getContent(),
                result.getTotalElements(), safePage, safeSize);
    }

    /** 详情（公开可读；非管理员/匿名访客仅可见 published，其余按不存在处理） */
    @Transactional(readOnly = true)
    public AnnouncementItem detail(AuthPrincipal operator, long id) {
        requirePositiveId(id);
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "公告不存在"));
        boolean manager = operator != null && MANAGER_ROLES.contains(operator.role());
        if (!manager && !STATUS_PUBLISHED.equals(announcement.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        return toItem(announcement);
    }

    /** 修改（管理员）：title/content/coverImage 给了才改（coverImage 空串即清除）；status 变更即发布（记录发布人/时间）或撤下 */
    @Transactional
    public AnnouncementItem update(AuthPrincipal operator, long id, String title, String content,
                                   String coverImage, String status) {
        requireManager(operator);
        requirePositiveId(id);
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "公告不存在"));

        if (title != null) {
            announcement.setTitle(title);
        }
        if (content != null) {
            announcement.setContent(content);
        }
        if (coverImage != null) {
            announcement.setCoverImage(normalizeCover(coverImage));
        }
        // [P1/M14] 首次进入 published 判定：变更前状态 != published（重复 PATCH published 不重复触发）
        boolean firstPublish = false;
        if (status != null) {
            if (!STATUS_DRAFT.equals(status) && !STATUS_PUBLISHED.equals(status)
                    && !STATUS_WITHDRAWN.equals(status)) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "status 仅允许 draft/published/withdrawn");
            }
            firstPublish = STATUS_PUBLISHED.equals(status) && !STATUS_PUBLISHED.equals(announcement.getStatus());
            announcement.setStatus(status);
            if (STATUS_PUBLISHED.equals(status)) {
                announcement.setPublishedBy(operator.userId());
                announcement.setPublishedAt(Instant.now());
            }
        }
        announcement = announcementRepository.save(announcement);
        if (firstPublish) {
            notifyAnnouncementPublished(announcement);
        }
        return toItem(announcement);
    }

    /** 删除（管理员，物理删除） */
    @Transactional
    public void delete(AuthPrincipal operator, long id) {
        requireManager(operator);
        requirePositiveId(id);
        if (!announcementRepository.existsById(id)) {
            throw new BizException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        announcementRepository.deleteById(id);
    }

    /* ---------------- 私有工具 ---------------- */

    /**
     * [P1/M14] 事件源「公告发布」：全体用户各一条站内通知（content=公告标题，不含学号），
     * 外部订阅（sms/email 且订阅 announcement 场景）模拟发送。通知失败仅记日志，不影响公告主流程。
     */
    private void notifyAnnouncementPublished(Announcement announcement) {
        try {
            int inserted = jdbcTemplate.update(
                    "INSERT INTO notifications (user_id, type, title, content, biz_type, biz_id, is_read, created_at) "
                            + "SELECT id, 'announcement', ?, ?, 'announcement', ?, 0, UTC_TIMESTAMP(6) FROM users",
                    NOTIFY_TITLE, announcement.getTitle(), announcement.getId());
            log.info("公告发布站内通知已生成：announcementId={}, 通知数={}", announcement.getId(), inserted);
        } catch (Exception e) {
            log.error("公告发布站内通知生成失败：announcementId={}", announcement.getId(), e);
        }
        try {
            List<Long> subscriberIds = jdbcTemplate.queryForList(
                    "SELECT user_id FROM notification_subscriptions"
                            + " WHERE enabled = 1 AND JSON_CONTAINS(scenes, '\"announcement\"')",
                    Long.class);
            if (!subscriberIds.isEmpty()) {
                log.info("模拟发送公告外部订阅通知：announcementId={}, 订阅用户数={}",
                        announcement.getId(), subscriberIds.size());
            }
        } catch (Exception e) {
            log.error("公告外部订阅模拟发送失败：announcementId={}", announcement.getId(), e);
        }
    }

    private void requireManager(AuthPrincipal operator) {
        if (operator == null || !MANAGER_ROLES.contains(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可管理公告");
        }
    }

    private void requirePositiveId(long id) {
        if (id <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "id 必须为正整数");
        }
    }

    /** null/空串 → null（全部）；否则校验取值 */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status) {
            case STATUS_DRAFT, STATUS_PUBLISHED, STATUS_WITHDRAWN -> status;
            default -> null; // 非法值按未筛选处理或由 update 路径拒绝
        };
    }

    /** 封面：trim 后空串 → null（清除封面） */
    private static String normalizeCover(String coverImage) {
        if (coverImage == null) {
            return null;
        }
        String trimmed = coverImage.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AnnouncementItem toItem(Announcement a) {
        return new AnnouncementItem(a.getId(), a.getTitle(), a.getContent(), a.getCoverImage(), a.getStatus(),
                a.getPublishedBy(), a.getPublishedAt(), a.getCreatedBy(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
