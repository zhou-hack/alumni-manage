package com.alumni.audit.controller;

import com.alumni.audit.common.ApiResponse;
import com.alumni.audit.dto.AuditLogDtos.PageResult;
import com.alumni.audit.service.AuditLogService;
import com.alumni.common.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 审计日志控制器（P1 [M16] 安全、隐私与审计；路径相对于 /api/v1）：
 *
 * <ul>
 *   <li>GET /audit-logs         —— 审计日志查询（仅 system_admin）</li>
 *   <li>GET /audit-logs/actions —— 动作字典（筛选下拉用）</li>
 *   <li>GET /audit-logs/export  —— 导出 xlsx（仅 system_admin，单账号 5 次/小时）</li>
 * </ul>
 *
 * <p>职责：参数解析 → 调用 Service → 组装统一响应；不含业务规则与 SQL。</p>
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /** GET /api/v1/audit-logs —— 查询（?user_id=&action=&biz_type=&date_from=&date_to=&page=&page_size=） */
    @GetMapping
    public ApiResponse<PageResult> page(
            @RequestParam(name = "user_id", required = false) Long userId,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "biz_type", required = false) String bizType,
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @RequestParam(name = "date_to", required = false) String dateTo,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(auditLogService.page(currentUser(),
                userId, action, bizType, dateFrom, dateTo, page, pageSize));
    }

    /** GET /api/v1/audit-logs/actions —— 动作字典（筛选下拉用） */
    @GetMapping("/actions")
    public ApiResponse<Map<String, Object>> actions() {
        return ApiResponse.ok(auditLogService.actions(currentUser()));
    }

    /** GET /api/v1/audit-logs/export —— 导出 xlsx（同步流式，Content-Disposition attachment） */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(name = "user_id", required = false) Long userId,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "biz_type", required = false) String bizType,
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @RequestParam(name = "date_to", required = false) String dateTo,
            HttpServletRequest request) {
        byte[] bytes = auditLogService.export(currentUser(),
                userId, action, bizType, dateFrom, dateTo, clientIp(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-logs.xlsx\"")
                .contentType(XLSX_MEDIA_TYPE)
                .body(bytes);
    }

    /** 从 SecurityContext 取已认证主体（SecurityConfig 已限定 /api/v1/audit-logs/** 仅 SYSTEM_ADMIN） */
    private AuthPrincipal currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
