package com.alumni.drafts.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.drafts.common.ApiResponse;
import com.alumni.drafts.common.BizException;
import com.alumni.drafts.common.ErrorCode;
import com.alumni.drafts.common.RequestIdHolder;
import com.alumni.drafts.dto.DraftDtos.SyncRequest;
import com.alumni.drafts.service.DraftService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 草稿控制器（Controller 层）。
 *
 * <p>实现《[定稿]API规范》第十二章接口（路径相对于 /api/v1）：</p>
 * <ul>
 *   <li>POST   /drafts/sync  —— 草稿同步（版本号乐观锁，冲突 409 携带服务端最新草稿）</li>
 *   <li>GET    /drafts/my    —— 我的草稿列表（换设备恢复用）</li>
 *   <li>DELETE /drafts/{id}  —— 删除草稿（仅本人）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/drafts")
public class DraftController {

    private final DraftService draftService;

    public DraftController(DraftService draftService) {
        this.draftService = draftService;
    }

    /**
     * POST /api/v1/drafts/sync —— 版本冲突时返回 409 + DRAFT_VERSION_CONFLICT，
     * data 携带服务端最新草稿与版本号（前端覆盖本地）。
     */
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sync(@Valid @RequestBody SyncRequest request) {
        DraftService.SyncResult result = draftService.sync(currentUser(), request);
        if (result.conflict()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(
                    ErrorCode.DRAFT_VERSION_CONFLICT.code(), "草稿已被其他设备修改",
                    result.data(), RequestIdHolder.get(), System.currentTimeMillis()));
        }
        return ResponseEntity.ok(ApiResponse.ok(result.data()));
    }

    /** GET /api/v1/drafts/my?biz_type=archive —— 我的草稿列表（biz_type 可选） */
    @GetMapping("/my")
    public ApiResponse<Map<String, Object>> my(@RequestParam(value = "biz_type", required = false) String bizType) {
        if (bizType != null && !bizType.matches("^[a-z][a-z0-9_]{0,31}$")) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "biz_type 取值不合法");
        }
        return ApiResponse.ok(draftService.my(currentUser(), bizType));
    }

    /** DELETE /api/v1/drafts/{id} —— 删除草稿（仅本人） */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable long id) {
        if (id <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "id 必须为正整数");
        }
        return ApiResponse.ok(draftService.delete(currentUser(), id));
    }

    /** 从 SecurityContext 取已认证主体（SecurityConfig 已保证 /api/v1/drafts/** 必须登录） */
    private AuthPrincipal currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        return null;
    }
}
