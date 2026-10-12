package com.alumni.attachments.dto;

import java.util.List;

/**
 * 附件模块出入参 DTO（全局 Jackson 已配置 snake_case）。
 */
public final class AttachmentDtos {

    private AttachmentDtos() {
    }

    /** 单文件上传成功出参 */
    public record UploadedFile(Long id, String bizType, String url, String name, Long size, String mime) {
    }

    /** 批量上传：单条成功项 */
    public record SuccessItem(Long id, String url, String name) {
    }

    /** 批量上传：单条失败项 */
    public record FailedItem(String name, String reason) {
    }

    /** 批量上传出参 */
    public record BatchResult(List<SuccessItem> success, List<FailedItem> failed) {
    }

    /** 删除请求体（删除原因，可选） */
    public record DeleteRequest(String reason) {
    }
}
