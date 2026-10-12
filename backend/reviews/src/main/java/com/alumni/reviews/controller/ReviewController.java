package com.alumni.reviews.controller;

import com.alumni.reviews.common.ApiResponse;
import com.alumni.reviews.common.BizException;
import com.alumni.reviews.common.ErrorCode;
import com.alumni.reviews.entity.ReviewLog;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.reviews.service.ReviewService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审核中心控制器（《[定稿]API规范》第七章）：
 * <ul>
 *   <li>GET  /api/v1/reviews —— 待办列表（学院看 first，校级看 second）</li>
 *   <li>GET  /api/v1/reviews/{id} —— 审核详情</li>
 *   <li>POST /api/v1/reviews/{id}/approve —— 通过</li>
 *   <li>POST /api/v1/reviews/{id}/reject —— 驳回（reason 必填）</li>
 *   <li>GET  /api/v1/reviews/{id}/logs —— 审核日志（审核角色 + 本人）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** GET /api/v1/reviews?biz_type=&status=&stage=&page=&page_size= —— 待办列表 */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(name = "biz_type", required = false) String bizType,
                                                 @RequestParam(name = "status", required = false) String status,
                                                 @RequestParam(name = "stage", required = false) String stage,
                                                 @RequestParam(name = "page", required = false) String page,
                                                 @RequestParam(name = "page_size", required = false) String pageSize) {
        int pageNum = parsePositiveInt(page, 1, "page");
        int size = parsePositiveInt(pageSize, ReviewService.DEFAULT_PAGE_SIZE, "page_size");
        return ApiResponse.ok(reviewService.listFor(currentUser(), bizType, status, stage, pageNum, size));
    }

    /** GET /api/v1/reviews/{id} —— 审核详情 */
    @GetMapping("/{id}")
    public ApiResponse<?> detail(@PathVariable("id") long id) {
        return ApiResponse.ok(reviewService.detail(currentUser(), id));
    }

    /** POST /api/v1/reviews/{id}/approve —— 通过 */
    @PostMapping("/{id}/approve")
    public ApiResponse<?> approve(@PathVariable("id") long id) {
        return ApiResponse.ok(reviewService.approve(currentUser(), id));
    }

    /** POST /api/v1/reviews/{id}/reject —— 驳回（body: {"reason": "..."}） */
    @PostMapping("/{id}/reject")
    public ApiResponse<?> reject(@PathVariable("id") long id,
                                 @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.ok(reviewService.reject(currentUser(), id, reason));
    }

    /** GET /api/v1/reviews/{id}/logs —— 审核日志 */
    @GetMapping("/{id}/logs")
    public ApiResponse<Map<String, Object>> logs(@PathVariable("id") long id) {
        List<ReviewLog> entries = reviewService.logs(currentUser(), id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", entries);
        return ApiResponse.ok(data);
    }

    /** 从 SecurityContext 取当前认证主体（/api/v1/reviews/** 一律已认证） */
    private AuthPrincipal currentUser() {
        return (AuthPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /** 解析正整数 query 参数，未传返回默认值，非法抛出 INVALID_PARAMETER */
    private int parsePositiveInt(String raw, int defaultValue, String name) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, name + " 必须为整数");
        }
    }
}
