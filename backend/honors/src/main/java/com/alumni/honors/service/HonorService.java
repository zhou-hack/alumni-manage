package com.alumni.honors.service;

import com.alumni.honors.common.BizException;
import com.alumni.honors.common.ErrorCode;
import com.alumni.honors.dto.HonorDtos.CreateHonorRequest;
import com.alumni.honors.dto.HonorDtos.UpdateHonorRequest;
import com.alumni.honors.entity.AlumniBase;
import com.alumni.honors.entity.Archive;
import com.alumni.honors.entity.Attachment;
import com.alumni.honors.entity.DictItem;
import com.alumni.honors.entity.Honor;
import com.alumni.honors.entity.Review;
import com.alumni.honors.entity.ReviewLog;
import com.alumni.honors.entity.User;
import com.alumni.honors.repository.ArchiveRepository;
import com.alumni.honors.repository.AttachmentRepository;
import com.alumni.honors.repository.DictItemRepository;
import com.alumni.honors.repository.HonorRepository;
import com.alumni.honors.repository.ReviewLogRepository;
import com.alumni.honors.repository.ReviewRepository;
import com.alumni.common.pinyin.PinyinUtils;
import com.alumni.common.security.AuthPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 校友荣誉业务实现（Service 层）。
 *
 * <p>职责：角色校验、字典校验、档案存在性与归属校验、附件引用/释放编排、
 * 创建即生成 Reviews 待审记录（biz_type='honor'）与系统审核日志、
 * 列表多条件筛选（JPA Specification 组合）；不含 SQL 与 HTTP 细节，全部通过 Repository 取数。</p>
 *
 * <p>状态机（P1 三级审核，规范 2.1）：创建时先做导员覆盖检查，命中 → status='pending_counselor'
 * （Review stage=counselor），未命中任何 active 导员范围 → 兜底 'pending_first'（stage=first）；
 * 由 reviews 模块驱动 counselor → first → second → approved/rejected；
 * 本模块仅在 pending_counselor / pending_first（审核未处理）时允许本人修改。</p>
 */
@Service
public class HonorService {

    /** 荣誉状态：待导员初审（P1 起新提交且有导员覆盖时为此状态） */
    public static final String STATUS_PENDING_COUNSELOR = "pending_counselor";
    /** 荣誉状态：待院系初审（无导员覆盖兜底 / 存量在途单） */
    public static final String STATUS_PENDING_FIRST = "pending_first";
    /** 合法荣誉状态集合（列表 status 筛选用） */
    private static final Set<String> HONOR_STATUSES =
            Set.of("pending_counselor", "pending_first", "pending_second", "approved", "rejected");
    /** 幂等占用状态：处于这些状态的相同（标题+获奖日期）荣誉禁止重复创建（rejected 允许重提） */
    private static final Set<String> IDEMPOTENT_STATUSES =
            Set.of("pending_counselor", "pending_first", "pending_second", "approved");
    /** 允许本人修改的状态：counselor 与 first 环节均属"审核未处理" */
    private static final Set<String> EDITABLE_STATUSES =
            Set.of("pending_counselor", "pending_first");
    /** 创建限定角色 */
    private static final String ROLE_STUDENT = "student";
    private static final String ROLE_ALUMNI = "alumni";
    /** 字典类型：荣誉类别 */
    private static final String DICT_TYPE_HONOR_CATEGORY = "honor_category";
    /** 字典类型：学院（college_id → label 转换） */
    private static final String DICT_TYPE_COLLEGE = "college";
    /** Reviews 业务类型 */
    private static final String BIZ_TYPE_HONOR = "honor";
    /** 分页上限（规范 1.4 节 page_size 最大 100） */
    public static final int MAX_PAGE_SIZE = 100;
    /** 默认排序：created_at 降序 */
    private static final String DEFAULT_SORT_FIELD = "createdAt";
    /** 允许排序的字段映射（snake_case → 实体属性） */
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "created_at", "createdAt",
            "awarded_at", "awardedAt",
            "title", "title");

    /**
     * 导员覆盖检查（共享库直查）：提交人（users⋈alumni_base）是否命中任一 active 导员的
     * counselor_scopes（同学院 + 届别∪专业∪学号任一命中；major_id 经 dicts 与 alumni_base.major 文本对齐）。
     */
    private static final String COVERAGE_SQL = """
            SELECT COUNT(*) FROM users su
            JOIN alumni_base ab ON ab.id = su.alumni_base_id
            WHERE su.id = ?
              AND EXISTS (
                SELECT 1 FROM counselor_scopes cs
                JOIN users cu ON cu.id = cs.counselor_id AND cu.status = 'active'
                WHERE ab.college = (SELECT d.label FROM dicts d WHERE d.id = cs.college_id AND d.type = 'college')
                  AND (cs.grad_year = ab.grad_year
                       OR (cs.major_id IS NOT NULL
                           AND cs.major_id = (SELECT m.id FROM dicts m WHERE m.type = 'major' AND m.label = ab.major))
                       OR cs.student_id = ab.student_id))
            """;

    private final HonorRepository honorRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final AttachmentRepository attachmentRepository;
    private final ArchiveRepository archiveRepository;
    private final DictItemRepository dictItemRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    public HonorService(HonorRepository honorRepository,
                        ReviewRepository reviewRepository,
                        ReviewLogRepository reviewLogRepository,
                        AttachmentRepository attachmentRepository,
                        ArchiveRepository archiveRepository,
                        DictItemRepository dictItemRepository,
                        EntityManager entityManager,
                        JdbcTemplate jdbcTemplate) {
        this.honorRepository = honorRepository;
        this.reviewRepository = reviewRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.attachmentRepository = attachmentRepository;
        this.archiveRepository = archiveRepository;
        this.dictItemRepository = dictItemRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 创建荣誉：仅 student/alumni；category 必须命中 honor_category 字典；
     * archiveId 非空时档案必须存在且归属本人；附件逐条校验存在性/归属/未引用。
     * 保存荣誉（含标题拼音列）、引用附件、生成 Review、写系统 ReviewLog 在同一个 @Transactional 中。
     * P1 三级审核：先做导员覆盖检查，有覆盖 → pending_counselor + Review stage=counselor；
     * 无覆盖 → 兜底 pending_first + Review stage=first。
     */
    @Transactional
    public Map<String, Object> create(AuthPrincipal operator, CreateHonorRequest request) {
        requireStudentOrAlumni(operator);
        requireCategoryExists(request.category());
        if (request.archiveId() != null) {
            requireOwnArchive(operator, request.archiveId());
        }
        List<Attachment> attachments = validateAttachments(operator, request.attachmentIds());
        requireNotDuplicate(operator, request);

        boolean counselorCovered = coveredByCounselor(operator.userId());

        Honor honor = new Honor();
        honor.setUserId(operator.userId());
        honor.setArchiveId(request.archiveId());
        honor.setCreatedBy(operator.userId()); // 单人提交：提交人=本人（[M03] 列自洽）
        honor.setTitle(request.title());
        honor.setTitlePinyin(PinyinUtils.toFullPinyin(request.title()));
        honor.setTitlePinyinAbbr(PinyinUtils.toAbbr(request.title()));
        honor.setCategory(request.category());
        honor.setLevel(request.level());
        honor.setAwardedAt(request.awardedAt());
        honor.setRedDocUrl(request.redDocUrl());
        honor.setStatus(counselorCovered ? STATUS_PENDING_COUNSELOR : STATUS_PENDING_FIRST);
        honor = honorRepository.saveAndFlush(honor);

        referenceAttachments(attachments, honor.getId());

        Review review = new Review();
        review.setBizType(BIZ_TYPE_HONOR);
        review.setBizId(honor.getId());
        review.setSubmitterId(operator.userId());
        review.setStage(counselorCovered ? "counselor" : "first");
        review.setStatus("pending");
        review = reviewRepository.save(review);

        ReviewLog log = new ReviewLog();
        log.setReviewId(review.getId());
        log.setOperatorId(null); // NULL=系统动作（自动提交生成）
        log.setAction(ReviewLog.ACTION_CREATE);
        reviewLogRepository.save(log);

        // created_at/updated_at 由数据库默认值生成，回读以返回精确时间
        entityManager.refresh(honor);
        return toData(honor);
    }

    /**
     * 荣誉列表：任意登录用户。分页参数越界 400；status 非法 400；
     * date_from/date_to 解析失败 400；college_id 字典项不存在直接返回空列表。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> list(String keyword, Long collegeId, Integer gradYear, String category,
                                    String status, String dateFrom, String dateTo, String sort,
                                    int page, int pageSize) {
        if (page < 1) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page 必须从 1 开始");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page_size 必须在 1~" + MAX_PAGE_SIZE + " 之间");
        }
        if (status != null && !status.isBlank() && !HONOR_STATUSES.contains(status)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "status 仅支持 pending_counselor / pending_first / pending_second / approved / rejected");
        }
        LocalDate from = parseDate(dateFrom, "date_from");
        LocalDate to = parseDate(dateTo, "date_to");

        // college_id → dicts(type='college').label，字典项不存在时直接返回空列表
        String collegeLabel = null;
        if (collegeId != null) {
            Optional<DictItem> dict = dictItemRepository.findByTypeAndId(DICT_TYPE_COLLEGE, collegeId);
            if (dict.isEmpty()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("list", List.of());
                data.put("total", 0L);
                data.put("page", page);
                data.put("page_size", pageSize);
                return data;
            }
            collegeLabel = dict.get().getLabel();
        }

        Pageable pageable = PageRequest.of(page - 1, pageSize, resolveSort(sort));
        Page<Honor> records = honorRepository.findAll(
                buildSpec(keyword, collegeLabel, gradYear, category, status, from, to), pageable);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", records.getContent().stream().map(this::toData).toList());
        data.put("total", records.getTotalElements());
        data.put("page", page);
        data.put("page_size", pageSize);
        return data;
    }

    /** 荣誉详情：任意登录用户；附带该荣誉已引用的附件 ID 列表 */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(long id) {
        Honor honor = mustFind(id);
        Map<String, Object> data = toData(honor);
        List<Long> attachmentIds = attachmentRepository
                .findByBizTypeAndRefId(Attachment.BIZ_TYPE_HONOR, honor.getId())
                .stream().map(Attachment::getId).toList();
        data.put("attachment_ids", attachmentIds);
        return data;
    }

    /**
     * 修改荣誉：仅本人；status 必须为 pending_counselor / pending_first（审核未处理）；
     * 逐字段更新非 null 字段（category 字典校验、archive 归属校验同创建，标题变更同步重算拼音列）；
     * attachmentIds 非 null 时先释放旧附件再引用新附件，全程同一事务。
     */
    @Transactional
    public Map<String, Object> update(AuthPrincipal operator, long id, UpdateHonorRequest request) {
        Honor honor = mustFind(id);
        if (operator == null || !honor.getUserId().equals(operator.userId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能修改本人荣誉");
        }
        if (!EDITABLE_STATUSES.contains(honor.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE, "审核已开始或已结束，不可修改");
        }

        if (request.title() != null) {
            honor.setTitle(request.title());
            honor.setTitlePinyin(PinyinUtils.toFullPinyin(request.title()));
            honor.setTitlePinyinAbbr(PinyinUtils.toAbbr(request.title()));
        }
        if (request.category() != null) {
            requireCategoryExists(request.category());
            honor.setCategory(request.category());
        }
        if (request.level() != null) {
            honor.setLevel(request.level());
        }
        if (request.awardedAt() != null) {
            honor.setAwardedAt(request.awardedAt());
        }
        if (request.redDocUrl() != null) {
            honor.setRedDocUrl(request.redDocUrl());
        }
        if (request.archiveId() != null) {
            requireOwnArchive(operator, request.archiveId());
            honor.setArchiveId(request.archiveId());
        }
        if (request.attachmentIds() != null) {
            List<Attachment> newAttachments = validateAttachments(operator, request.attachmentIds());
            releaseAttachments(honor.getId());
            referenceAttachments(newAttachments, honor.getId());
        }

        honor = honorRepository.saveAndFlush(honor);
        // updated_at 由数据库 ON UPDATE 生成，回读以返回精确时间
        entityManager.refresh(honor);
        return toData(honor);
    }

    // ------------------------------------------------------------------
    // 内部规则
    // ------------------------------------------------------------------

    /** 仅 student / alumni 可创建荣誉（SecurityConfig 已拦截未登录，此处二次校验角色） */
    private void requireStudentOrAlumni(AuthPrincipal operator) {
        if (operator == null || (!ROLE_STUDENT.equals(operator.role()) && !ROLE_ALUMNI.equals(operator.role()))) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅学生/校友可创建荣誉");
        }
    }

    /** category 必须存在于 honor_category 字典 */
    private void requireCategoryExists(String category) {
        if (!dictItemRepository.existsByTypeAndValue(DICT_TYPE_HONOR_CATEGORY, category)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "荣誉类别不存在");
        }
    }

    /**
     * 业务幂等校验：同用户 + 同标题 + 同获奖日期 的荣誉处于待审/已通过状态时禁止重复创建（409）；
     * 已驳回（rejected）的相同荣誉允许重新提交。
     */
    private void requireNotDuplicate(AuthPrincipal operator, CreateHonorRequest request) {
        boolean exists = honorRepository.existsByUserIdAndTitleAndAwardedAtAndStatusIn(
                operator.userId(), request.title(), request.awardedAt(), IDEMPOTENT_STATUSES);
        if (exists) {
            throw new BizException(ErrorCode.INVALID_STATE, "相同荣誉已提交（待审核或已通过），请勿重复创建");
        }
    }

    /** 档案必须存在且归属当前用户（越权红线） */
    private void requireOwnArchive(AuthPrincipal operator, Long archiveId) {
        Archive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "关联档案不存在"));
        if (!archive.getUserId().equals(operator.userId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能关联本人档案");
        }
    }

    /** 逐条校验附件：存在、本人上传、状态为 unreferenced */
    private List<Attachment> validateAttachments(AuthPrincipal operator, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        List<Attachment> attachments = new ArrayList<>();
        for (Long attachmentId : attachmentIds) {
            Attachment attachment = attachmentRepository.findById(attachmentId)
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "附件不存在: " + attachmentId));
            if (!attachment.getUploaderId().equals(operator.userId())) {
                throw new BizException(ErrorCode.FORBIDDEN, "只能引用本人上传的附件");
            }
            if (!Attachment.STATUS_UNREFERENCED.equals(attachment.getStatus())) {
                throw new BizException(ErrorCode.INVALID_STATE, "附件已被引用: " + attachmentId);
            }
            attachments.add(attachment);
        }
        return attachments;
    }

    /** 引用附件：置 referenced 并回填业务关联 */
    private void referenceAttachments(List<Attachment> attachments, Long honorId) {
        for (Attachment attachment : attachments) {
            attachment.setStatus(Attachment.STATUS_REFERENCED);
            attachment.setBizType(Attachment.BIZ_TYPE_HONOR);
            attachment.setRefId(honorId);
            attachment.setReferencedAt(Instant.now());
            attachmentRepository.save(attachment);
        }
    }

    /** 释放该荣誉全部旧附件：置回 unreferenced（bizType 保留 'honor'） */
    private void releaseAttachments(Long honorId) {
        List<Attachment> oldAttachments =
                attachmentRepository.findByBizTypeAndRefId(Attachment.BIZ_TYPE_HONOR, honorId);
        for (Attachment old : oldAttachments) {
            old.setStatus(Attachment.STATUS_UNREFERENCED);
            old.setRefId(null);
            old.setReferencedAt(null);
            attachmentRepository.save(old);
        }
    }

    /** 无导员覆盖兜底：提交人未命中任何 active 导员管辖范围时返回 false，审核流自动跳过 counselor 环节 */
    private boolean coveredByCounselor(Long userId) {
        Integer cnt = jdbcTemplate.queryForObject(COVERAGE_SQL, Integer.class, userId);
        return cnt != null && cnt > 0;
    }

    /** 查询荣誉，id 非正整数或不存在均抛 404 */
    private Honor mustFind(long id) {
        if (id <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "荣誉不存在");
        }
        return honorRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "荣誉不存在"));
    }

    /** 解析 YYYY-MM-DD 日期筛选参数，失败抛 400 */
    private LocalDate parseDate(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, name + " 格式必须为 YYYY-MM-DD");
        }
    }

    /** 解析排序参数：'-' 前缀降序；仅允许 created_at/awarded_at/title，非法字段回退默认 -created_at */
    private Sort resolveSort(String sort) {
        if (sort != null && !sort.isBlank()) {
            String s = sort.trim();
            Sort.Direction direction = Sort.Direction.ASC;
            if (s.startsWith("-")) {
                direction = Sort.Direction.DESC;
                s = s.substring(1);
            }
            String field = SORTABLE_FIELDS.get(s);
            if (field != null) {
                return Sort.by(direction, field);
            }
        }
        return Sort.by(Sort.Direction.DESC, DEFAULT_SORT_FIELD);
    }

    /** 组合列表筛选条件：keyword/college/grad_year 走 user → alumniBase 关联，其余为 honors 本表条件 */
    private Specification<Honor> buildSpec(String keyword, String collegeLabel, Integer gradYear,
                                           String category, String status,
                                           LocalDate dateFrom, LocalDate dateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            boolean needJoin = (keyword != null && !keyword.isBlank())
                    || collegeLabel != null || gradYear != null;
            if (needJoin) {
                Join<Honor, User> user = root.join("user");
                Join<User, AlumniBase> base = user.join("alumniBase");
                if (keyword != null && !keyword.isBlank()) {
                    // 拼音容错（规范 3.3）：学号/姓名/姓名拼音列 + 标题拼音列（拼音列 NULL 兼容，LIKE 天然不匹配 NULL）
                    String like = "%" + keyword.trim() + "%";
                    predicates.add(cb.or(
                            cb.like(user.get("studentId"), like),
                            cb.like(base.get("realName"), like),
                            cb.like(base.get("realNamePinyin"), like),
                            cb.like(base.get("realNamePinyinAbbr"), like),
                            cb.like(root.get("titlePinyin"), like),
                            cb.like(root.get("titlePinyinAbbr"), like)));
                }
                if (collegeLabel != null) {
                    predicates.add(cb.equal(base.get("college"), collegeLabel));
                }
                if (gradYear != null) {
                    predicates.add(cb.equal(base.get("gradYear"), gradYear));
                }
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("awardedAt"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("awardedAt"), dateTo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 实体 → 出参 Map（snake_case 键，created_at/updated_at 为 Instant，由 Jackson 输出 ISO 8601 UTC） */
    private Map<String, Object> toData(Honor honor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", honor.getId());
        map.put("user_id", honor.getUserId());
        map.put("archive_id", honor.getArchiveId());
        map.put("team_id", honor.getTeamId());
        map.put("created_by", honor.getCreatedBy());
        map.put("red_doc_url", honor.getRedDocUrl());
        map.put("title", honor.getTitle());
        map.put("category", honor.getCategory());
        map.put("level", honor.getLevel());
        map.put("awarded_at", honor.getAwardedAt());
        map.put("status", honor.getStatus());
        map.put("created_at", honor.getCreatedAt());
        map.put("updated_at", honor.getUpdatedAt());
        return map;
    }
}
