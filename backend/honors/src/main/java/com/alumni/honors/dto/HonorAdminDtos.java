package com.alumni.honors.dto;

import java.util.List;

/** [M03] 荣誉管理端（列表/导入/导出）出入参 DTO（Jackson SNAKE_CASE 自动映射）。
 */
public final class HonorAdminDtos {

    private HonorAdminDtos() {
    }

    /** GET /admin/honors 列表项（联查学籍信息 + 提交人姓名 + 大屏上下架状态） */
    public record AdminHonorItem(Long id, Long userId, String studentId, String realName,
                                 String college, Integer gradYear, String teamId, String title,
                                 String category, String level, String awardedAt, String status,
                                 String screenStatus, String redDocUrl, String createdBy,
                                 String createdByName, String createdAt) {
    }

    /** 分页包装（与平台 PageResult 格式一致） */
    public record PageResult(List<AdminHonorItem> list, long total, int page, int pageSize) {
    }

    /** 导入错误行（行号用 Excel 原始行号，1-based） */
    public record ImportErrorItem(int row, String studentId, String reason) {
    }

    /** POST /admin/honors/import 响应 data */
    public record ImportResult(int total, int success, int failed, List<ImportErrorItem> errors) {
    }

    /* ----------------------- [P1/M17] 荣誉大屏上下架 ----------------------- */

    /** 单条上下架请求 body */
    public record SetScreenStatusRequest(String status, String reason) {
    }

    /** 批量上下架请求 body（ids 为空时返回 400） */
    public record BatchScreenStatusRequest(List<Long> ids, String status, String reason) {
    }

    /** 单条/批量操作响应 data（包含实际变更条数） */
    public record ScreenStatusResult(int requested, int applied, int skipped, int failed,
                                   List<FailedItem> failures) {
        public record FailedItem(Long id, String reason) {
        }
    }

    /** 上架/下架日志项 */
    public record ScreenLogItem(Long id, Long honorId, String honorTitle, String action,
                              String beforeStatus, String afterStatus, String reason,
                              Long operatorId, String operatorName, String operatorRole,
                              String clientIp, Integer batchSize, String createdAt) {
    }

    /** 上架日志分页 */
    public record ScreenLogPage(List<ScreenLogItem> list, long total, int page, int pageSize) {
    }
}
