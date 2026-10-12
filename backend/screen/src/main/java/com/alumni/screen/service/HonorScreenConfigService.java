package com.alumni.screen.service;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.screen.common.BizException;
import com.alumni.screen.common.ErrorCode;
import com.alumni.screen.dto.ScreenDtos;
import com.alumni.screen.dto.ScreenDtos.ConfigItem;
import com.alumni.screen.dto.ScreenDtos.PageResult;
import com.alumni.screen.dto.ScreenDtos.UpsertRequest;
import com.alumni.screen.entity.HonorScreenConfig;
import com.alumni.screen.repository.HonorScreenConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 荣誉大屏配置 Service（[M03 大屏管理]）：
 *
 * <p>职责：
 * <ul>
 *   <li>配置 CRUD（list / detail / upsert / delete）</li>
 *   <li>show_fields JSON ↔ Map 互转</li>
 *   <li>状态切换：创建新 active 时，旧 active 自动 archive（同一时刻最多一条 active）</li>
 *   <li>权限：仅 school_admin / system_admin（URL 级 SecurityConfig + Service 二校验）</li>
 * </ul>
 */
@Service
public class HonorScreenConfigService {

    private final HonorScreenConfigRepository repository;
    private final ObjectMapper objectMapper;

    public HonorScreenConfigService(HonorScreenConfigRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /* ==================== 查询 ==================== */

    /** 当前生效配置（status='active'）。不存在则自动初始化默认。 */
    @Transactional
    public ConfigItem getActive() {
        return repository.findFirstByStatusOrderByUpdatedAtDesc("active")
                .map(this::toItem)
                .orElseGet(() -> {
                    // 极端情况：DB 被清空，初始化默认
                    HonorScreenConfig init = new HonorScreenConfig();
                    init.setName("default");
                    init.setStatus("active");
                    init.setShowFields(ScreenDtos.DEFAULT_SHOW_FIELDS_JSON);
                    return toItem(repository.save(init));
                });
    }

    /** 详情 */
    @Transactional(readOnly = true)
    public ConfigItem detail(long id) {
        requirePositiveId(id);
        return repository.findById(id)
                .map(this::toItem)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "大屏配置不存在"));
    }

    /** 分页列表（按 status 筛选；status 为空则全量） */
    @Transactional(readOnly = true)
    public PageResult<ConfigItem> list(String status, int page, int pageSize) {
        if (pageSize < 1 || pageSize > 100) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page_size 须在 1~100 之间");
        }
        int safePage = Math.max(page, 1);
        Page<HonorScreenConfig> result = (status == null || status.isBlank())
                ? repository.findAll(PageRequest.of(safePage - 1, pageSize, Sort.by(Sort.Direction.DESC, "updatedAt")))
                : repository.findByStatus(status.trim(),
                        PageRequest.of(safePage - 1, pageSize, Sort.by(Sort.Direction.DESC, "updatedAt")));
        List<ConfigItem> list = result.getContent().stream().map(this::toItem).toList();
        return new PageResult<>(list, result.getTotalElements(), safePage, pageSize);
    }

    /* ==================== 写操作 ==================== */

    /** 创建配置 */
    @Transactional
    public ConfigItem create(AuthPrincipal operator, UpsertRequest req) {
        requireManager(operator);
        validateRequest(req);
        repository.findByName(req.name()).ifPresent(c -> {
            throw new BizException(ErrorCode.INVALID_STATE, "配置 name 已存在：" + req.name());
        });
        // 若新建为 active，则把当前 active 转 archived（保证同时刻仅一条 active）
        if ("active".equals(req.status())) {
            archiveCurrentActive();
        }

        HonorScreenConfig entity = new HonorScreenConfig();
        entity.setName(req.name());
        applyToEntity(entity, req);
        entity.setCreatedBy(operator.userId());
        entity.setUpdatedBy(operator.userId());
        return toItem(repository.save(entity));
    }

    /** 更新配置（全量替换关键字段） */
    @Transactional
    public ConfigItem update(AuthPrincipal operator, long id, UpsertRequest req) {
        requireManager(operator);
        requirePositiveId(id);
        validateRequest(req);
        HonorScreenConfig entity = repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "大屏配置不存在"));

        // name 唯一性（排除自身）
        repository.findByName(req.name()).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new BizException(ErrorCode.INVALID_STATE, "配置 name 已被占用：" + req.name());
            }
        });

        // 关键修复：DTO 里 status 为 null 时，保留 DB 中原值，避免违反 NOT NULL 约束导致 500
        String newStatus = (req.status() != null && !req.status().isBlank())
                ? req.status()
                : entity.getStatus();
        // status 从非 active → active：归档现有 active
        if ("active".equals(newStatus) && !"active".equals(entity.getStatus())) {
            archiveCurrentActive();
        }

        entity.setName(req.name());
        entity.setLayout(req.layout());
        entity.setThemeColor(req.themeColor());
        entity.setRefreshSec(req.refreshSec());
        entity.setSortBy(req.sortBy());
        entity.setMaxItems(req.maxItems());
        entity.setStatus(newStatus);
        entity.setRemark(req.remark());
        entity.setShowFields(serializeShowFields(req.showFields()));
        entity.setUpdatedBy(operator.userId());
        return toItem(repository.save(entity));
    }

    /** 删除配置（仅允许删非 active 历史版本） */
    @Transactional
    public void delete(AuthPrincipal operator, long id) {
        requireManager(operator);
        requirePositiveId(id);
        HonorScreenConfig entity = repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "大屏配置不存在"));
        if ("active".equals(entity.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE, "当前生效配置不可删除，请先停用");
        }
        repository.delete(entity);
    }

    /* ==================== 私有 ==================== */

    private void archiveCurrentActive() {
        repository.findFirstByStatusOrderByUpdatedAtDesc("active")
                .ifPresent(cur -> {
                    cur.setStatus("archived");
                    repository.save(cur);
                });
    }

    private void applyToEntity(HonorScreenConfig entity, UpsertRequest req) {
        entity.setLayout(req.layout());
        entity.setThemeColor(req.themeColor());
        entity.setRefreshSec(req.refreshSec());
        entity.setSortBy(req.sortBy());
        entity.setMaxItems(req.maxItems());
        // 仅当 DTO 显式传了 status 时才覆盖；null 视为"沿用 entity 现有值"（create 路径下 entity 默认 "active"）
        if (req.status() != null && !req.status().isBlank()) {
            entity.setStatus(req.status());
        }
        entity.setRemark(req.remark());
        entity.setShowFields(serializeShowFields(req.showFields()));
    }

    private void validateRequest(UpsertRequest req) {
        if (req.maxItems() != null && req.refreshSec() != null && req.refreshSec() == 0 && req.maxItems() > 200) {
            // 手动刷新模式下 max_items 上限保持 200，业务上无特别限制，保留宽松
        }
    }

    private String serializeShowFields(Map<String, Boolean> showFields) {
        if (showFields == null) {
            return ScreenDtos.DEFAULT_SHOW_FIELDS_JSON;
        }
        try {
            return objectMapper.writeValueAsString(showFields);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "showFields 序列化失败：" + e.getMessage());
        }
    }

    private Map<String, Boolean> deserializeShowFields(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Boolean>>() {});
        } catch (Exception e) {
            // 兜底：JSON 损坏时返回空 map，由前端回退到默认展示
            return new LinkedHashMap<>();
        }
    }

    private ConfigItem toItem(HonorScreenConfig e) {
        return new ConfigItem(
                e.getId(),
                e.getName(),
                e.getLayout(),
                e.getThemeColor(),
                e.getRefreshSec(),
                deserializeShowFields(e.getShowFields()),
                e.getSortBy(),
                e.getMaxItems(),
                e.getStatus(),
                e.getRemark(),
                e.getCreatedBy(),
                e.getUpdatedBy(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private void requireManager(AuthPrincipal operator) {
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        String role = operator.role();
        if (!"school_admin".equals(role) && !"system_admin".equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅 school_admin / system_admin 可管理大屏配置");
        }
    }

    private void requirePositiveId(long id) {
        if (id <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "id 必须为正整数");
        }
    }
}
