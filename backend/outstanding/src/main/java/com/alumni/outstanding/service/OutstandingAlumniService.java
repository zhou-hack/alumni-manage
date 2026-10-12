package com.alumni.outstanding.service;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.outstanding.common.BizException;
import com.alumni.outstanding.common.ErrorCode;
import com.alumni.outstanding.dto.OutstandingDtos.AdminItem;
import com.alumni.outstanding.dto.OutstandingDtos.CreateRequest;
import com.alumni.outstanding.dto.OutstandingDtos.OutstandingItem;
import com.alumni.outstanding.dto.OutstandingDtos.OutstandingListResult;
import com.alumni.outstanding.dto.OutstandingDtos.PageResult;
import com.alumni.outstanding.dto.OutstandingDtos.UpdateRequest;
import com.alumni.outstanding.entity.DictItem;
import com.alumni.outstanding.entity.OutstandingAlumni;
import com.alumni.outstanding.repository.DictItemRepository;
import com.alumni.outstanding.repository.DictItemRepository.AlumnusInfoProjection;
import com.alumni.outstanding.repository.OutstandingAlumniRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 优秀校友业务实现（Service 层，扩展模块 [M14]）。
 *
 * <p>职责：角色校验（school_admin 维护精选位）、参数二次校验、
 * 生命周期编排（draft → published ↔ withdrawn）、缺省字段从学籍自动补全；
 * 不含 SQL 与 HTTP 细节，全部通过 Repository 取数。</p>
 */
@Service
public class OutstandingAlumniService {

    /** 精选位维护限定角色（开发文档 4.2：管理端点 school_admin） */
    private static final Set<String> MANAGER_ROLES = Set.of("school_admin", "system_admin");
    /** 可入选精选位的用户身份（已激活校友侧） */
    private static final Set<String> ALUMNI_ROLES = Set.of("student", "alumni");

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_WITHDRAWN = "withdrawn";
    private static final String DICT_TYPE_COLLEGE = "college";

    /** 公开列表 limit 上限（与全平台分页 page_size 上限一致） */
    public static final int MAX_LIMIT = 100;

    private final OutstandingAlumniRepository outstandingRepository;
    private final DictItemRepository dictItemRepository;

    public OutstandingAlumniService(OutstandingAlumniRepository outstandingRepository,
                                    DictItemRepository dictItemRepository) {
        this.outstandingRepository = outstandingRepository;
        this.dictItemRepository = dictItemRepository;
    }

    /* ==================== 公开端点 ==================== */

    /**
     * 公开列表（首页环形展厅）：仅 published，按 display_order 升序。
     *
     * @param limit null=全部；有值时须在 1~100
     */
    @Transactional(readOnly = true)
    public OutstandingListResult outstanding(Integer limit) {
        List<OutstandingAlumni> rows = outstandingRepository
                .findByStatusOrderByDisplayOrderAscIdAsc(STATUS_PUBLISHED);
        if (limit != null) {
            if (limit < 1 || limit > MAX_LIMIT) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "limit 必须在 1~" + MAX_LIMIT + " 之间");
            }
            rows = rows.size() > limit ? rows.subList(0, limit) : rows;
        }
        Map<Long, String> collegeLabels = collegeLabelMap();
        List<OutstandingItem> list = new ArrayList<>(rows.size());
        for (OutstandingAlumni row : rows) {
            list.add(new OutstandingItem(row.getStudentId(), row.getDisplayName(), row.getPhotoUrl(),
                    row.getMessage(), collegeLabels.get(row.getCollegeId()), row.getGradYear(),
                    joinCurrent(row.getCurrentTitle(), row.getCurrentCity()),
                    row.getTimeline(), row.getAwards(), Boolean.TRUE.equals(row.getIsFeatured())));
        }
        return new OutstandingListResult(list, list.size());
    }

    /* ==================== 管理端点（school_admin） ==================== */

    /** 管理列表：全状态，支持 status 筛选与分页 */
    @Transactional(readOnly = true)
    public PageResult page(AuthPrincipal operator, String status, int page, int pageSize) {
        requireManager(operator);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        String effectiveStatus = normalizeStatus(status);

        Page<OutstandingAlumni> result = effectiveStatus == null
                ? outstandingRepository.findAllByOrderByDisplayOrderAscIdAsc(PageRequest.of(safePage - 1, safeSize))
                : outstandingRepository.findByStatusOrderByDisplayOrderAscIdAsc(effectiveStatus,
                        PageRequest.of(safePage - 1, safeSize));

        Map<Long, String> collegeLabels = collegeLabelMap();
        List<AdminItem> list = result.map(row -> toAdminItem(row, collegeLabels)).getContent();
        return new PageResult(list, result.getTotalElements(), safePage, safeSize);
    }

    /**
     * 新增精选位（school_admin）。
     * 校验：user_id 必须存在且为校友身份；重复添加 409 INVALID_STATE；
     * display_name/grad_year/college_id 缺省时从学籍自动补全。
     */
    @Transactional
    public AdminItem create(AuthPrincipal operator, CreateRequest req) {
        requireManager(operator);

        AlumnusInfoProjection alumnus = dictItemRepository.findAlumnusInfo(req.userId());
        if (alumnus == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (!ALUMNI_ROLES.contains(alumnus.getRole())) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "仅支持校友身份的用户加入精选位");
        }
        if (outstandingRepository.existsByUserId(req.userId())) {
            throw new BizException(ErrorCode.INVALID_STATE, "该校友已在精选位，请直接编辑");
        }

        OutstandingAlumni row = new OutstandingAlumni();
        row.setUserId(alumnus.getUserId());
        row.setStudentId(alumnus.getStudentId());
        row.setDisplayName(req.displayName() != null ? req.displayName() : alumnus.getRealName());
        row.setPhotoUrl(req.photoUrl());
        row.setMessage(req.message());
        row.setCollegeId(resolveCollegeId(req.collegeId(), alumnus.getCollege()));
        Integer gradYear = req.gradYear() != null ? req.gradYear() : alumnus.getGradYear();
        requireValidGradYear(gradYear);
        row.setGradYear(gradYear);
        row.setCurrentTitle(req.currentTitle());
        row.setCurrentCity(req.currentCity());
        row.setTimeline(req.timeline());
        row.setAwards(req.awards());
        row.setDisplayOrder(req.displayOrder() != null ? req.displayOrder() : 0);
        row.setIsFeatured(Boolean.TRUE.equals(req.isFeatured()));
        row.setStatus(req.status() != null ? req.status() : STATUS_DRAFT);

        row = outstandingRepository.save(row);
        return toAdminItem(row, collegeLabelMap());
    }

    /** 修改（school_admin）：字段给了才改；status 变更即发布/撤下 */
    @Transactional
    public AdminItem update(AuthPrincipal operator, long id, UpdateRequest req) {
        requireManager(operator);
        requirePositiveId(id);
        OutstandingAlumni row = outstandingRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "精选位不存在"));

        if (req.displayName() != null) {
            row.setDisplayName(req.displayName());
        }
        if (req.photoUrl() != null) {
            row.setPhotoUrl(req.photoUrl());
        }
        if (req.message() != null) {
            row.setMessage(req.message());
        }
        if (req.collegeId() != null) {
            row.setCollegeId(requireCollegeDict(req.collegeId()));
        }
        if (req.gradYear() != null) {
            requireValidGradYear(req.gradYear());
            row.setGradYear(req.gradYear());
        }
        if (req.currentTitle() != null) {
            row.setCurrentTitle(req.currentTitle());
        }
        if (req.currentCity() != null) {
            row.setCurrentCity(req.currentCity());
        }
        if (req.timeline() != null) {
            row.setTimeline(req.timeline());
        }
        if (req.awards() != null) {
            row.setAwards(req.awards());
        }
        if (req.displayOrder() != null) {
            row.setDisplayOrder(req.displayOrder());
        }
        if (req.isFeatured() != null) {
            row.setIsFeatured(Boolean.TRUE.equals(req.isFeatured()));
        }
        if (req.status() != null) {
            row.setStatus(req.status());
        }

        row = outstandingRepository.save(row);
        return toAdminItem(row, collegeLabelMap());
    }

    /** 删除精选位（school_admin，物理删除） */
    @Transactional
    public void delete(AuthPrincipal operator, long id) {
        requireManager(operator);
        requirePositiveId(id);
        if (!outstandingRepository.existsById(id)) {
            throw new BizException(ErrorCode.NOT_FOUND, "精选位不存在");
        }
        outstandingRepository.deleteById(id);
    }

    /* ---------------- 私有工具 ---------------- */

    private void requireManager(AuthPrincipal operator) {
        if (operator == null || !MANAGER_ROLES.contains(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅 school_admin 可维护优秀校友精选位");
        }
    }

    private void requirePositiveId(long id) {
        if (id <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "id 必须为正整数");
        }
    }

    /** null/空串 → null（全部）；否则校验取值，非法抛 400 */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status) {
            case STATUS_DRAFT, STATUS_PUBLISHED, STATUS_WITHDRAWN -> status;
            default -> throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "status 仅允许 draft/published/withdrawn");
        };
    }

    /** 毕业年份须在 2000 ~ 当前年+4（对齐 dicts grad_year 虚拟口径） */
    private void requireValidGradYear(Integer gradYear) {
        if (gradYear == null) {
            return;
        }
        int max = Year.now().getValue() + 4;
        if (gradYear < 2000 || gradYear > max) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "grad_year 须在 2000~" + max + " 之间");
        }
    }

    /** college_id 解析：显式给值则校验字典；未给则从学籍 college 文本反查（可反查不到，存 null） */
    private Long resolveCollegeId(Long collegeId, String collegeText) {
        if (collegeId != null) {
            return requireCollegeDict(collegeId);
        }
        if (collegeText != null && !collegeText.isBlank()) {
            return dictItemRepository.findByTypeAndLabel(DICT_TYPE_COLLEGE, collegeText)
                    .map(DictItem::getId)
                    .orElse(null);
        }
        return null;
    }

    private Long requireCollegeDict(Long collegeId) {
        requirePositiveId(collegeId);
        if (!dictItemRepository.existsByIdAndType(collegeId, DICT_TYPE_COLLEGE)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "college_id 不是有效的学院字典项");
        }
        return collegeId;
    }

    /** current 拼装："职位 / 城市"（服务端拼好，与前端展示格式对齐；双空返回 null） */
    private String joinCurrent(String title, String city) {
        if (title != null && city != null) {
            return title + " / " + city;
        }
        return title != null ? title : city;
    }

    /** college 字典 id → label 映射（字典量小，一次取全量内存组装） */
    private Map<Long, String> collegeLabelMap() {
        Map<Long, String> map = new HashMap<>();
        for (DictItem dict : dictItemRepository.findAllByTypeOrderByIdAsc(DICT_TYPE_COLLEGE)) {
            map.put(dict.getId(), dict.getLabel());
        }
        return map;
    }

    private AdminItem toAdminItem(OutstandingAlumni row, Map<Long, String> collegeLabels) {
        return new AdminItem(row.getId(), row.getUserId(), row.getStudentId(), row.getDisplayName(),
                row.getPhotoUrl(), row.getMessage(), row.getCollegeId(),
                collegeLabels.get(row.getCollegeId()), row.getGradYear(),
                row.getCurrentTitle(), row.getCurrentCity(), row.getTimeline(), row.getAwards(),
                row.getDisplayOrder(), Boolean.TRUE.equals(row.getIsFeatured()), row.getStatus(),
                row.getCreatedAt(), row.getUpdatedAt());
    }
}
