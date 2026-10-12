package com.alumni.reviews;

import com.alumni.reviews.common.BizException;
import com.alumni.reviews.common.ErrorCode;
import com.alumni.reviews.entity.Review;
import com.alumni.reviews.entity.ReviewLog;
import com.alumni.reviews.repository.ArchiveRepository;
import com.alumni.reviews.repository.HonorRepository;
import com.alumni.reviews.repository.ReviewLogRepository;
import com.alumni.reviews.repository.ReviewRepository;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.reviews.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReviewService 单元测试（Mockito mock 全部 repository 与 JdbcTemplate，不依赖数据库与 HTTP）。
 *
 * <p>覆盖：待办列表按角色分阶段（含 P1 导员 counselor 阶段管辖范围过滤、stage 筛选）、分页校验、
 * 三级流转（counselor→first→second→approved）、存量两级流转（first→second→approved）、
 * 单级人工核验、驳回原因必填、409 重复操作、403 角色-阶段不匹配、导员越权 403、
 * 404 不存在、日志权限（审核角色 + 本人）、业务状态同步、审核结果站内通知写入。</p>
 */
class ReviewServiceTest {

    private static final AuthPrincipal COLLEGE = new AuthPrincipal(1L, null, "college_admin");
    private static final AuthPrincipal SCHOOL = new AuthPrincipal(2L, null, "school_admin");
    private static final AuthPrincipal COUNSELOR = new AuthPrincipal(3L, null, "counselor");
    private static final AuthPrincipal ALUMNI_100 = new AuthPrincipal(100L, "2021000001", "alumni");
    private static final AuthPrincipal ALUMNI_200 = new AuthPrincipal(200L, "2021000002", "alumni");

    private ReviewRepository reviewRepository;
    private ReviewLogRepository reviewLogRepository;
    private ArchiveRepository archiveRepository;
    private HonorRepository honorRepository;
    private JdbcTemplate jdbcTemplate;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        reviewLogRepository = mock(ReviewLogRepository.class);
        archiveRepository = mock(ArchiveRepository.class);
        honorRepository = mock(HonorRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new ReviewService(reviewRepository, reviewLogRepository, archiveRepository, honorRepository,
                jdbcTemplate);
    }

    private static Review review(long id, String bizType, Long bizId, Long submitterId,
                                 String stage, String status) {
        Review r = new Review();
        r.setId(id);
        r.setBizType(bizType);
        r.setBizId(bizId);
        r.setSubmitterId(submitterId);
        r.setStage(stage);
        r.setStatus(status);
        r.setCreatedAt(Instant.now());
        r.setUpdatedAt(Instant.now());
        return r;
    }

    private void assertError(ErrorCode expected, org.junit.jupiter.api.function.Executable action, String scene) {
        BizException e = assertThrows(BizException.class, action, scene);
        assertEquals(expected, e.getErrorCode(), scene + " 错误码");
    }

    /** 待办列表：college_admin 只看 first 阶段 */
    @Test
    void list_collegeAdmin_seesFirstStageOnly() {
        Page<Review> page = new PageImpl<>(List.of(review(1, "archive", 1L, 100L, "first", "pending")));
        when(reviewRepository.findByStageAndStatus(eq("first"), eq("pending"), any(Pageable.class)))
                .thenReturn(page);
        when(reviewRepository.countByStageAndStatus("first", "pending")).thenReturn(1L);

        Map<String, Object> data = service.listFor(COLLEGE, null, null, null, 1, 20);

        assertEquals(1L, data.get("total"), "first 阶段待办总数");
        assertEquals(1, data.get("page"), "页码");
        assertEquals(20, data.get("page_size"), "每页数量");
        verify(reviewRepository).findByStageAndStatus(eq("first"), eq("pending"), any(Pageable.class));
        verify(reviewRepository, never()).findByStageAndStatusAndBizType(any(), any(), any(), any());
    }

    /** 待办列表：school_admin 只看 second 阶段 */
    @Test
    void list_schoolAdmin_seesSecondStageOnly() {
        when(reviewRepository.findByStageAndStatus(eq("second"), eq("pending"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(reviewRepository.countByStageAndStatus("second", "pending")).thenReturn(0L);

        Map<String, Object> data = service.listFor(SCHOOL, null, null, null, 1, 20);

        assertEquals(0L, data.get("total"), "second 阶段待办总数");
        verify(reviewRepository).findByStageAndStatus(eq("second"), eq("pending"), any(Pageable.class));
    }

    /** 待办列表：biz_type 过滤与 status 指定 */
    @Test
    void list_bizTypeFilterAndStatus() {
        when(reviewRepository.findByStageAndStatusAndBizType(eq("first"), eq("approved"), eq("archive"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(reviewRepository.countByStageAndStatusAndBizType("first", "approved", "archive")).thenReturn(0L);

        Map<String, Object> data = service.listFor(COLLEGE, "archive", "approved", null, 1, 20);

        assertEquals(0L, data.get("total"));
        verify(reviewRepository).findByStageAndStatusAndBizType(eq("first"), eq("approved"), eq("archive"),
                any(Pageable.class));
        verify(reviewRepository, never()).findByStageAndStatus(any(), any(), any(Pageable.class));
    }

    /** 待办列表：非法分页 / 状态参数 → 400 */
    @Test
    void list_invalidParams() {
        assertError(ErrorCode.INVALID_PARAMETER, () -> service.listFor(COLLEGE, null, null, null, 0, 20), "page=0");
        assertError(ErrorCode.INVALID_PARAMETER, () -> service.listFor(COLLEGE, null, null, null, 1, 0), "page_size=0");
        assertError(ErrorCode.INVALID_PARAMETER, () -> service.listFor(COLLEGE, null, null, null, 1, 101), "page_size=101");
        assertError(ErrorCode.INVALID_PARAMETER, () -> service.listFor(COLLEGE, null, "bad", null, 1, 20), "非法 status");
        assertError(ErrorCode.INVALID_PARAMETER, () -> service.listFor(COLLEGE, null, null, "bad", 1, 20), "非法 stage");
    }

    /** 非审核角色调用列表 / 详情 / 通过 / 驳回 → 403 */
    @Test
    void nonReviewer_forbidden() {
        assertError(ErrorCode.FORBIDDEN, () -> service.listFor(ALUMNI_100, null, null, null, 1, 20), "alumni 列表");
        assertError(ErrorCode.FORBIDDEN, () -> service.detail(ALUMNI_100, 1), "alumni 详情");
        assertError(ErrorCode.FORBIDDEN, () -> service.approve(ALUMNI_100, 1), "alumni 通过");
        assertError(ErrorCode.FORBIDDEN, () -> service.reject(ALUMNI_100, 1, "x"), "alumni 驳回");
    }

    /** 详情：不存在 → 404 */
    @Test
    void detail_notFound() {
        when(reviewRepository.findById(999L)).thenReturn(Optional.empty());
        assertError(ErrorCode.NOT_FOUND, () -> service.detail(COLLEGE, 999), "不存在的 id");
    }

    /** 双级流转：初审通过 → stage=second + 业务 pending_second；复核通过 → approved + 业务 approved */
    @Test
    void approve_twoStageFlow() {
        Review record = review(1, "archive", 10L, 100L, "first", "pending");
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(record));

        Review afterFirst = service.approve(COLLEGE, 1);
        assertEquals("second", afterFirst.getStage(), "初审通过后阶段");
        assertEquals("pending", afterFirst.getStatus(), "初审通过后状态仍 pending");
        assertEquals(1L, afterFirst.getReviewerId(), "记录初审人");
        verify(archiveRepository).updateStatus(10L, "pending_second");

        Review afterSecond = service.approve(SCHOOL, 1);
        assertEquals("approved", afterSecond.getStatus(), "复核通过后状态");
        verify(archiveRepository).updateStatus(10L, "approved");
        verify(reviewLogRepository, times(2)).save(argThat(log ->
                ReviewLog.ACTION_APPROVE.equals(log.getAction()) && log.getReviewId() == 1L));
    }

    /** 角色-阶段不匹配：学院不能复核、校级不能初审 → 403 */
    @Test
    void approve_stageRoleMismatch_forbidden() {
        when(reviewRepository.findById(1L))
                .thenReturn(Optional.of(review(1, "archive", 10L, 100L, "first", "pending")));
        when(reviewRepository.findById(2L))
                .thenReturn(Optional.of(review(2, "archive", 11L, 100L, "second", "pending")));

        assertError(ErrorCode.FORBIDDEN, () -> service.approve(SCHOOL, 1), "校级初审");
        assertError(ErrorCode.FORBIDDEN, () -> service.approve(COLLEGE, 2), "学院复核");
    }

    /** 已处理记录再操作 → 409 INVALID_STATE */
    @Test
    void approve_alreadyProcessed_conflict() {
        when(reviewRepository.findById(5L))
                .thenReturn(Optional.of(review(5, "archive", 12L, 100L, "first", "approved")));
        assertError(ErrorCode.INVALID_STATE, () -> service.approve(COLLEGE, 5), "已 approved 再通过");

        when(reviewRepository.findById(7L))
                .thenReturn(Optional.of(review(7, "archive", 13L, 100L, "first", "rejected")));
        assertError(ErrorCode.INVALID_STATE, () -> service.reject(COLLEGE, 7, "再驳回"), "已 rejected 再驳回");
    }

    /** 单级人工核验：两个角色均可处理，通过即终态，无业务表同步 */
    @Test
    void approve_activateManual_singleStage() {
        Review record = review(4, "activate_manual", null, 300L, "first", "pending");
        when(reviewRepository.findById(4L)).thenReturn(Optional.of(record));

        Review after = service.approve(SCHOOL, 4);
        assertEquals("approved", after.getStatus(), "人工核验通过即 approved");
        assertEquals("first", after.getStage(), "单级审核阶段不变");
        verify(archiveRepository, never()).updateStatus(any(), any());
        verify(honorRepository, never()).updateStatus(any(), any());

        Review record2 = review(8, "activate_manual", null, 301L, "first", "pending");
        when(reviewRepository.findById(8L)).thenReturn(Optional.of(record2));
        Review after2 = service.approve(COLLEGE, 8);
        assertEquals("approved", after2.getStatus(), "学院角色也可处理人工核验");
    }

    /** 驳回：reason 必填 → 400 */
    @Test
    void reject_reasonRequired() {
        when(reviewRepository.findById(1L))
                .thenReturn(Optional.of(review(1, "archive", 10L, 100L, "first", "pending")));
        assertError(ErrorCode.INVALID_PARAMETER, () -> service.reject(COLLEGE, 1, null), "缺少 reason");
        assertError(ErrorCode.INVALID_PARAMETER, () -> service.reject(COLLEGE, 1, "  "), "空白 reason");
    }

    /** 驳回成功：置 rejected + 原因 + 业务状态同步 rejected（honor 走 honors 表） */
    @Test
    void reject_flow() {
        Review record = review(3, "honor", 20L, 200L, "first", "pending");
        when(reviewRepository.findById(3L)).thenReturn(Optional.of(record));

        Review after = service.reject(COLLEGE, 3, "证书照片不清晰");
        assertEquals("rejected", after.getStatus(), "驳回后状态");
        assertEquals("证书照片不清晰", after.getReason(), "驳回原因");
        assertEquals(1L, after.getReviewerId(), "记录审核人");
        verify(honorRepository).updateStatus(20L, "rejected");
        verify(reviewLogRepository).save(argThat(log ->
                ReviewLog.ACTION_REJECT.equals(log.getAction())
                        && "证书照片不清晰".equals(log.getReason())));
    }

    /** 删除审核：初审通过 → second；复核通过 → 业务 deleted */
    @Test
    void approve_archiveDelete_flow() {
        Review record = review(6, "archive_delete", 30L, 100L, "first", "pending");
        when(reviewRepository.findById(6L)).thenReturn(Optional.of(record));

        service.approve(COLLEGE, 6);
        assertEquals("second", record.getStage(), "删除审核初审通过后阶段");
        verify(archiveRepository).updateStatus(30L, "pending_second");

        Review after = service.approve(SCHOOL, 6);
        assertEquals("approved", after.getStatus(), "删除审核复核通过");
        verify(archiveRepository).updateStatus(30L, "deleted");
    }

    /** 日志：审核角色与提交人本人可见，其他校友 403 */
    @Test
    void logs_permission() {
        Review record = review(1, "archive", 10L, 100L, "first", "pending");
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(record));
        when(reviewLogRepository.findByReviewIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(new ReviewLog()));

        assertEquals(1, service.logs(SCHOOL, 1).size(), "审核角色可看日志");
        assertEquals(1, service.logs(ALUMNI_100, 1).size(), "提交人本人可看日志");
        assertError(ErrorCode.FORBIDDEN, () -> service.logs(ALUMNI_200, 1), "无关校友看日志");
    }

    // ------------------------------------------------------------------
    // P1 三级审核：stage 筛选 / 导员管辖范围 / 三级流转 / 站内通知
    // ------------------------------------------------------------------

    /** 待办列表：学院/校级可按 stage 筛选（如 college_admin 查 counselor 阶段积压） */
    @Test
    void list_adminStageFilter() {
        when(reviewRepository.findByStageAndStatus(eq("counselor"), eq("pending"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(reviewRepository.countByStageAndStatus("counselor", "pending")).thenReturn(0L);

        Map<String, Object> data = service.listFor(COLLEGE, null, null, "counselor", 1, 20);

        assertEquals(0L, data.get("total"));
        verify(reviewRepository).findByStageAndStatus(eq("counselor"), eq("pending"), any(Pageable.class));
    }

    /** 待办列表：导员强制 stage=counselor 且按管辖范围过滤（走共享库直查，不走 repository） */
    @Test
    void list_counselor_scopeFiltered() {
        Review inScope = review(11, "archive", 10L, 100L, "counselor", "pending");
        when(jdbcTemplate.queryForObject(contains("FROM reviews r"), eq(Long.class), eq("pending"), eq(3L)))
                .thenReturn(1L);
        when(jdbcTemplate.query(contains("SELECT r.id"), any(org.springframework.jdbc.core.RowMapper.class),
                eq("pending"), eq(3L), eq(20), eq(0)))
                .thenReturn(List.of(inScope));

        Map<String, Object> data = service.listFor(COUNSELOR, null, null, null, 1, 20);

        assertEquals(1L, data.get("total"), "导员仅见管辖范围内 counselor 阶段待办");
        @SuppressWarnings("unchecked")
        List<Review> list = (List<Review>) data.get("list");
        assertEquals(1, list.size());
        assertEquals("counselor", list.get(0).getStage());
        // 管辖范围过滤与强制 counselor 阶段由 SQL 承担
        verify(jdbcTemplate).queryForObject(contains("counselor_scopes"), eq(Long.class), any(), any());
        verifyNoInteractions(reviewRepository);
    }

    /** 三级流转：counselor→first（业务 pending_first）→second（pending_second）→approved（approved） */
    @Test
    void approve_threeStageFlow() {
        Review record = review(1, "archive", 10L, 100L, "counselor", "pending");
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(record));
        when(jdbcTemplate.queryForObject(contains("counselor_scopes"), eq(Integer.class), any(), any()))
                .thenReturn(1);

        Review afterCounselor = service.approve(COUNSELOR, 1);
        assertEquals("first", afterCounselor.getStage(), "导员通过后阶段");
        assertEquals("pending", afterCounselor.getStatus(), "导员通过后状态仍 pending");
        assertEquals(3L, afterCounselor.getReviewerId(), "记录导员审核人");
        verify(archiveRepository).updateStatus(10L, "pending_first");

        Review afterFirst = service.approve(COLLEGE, 1);
        assertEquals("second", afterFirst.getStage(), "院系通过后阶段");
        verify(archiveRepository).updateStatus(10L, "pending_second");

        Review afterSecond = service.approve(SCHOOL, 1);
        assertEquals("approved", afterSecond.getStatus(), "校级终审通过");
        verify(archiveRepository).updateStatus(10L, "approved");
        verify(reviewLogRepository, times(3)).save(argThat(log ->
                ReviewLog.ACTION_APPROVE.equals(log.getAction()) && log.getReviewId() == 1L));
    }

    /** 导员越权：提交人不在其管辖范围内，approve/reject 均 403 且不推进流程 */
    @Test
    void counselor_outOfScope_forbidden() {
        Review record = review(2, "archive", 11L, 200L, "counselor", "pending");
        when(reviewRepository.findById(2L)).thenReturn(Optional.of(record));
        when(jdbcTemplate.queryForObject(contains("counselor_scopes"), eq(Integer.class), any(), any()))
                .thenReturn(0);

        assertError(ErrorCode.FORBIDDEN, () -> service.approve(COUNSELOR, 2), "导员越权通过");
        assertError(ErrorCode.FORBIDDEN, () -> service.reject(COUNSELOR, 2, "越权驳回"), "导员越权驳回");
        assertEquals("pending", record.getStatus(), "越权操作不得改变状态");
        assertEquals("counselor", record.getStage(), "越权操作不得推进阶段");
        verify(archiveRepository, never()).updateStatus(any(), any());
        verify(reviewRepository, never()).save(any());
    }

    /** 导员阶段角色匹配：导员不能处理 first/second 阶段，学院/校级不能处理 counselor 阶段 */
    @Test
    void counselor_stageRoleMismatch_forbidden() {
        when(reviewRepository.findById(1L))
                .thenReturn(Optional.of(review(1, "archive", 10L, 100L, "first", "pending")));
        when(reviewRepository.findById(2L))
                .thenReturn(Optional.of(review(2, "archive", 11L, 100L, "counselor", "pending")));

        assertError(ErrorCode.FORBIDDEN, () -> service.approve(COUNSELOR, 1), "导员处理 first 阶段");
        assertError(ErrorCode.FORBIDDEN, () -> service.approve(COLLEGE, 2), "学院处理 counselor 阶段");
        assertError(ErrorCode.FORBIDDEN, () -> service.approve(SCHOOL, 2), "校级处理 counselor 阶段");
    }

    /** 导员驳回：管辖范围内 → rejected + 业务 rejected + 通知含 reason */
    @Test
    void reject_counselorInScope() {
        Review record = review(3, "honor", 20L, 200L, "counselor", "pending");
        when(reviewRepository.findById(3L)).thenReturn(Optional.of(record));
        when(jdbcTemplate.queryForObject(contains("counselor_scopes"), eq(Integer.class), any(), any()))
                .thenReturn(1);

        Review after = service.reject(COUNSELOR, 3, "材料不全");
        assertEquals("rejected", after.getStatus());
        assertEquals("材料不全", after.getReason());
        verify(honorRepository).updateStatus(20L, "rejected");
        verify(jdbcTemplate).update(contains("INSERT INTO notifications"), eq(200L), eq("审核驳回"),
                argThat((String content) -> content.contains("材料不全")), eq("honor"), eq(20L));
    }

    /** 审核结果站内通知：昵称+业务标题拼装（不含学号），通过标题为「审核通过」 */
    @Test
    void approve_writesNotification() {
        Review record = review(4, "archive", 10L, 100L, "first", "pending");
        when(reviewRepository.findById(4L)).thenReturn(Optional.of(record));
        when(jdbcTemplate.queryForList(contains("SELECT nickname FROM users"), any(Object[].class)))
                .thenReturn(List.of(Map.of("nickname", "小明")));
        when(jdbcTemplate.queryForList(contains("SELECT title FROM archives"), any(Object[].class)))
                .thenReturn(List.of(Map.of("title", "优秀志愿者事迹")));
        when(jdbcTemplate.queryForList(contains("notification_subscriptions"), any(Object[].class)))
                .thenReturn(List.of(Map.of("channel", "sms")));

        service.approve(COLLEGE, 4);

        verify(jdbcTemplate).update(contains("INSERT INTO notifications"), eq(100L), eq("审核通过"),
                argThat((String content) -> content.contains("小明")
                        && content.contains("优秀志愿者事迹")
                        && !content.contains("2021000001")),
                eq("archive"), eq(10L));
    }

    /** 通知失败不影响审核主流程：notifications 写入异常时 approve 仍成功 */
    @Test
    void approve_notificationFailure_doesNotBreakFlow() {
        Review record = review(5, "archive", 10L, 100L, "first", "pending");
        when(reviewRepository.findById(5L)).thenReturn(Optional.of(record));
        when(jdbcTemplate.update(contains("INSERT INTO notifications"), any(Object[].class)))
                .thenThrow(new RuntimeException("notifications 表不可用"));

        Review after = service.approve(COLLEGE, 5);

        assertEquals("second", after.getStage(), "通知失败不影响审核推进");
        verify(archiveRepository).updateStatus(10L, "pending_second");
        verify(reviewRepository).save(record);
    }
}
