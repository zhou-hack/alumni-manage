package com.alumni.archives.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 档案模块出入参 DTO（全局 Jackson 已配置 snake_case）。
 */
public final class ArchiveDtos {

    private ArchiveDtos() {
    }

    /** POST /archives 创建（submit=true 直接进入 pending_first 并生成审核记录） */
    public record CreateRequest(
            @NotBlank(message = "title 不能为空")
            @Size(max = 128, message = "title 最长 128 字符") String title,
            @NotBlank(message = "category 不能为空")
            @Pattern(regexp = "^[a-z][a-z0-9_]{0,31}$", message = "category 格式错误（小写 snake_case）") String category,
            String content,
            @Size(max = 255, message = "remark 最长 255 字符") String remark,
            List<Long> attachmentIds,
            Boolean submit) {
    }

    /** PATCH /archives/{id} 修改（仅 draft/rejected；字段均可选，给了才改） */
    public record UpdateRequest(
            @Size(max = 128, message = "title 最长 128 字符") String title,
            @Pattern(regexp = "^[a-z][a-z0-9_]{0,31}$", message = "category 格式错误（小写 snake_case）") String category,
            String content,
            @Size(max = 255, message = "remark 最长 255 字符") String remark,
            List<Long> attachmentIds) {
    }

    /** 列表项（my / 管理端共用；管理端额外带 owner 字段 + published 上架标记） */
    public record ArchiveItem(Long id, String title, String category, String status,
                              String rejectReason, Integer version, Instant createdAt,
                              String studentId, String realName, String college, Integer gradYear,
                              Boolean published) {
    }

    /** 附件出参 */
    public record AttachmentItem(Long id, String name, String url, String mime, Long size) {
    }

    /** 详情（含 published 上架标记、consentActive 大屏授权标记） */
    public record ArchiveDetail(Long id, String title, String category, String content, String remark,
                                String status, String rejectReason, Integer version,
                                Instant createdAt, Instant updatedAt,
                                List<AttachmentItem> attachments,
                                Boolean published, Boolean consentActive) {
    }

    /** 分页包装 */
    public record PageResult<T>(List<T> list, long total, int page, int pageSize) {
    }

    /** 按人聚合·组级行（院系/专业层）：key 为学院或专业名（空值归并为「（未填写）」） */
    public record AggGroupRow(String key, long people, long archives) {
    }

    /** 按人聚合·个人行：档案数按状态拆分（pending 含 pending_counselor/first/second/deleting；不含 deleted） */
    public record AggPersonRow(Long userId, String studentId, String realName, String college, String major,
                               Integer gradYear, long total, long approved, long pending, long rejected,
                               long draft, Instant latestAt) {
    }
}
