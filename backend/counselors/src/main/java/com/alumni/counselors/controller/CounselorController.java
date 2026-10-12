package com.alumni.counselors.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.counselors.common.ApiResponse;
import com.alumni.counselors.dto.CounselorDtos.CounselorDetail;
import com.alumni.counselors.dto.CounselorDtos.CounselorItem;
import com.alumni.counselors.dto.CounselorDtos.CreateRequest;
import com.alumni.counselors.dto.CounselorDtos.PageResult;
import com.alumni.counselors.dto.CounselorDtos.ResetPasswordRequest;
import com.alumni.counselors.dto.CounselorDtos.UpdateRequest;
import com.alumni.counselors.service.CounselorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 导员管理控制器（P1 [M34]；路径相对于 /api/v1，仅 school_admin，SecurityConfig 已拦截）：
 *
 * <ul>
 *   <li>GET    /counselors                       —— 列表（?college_id=&keyword=&status=&page=&page_size=）</li>
 *   <li>POST   /counselors                       —— 创建导员账号（201）</li>
 *   <li>GET    /counselors/{id}                  —— 详情（含管辖范围）</li>
 *   <li>PATCH  /counselors/{id}                  —— 修改信息 / 管辖范围</li>
 *   <li>POST   /counselors/{id}/disable          —— 停用（在途待办回退至院系初审）</li>
 *   <li>POST   /counselors/{id}/enable           —— 启用</li>
 *   <li>POST   /counselors/{id}/password/reset   —— 重置密码（散列值）</li>
 * </ul>
 *
 * <p>职责：参数解析 → 调用 Service → 组装统一响应；不含业务规则与 SQL。</p>
 */
@RestController
@RequestMapping("/api/v1/counselors")
public class CounselorController {

    private final CounselorService counselorService;

    public CounselorController(CounselorService counselorService) {
        this.counselorService = counselorService;
    }

    /** GET /api/v1/counselors —— 导员列表 */
    @GetMapping
    public ApiResponse<PageResult<CounselorItem>> page(
            @RequestParam(name = "college_id", required = false) Long collegeId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(counselorService.page(currentUser(), collegeId, keyword, status, page, pageSize));
    }

    /** POST /api/v1/counselors —— 创建导员，成功返回 201 */
    @PostMapping
    public ResponseEntity<ApiResponse<CounselorDetail>> create(@Valid @RequestBody CreateRequest request) {
        CounselorDetail data = counselorService.create(currentUser(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
    }

    /** GET /api/v1/counselors/{id} —— 详情（含管辖范围） */
    @GetMapping("/{id}")
    public ApiResponse<CounselorDetail> detail(@PathVariable long id) {
        return ApiResponse.ok(counselorService.detail(currentUser(), id));
    }

    /** PATCH /api/v1/counselors/{id} —— 修改信息 / 管辖范围（字段均可选，scope 给了即全量替换） */
    @PatchMapping("/{id}")
    public ApiResponse<CounselorDetail> update(@PathVariable long id,
                                               @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(counselorService.update(currentUser(), id, request));
    }

    /** POST /api/v1/counselors/{id}/disable —— 停用（在途待办回退至院系初审） */
    @PostMapping("/{id}/disable")
    public ApiResponse<Map<String, Object>> disable(@PathVariable long id) {
        return ApiResponse.ok(counselorService.disable(currentUser(), id));
    }

    /** POST /api/v1/counselors/{id}/enable —— 启用 */
    @PostMapping("/{id}/enable")
    public ApiResponse<Map<String, Object>> enable(@PathVariable long id) {
        return ApiResponse.ok(counselorService.enable(currentUser(), id));
    }

    /** POST /api/v1/counselors/{id}/password/reset —— 重置密码（散列值，吊销全部 refresh_token） */
    @PostMapping("/{id}/password/reset")
    public ApiResponse<Map<String, Object>> resetPassword(@PathVariable long id,
                                                          @Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.ok(counselorService.resetPassword(currentUser(), id, request.newPassword()));
    }

    /** 从 SecurityContext 取已认证主体（SecurityConfig 已保证 /api/v1/counselors/** 仅 SCHOOL_ADMIN） */
    private AuthPrincipal currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        return null;
    }
}
