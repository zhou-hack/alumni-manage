package com.alumni.showcase;

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
import com.alumni.showcase.service.ShowcaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ShowcaseService 单元测试（Mockito mock Repository，无 HTTP / 无数据库）。
 *
 * <p>覆盖：上架四重条件（存在性 / 过审 / 授权 / 未上架）、并发重复上架、
 * 角色校验（仅 school_admin）、下架、feed 红线口径（撤回即隐藏）与学院筛选。</p>
 */
@ExtendWith(MockitoExtension.class)
class ShowcaseServiceTest {

    private static final AuthPrincipal SCHOOL = new AuthPrincipal(2L, null, "school_admin");
    private static final AuthPrincipal COLLEGE = new AuthPrincipal(1L, null, "college_admin");
    private static final AuthPrincipal ALUMNI = new AuthPrincipal(100L, null, "alumni");

    @Mock
    private ArchiveRepository archiveRepository;
    @Mock
    private ConsentRepository consentRepository;
    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private ShowcaseRepository showcaseRepository;

    private ShowcaseService service;

    @BeforeEach
    void setUp() {
        service = new ShowcaseService(archiveRepository, consentRepository,
                attachmentRepository, showcaseRepository);
    }

    /** 正常上架：返回 showcase_id 与封面附件 ID（取首个已引用图片附件） */
    @Test
    void publish_happyPath() {
        stubPublishable(13L, 102L);
        when(showcaseRepository.saveAndFlush(any(Showcase.class))).thenAnswer(inv -> {
            Showcase s = inv.getArgument(0);
            s.setId(101L);
            return s;
        });

        Map<String, Object> data = service.publish(SCHOOL, 13L);

        assertEquals(101L, data.get("showcase_id"));
        assertEquals(13L, data.get("archive_id"));
        assertEquals(102L, data.get("cover_attachment_id"), "取首个已引用图片附件作为封面");

        ArgumentCaptor<Showcase> captor = ArgumentCaptor.forClass(Showcase.class);
        verify(showcaseRepository).saveAndFlush(captor.capture());
        assertEquals(13L, captor.getValue().getArchiveId());
        assertEquals(102L, captor.getValue().getCoverAttachmentId());
        assertEquals(2L, captor.getValue().getPublishedBy(), "published_by 记录操作管理员");
    }

    /** 无可用封面附件时仍可上架，cover_attachment_id 为 null */
    @Test
    void publish_noCover_stillOk() {
        stubPublishable(13L, null);
        when(showcaseRepository.saveAndFlush(any(Showcase.class))).thenAnswer(inv -> {
            Showcase s = inv.getArgument(0);
            s.setId(101L);
            return s;
        });

        Map<String, Object> data = service.publish(SCHOOL, 13L);

        assertEquals(101L, data.get("showcase_id"));
        assertNull(data.get("cover_attachment_id"));
    }

    /** 档案不存在 → 404 */
    @Test
    void publish_archiveNotFound() {
        when(archiveRepository.findById(99L)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class, () -> service.publish(SCHOOL, 99L));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verifyNoInteractions(showcaseRepository, consentRepository, attachmentRepository);
    }

    /** 档案未过审（pending_first）→ 409 */
    @Test
    void publish_notApproved_conflict() {
        Archive archive = archive(15L, "pending_first");
        when(archiveRepository.findById(15L)).thenReturn(Optional.of(archive));

        BizException e = assertThrows(BizException.class, () -> service.publish(SCHOOL, 15L));
        assertEquals(ErrorCode.INVALID_STATE, e.getErrorCode());
        verify(showcaseRepository, never()).saveAndFlush(any());
    }

    /** 重复上架 → 409 */
    @Test
    void publish_alreadyPublished_conflict() {
        when(archiveRepository.findById(12L)).thenReturn(Optional.of(archive(12L, "approved")));
        when(showcaseRepository.existsByArchiveId(12L)).thenReturn(true);

        BizException e = assertThrows(BizException.class, () -> service.publish(SCHOOL, 12L));
        assertEquals(ErrorCode.INVALID_STATE, e.getErrorCode());
        verify(showcaseRepository, never()).saveAndFlush(any());
    }

    /** 缺少有效大屏授权 → 409，提示应指明缺授权 */
    @Test
    void publish_noConsent_conflict() {
        when(archiveRepository.findById(14L)).thenReturn(Optional.of(archive(14L, "approved")));
        when(showcaseRepository.existsByArchiveId(14L)).thenReturn(false);
        when(consentRepository.existsByArchiveIdAndScope(14L, "screen")).thenReturn(false);

        BizException e = assertThrows(BizException.class, () -> service.publish(SCHOOL, 14L));
        assertEquals(ErrorCode.INVALID_STATE, e.getErrorCode());
        assertTrue(e.getMessage().contains("授权"), "提示应指明缺授权");
        verify(showcaseRepository, never()).saveAndFlush(any());
    }

    /** 并发重复上架：uk_showcase_archive 唯一约束冲突 → 409 */
    @Test
    void publish_concurrentDuplicate_conflict() {
        stubPublishable(13L, 102L);
        when(showcaseRepository.saveAndFlush(any(Showcase.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_showcase_archive'"));

        BizException e = assertThrows(BizException.class, () -> service.publish(SCHOOL, 13L));
        assertEquals(ErrorCode.INVALID_STATE, e.getErrorCode());
        assertTrue(e.getMessage().contains("已上架"));
    }

    /** college_admin 不是上架角色 → 403 */
    @Test
    void publish_collegeAdmin_forbidden() {
        BizException e = assertThrows(BizException.class, () -> service.publish(COLLEGE, 13L));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(archiveRepository, consentRepository, attachmentRepository, showcaseRepository);
    }

    /** 校友角色 → 403 */
    @Test
    void publish_alumni_forbidden() {
        BizException e = assertThrows(BizException.class, () -> service.publish(ALUMNI, 13L));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(archiveRepository, consentRepository, attachmentRepository, showcaseRepository);
    }

    /** 正常下架：返回 archive_id 与 unpublished=true */
    @Test
    void unpublish_happyPath() {
        when(showcaseRepository.deleteByArchiveId(12L)).thenReturn(1L);

        Map<String, Object> data = service.unpublish(SCHOOL, 12L);

        assertEquals(12L, data.get("archive_id"));
        assertEquals(true, data.get("unpublished"));
    }

    /** 未上架档案下架 → 404 */
    @Test
    void unpublish_notPublished_notFound() {
        when(showcaseRepository.deleteByArchiveId(13L)).thenReturn(0L);

        BizException e = assertThrows(BizException.class, () -> service.unpublish(SCHOOL, 13L));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }

    /** college_admin 不是下架角色 → 403 */
    @Test
    void unpublish_collegeAdmin_forbidden() {
        BizException e = assertThrows(BizException.class, () -> service.unpublish(COLLEGE, 12L));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(showcaseRepository);
    }

    /** feed 无筛选：仅返回已上架 + approved + 有授权的数据（SQL 口径由 Repository 保证） */
    @Test
    void feed_all_onlyPublishedApprovedConsented() {
        FeedProjection item = feedItem(12L, "全国大学生程序设计竞赛金奖", "award",
                "测试校友001", "计算机学院", 2025, "/files/archive/2026/09/gold.jpg");
        when(showcaseRepository.feedAll()).thenReturn(List.of(item));

        List<FeedProjection> result = service.feed(null);

        assertEquals(1, result.size());
        assertEquals(12L, result.get(0).getId());
        assertEquals("测试校友001", result.get(0).getOwner());
        assertEquals(2025, result.get(0).getGradYear());
        verify(showcaseRepository).feedAll();
        verify(showcaseRepository, never()).feedByCollege(any());
    }

    /** 授权撤回即隐藏：同一查询在撤回后不再返回该条目（用 mock 连续返回值体现） */
    @Test
    void feed_consentRevoked_itemHidden() {
        FeedProjection item = feedItem(12L, "全国大学生程序设计竞赛金奖", "award",
                "测试校友001", "计算机学院", 2025, "/files/archive/2026/09/gold.jpg");
        // 第一次调用：授权有效，返回 1 条；第二次调用：授权已撤回，不再返回
        when(showcaseRepository.feedAll()).thenReturn(List.of(item), List.of());

        assertEquals(1, service.feed(null).size(), "撤回前可见");
        assertTrue(service.feed(null).isEmpty(), "撤回授权后应立即隐藏");
    }

    /** feed 学院筛选：college_id 走学院变体查询 */
    @Test
    void feed_byCollege_filtered() {
        FeedProjection item = feedItem(12L, "全国大学生程序设计竞赛金奖", "award",
                "测试校友001", "计算机学院", 2025, "/files/archive/2026/09/gold.jpg");
        when(showcaseRepository.feedByCollege(1L)).thenReturn(List.of(item));

        List<FeedProjection> result = service.feed(1L);

        assertEquals(1, result.size());
        assertEquals("计算机学院", result.get(0).getCollege());
        verify(showcaseRepository).feedByCollege(1L);
        verify(showcaseRepository, never()).feedAll();
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    private Archive archive(long id, String status) {
        Archive a = new Archive();
        a.setId(id);
        a.setStatus(status);
        return a;
    }

    /** 打通上架全部前置校验：approved + 未上架 + 有授权，封面为 coverId */
    private void stubPublishable(long archiveId, Long coverId) {
        when(archiveRepository.findById(archiveId)).thenReturn(Optional.of(archive(archiveId, "approved")));
        when(showcaseRepository.existsByArchiveId(archiveId)).thenReturn(false);
        when(consentRepository.existsByArchiveIdAndScope(archiveId, "screen")).thenReturn(true);
        when(attachmentRepository.findCoverAttachmentId(archiveId)).thenReturn(coverId);
    }

    private FeedProjection feedItem(Long id, String title, String category, String owner,
                                    String college, Integer gradYear, String coverUrl) {
        return new FeedProjection() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getTitle() {
                return title;
            }

            @Override
            public String getCategory() {
                return category;
            }

            @Override
            public String getOwner() {
                return owner;
            }

            @Override
            public String getCollege() {
                return college;
            }

            @Override
            public Integer getGradYear() {
                return gradYear;
            }

            @Override
            public String getCoverUrl() {
                return coverUrl;
            }
        };
    }
}
