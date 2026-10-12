package com.alumni.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 公告模块出入参 DTO（全局 Jackson 已配置 snake_case）。
 */
public final class AnnouncementDtos {

    private AnnouncementDtos() {
    }

    /** 正文上限：10MB（字符数） */
    static final int CONTENT_MAX = 10 * 1024 * 1024;

    /** POST /announcements 创建（默认草稿） */
    public record CreateRequest(
            @NotBlank(message = "title 不能为空")
            @Size(max = 128, message = "title 最长 128 字符") String title,
            @NotBlank(message = "content 不能为空")
            @Size(max = CONTENT_MAX, message = "content 最长 10MB") String content,
            @Size(max = 512, message = "cover_image 最长 512 字符") String coverImage) {
    }

    /** PATCH /announcements/{id} 修改（字段均可选，给了才改；cover_image 空串即清除；status 变更即发布/撤下） */
    public record UpdateRequest(
            @Size(max = 128, message = "title 最长 128 字符") String title,
            @Size(max = CONTENT_MAX, message = "content 最长 10MB") String content,
            @Size(max = 512, message = "cover_image 最长 512 字符") String coverImage,
            @Pattern(regexp = "^(draft|published|withdrawn)$",
                    message = "status 仅允许 draft/published/withdrawn") String status) {
    }

    /** 列表项 */
    public record AnnouncementItem(Long id, String title, String content, String coverImage, String status,
                                   Long publishedBy, Instant publishedAt,
                                   Long createdBy, Instant createdAt, Instant updatedAt) {
    }

    /** 分页包装（与 archives 模块 PageResult 格式一致） */
    public record PageResult(List<AnnouncementItem> list, long total, int page, int pageSize) {
    }
}
