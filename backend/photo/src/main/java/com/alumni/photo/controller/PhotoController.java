package com.alumni.photo.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.photo.common.ApiResponse;
import com.alumni.photo.dto.PhotoDtos.PageResult;
import com.alumni.photo.dto.PhotoDtos.PhotoItem;
import com.alumni.photo.dto.PhotoDtos.PushLogItem;
import com.alumni.photo.dto.PhotoDtos.PushRequest;
import com.alumni.photo.dto.PhotoDtos.PushResult;
import com.alumni.photo.dto.PhotoDtos.ReviewRequest;
import com.alumni.photo.dto.PhotoDtos.SubmitRequest;
import com.alumni.photo.service.PhotoCollectService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 校友照片管理 Controller（路径相对于 /api/v1）：
 *
 * <ul>
 *   <li>POST   /photos/submit             —— 提交照片（登录用户）</li>
 *   <li>GET    /photos/mine              —— 我的照片列表</li>
 *   <li>POST   /admin/photos/list        —— 后台审核队列</li>
 *   <li>POST   /admin/photos/{id}/review —— 审核通过/驳回</li>
 *   <li>POST   /admin/photos/{id}/push   —— 推送照片到目标群体</li>
 *   <li>GET    /admin/photos/push-logs   —— 推送日志</li>
 * </ul>
 *
 * <p>权限：URL 级 SecurityConfig 仅放行 photo_collect 写入路径（用户提交）；后台审核 / 推送由 review/push role 二次校验。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class PhotoController {

    private final PhotoCollectService photoService;

    public PhotoController(PhotoCollectService photoService) {
        this.photoService = photoService;
    }

    @PostMapping("/photos/submit")
    public ApiResponse<PhotoItem> submit(@AuthenticationPrincipal AuthPrincipal operator,
                                        @Valid @RequestBody SubmitRequest req) {
        return ApiResponse.ok(photoService.submit(operator, req));
    }

    @GetMapping("/photos/mine")
    public ApiResponse<PageResult<PhotoItem>> mine(@AuthenticationPrincipal AuthPrincipal operator,
                                                 @RequestParam(name = "status", required = false) String status,
                                                 @RequestParam(name = "page", defaultValue = "1") int page,
                                                 @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(photoService.mine(operator, status, page, pageSize));
    }

    @GetMapping("/admin/photos")
    public ApiResponse<PageResult<PhotoItem>> adminList(@AuthenticationPrincipal AuthPrincipal operator,
                                                       @RequestParam(name = "status", required = false) String status,
                                                       @RequestParam(name = "page", defaultValue = "1") int page,
                                                       @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(photoService.adminList(operator, status, page, pageSize));
    }

    @PostMapping("/admin/photos/{id}/review")
    public ApiResponse<PhotoItem> review(@AuthenticationPrincipal AuthPrincipal operator,
                                        @PathVariable long id,
                                        @Valid @RequestBody ReviewRequest req) {
        return ApiResponse.ok(photoService.review(operator, id, req));
    }

    @PostMapping("/admin/photos/{id}/push")
    public ApiResponse<PushResult> push(@AuthenticationPrincipal AuthPrincipal operator,
                                        @PathVariable long id,
                                        @Valid @RequestBody PushRequest req) {
        return ApiResponse.ok(photoService.push(operator, id, req));
    }

    @GetMapping("/admin/photos/push-logs")
    public ApiResponse<PageResult<PushLogItem>> pushLogs(@AuthenticationPrincipal AuthPrincipal operator,
                                                       @RequestParam(name = "photo_id", required = false) Long photoId,
                                                       @RequestParam(name = "page", defaultValue = "1") int page,
                                                       @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(photoService.pushLog(operator, photoId, page, pageSize));
    }
}
