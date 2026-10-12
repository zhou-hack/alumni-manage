package com.alumni.consents.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 授权模块请求 DTO（Jackson SNAKE_CASE 自动映射 archive_id ↔ archiveId）。
 */
public final class ConsentDtos {

    private ConsentDtos() {
    }

    /** POST /consents 请求体 */
    public record CreateConsentRequest(
            @NotNull(message = "archive_id 不能为空")
            @Positive(message = "archive_id 必须为正整数")
            Long archiveId,

            @NotBlank(message = "scope 不能为空")
            @Pattern(regexp = "screen|site", message = "scope 仅支持 screen/site")
            String scope) {
    }
}
