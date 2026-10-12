package com.alumni.outstanding.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 优秀校友模块出入参 DTO（全局 Jackson 已配置 snake_case；
 * timeline/awards 为 JSON 透传结构，键名 year/event、title/year/level 原样保留）。
 */
public final class OutstandingDtos {

    private OutstandingDtos() {
    }

    /** GET /alumni/outstanding 公开列表项（首页环形展厅） */
    public record OutstandingItem(String studentId, String name, String photoUrl, String message,
                                  String college, Integer gradYear, String current,
                                  List<Map<String, String>> timeline, List<Map<String, String>> awards,
                                  boolean featured) {
    }

    /** GET /alumni/outstanding 响应 data */
    public record OutstandingListResult(List<OutstandingItem> list, long total) {
    }

    /** POST /admin/outstanding-alumni 创建（仅 user_id 必填，其余字段缺省从学籍自动补全） */
    public record CreateRequest(
            @NotNull(message = "user_id 不能为空") Long userId,
            @Size(max = 50, message = "display_name 最长 50 字符") String displayName,
            @Size(max = 300000, message = "photo_url 最长 300000 字符（base64 照片约 ≤100KB 原图）") String photoUrl,
            @Size(max = 200, message = "message 最长 200 字符") String message,
            Long collegeId,
            @Min(value = 2000, message = "grad_year 不得早于 2000")
            @Max(value = 2100, message = "grad_year 不得晚于 2100") Integer gradYear,
            @Size(max = 100, message = "current_title 最长 100 字符") String currentTitle,
            @Size(max = 50, message = "current_city 最长 50 字符") String currentCity,
            @Size(max = 20, message = "timeline 最多 20 项") List<Map<String, String>> timeline,
            @Size(max = 20, message = "awards 最多 20 项") List<Map<String, String>> awards,
            Integer displayOrder,
            Boolean isFeatured,
            @Pattern(regexp = "^(draft|published|withdrawn)$",
                    message = "status 仅允许 draft/published/withdrawn") String status) {
    }

    /** PATCH /admin/outstanding-alumni/{id} 修改（字段均可选，给了才改；status 变更即发布/撤下） */
    public record UpdateRequest(
            @Size(max = 50, message = "display_name 最长 50 字符") String displayName,
            @Size(max = 300000, message = "photo_url 最长 300000 字符（base64 照片约 ≤100KB 原图）") String photoUrl,
            @Size(max = 200, message = "message 最长 200 字符") String message,
            Long collegeId,
            @Min(value = 2000, message = "grad_year 不得早于 2000")
            @Max(value = 2100, message = "grad_year 不得晚于 2100") Integer gradYear,
            @Size(max = 100, message = "current_title 最长 100 字符") String currentTitle,
            @Size(max = 50, message = "current_city 最长 50 字符") String currentCity,
            @Size(max = 20, message = "timeline 最多 20 项") List<Map<String, String>> timeline,
            @Size(max = 20, message = "awards 最多 20 项") List<Map<String, String>> awards,
            Integer displayOrder,
            Boolean isFeatured,
            @Pattern(regexp = "^(draft|published|withdrawn)$",
                    message = "status 仅允许 draft/published/withdrawn") String status) {
    }

    /** 管理列表项（全状态，含学院文本与排序字段） */
    public record AdminItem(Long id, Long userId, String studentId, String displayName, String photoUrl,
                            String message, Long collegeId, String college, Integer gradYear,
                            String currentTitle, String currentCity,
                            List<Map<String, String>> timeline, List<Map<String, String>> awards,
                            Integer displayOrder, boolean featured, String status,
                            Instant createdAt, Instant updatedAt) {
    }

    /** 分页包装（与 archives 模块 PageResult 格式一致） */
    public record PageResult(List<AdminItem> list, long total, int page, int pageSize) {
    }
}
