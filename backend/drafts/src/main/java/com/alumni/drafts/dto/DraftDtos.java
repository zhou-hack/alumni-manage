package com.alumni.drafts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Map;

/**
 * 草稿模块请求 DTO（Jackson SNAKE_CASE 自动映射 biz_type ↔ bizType）。
 */
public final class DraftDtos {

    private DraftDtos() {
    }

    /** POST /drafts/sync 请求体（前端每 1 分钟定时调用，本地有变更才发） */
    public record SyncRequest(
            @NotBlank(message = "biz_type 不能为空")
            @Pattern(regexp = "^[a-z][a-z0-9_]{0,31}$", message = "biz_type 仅允许小写字母/数字/下划线，最长 32 字符")
            String bizType,

            /** 客户端持有的服务端版本号（首次同步传 0） */
            @NotNull(message = "version 不能为空")
            @PositiveOrZero(message = "version 必须为非负整数")
            Integer version,

            @NotNull(message = "payload 不能为空")
            Map<String, Object> payload) {
    }
}
