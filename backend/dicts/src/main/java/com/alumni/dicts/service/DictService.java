package com.alumni.dicts.service;

import com.alumni.dicts.common.BizException;
import com.alumni.dicts.common.ErrorCode;
import com.alumni.dicts.dto.DictDtos.CreateDictRequest;
import com.alumni.dicts.dto.DictDtos.DictItemVO;
import com.alumni.dicts.dto.DictDtos.UpdateDictRequest;
import com.alumni.dicts.entity.DictItem;
import com.alumni.dicts.repository.DictItemRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 基础字典业务：按类型查询 + school_admin 增删改。
 * 层级规则：major 必须归属 college（parent_id）；删除父级前必须先处理子项。
 * 删除/改值前做跨表引用检查（被引用 409 RESOURCE_REFERENCED）。
 */
@Service
public class DictService {

    /** type 命名规则：小写字母开头的 snake_case */
    private static final Pattern TYPE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,31}$");

    /** 虚拟类型：grad_year 不落库，按当前年动态生成（年份是可枚举数据，不是业务主数据） */
    private static final String VIRTUAL_GRAD_YEAR = "grad_year";

    private final DictItemRepository dictItemRepository;
    private final JdbcTemplate jdbcTemplate;
    private final int gradYearFromOffset;
    private final int gradYearToOffset;

    public DictService(DictItemRepository dictItemRepository,
                       JdbcTemplate jdbcTemplate,
                       @org.springframework.beans.factory.annotation.Value("${app.dicts.grad-year-from-offset:-20}") int gradYearFromOffset,
                       @org.springframework.beans.factory.annotation.Value("${app.dicts.grad-year-to-offset:4}") int gradYearToOffset) {
        this.dictItemRepository = dictItemRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.gradYearFromOffset = gradYearFromOffset;
        this.gradYearToOffset = gradYearToOffset;
    }

    /** GET /dicts/{type}?parent_id=：按类型取字典项（grad_year 动态生成；其余可选按父级过滤） */
    public List<DictItemVO> list(String type, Long parentId) {
        checkType(type);
        if (VIRTUAL_GRAD_YEAR.equals(type)) {
            int currentYear = java.time.Year.now().getValue();
            return java.util.stream.IntStream.rangeClosed(currentYear + gradYearFromOffset, currentYear + gradYearToOffset)
                    .mapToObj(y -> new DictItemVO(null, String.valueOf(y), String.valueOf(y), y - (currentYear + gradYearFromOffset) + 1, null))
                    .toList();
        }
        if (!dictItemRepository.existsByType(type)) {
            throw new BizException(ErrorCode.NOT_FOUND, "字典类型不存在：" + type);
        }
        List<DictItem> items = parentId != null
                ? dictItemRepository.findByTypeAndParentIdOrderBySortAscIdAsc(type, parentId)
                : dictItemRepository.findByTypeOrderBySortAscIdAsc(type);
        return items.stream().map(this::toVO).toList();
    }

    /** POST /dicts/{type}：新增字典项（同类型下 value 唯一；major 必须归属 college；grad_year 系统维护禁改） */
    @Transactional
    public DictItemVO create(String type, CreateDictRequest req) {
        checkType(type);
        rejectVirtual(type);
        if (dictItemRepository.existsByTypeAndValue(type, req.value())) {
            throw new BizException(ErrorCode.INVALID_STATE, "该字典值已存在：" + req.value());
        }
        DictItem item = new DictItem();
        item.setType(type);
        item.setParentId(resolveParent(type, req.parentId(), true));
        item.setLabel(req.label());
        item.setValue(req.value());
        item.setSort(req.sort() != null ? req.sort() : 0);
        return toVO(dictItemRepository.save(item));
    }

    /** PATCH /dicts/{type}/{id}：修改（被引用项禁止改 value，等同于删旧值） */
    @Transactional
    public DictItemVO update(String type, Long id, UpdateDictRequest req) {
        checkType(type);
        rejectVirtual(type);
        DictItem item = findAndCheckType(type, id);
        if (req.value() != null && !req.value().equals(item.getValue())) {
            if (isReferenced(item)) {
                throw new BizException(ErrorCode.RESOURCE_REFERENCED, "该字典项已被引用，禁止修改 value");
            }
            if (dictItemRepository.existsByTypeAndValueAndIdNot(type, req.value(), id)) {
                throw new BizException(ErrorCode.INVALID_STATE, "该字典值已存在：" + req.value());
            }
            item.setValue(req.value());
        }
        if (req.parentId() != null) {
            item.setParentId(resolveParent(type, req.parentId(), false));
        }
        if (req.label() != null) {
            item.setLabel(req.label());
        }
        if (req.sort() != null) {
            item.setSort(req.sort());
        }
        return toVO(dictItemRepository.save(item));
    }

    /** DELETE /dicts/{type}/{id}：删除（存在子项或被业务引用均禁止） */
    @Transactional
    public void delete(String type, Long id) {
        checkType(type);
        rejectVirtual(type);
        DictItem item = findAndCheckType(type, id);
        if (dictItemRepository.countByParentId(id) > 0) {
            throw new BizException(ErrorCode.RESOURCE_REFERENCED, "该字典项下存在子级，请先移除其子项");
        }
        if (isReferenced(item)) {
            throw new BizException(ErrorCode.RESOURCE_REFERENCED, "该字典项已被引用，禁止删除");
        }
        dictItemRepository.delete(item);
    }

    // ---------- 内部 ----------

    private DictItem findAndCheckType(String type, Long id) {
        DictItem item = dictItemRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "字典项不存在"));
        if (!item.getType().equals(type)) {
            throw new BizException(ErrorCode.NOT_FOUND, "字典项不存在");
        }
        return item;
    }

    /**
     * 父级校验：
     * - type=major：必须归属 college（required 时不允许为空）
     * - 其余类型：parent_id 可空，给了则必须存在且不得自引用类型
     */
    private Long resolveParent(String type, Long parentId, boolean create) {
        if (parentId == null) {
            if ("major".equals(type) && create) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "major 必须指定 parent_id（归属学院）");
            }
            return null;
        }
        DictItem parent = dictItemRepository.findById(parentId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "父级字典项不存在"));
        if ("major".equals(type) && !"college".equals(parent.getType())) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "major 的父级必须是 college 类型");
        }
        return parentId;
    }

    /**
     * 跨表引用检查（单库 alumni_platform 内直接 COUNT）：
     * college/major → alumni_base 文本对齐；honor_category → honors.category；
     * 其余类型（含 grad_year 虚拟类型）无落库项可删，不涉及。
     */
    private boolean isReferenced(DictItem item) {
        return switch (item.getType()) {
            case "college" -> count("SELECT COUNT(*) FROM alumni_base WHERE college = ?", item.getLabel()) > 0;
            case "major" -> count("SELECT COUNT(*) FROM alumni_base WHERE major = ?", item.getLabel()) > 0;
            case "honor_category" -> count("SELECT COUNT(*) FROM honors WHERE category = ?", item.getValue()) > 0;
            default -> false;
        };
    }

    private long count(String sql, Object param) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, param);
        return result != null ? result : 0;
    }

    private void checkType(String type) {
        if (type == null || !TYPE_PATTERN.matcher(type).matches()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "字典类型格式错误（小写 snake_case）");
        }
    }

    /** 虚拟类型拦截：grad_year 由系统按当前年动态生成，不开放增删改 */
    private void rejectVirtual(String type) {
        if (VIRTUAL_GRAD_YEAR.equals(type)) {
            throw new BizException(ErrorCode.INVALID_STATE, "grad_year 为系统维护类型（按当前年动态生成），无需增删改");
        }
    }

    private DictItemVO toVO(DictItem item) {
        return new DictItemVO(item.getId(), item.getLabel(), item.getValue(), item.getSort(), item.getParentId());
    }
}
