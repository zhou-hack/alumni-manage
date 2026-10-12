package com.alumni.photo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 照片模块 DTO（snake_case）
 */
public final class PhotoDtos {

    private PhotoDtos() {
    }

    /* ==================== 请求 ==================== */

    /** 提交照片 */
    public record SubmitRequest(
            @NotNull(message = "attachment_id 不能为空") Long attachmentId,
            @Size(max = 128) String title,
            @Size(max = 500) String caption,
            /** 标签编码数组（前端按 dicts.photo_tag 字典翻译），空数组 = 待归类 */
            List<String> tags) {
    }

    /** 审核 */
    public record ReviewRequest(
            @NotBlank(message = "action 不能为空")
            @jakarta.validation.constraints.Pattern(
                    regexp = "^(approve|reject)$",
                    message = "action 仅支持 approve / reject") String action,
            @Size(max = 255) String reason) {
    }

    /** 推送 */
    public record PushRequest(
            @NotBlank(message = "target_type 不能为空")
            @jakarta.validation.constraints.Pattern(
                    regexp = "^(all_users|college|role|single_user)$",
                    message = "target_type 仅支持 all_users / college / role / single_user") String targetType,
            String targetId,
            @Size(max = 255) String note) {
    }

    /* ==================== 响应 ==================== */

    public record PhotoItem(
            Long id,
            Long userId,
            Long attachmentId,
            String attachmentUrl,
            String attachmentName,
            String title,
            String caption,
            List<String> tags,
            String status,
            String rejectReason,
            Long reviewedBy,
            Instant reviewedAt,
            Integer pushedCount,
            Instant lastPushAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record PageResult<T>(List<T> list, long total, int page, int pageSize) {
    }

    public record PushLogItem(
            Long id,
            Long photoId,
            String targetType,
            String targetId,
            Integer recipients,
            Long operatorId,
            String note,
            Instant createdAt) {
    }

    public record PushResult(int recipients, Long logId) {
    }
}
