package com.alumni.honors;

import com.alumni.honors.common.BizException;
import com.alumni.honors.common.ErrorCode;
import com.alumni.honors.dto.HonorDtos.CreateHonorRequest;
import com.alumni.honors.dto.HonorDtos.UpdateHonorRequest;
import com.alumni.honors.entity.Archive;
import com.alumni.honors.entity.Attachment;
import com.alumni.honors.entity.DictItem;
import com.alumni.honors.entity.Honor;
import com.alumni.honors.entity.Review;
import com.alumni.honors.entity.ReviewLog;
import com.alumni.honors.repository.ArchiveRepository;
import com.alumni.honors.repository.AttachmentRepository;
import com.alumni.honors.repository.DictItemRepository;
import com.alumni.honors.repository.HonorRepository;
import com.alumni.honors.repository.ReviewLogRepository;
import com.alumni.honors.repository.ReviewRepository;
import com.alumni.common.pinyin.PinyinUtils;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.honors.service.HonorService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * HonorService 单元测试（Mockito mock Repository 与 JdbcTemplate，无 HTTP / 无数据库）。
 *
 * <p>覆盖：创建（正常流程=存荣誉+引用附件+建 Review+建系统日志 / 非 student/alumni 403 /
 * category 不在字典 400 / 档案不存在 404 / 档案他人 403 / 附件不存在 404 / 附件他人上传 403 /
 * 附件已引用 409 / 无档案无附件创建成功 / P1 导员覆盖三级 / 标题拼音列写入）、
 * 列表（分页参数非法 400 / status 非法 400 / college_id 字典不存在返回空列表 / 正常分页查询）、
 * 详情（不存在 404 / 含 attachment_ids）、
 * 修改（非本人 403 / 审核已处理 409 / pending_counselor 可改 / 成功含附件换绑 / category 非法 400）。</p>
 *
 * <p>P0 语义保持：JdbcTemplate 覆盖检查默认返回 null（视为 0=无导员覆盖），
 * 既有创建用例仍落 pending_first + Review stage=first。</p>
 */
@ExtendWith(MockitoExtension.class)
class HonorServiceTest {

    private static final AuthPrincipal ALUMNI = new AuthPrincipal(100L, "2021001", "alumni");
    private static final AuthPrincipal STUDENT = new AuthPrincipal(101L, "2023002", "student");
    private static final AuthPrincipal SCHOOL = new AuthPrincipal(2L, null, "school_admin");

    @Mock
    private HonorRepository honorRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ReviewLogRepository reviewLogRepository;
    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private ArchiveRepository archiveRepository;
    @Mock
    private DictItemRepository dictItemRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private HonorService service;

    @BeforeEach
    void setUp() {
        service = new HonorService(honorRepository, reviewRepository, reviewLogRepository,
                attachmentRepository, archiveRepository, dictItemRepository, entityManager, jdbcTemplate);
    }

    /** 正常创建：存荣誉（pending_first/归属当前用户）+ 附件逐条置 referenced + 建 first/pending Review + 建系统 create 日志 */
    @Test
    void create_happyPath() {
        CreateHonorRequest request = new CreateHonorRequest(12L, "国家奖学金", "scholarship", "national",
                LocalDate.parse("2025-11-01"), null, List.of(7L, 8L));
        when(dictItemRepository.existsByTypeAndValue("honor_category", "scholarship")).thenReturn(true);
        when(archiveRepository.findById(12L)).thenReturn(Optional.of(archive(12L, ALUMNI.userId())));
        when(attachmentRepository.findById(7L))
                .thenReturn(Optional.of(attachment(7L, ALUMNI.userId(), Attachment.STATUS_UNREFERENCED)));
        when(attachmentRepository.findById(8L))
                .thenReturn(Optional.of(attachment(8L, ALUMNI.userId(), Attachment.STATUS_UNREFERENCED)));
        when(honorRepository.saveAndFlush(any(Honor.class))).thenAnswer(inv -> {
            Honor h = inv.getArgument(0);
            h.setId(501L);
            h.setCreatedAt(Instant.parse("2026-09-18T02:56:00Z"));
            h.setUpdatedAt(Instant.parse("2026-09-18T02:56:00Z"));
            return h;
        });
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(900L);
            return r;
        });

        Map<String, Object> data = service.create(ALUMNI, request);

        // 返回完整荣誉数据（created_at 回读）
        assertEquals(501L, data.get("id"));
        assertEquals(100L, data.get("user_id"));
        assertEquals(12L, data.get("archive_id"));
        assertEquals("国家奖学金", data.get("title"));
        assertEquals("scholarship", data.get("category"));
        assertEquals("national", data.get("level"));
        assertEquals(LocalDate.parse("2025-11-01"), data.get("awarded_at"));
        assertEquals("pending_first", data.get("status"));
        assertEquals(Instant.parse("2026-09-18T02:56:00Z"), data.get("created_at"));

        // 荣誉落库：归属当前用户、初始待初审
        ArgumentCaptor<Honor> honorCaptor = ArgumentCaptor.forClass(Honor.class);
        verify(honorRepository).saveAndFlush(honorCaptor.capture());
        assertEquals(100L, honorCaptor.getValue().getUserId(), "荣誉归属当前用户");
        assertEquals("pending_first", honorCaptor.getValue().getStatus());

        // 附件逐条引用：referenced + biz_type=honor + ref_id=荣誉ID + referenced_at 回填
        ArgumentCaptor<Attachment> attachmentCaptor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository, times(2)).save(attachmentCaptor.capture());
        List<Attachment> referenced = attachmentCaptor.getAllValues();
        assertEquals(List.of(7L, 8L), referenced.stream().map(Attachment::getId).toList());
        for (Attachment a : referenced) {
            assertEquals(Attachment.STATUS_REFERENCED, a.getStatus());
            assertEquals(Attachment.BIZ_TYPE_HONOR, a.getBizType());
            assertEquals(501L, a.getRefId());
            assertNotNull(a.getReferencedAt(), "referenced_at 必须回填");
        }

        // Review：biz_type=honor，first 阶段 pending
        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(reviewCaptor.capture());
        assertEquals("honor", reviewCaptor.getValue().getBizType());
        assertEquals(501L, reviewCaptor.getValue().getBizId());
        assertEquals(100L, reviewCaptor.getValue().getSubmitterId());
        assertEquals("first", reviewCaptor.getValue().getStage());
        assertEquals("pending", reviewCaptor.getValue().getStatus());

        // ReviewLog：系统动作（operator_id=null），action=create
        ArgumentCaptor<ReviewLog> logCaptor = ArgumentCaptor.forClass(ReviewLog.class);
        verify(reviewLogRepository).save(logCaptor.capture());
        assertEquals(900L, logCaptor.getValue().getReviewId());
        assertNull(logCaptor.getValue().getOperatorId(), "创建日志为系统动作");
        assertEquals("create", logCaptor.getValue().getAction());

        verify(entityManager).refresh(any(Honor.class));
    }

    /** 幂等校验：同用户+同标题+同获奖日期 已有待审/已通过荣誉 → 409 INVALID_STATE */
    @Test
    void create_duplicate_conflict() {
        CreateHonorRequest request = new CreateHonorRequest(null, "国家奖学金", "scholarship", "national",
                LocalDate.parse("2025-11-01"), null, null);
        when(dictItemRepository.existsByTypeAndValue("honor_category", "scholarship")).thenReturn(true);
        when(honorRepository.existsByUserIdAndTitleAndAwardedAtAndStatusIn(
                eq(ALUMNI.userId()), eq("国家奖学金"), eq(LocalDate.parse("2025-11-01")), any()))
                .thenReturn(true);

        BizException e = assertThrows(BizException.class, () -> service.create(ALUMNI, request));
        assertEquals(ErrorCode.INVALID_STATE, e.getErrorCode());
        assertEquals("相同荣誉已提交（待审核或已通过），请勿重复创建", e.getMessage());
        verify(honorRepository, never()).saveAndFlush(any());
        verifyNoInteractions(reviewRepository, reviewLogRepository);
    }

    /** 幂等校验放行：不存在占用中的相同荣誉（如旧记录已 rejected）→ 正常创建 */
    @Test
    void create_duplicateRejected_allowed() {
        CreateHonorRequest request = new CreateHonorRequest(null, "国家奖学金", "scholarship", "national",
                LocalDate.parse("2025-11-01"), null, null);
        when(dictItemRepository.existsByTypeAndValue("honor_category", "scholarship")).thenReturn(true);
        when(honorRepository.existsByUserIdAndTitleAndAwardedAtAndStatusIn(
                eq(ALUMNI.userId()), eq("国家奖学金"), eq(LocalDate.parse("2025-11-01")), any()))
                .thenReturn(false);
        when(honorRepository.saveAndFlush(any(Honor.class))).thenAnswer(inv -> {
            Honor h = inv.getArgument(0);
            h.setId(502L);
            return h;
        });
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(901L);
            return r;
        });

        Map<String, Object> data = service.create(ALUMNI, request);

        assertEquals(502L, data.get("id"));
        assertEquals("pending_first", data.get("status"));
        verify(reviewRepository).save(any(Review.class));
        verify(reviewLogRepository).save(any(ReviewLog.class));
    }

    /** school_admin 等非 student/alumni 角色 → 403「仅学生/校友可创建荣誉」，且不触库 */
    @Test
    void create_nonStudentOrAlumni_forbidden() {
        CreateHonorRequest request = new CreateHonorRequest(null, "国家奖学金", "scholarship", "national",
                LocalDate.parse("2025-11-01"), null, null);

        BizException e = assertThrows(BizException.class, () -> service.create(SCHOOL, request));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        assertEquals("仅学生/校友可创建荣誉", e.getMessage());
        verifyNoInteractions(honorRepository, reviewRepository, reviewLogRepository,
                attachmentRepository, archiveRepository, dictItemRepository);
    }

    /** category 不存在于 honor_category 字典 → 400「荣誉类别不存在」 */
    @Test
    void create_categoryNotInDict_badRequest() {
        CreateHonorRequest request = new CreateHonorRequest(null, "某奖", "unknown_cat", "national",
                LocalDate.parse("2025-11-01"), null, null);
        when(dictItemRepository.existsByTypeAndValue("honor_category", "unknown_cat")).thenReturn(false);

        BizException e = assertThrows(BizException.class, () -> service.create(ALUMNI, request));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        assertEquals("荣誉类别不存在", e.getMessage());
        verify(honorRepository, never()).saveAndFlush(any());
    }

    /** archive_id 指向的档案不存在 → 404「关联档案不存在」 */
    @Test
    void create_archiveNotFound() {
        CreateHonorRequest request = new CreateHonorRequest(99L, "国家奖学金", "scholarship", "national",
                LocalDate.parse("2025-11-01"), null, null);
        when(dictItemRepository.existsByTypeAndValue("honor_category", "scholarship")).thenReturn(true);
        when(archiveRepository.findById(99L)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class, () -> service.create(ALUMNI, request));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        assertEquals("关联档案不存在", e.getMessage());
        verify(honorRepository, never()).saveAndFlush(any());
    }

    /** 越权红线：关联他人档案 → 403「只能关联本人档案」 */
    @Test
    void create_otherUsersArchive_forbidden() {
        CreateHonorRequest request = new CreateHonorRequest(12L, "国家奖学金", "scholarship", "national",
                LocalDate.parse("2025-11-01"), null, null);
        when(dictItemRepository.existsByTypeAndValue("honor_category", "scholarship")).thenReturn(true);
        when(archiveRepository.findById(12L)).thenReturn(Optional.of(archive(12L, 200L))); // 归属 user 200

        BizException e = assertThrows(BizException.class, () -> service.create(ALUMNI, request));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        assertEquals("只能关联本人档案", e.getMessage());
        verify(honorRepository, never()).saveAndFlush(any());
    }

    /** 附件不存在 → 404「附件不存在: {id}」 */
    @Test
    void create_attachmentNotFound() {
        CreateHonorRequest request = new CreateHonorRequest(null, "国家奖学金", "scholarship", "national",
                LocalDate.parse("2025-11-01"), null, List.of(77L));
        when(dictItemRepository.existsByTypeAndValue("honor_category", "scholarship")).thenReturn(true);
        when(attachmentRepository.findById(77L)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class, () -> service.create(ALUMNI, request));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        assertEquals("附件不存在: 77", e.getMessage());
        verify(honorRepository, never()).saveAndFlush(any());
    }

    /** 越权红线：引用他人上传的附件 → 403「只能引用本人上传的附件」 */
    @Test
    void create_attachmentUploadedByOther_forbidden() {
        CreateHonorRequest request = new CreateHonorRequest(null, "国家奖学金", "scholarship", "national",
                LocalDate.parse("2025-11-01"), null, List.of(7L));
        when(dictItemRepository.existsByTypeAndValue("honor_category", "scholarship")).thenReturn(true);
        when(attachmentRepository.findById(7L))
                .thenReturn(Optional.of(attachment(7L, 200L, Attachment.STATUS_UNREFERENCED))); // 他人上传

        BizException e = assertThrows(BizException.class, () -> service.create(ALUMNI, request));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        assertEquals("只能引用本人上传的附件", e.getMessage());
        verify(honorRepository, never()).saveAndFlush(any());
    }

    /** 附件已被引用（非 unreferenced）→ 409「附件已被引用: {id}」 */
    @Test
    void create_attachmentAlreadyReferenced_conflict() {
        CreateHonorRequest request = new CreateHonorRequest(null, "国家奖学金", "scholarship", "national",
                LocalDate.parse("2025-11-01"), null, List.of(7L));
        when(dictItemRepository.existsByTypeAndValue("honor_category", "scholarship")).thenReturn(true);
        when(attachmentRepository.findById(7L))
                .thenReturn(Optional.of(attachment(7L, ALUMNI.userId(), Attachment.STATUS_REFERENCED)));

        BizException e = assertThrows(BizException.class, () -> service.create(ALUMNI, request));
        assertEquals(ErrorCode.INVALID_STATE, e.getErrorCode());
        assertEquals("附件已被引用: 7", e.getMessage());
        verify(honorRepository, never()).saveAndFlush(any());
    }

    /** 无 archive 无附件的最简创建：不触碰档案/附件库，仍生成 Review 与系统日志 */
    @Test
    void create_withoutArchiveAndAttachments_ok() {
        CreateHonorRequest request = new CreateHonorRequest(null, "数学建模国赛一等奖", "competition", "national",
                LocalDate.parse("2025-09-01"), null, null);
        when(dictItemRepository.existsByTypeAndValue("honor_category", "competition")).thenReturn(true);
        when(honorRepository.saveAndFlush(any(Honor.class))).thenAnswer(inv -> {
            Honor h = inv.getArgument(0);
            h.setId(502L);
            h.setCreatedAt(Instant.parse("2026-09-18T03:00:00Z"));
            h.setUpdatedAt(Instant.parse("2026-09-18T03:00:00Z"));
            return h;
        });
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(901L);
            return r;
        });

        Map<String, Object> data = service.create(STUDENT, request);

        assertEquals(502L, data.get("id"));
        assertEquals(101L, data.get("user_id"));
        assertNull(data.get("archive_id"));
        assertEquals("pending_first", data.get("status"));
        verifyNoInteractions(archiveRepository);
        verify(attachmentRepository, never()).save(any(Attachment.class));
        verify(reviewRepository).save(any(Review.class));
        verify(reviewLogRepository).save(any(ReviewLog.class));
    }

    /** 列表：page < 1 → 400，且不触库 */
    @Test
    void list_invalidPage_badRequest() {
        BizException e = assertThrows(BizException.class,
                () -> service.list(null, null, null, null, null, null, null, null, 0, 20));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verifyNoInteractions(honorRepository, dictItemRepository);
    }

    /** 列表：page_size > 100 → 400，且不触库 */
    @Test
    void list_invalidPageSize_badRequest() {
        BizException e = assertThrows(BizException.class,
                () -> service.list(null, null, null, null, null, null, null, null, 1, 101));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verifyNoInteractions(honorRepository, dictItemRepository);
    }

    /** 列表：status 筛选非法（仅允许四态）→ 400 */
    @Test
    void list_invalidStatusFilter_badRequest() {
        BizException e = assertThrows(BizException.class,
                () -> service.list(null, null, null, null, "pending", null, null, null, 1, 20));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        assertTrue(e.getMessage().contains("status"));
        verifyNoInteractions(honorRepository, dictItemRepository);
    }

    /** 列表：college_id 字典项不存在 → 直接返回空列表，不查 honors */
    @Test
    void list_collegeIdNotInDict_returnsEmpty() {
        when(dictItemRepository.findByTypeAndId("college", 999L)).thenReturn(Optional.empty());

        Map<String, Object> data = service.list(null, 999L, null, null, null, null, null, null, 1, 20);

        assertEquals(List.of(), data.get("list"));
        assertEquals(0L, data.get("total"));
        assertEquals(1, data.get("page"));
        assertEquals(20, data.get("page_size"));
        verify(honorRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    /** 列表：正常分页查询，college_id 转 label 后走 Specification，排序 -awarded_at 生效 */
    @Test
    @SuppressWarnings("unchecked")
    void list_happyPath() {
        DictItem college = new DictItem();
        college.setId(5L);
        college.setType("college");
        college.setLabel("计算机学院");
        college.setValue("cs");
        when(dictItemRepository.findByTypeAndId("college", 5L)).thenReturn(Optional.of(college));

        Honor honor = honor(501L, 100L, "approved");
        honor.setTitle("国家奖学金");
        honor.setCategory("scholarship");
        honor.setLevel("national");
        honor.setAwardedAt(LocalDate.parse("2025-11-01"));
        honor.setCreatedAt(Instant.parse("2026-09-18T02:56:00Z"));
        honor.setUpdatedAt(Instant.parse("2026-09-18T02:56:00Z"));
        when(honorRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> {
                    Pageable pageable = inv.getArgument(1);
                    return new PageImpl<>(List.of(honor), pageable, 1L);
                });

        Map<String, Object> data = service.list("张", 5L, 2020, "scholarship", "approved",
                "2025-01-01", "2025-12-31", "-awarded_at", 1, 10);

        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        assertEquals(1, list.size());
        assertEquals(501L, list.get(0).get("id"));
        assertEquals("国家奖学金", list.get(0).get("title"));
        assertEquals(1L, data.get("total"));
        assertEquals(1, data.get("page"));
        assertEquals(10, data.get("page_size"));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(honorRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("awardedAt");
        assertNotNull(order, "排序字段 awarded_at 应映射为 awardedAt");
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    /** 详情：荣誉不存在（含 id 非正整数）→ 404「荣誉不存在」 */
    @Test
    void detail_notFound() {
        when(honorRepository.findById(999L)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class, () -> service.detail(999L));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        assertEquals("荣誉不存在", e.getMessage());

        // id 非正整数同样 404，且不触库
        BizException e2 = assertThrows(BizException.class, () -> service.detail(0L));
        assertEquals(ErrorCode.NOT_FOUND, e2.getErrorCode());
        verify(honorRepository, times(1)).findById(anyLong());
    }

    /** 详情：返回荣誉字段 + attachment_ids（该荣誉已引用附件的 ID 列表） */
    @Test
    void detail_happyPath_withAttachmentIds() {
        Honor honor = honor(501L, 100L, "pending_first");
        honor.setTitle("国家奖学金");
        honor.setCategory("scholarship");
        honor.setLevel("national");
        honor.setAwardedAt(LocalDate.parse("2025-11-01"));
        honor.setArchiveId(12L);
        honor.setCreatedAt(Instant.parse("2026-09-18T02:56:00Z"));
        honor.setUpdatedAt(Instant.parse("2026-09-18T02:56:00Z"));
        when(honorRepository.findById(501L)).thenReturn(Optional.of(honor));

        Attachment a7 = attachment(7L, 100L, Attachment.STATUS_REFERENCED);
        a7.setBizType("honor");
        a7.setRefId(501L);
        Attachment a8 = attachment(8L, 100L, Attachment.STATUS_REFERENCED);
        a8.setBizType("honor");
        a8.setRefId(501L);
        when(attachmentRepository.findByBizTypeAndRefId("honor", 501L)).thenReturn(List.of(a7, a8));

        Map<String, Object> data = service.detail(501L);

        assertEquals(501L, data.get("id"));
        assertEquals("国家奖学金", data.get("title"));
        assertEquals(List.of(7L, 8L), data.get("attachment_ids"));
    }

    /** 越权红线：修改他人荣誉 → 403「只能修改本人荣誉」 */
    @Test
    void update_notOwner_forbidden() {
        when(honorRepository.findById(501L)).thenReturn(Optional.of(honor(501L, 200L, "pending_first")));
        UpdateHonorRequest request = new UpdateHonorRequest(null, "新标题", null, null, null, null, null);

        BizException e = assertThrows(BizException.class, () -> service.update(ALUMNI, 501L, request));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        assertEquals("只能修改本人荣誉", e.getMessage());
        verify(honorRepository, never()).saveAndFlush(any());
    }

    /** 审核已开始或已结束（非 pending_first）→ 409，不可修改 */
    @Test
    void update_statusNotPendingFirst_conflict() {
        when(honorRepository.findById(501L))
                .thenReturn(Optional.of(honor(501L, ALUMNI.userId(), "pending_second")));
        UpdateHonorRequest request = new UpdateHonorRequest(null, "新标题", null, null, null, null, null);

        BizException e = assertThrows(BizException.class, () -> service.update(ALUMNI, 501L, request));
        assertEquals(ErrorCode.INVALID_STATE, e.getErrorCode());
        assertEquals("审核已开始或已结束，不可修改", e.getMessage());
        verify(honorRepository, never()).saveAndFlush(any());
    }

    /** 正常修改：逐字段更新 + 附件换绑（旧附件释放为 unreferenced，新附件引用为 referenced） */
    @Test
    void update_happyPath_withAttachmentRebind() {
        Honor honor = honor(501L, ALUMNI.userId(), "pending_first");
        honor.setTitle("旧标题");
        honor.setCategory("scholarship");
        honor.setLevel("school");
        honor.setAwardedAt(LocalDate.parse("2024-05-01"));
        when(honorRepository.findById(501L)).thenReturn(Optional.of(honor));
        when(dictItemRepository.existsByTypeAndValue("honor_category", "competition")).thenReturn(true);
        when(archiveRepository.findById(20L)).thenReturn(Optional.of(archive(20L, ALUMNI.userId())));

        // 旧附件（当前被该荣誉引用）→ 应释放
        Attachment oldAtt = attachment(7L, ALUMNI.userId(), Attachment.STATUS_REFERENCED);
        oldAtt.setBizType("honor");
        oldAtt.setRefId(501L);
        oldAtt.setReferencedAt(Instant.parse("2026-09-01T00:00:00Z"));
        // 新附件（本人上传、未引用）→ 应引用
        Attachment newAtt = attachment(9L, ALUMNI.userId(), Attachment.STATUS_UNREFERENCED);
        when(attachmentRepository.findById(9L)).thenReturn(Optional.of(newAtt));
        when(attachmentRepository.findByBizTypeAndRefId("honor", 501L)).thenReturn(List.of(oldAtt));

        when(honorRepository.saveAndFlush(any(Honor.class))).thenAnswer(inv -> {
            Honor h = inv.getArgument(0);
            h.setUpdatedAt(Instant.parse("2026-09-18T04:00:00Z"));
            return h;
        });

        UpdateHonorRequest request = new UpdateHonorRequest(20L, "全国大学生数学建模竞赛一等奖",
                "competition", "national", LocalDate.parse("2025-09-01"), null, List.of(9L));

        Map<String, Object> data = service.update(ALUMNI, 501L, request);

        // 字段逐条更新
        assertEquals("全国大学生数学建模竞赛一等奖", data.get("title"));
        assertEquals("competition", data.get("category"));
        assertEquals("national", data.get("level"));
        assertEquals(LocalDate.parse("2025-09-01"), data.get("awarded_at"));
        assertEquals(20L, data.get("archive_id"));

        // 旧附件已释放：unreferenced + ref_id/referenced_at 置空（bizType 保留 honor）
        assertEquals(Attachment.STATUS_UNREFERENCED, oldAtt.getStatus(), "旧附件应释放");
        assertNull(oldAtt.getRefId());
        assertNull(oldAtt.getReferencedAt());
        // 新附件已引用
        assertEquals(Attachment.STATUS_REFERENCED, newAtt.getStatus(), "新附件应引用");
        assertEquals("honor", newAtt.getBizType());
        assertEquals(501L, newAtt.getRefId());
        assertNotNull(newAtt.getReferencedAt());
        // 释放与引用各落库一次
        verify(attachmentRepository).save(oldAtt);
        verify(attachmentRepository).save(newAtt);
    }

    /** 修改：category 不存在于 honor_category 字典 → 400，且不保存 */
    @Test
    void update_invalidCategory_badRequest() {
        when(honorRepository.findById(501L))
                .thenReturn(Optional.of(honor(501L, ALUMNI.userId(), "pending_first")));
        when(dictItemRepository.existsByTypeAndValue("honor_category", "unknown_cat")).thenReturn(false);
        UpdateHonorRequest request = new UpdateHonorRequest(null, null, "unknown_cat", null, null, null, null);

        BizException e = assertThrows(BizException.class, () -> service.update(ALUMNI, 501L, request));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        assertEquals("荣誉类别不存在", e.getMessage());
        verify(honorRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------
    // P1 三级审核：导员覆盖 / 无覆盖兜底 / 标题拼音列 / pending_counselor 可改
    // ------------------------------------------------------------------

    /** P1：有导员覆盖 → status=pending_counselor + Review stage=counselor，标题拼音列随创建写入 */
    @Test
    void create_withCounselorCoverage_counselorStage() {
        CreateHonorRequest request = new CreateHonorRequest(null, "国家奖学金", "scholarship", "national",
                LocalDate.parse("2025-11-01"), null, null);
        when(dictItemRepository.existsByTypeAndValue("honor_category", "scholarship")).thenReturn(true);
        when(jdbcTemplate.queryForObject(contains("counselor_scopes"), eq(Integer.class), any())).thenReturn(1);
        when(honorRepository.saveAndFlush(any(Honor.class))).thenAnswer(inv -> {
            Honor h = inv.getArgument(0);
            h.setId(601L);
            return h;
        });
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(902L);
            return r;
        });

        Map<String, Object> data = service.create(ALUMNI, request);

        assertEquals("pending_counselor", data.get("status"), "有导员覆盖时进 counselor 环节");

        ArgumentCaptor<Honor> honorCaptor = ArgumentCaptor.forClass(Honor.class);
        verify(honorRepository).saveAndFlush(honorCaptor.capture());
        assertEquals("pending_counselor", honorCaptor.getValue().getStatus());
        assertEquals(PinyinUtils.toFullPinyin("国家奖学金"), honorCaptor.getValue().getTitlePinyin(), "标题全拼");
        assertEquals(PinyinUtils.toAbbr("国家奖学金"), honorCaptor.getValue().getTitlePinyinAbbr(), "标题首字母");

        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(reviewCaptor.capture());
        assertEquals("counselor", reviewCaptor.getValue().getStage());
        assertEquals("pending", reviewCaptor.getValue().getStatus());
    }

    /** P1 无导员覆盖兜底：覆盖检查返回 0 → 与 P0 一致 pending_first + Review stage=first，流程不卡死 */
    @Test
    void create_withoutCounselorCoverage_fallbackFirst() {
        CreateHonorRequest request = new CreateHonorRequest(null, "数学建模国赛一等奖", "competition", "national",
                LocalDate.parse("2025-09-01"), null, null);
        when(dictItemRepository.existsByTypeAndValue("honor_category", "competition")).thenReturn(true);
        when(jdbcTemplate.queryForObject(contains("counselor_scopes"), eq(Integer.class), any())).thenReturn(0);
        when(honorRepository.saveAndFlush(any(Honor.class))).thenAnswer(inv -> {
            Honor h = inv.getArgument(0);
            h.setId(602L);
            return h;
        });
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(903L);
            return r;
        });

        Map<String, Object> data = service.create(STUDENT, request);

        assertEquals("pending_first", data.get("status"), "无导员覆盖兜底跳过 counselor 环节");
        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(reviewCaptor.capture());
        assertEquals("first", reviewCaptor.getValue().getStage());
    }

    /** 列表：status=pending_counselor 为 P1 新增合法筛选值，不再 400 */
    @Test
    void list_pendingCounselorStatus_allowed() {
        when(honorRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Map<String, Object> data = service.list(null, null, null, null, "pending_counselor",
                null, null, null, 1, 20);

        assertEquals(0L, data.get("total"));
        verify(honorRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    /** 修改：pending_counselor 环节同样"审核未处理"，允许本人修改且标题拼音列同步重算 */
    @Test
    void update_pendingCounselor_allowed() {
        Honor honor = honor(501L, ALUMNI.userId(), "pending_counselor");
        honor.setTitle("旧标题");
        honor.setCategory("scholarship");
        honor.setLevel("school");
        honor.setAwardedAt(LocalDate.parse("2024-05-01"));
        when(honorRepository.findById(501L)).thenReturn(Optional.of(honor));
        when(honorRepository.saveAndFlush(any(Honor.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateHonorRequest request = new UpdateHonorRequest(null, "新标题", null, null, null, null, null);
        Map<String, Object> data = service.update(ALUMNI, 501L, request);

        assertEquals("新标题", data.get("title"));
        assertEquals(PinyinUtils.toFullPinyin("新标题"), honor.getTitlePinyin(), "标题变更重算全拼");
        assertEquals(PinyinUtils.toAbbr("新标题"), honor.getTitlePinyinAbbr(), "标题变更重算首字母");
        verify(honorRepository).saveAndFlush(honor);
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    private Honor honor(long id, long userId, String status) {
        Honor h = new Honor();
        h.setId(id);
        h.setUserId(userId);
        h.setStatus(status);
        return h;
    }

    private Archive archive(long id, long userId) {
        Archive a = new Archive();
        a.setId(id);
        a.setUserId(userId);
        a.setStatus("approved");
        return a;
    }

    private Attachment attachment(long id, long uploaderId, String status) {
        Attachment a = new Attachment();
        a.setId(id);
        a.setUploaderId(uploaderId);
        a.setStatus(status);
        return a;
    }
}
