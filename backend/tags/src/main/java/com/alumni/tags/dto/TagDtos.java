package com.alumni.tags.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 标签体系出入参 DTO（全局 Jackson 已配置 snake_case）。
 */
public final class TagDtos {

    private TagDtos() {
    }

    /** POST /tags 创建标签（school_admin） */
    public record CreateRequest(
            @NotBlank(message = "name 不能为空")
            @Size(max = 32, message = "name 最长 32 字符") String name,
            @NotBlank(message = "group 不能为空") String group,
            Integer sort) {
    }

    /** PATCH /tags/{id} 改名 / 调排序（字段均可选，给了才改） */
    public record UpdateRequest(
            @Size(max = 32, message = "name 最长 32 字符") String name,
            Integer sort) {
    }

    /** PUT /tags/bindings/{biz_type}/{biz_id} 全量覆盖式绑定（≤20 在 Service 校验，返回 BATCH_LIMIT_EXCEEDED） */
    public record BindRequest(List<Long> tagIds) {
    }

    /** 标签项 */
    public record TagItem(Long id, String name, String group, Integer sort,
                          Long createdBy, Instant createdAt, Instant updatedAt) {
    }

    /** 分页包装（与 announcement 模块 PageResult 格式一致） */
    public record PageResult(List<TagItem> list, long total, int page, int pageSize) {
    }
}
