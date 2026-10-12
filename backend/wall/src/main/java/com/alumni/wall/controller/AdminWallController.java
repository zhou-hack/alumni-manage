package com.alumni.wall.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.wall.common.ApiResponse;
import com.alumni.wall.dto.WallDtos.AdminItem;
import com.alumni.wall.dto.WallDtos.MyItem;
import com.alumni.wall.dto.WallDtos.PageResult;
import com.alumni.wall.dto.WallDtos.ReviewRequest;
import com.alumni.wall.service.WallService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 留言墙管理控制器（扩展模块 [M16]；路径相对于 /api/v1，school_admin）：
 *
 * <ul>
 *   <li>GET /admin/wall/messages            —— 审核队列（status 筛选 + keyword 搜正文/留言人/学号 + 分页）</li>
 *   <li>PUT /admin/wall/messages/{id}/review —— 审核（approve 通过 / reject 驳回必填原因）</li>
 * </ul>
 *
 * <p>职责：参数解析 → 调用 Service → 组装统一响应；
 * 角色校验由 SecurityConfig（URL 级）+ Service（二次校验）双层保证。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/wall/messages")
public class AdminWallController {

    private final WallService wallService;

    public AdminWallController(WallService wallService) {
        this.wallService = wallService;
    }

    /** GET /api/v1/admin/wall/messages?status=&keyword=&page=&page_size= —— 审核队列 */
    @GetMapping
    public ApiResponse<PageResult<AdminItem>> page(
            @AuthenticationPrincipal AuthPrincipal operator,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(wallService.adminPage(operator, status, keyword, page, pageSize));
    }

    /** PUT /api/v1/admin/wall/messages/{id}/review —— 审核（action=approve|reject，reject 需 note） */
    @PutMapping("/{id}/review")
    public ApiResponse<MyItem> review(@AuthenticationPrincipal AuthPrincipal operator,
                                      @PathVariable long id,
                                      @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(wallService.review(operator, id, request.action(), request.note()));
    }

    /** DELETE /api/v1/admin/wall/messages/{id} —— 删除留言（物理删除，不可恢复） */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@AuthenticationPrincipal AuthPrincipal operator,
                                                   @PathVariable long id) {
        wallService.adminDelete(operator, id);
        return ApiResponse.ok(Map.of("id", id, "deleted", true));
    }

    /**
     * PUT /api/v1/admin/wall/messages/{id}/pin —— 切换置顶状态（true/false）
     * 已 approved 留言方可置顶；置顶优先于按时间倒序排序展示。
     */
    @PutMapping("/{id}/pin")
    public ApiResponse<Map<String, Object>> pin(@AuthenticationPrincipal AuthPrincipal operator,
                                               @PathVariable long id,
                                               @Valid @RequestBody PinRequest req) {
        wallService.setPinned(operator, id, req.pinned());
        return ApiResponse.ok(Map.of("id", id, "is_pinned", req.pinned()));
    }

    /**
     * POST /api/v1/admin/wall/users/{userId}/mute —— 禁言指定用户（duration_days = null 表示永久）
     * body: { duration_days?: int, reason: string }
     */
    @PostMapping("/users/{userId}/mute")
    public ApiResponse<Map<String, Object>> mute(@AuthenticationPrincipal AuthPrincipal operator,
                                                 @PathVariable long userId,
                                                 @Valid @RequestBody MuteRequest req) {
        wallService.mute(operator, userId, req.durationDays(), req.reason());
        return ApiResponse.ok(Map.of("user_id", userId, "muted", true));
    }

    /** 置顶切换请求 */
    public record PinRequest(boolean pinned) {
    }

    /** 禁言请求 */
    public record MuteRequest(Integer durationDays, String reason) {
    }
}
