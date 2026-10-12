package com.alumni.dicts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 字典模块出入参 DTO（全局 Jackson 已配置 snake_case）。
 */
public final class DictDtos {

    private DictDtos() {
    }

    /** 字典项出参（parent_id 仅层级类型如 major 有值） */
    public record DictItemVO(Long id, String label, String value, Integer sort, Long parentId) {
    }

    /** 新增字典项（major 必须带 parent_id 归属学院） */
    public record CreateDictRequest(
            @NotBlank(message = "label 不能为空")
            @Size(max = 64, message = "label 最长 64 字符") String label,
            @NotBlank(message = "value 不能为空")
            @Pattern(regexp = "^[A-Za-z0-9_\\-]{1,64}$", message = "value 仅支持字母/数字/下划线/中划线") String value,
            Integer sort,
            Long parentId) {
    }

    /** 修改字典项（PATCH 局部更新，字段均可选） */
    public record UpdateDictRequest(
            @Size(max = 64, message = "label 最长 64 字符") String label,
            @Pattern(regexp = "^[A-Za-z0-9_\\-]{1,64}$", message = "value 仅支持字母/数字/下划线/中划线") String value,
            Integer sort,
            Long parentId) {
    }
}
