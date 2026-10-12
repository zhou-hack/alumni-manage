package com.alumni.outstanding.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.outstanding.common.ApiResponse;
import com.alumni.outstanding.dto.OutstandingDtos.AdminItem;
import com.alumni.outstanding.dto.OutstandingDtos.CreateRequest;
import com.alumni.outstanding.dto.OutstandingDtos.PageResult;
import com.alumni.outstanding.dto.OutstandingDtos.UpdateRequest;
import com.alumni.outstanding.service.OutstandingAlumniService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 优秀校友管理控制器（扩展模块 [M14]；路径相对于 /api/v1，school_admin）：
 *
 * <ul>
 *   <li>GET    /admin/outstanding-alumni          —— 全量列表（含 draft/withdrawn），支持 status 筛选</li>
 *   <li>POST   /admin/outstanding-alumni          —— 新增精选位（重复添加 409 INVALID_STATE），201</li>
 *   <li>PATCH  /admin/outstanding-alumni/{id}     —— 修改展示信息 / 排序 / 上下架（status）</li>
 *   <li>DELETE /admin/outstanding-alumni/{id}     —— 移除精选位（物理删除）</li>
 * </ul>
 *
 * <p>职责：参数解析 → 调用 Service → 组装统一响应；
 * 角色校验由 SecurityConfig（URL 级）+ Service（二次校验）双层保证。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/outstanding-alumni")
public class AdminOutstandingController {

    private final OutstandingAlumniService outstandingService;

    public AdminOutstandingController(OutstandingAlumniService outstandingService) {
        this.outstandingService = outstandingService;
    }

    /** GET /api/v1/admin/outstanding-alumni?status=&page=&page_size= —— 管理列表 */
    @GetMapping
    public ApiResponse<PageResult> page(@AuthenticationPrincipal AuthPrincipal operator,
                                        @RequestParam(name = "status", required = false) String status,
                                        @RequestParam(name = "page", defaultValue = "1") int page,
                                        @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(outstandingService.page(operator, status, page, pageSize));
    }

    /** POST /api/v1/admin/outstanding-alumni —— 新增精选位，成功返回 201 */
    @PostMapping
    public ResponseEntity<ApiResponse<AdminItem>> create(@AuthenticationPrincipal AuthPrincipal operator,
                                                         @Valid @RequestBody CreateRequest request) {
        AdminItem data = outstandingService.create(operator, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
    }

    /** PATCH /api/v1/admin/outstanding-alumni/{id} —— 修改（字段均可选，给了才改） */
    @PatchMapping("/{id}")
    public ApiResponse<AdminItem> update(@AuthenticationPrincipal AuthPrincipal operator,
                                         @PathVariable long id,
                                         @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(outstandingService.update(operator, id, request));
    }

    /** DELETE /api/v1/admin/outstanding-alumni/{id} —— 移除精选位 */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@AuthenticationPrincipal AuthPrincipal operator,
                                                   @PathVariable long id) {
        outstandingService.delete(operator, id);
        return ApiResponse.ok(Map.of("id", id, "deleted", true));
    }
}
