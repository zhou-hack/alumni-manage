package com.alumni.attachments.dto;

import java.time.Instant;
import java.util.List;

/**
 * 批量导入出入参 DTO（规范第十一章；全局 Jackson 已配置 snake_case）。
 */
public final class ImportDtos {

    private ImportDtos() {
    }

    /** POST /imports 受理响应 */
    public record Created(Long importId) {
    }

    /** GET /imports/{id} 导入结果（status 供前端轮询判断处理完成） */
    public record Result(
            Long id,
            String status,
            int total,
            int success,
            int failed,
            String filename,
            Long fileSize,
            Instant createdAt,
            Instant finishedAt
    ) {
    }

    /** GET /imports/{id}/errors 错误行 */
    public record ErrorItem(int row, String studentId, String reason) {
    }

    public record ErrorList(List<ErrorItem> list) {
    }

    /** GET /imports 导入任务列表（仅本人上传的任务，按创建时间倒序） */
    public record TaskItem(
            Long id,
            String status,
            int total,
            int success,
            int failed,
            String filename,
            Long fileSize,
            Instant createdAt,
            Instant finishedAt
    ) {
    }

    public record TaskList(List<TaskItem> list, int total) {
    }
}
