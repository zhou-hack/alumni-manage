package com.alumni.showcase.service;

import com.alumni.showcase.common.BizException;
import com.alumni.showcase.common.ErrorCode;
import com.alumni.showcase.entity.Archive;
import com.alumni.showcase.entity.Showcase;
import com.alumni.showcase.repository.ArchiveRepository;
import com.alumni.showcase.repository.AttachmentRepository;
import com.alumni.showcase.repository.ConsentRepository;
import com.alumni.showcase.repository.FeedProjection;
import com.alumni.showcase.repository.ShowcaseRepository;
import com.alumni.common.security.AuthPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 展览馆 / 大屏业务实现（Service 层）。
 *
 * <p>职责：上架条件校验编排、角色校验、结果组装；
 * 不含 SQL 与 HTTP 细节，全部通过 Repository 取数。</p>
 */
@Service
public class ShowcaseService {

    /** 档案已审核通过状态（上架唯一放行状态） */
    private static final String STATUS_APPROVED = "approved";
    /** 上架操作限定角色 */
    private static final Set<String> MANAGER_ROLES = Set.of("school_admin", "system_admin");
    /** 大屏授权范围 */
    private static final String SCOPE_SCREEN = "screen";

    private final ArchiveRepository archiveRepository;
    private final ConsentRepository consentRepository;
    private final AttachmentRepository attachmentRepository;
    private final ShowcaseRepository showcaseRepository;

    public ShowcaseService(ArchiveRepository archiveRepository,
                           ConsentRepository consentRepository,
                           AttachmentRepository attachmentRepository,
                           ShowcaseRepository showcaseRepository) {
        this.archiveRepository = archiveRepository;
        this.consentRepository = consentRepository;
        this.attachmentRepository = attachmentRepository;
        this.showcaseRepository = showcaseRepository;
    }

    /** 上架：approved + 未上架 + 有效大屏授权，封面取首个已引用图片附件 */
    @Transactional
    public Map<String, Object> publish(AuthPrincipal operator, long archiveId) {
        requireSchoolAdmin(operator);

        Archive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "档案不存在"));
        if (!STATUS_APPROVED.equals(archive.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE, "档案未通过审核，不能上架");
        }
        if (showcaseRepository.existsByArchiveId(archiveId)) {
            throw new BizException(ErrorCode.INVALID_STATE, "该档案已上架");
        }
        if (!consentRepository.existsByArchiveIdAndScope(archiveId, SCOPE_SCREEN)) {
            throw new BizException(ErrorCode.INVALID_STATE, "该档案缺少有效大屏授权，不能上架");
        }

        Long coverId = attachmentRepository.findCoverAttachmentId(archiveId);
        Showcase showcase = new Showcase();
        showcase.setArchiveId(archiveId);
        showcase.setCoverAttachmentId(coverId);
        showcase.setPublishedBy(operator.userId());
        try {
            showcase = showcaseRepository.saveAndFlush(showcase);
        } catch (DataIntegrityViolationException e) {
            // uk_showcase_archive 唯一约束冲突 → 并发重复上架，按业务冲突抛出
            throw new BizException(ErrorCode.INVALID_STATE, "该档案已上架");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("showcase_id", showcase.getId());
        data.put("archive_id", archiveId);
        data.put("cover_attachment_id", coverId);
        return data;
    }

    /** 下架：删除上架行 */
    @Transactional
    public Map<String, Object> unpublish(AuthPrincipal operator, long archiveId) {
        requireSchoolAdmin(operator);
        if (showcaseRepository.deleteByArchiveId(archiveId) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "该档案未上架");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("archive_id", archiveId);
        data.put("unpublished", true);
        return data;
    }

    /** 大屏轮播数据：collegeId 为 null 时返回全部 */
    @Transactional(readOnly = true)
    public List<FeedProjection> feed(Long collegeId) {
        return collegeId == null ? showcaseRepository.feedAll() : showcaseRepository.feedByCollege(collegeId);
    }

    /** 仅 school_admin 可上下架（SecurityConfig 已拦截，此处二次校验） */
    private void requireSchoolAdmin(AuthPrincipal operator) {
        if (operator == null || !MANAGER_ROLES.contains(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅校级管理员可执行该操作");
        }
    }
}
