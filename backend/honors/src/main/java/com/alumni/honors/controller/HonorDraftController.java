package com.alumni.honors.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.honors.common.ApiResponse;
import com.alumni.honors.dto.HonorDraftDtos.*;
import com.alumni.honors.entity.HonorDraft;
import com.alumni.honors.service.HonorDraftService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * [M03 扩展] 荣誉证书解析草稿控制器（路径相对于 /api/v1）：
 *
 * <ul>
 *   <li>POST   /honor-drafts             —— 创建草稿（201）</li>
 *   <li>GET    /honor-drafts/{id}        —— 草稿详情（含实时成员匹配预览）</li>
 *   <li>PATCH  /honor-drafts/{id}        —— 人工修正字段</li>
 *   <li>POST   /honor-drafts/{id}/preview-members —— 实时成员匹配预览（不落库）</li>
 *   <li>POST   /honor-drafts/{id}/confirm —— 草稿 → 正式 honors（团队生成公示行+个人行）</li>
 *   <li>DELETE /honor-drafts/{id}        —— 丢弃草稿（释放附件）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/honor-drafts")
public class HonorDraftController {

    private final HonorDraftService draftService;

    public HonorDraftController(HonorDraftService draftService) {
        this.draftService = draftService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HonorDraft>> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @Valid @RequestBody CreateDraftRequest request) {
        HonorDraft draft = draftService.create(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(draft));
    }

    @GetMapping("/{id}")
    public ApiResponse<Object> detail(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable long id) {
        return ApiResponse.ok(draftService.detail(principal, id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<HonorDraft> update(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable long id,
                                          @Valid @RequestBody UpdateDraftRequest request) {
        return ApiResponse.ok(draftService.update(principal, id, request));
    }

    @PostMapping("/{id}/preview-members")
    public ApiResponse<PreviewResult> previewMembers(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @PathVariable long id,
                                                     @Valid @RequestBody PreviewRequest request) {
        return ApiResponse.ok(draftService.preview(principal, id, request));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<ConfirmResult> confirm(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable long id,
                                              @Valid @RequestBody ConfirmRequest request) {
        return ApiResponse.ok(draftService.confirm(principal, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> discard(@AuthenticationPrincipal AuthPrincipal principal,
                                     @PathVariable long id) {
        draftService.discard(principal, id);
        return ApiResponse.ok(null);
    }
}