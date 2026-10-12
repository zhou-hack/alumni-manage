package com.alumni.showcase.dto;

import jakarta.validation.constraints.Size;

/**
 * 校友大屏展示字段 DTO（V18）。
 */
public final class AlumniProfileDtos {

    private AlumniProfileDtos() {}

    /** PATCH /api/v1/admin/alumni/{id}/profile 与 /api/v1/me/profile 共用 */
    public record ProfileRequest(
            @Size(max = 512, message = "photoUrl 最长 512 字符")
            String photoUrl,

            @Size(max = 64, message = "industry 最长 64 字符")
            String industry,

            @Size(max = 500, message = "intro 最长 500 字符")
            String intro,

            @Size(max = 200, message = "quote 最长 200 字符")
            String quote,

            @Size(max = 200, message = "motto 最长 200 字符")
            String motto
    ) {
    }

    /** GET /api/v1/admin/alumni/{id}/profile 与 /api/v1/alumni/{id}/profile 返回 */
    public record ProfileResponse(
            long id,
            String realName,
            String studentId,
            String college,
            Integer gradYear,
            String photoUrl,
            String industry,
            String intro,
            String quote,
            String motto,
            long updatedAt // epoch millis；前端 formatTime 处理
    ) {
    }
}
