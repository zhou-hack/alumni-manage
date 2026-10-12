package com.alumni.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 管理模块（P1 [M15]）DTO 集合。序列化命名由全局 SNAKE_CASE 策略处理。
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** 角色—字段可见性矩阵：{roles:{counselor:{phone:"masked",...},...}} */
    public record MatrixResult(Map<String, Map<String, String>> roles) {
    }

    /** PUT /admin/field-permissions/{role} 请求体 */
    public record FieldPermissionUpdateRequest(@NotNull(message = "fields 必填") Map<String, String> fields) {
    }

    /** POST /admin/users/{id}/suspend 请求体 */
    public record SuspendRequest(@NotBlank(message = "reason 必填") String reason) {
    }

    /** POST /admin/users/{id}/handover 请求体 */
    public record HandoverRequest(@NotNull(message = "target_user_id 必填") Long targetUserId,
                                  @NotBlank(message = "reason 必填") String reason) {
    }

    /** POST /admin/users/{id}/handover 响应 data */
    public record HandoverResult(int transferredReviews, String originStatus) {
    }

    /** 账号变更留痕条目 */
    public record ChangeLogItem(Long id, String action, Long operatorId,
                                Map<String, Object> detail, Instant createdAt) {
    }

    /** GET /admin/users/{id}/change-logs 响应 data */
    public record ChangeLogPage(List<ChangeLogItem> list, long total, int page, int pageSize) {
    }
}
