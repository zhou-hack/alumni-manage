package com.alumni.archives.controller;

import com.alumni.archives.common.ApiResponse;
import com.alumni.archives.dto.ArchiveDtos.ArchiveDetail;
import com.alumni.archives.dto.ArchiveDtos.ArchiveItem;
import com.alumni.archives.dto.ArchiveDtos.CreateRequest;
import com.alumni.archives.dto.ArchiveDtos.PageResult;
import com.alumni.archives.dto.ArchiveDtos.UpdateRequest;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.archives.service.ArchiveService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 档案接口（Base URL: /api/v1，见 API 规范第四章）。
 * 角色粗粒度由 SecurityConfig 拦截，本人/审核角色细粒度在业务层校验。
 */
@RestController
@RequestMapping("/api/v1/archives")
public class ArchiveController {

    private final ArchiveService archiveService;

    public ArchiveController(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    /** 创建档案（student/alumni；submit=true 进入 pending_first，返回 201） */
    @PostMapping
    public ResponseEntity<ApiResponse<ArchiveDetail>> create(@Valid @RequestBody CreateRequest req,
                                                             @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(archiveService.create(req, principal)));
    }

    /** 管理端列表（审核角色；组合筛选 + 分页；院级锁本院、导员锁管辖范围） */
    @GetMapping
    public ApiResponse<PageResult<ArchiveItem>> adminList(
            @RequestParam Map<String, String> filters,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(archiveService.adminList(principal, filters, page, pageSize));
    }

    /**
     * 按人聚合（审核角色；院系 → 专业 → 个人 层层递进）：
     * level=college 院级卡片 / level=major&college=X 专业卡片 / level=person&college=X&major=Y 个人卡片。
     * deleted 不计；数据范围与列表同口径。
     */
    @GetMapping("/agg")
    public ApiResponse<List<?>> agg(@RequestParam String level,
                                    @RequestParam(required = false) String college,
                                    @RequestParam(required = false) String major,
                                    @AuthenticationPrincipal AuthPrincipal principal) {
        if ("person".equals(level)) {
            return ApiResponse.ok(archiveService.aggPersons(principal, college, major));
        }
        return ApiResponse.ok(archiveService.aggGroups(principal, level, college));
    }

    /** 我的记录（含状态+驳回原因） */
    @GetMapping("/my")
    public ApiResponse<PageResult<ArchiveItem>> my(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(archiveService.my(principal, page, pageSize));
    }

    /** 详情（本人或审核角色） */
    @GetMapping("/{id}")
    public ApiResponse<ArchiveDetail> detail(@PathVariable Long id,
                                             @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(archiveService.detail(id, principal));
    }

    /** 修改（仅本人 + draft/rejected） */
    @PatchMapping("/{id}")
    public ApiResponse<ArchiveDetail> update(@PathVariable Long id,
                                             @Valid @RequestBody UpdateRequest req,
                                             @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(archiveService.update(id, req, principal));
    }

    /** 驳回重提（version+1） */
    @PostMapping("/{id}/resubmit")
    public ApiResponse<ArchiveDetail> resubmit(@PathVariable Long id,
                                               @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(archiveService.resubmit(id, principal));
    }

    /** 发起删除申请（非立即删除；走 archive_delete 审核通道） */
    @PostMapping("/{id}/delete-request")
    public ApiResponse<ArchiveDetail> deleteRequest(@PathVariable Long id,
                                                    @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(archiveService.deleteRequest(id, principal));
    }
}
