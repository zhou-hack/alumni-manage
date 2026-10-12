package com.alumni.orgsync.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.orgsync.common.ApiResponse;
import com.alumni.orgsync.dto.OrgSyncDtos.LogItem;
import com.alumni.orgsync.dto.OrgSyncDtos.PageResult;
import com.alumni.orgsync.dto.OrgSyncDtos.SyncRequest;
import com.alumni.orgsync.dto.OrgSyncDtos.SyncResult;
import com.alumni.orgsync.service.OrgSyncService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 组织架构同步 Controller（路径相对于 /api/v1，仅 school_admin / system_admin）。
 *
 * <ul>
 *   <li>POST /admin/org-sync/sync —— 触发同步</li>
 *   <li>GET  /admin/org-sync/logs —— 同步日志</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/org-sync")
public class OrgSyncController {

    private final OrgSyncService service;

    public OrgSyncController(OrgSyncService service) {
        this.service = service;
    }

    @PostMapping("/sync")
    public ApiResponse<SyncResult> sync(@AuthenticationPrincipal AuthPrincipal operator,
                                        @Valid @RequestBody SyncRequest req) {
        return ApiResponse.ok(service.sync(operator, req));
    }

    @GetMapping("/logs")
    public ApiResponse<PageResult<LogItem>> logs(@AuthenticationPrincipal AuthPrincipal operator,
                                                @RequestParam(name = "page", defaultValue = "1") int page,
                                                @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.listLog(operator, page, pageSize));
    }
}
