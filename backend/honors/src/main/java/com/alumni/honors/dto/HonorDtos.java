package com.alumni.honors.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 荣誉模块请求 DTO（Jackson SNAKE_CASE 自动映射 archive_id ↔ archiveId）。
 * awarded_at 按 "YYYY-MM-DD" 反序列化为 LocalDate。
 */
public final class HonorDtos {

    private HonorDtos() {
    }

    /** POST /honors 请求体 */
    public record CreateHonorRequest(
            @Positive(message = "archive_id 必须为正整数")
            Long archiveId,

            @NotBlank(message = "title 不能为空")
            @Size(max = 128, message = "title 最长 128 字符")
            String title,

            @NotBlank(message = "category 不能为空")
            @Size(max = 32, message = "category 最长 32 字符")
            String category,

            @NotBlank(message = "level 不能为空")
            @Size(max = 32, message = "level 最长 32 字符")
            String level,

            @NotNull(message = "awarded_at 不能为空")
            LocalDate awardedAt,

            @Size(max = 512, message = "red_doc_url 最长 512 字符")
            String redDocUrl,

            List<@Positive(message = "attachment_ids 元素必须为正整数") Long> attachmentIds) {
    }

    /** PATCH /honors/{id} 请求体（全部字段可选，仅更新非 null 字段） */
    public record UpdateHonorRequest(
            @Positive(message = "archive_id 必须为正整数")
            Long archiveId,

            @Size(max = 128, message = "title 最长 128 字符")
            String title,

            @Size(max = 32, message = "category 最长 32 字符")
            String category,

            @Size(max = 32, message = "level 最长 32 字符")
            String level,

            LocalDate awardedAt,

            @Size(max = 512, message = "red_doc_url 最长 512 字符")
            String redDocUrl,

            List<@Positive(message = "attachment_ids 元素必须为正整数") Long> attachmentIds) {
    }
}
