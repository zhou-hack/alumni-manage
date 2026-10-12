package com.alumni.tags.service;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.tags.common.BizException;
import com.alumni.tags.common.ErrorCode;
import com.alumni.tags.dto.TagDtos.PageResult;
import com.alumni.tags.dto.TagDtos.TagItem;
import com.alumni.tags.entity.Tag;
import com.alumni.tags.entity.TagBinding;
import com.alumni.tags.repository.TagBindingRepository;
import com.alumni.tags.repository.TagRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 标签体系业务实现（Service 层，P1 [M13]）。
 *
 * <p>职责：角色校验、域隔离规则（同域重名 409 TAG_DUPLICATED / 跨域绑定 400）、
 * 绑定编排（全量覆盖式，同事务删旧插新）、引用保护（被引用删除 409 RESOURCE_REFERENCED）、
 * 审计写入（audit_logs action='tag_change'，JdbcTemplate 直写共享库）。</p>
 */
@Service
public class TagService {

    /** 标签域 / 业务类型取值 */
    public static final Set<String> GROUPS = Set.of("honor", "archive", "alumni");
    /** 标签写操作（增删改）限定角色 */
    private static final Set<String> TAG_MANAGER_ROLES = Set.of("school_admin", "system_admin");
    /** 标签绑定（PUT）限定角色 */
    private static final Set<String> BIND_ROLES = Set.of("college_admin", "school_admin", "system_admin");
    /** 单对象绑定上限（文档 3.2：单次上限 20 个） */
    public static final int MAX_BINDINGS = 20;

    private final TagRepository tagRepository;
    private final TagBindingRepository tagBindingRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TagService(TagRepository tagRepository, TagBindingRepository tagBindingRepository,
                      JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.tagRepository = tagRepository;
        this.tagBindingRepository = tagBindingRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /* ==================== 标签 CRUD ==================== */

    /** GET /tags：列表（登录；group 可选过滤、keyword 模糊 name，按 sort,id 升序分页） */
    @Transactional(readOnly = true)
    public PageResult page(AuthPrincipal viewer, String group, String keyword, int page, int pageSize) {
        requireLogin(viewer);
        String normalizedGroup = normalizeGroup(group);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        PageRequest pageable = PageRequest.of(safePage - 1, safeSize);

        Page<Tag> result;
        if (normalizedGroup != null && kw != null) {
            result = tagRepository.findByTagGroupAndNameContainingOrderBySortAscIdAsc(normalizedGroup, kw, pageable);
        } else if (normalizedGroup != null) {
            result = tagRepository.findByTagGroupOrderBySortAscIdAsc(normalizedGroup, pageable);
        } else if (kw != null) {
            result = tagRepository.findByNameContainingOrderBySortAscIdAsc(kw, pageable);
        } else {
            result = tagRepository.findAllByOrderBySortAscIdAsc(pageable);
        }
        return new PageResult(result.map(this::toItem).getContent(),
                result.getTotalElements(), safePage, safeSize);
    }

    /** POST /tags：创建（school_admin；同域重名 409 TAG_DUPLICATED，uk 冲突兜底同码） */
    @Transactional
    public TagItem create(AuthPrincipal operator, String name, String group, Integer sort) {
        requireSchoolAdmin(operator);
        requireGroup(group);
        String trimmedName = requireName(name);
        if (tagRepository.existsByTagGroupAndName(group, trimmedName)) {
            throw new BizException(ErrorCode.TAG_DUPLICATED, "同域标签重名：" + trimmedName);
        }
        Tag tag = new Tag();
        tag.setName(trimmedName);
        tag.setTagGroup(group);
        tag.setSort(sort != null ? sort : 0);
        tag.setCreatedBy(operator.userId());
        try {
            tag = tagRepository.saveAndFlush(tag);
        } catch (DataIntegrityViolationException e) {
            // 并发下同域重名：uk_tags_group_name 冲突兜底 409
            throw new BizException(ErrorCode.TAG_DUPLICATED, "同域标签重名：" + trimmedName);
        }
        audit(operator, "create", tag);
        return toItem(tag);
    }

    /** PATCH /tags/{id}：改名 / 调排序（school_admin；同域重名 409） */
    @Transactional
    public TagItem update(AuthPrincipal operator, long id, String name, Integer sort) {
        requireSchoolAdmin(operator);
        requirePositiveId(id);
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "标签不存在"));
        if (name != null && !name.isBlank() && !name.trim().equals(tag.getName())) {
            String newName = name.trim();
            if (tagRepository.existsByTagGroupAndNameAndIdNot(tag.getTagGroup(), newName, id)) {
                throw new BizException(ErrorCode.TAG_DUPLICATED, "同域标签重名：" + newName);
            }
            tag.setName(newName);
        }
        if (sort != null) {
            tag.setSort(sort);
        }
        try {
            tag = tagRepository.saveAndFlush(tag);
        } catch (DataIntegrityViolationException e) {
            throw new BizException(ErrorCode.TAG_DUPLICATED, "同域标签重名");
        }
        audit(operator, "update", tag);
        return toItem(tag);
    }

    /** DELETE /tags/{id}：删除（school_admin；被 tag_bindings 引用 409 RESOURCE_REFERENCED） */
    @Transactional
    public void delete(AuthPrincipal operator, long id) {
        requireSchoolAdmin(operator);
        requirePositiveId(id);
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "标签不存在"));
        if (tagBindingRepository.countByTagId(id) > 0) {
            throw new BizException(ErrorCode.RESOURCE_REFERENCED, "标签已被绑定引用，禁止删除");
        }
        tagRepository.delete(tag);
        audit(operator, "delete", tag);
    }

    /* ==================== 标签绑定 ==================== */

    /**
     * PUT /tags/bindings/{biz_type}/{biz_id}：全量覆盖式绑定（college_admin/school_admin）。
     * 同事务内删旧插新（幂等）；tag_ids ≤20；标签须存在且域 == biz_type；业务对象须存在。
     */
    @Transactional
    public List<TagItem> bind(AuthPrincipal operator, String bizType, long bizId, List<Long> tagIds) {
        if (operator == null || !BIND_ROLES.contains(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅院系 / 校级管理员可设置标签绑定");
        }
        requireGroup(bizType);
        requirePositiveId(bizId);
        if (tagIds != null && tagIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "tag_ids 含空值");
        }
        List<Long> distinctIds = tagIds == null ? List.of()
                : List.copyOf(new LinkedHashSet<>(tagIds));
        if (distinctIds.size() > MAX_BINDINGS) {
            throw new BizException(ErrorCode.BATCH_LIMIT_EXCEEDED,
                    "单对象绑定标签数超限（最多 " + MAX_BINDINGS + " 个）");
        }

        List<Tag> tags = distinctIds.isEmpty() ? List.of() : tagRepository.findByIdIn(distinctIds);
        if (tags.size() != distinctIds.size()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "存在不合法的标签 id（标签不存在）");
        }
        for (Tag tag : tags) {
            if (!bizType.equals(tag.getTagGroup())) {
                throw new BizException(ErrorCode.INVALID_PARAMETER,
                        "标签域与业务类型不匹配（禁止跨域绑定）：" + tag.getName());
            }
        }
        if (!bizExists(bizType, bizId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "业务对象不存在：" + bizType + "/" + bizId);
        }

        tagBindingRepository.deleteByBizTypeAndBizId(bizType, bizId);
        List<TagBinding> bindings = distinctIds.stream().map(tagId -> {
            TagBinding binding = new TagBinding();
            binding.setTagId(tagId);
            binding.setBizType(bizType);
            binding.setBizId(bizId);
            return binding;
        }).toList();
        tagBindingRepository.saveAll(bindings);
        return tags.stream().sorted(TAG_ORDER).map(this::toItem).toList();
    }

    /** GET /tags/bindings/{biz_type}/{biz_id}：查询已绑标签（登录） */
    @Transactional(readOnly = true)
    public List<TagItem> bindings(AuthPrincipal viewer, String bizType, long bizId) {
        requireLogin(viewer);
        requireGroup(bizType);
        requirePositiveId(bizId);
        List<TagBinding> bindings = tagBindingRepository.findByBizTypeAndBizId(bizType, bizId);
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<Long> tagIds = bindings.stream().map(TagBinding::getTagId).toList();
        return tagRepository.findByIdIn(tagIds).stream().sorted(TAG_ORDER).map(this::toItem).toList();
    }

    /* ---------------- 私有工具 ---------------- */

    private static final java.util.Comparator<Tag> TAG_ORDER =
            java.util.Comparator.comparing(Tag::getSort,
                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                    .thenComparing(Tag::getId);

    /** 跨模块存在性检查（共享库直读，不依赖其他业务模块）：honor→honors / archive→archives / alumni→users */
    private boolean bizExists(String bizType, long bizId) {
        String table = switch (bizType) {
            case "honor" -> "honors";
            case "archive" -> "archives";
            default -> "users";
        };
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE id = ?", Long.class, bizId);
        return count != null && count > 0;
    }

    /** 审计：audit_logs action='tag_change'，detail 含操作类型 create/update/delete 与标签名 */
    private void audit(AuthPrincipal operator, String op, Tag tag) {
        String detail;
        try {
            detail = objectMapper.writeValueAsString(Map.of("op", op, "name", tag.getName()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("审计 detail 序列化失败", e);
        }
        jdbcTemplate.update(
                "INSERT INTO audit_logs (user_id, user_role, action, biz_type, biz_id, detail)"
                        + " VALUES (?, ?, 'tag_change', 'tag', ?, ?)",
                operator.userId(), operator.role(), tag.getId(), detail);
    }

    private void requireLogin(AuthPrincipal viewer) {
        if (viewer == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或 Token 已过期");
        }
    }

    private void requireSchoolAdmin(AuthPrincipal operator) {
        if (operator == null || !TAG_MANAGER_ROLES.contains(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅校级管理员可管理标签");
        }
    }

    /** null/空串 → null（不过滤）；否则校验取值 ∈ honor/archive/alumni */
    private String normalizeGroup(String group) {
        if (group == null || group.isBlank()) {
            return null;
        }
        requireGroup(group.trim());
        return group.trim();
    }

    private void requireGroup(String group) {
        if (group == null || !GROUPS.contains(group)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "group/biz_type 仅允许 honor/archive/alumni");
        }
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "name 不能为空");
        }
        return name.trim();
    }

    private void requirePositiveId(long id) {
        if (id <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "id 必须为正整数");
        }
    }

    private TagItem toItem(Tag tag) {
        return new TagItem(tag.getId(), tag.getName(), tag.getTagGroup(), tag.getSort(),
                tag.getCreatedBy(), tag.getCreatedAt(), tag.getUpdatedAt());
    }
}
