package com.alumni.teachers.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.teachers.common.ApiResponse;
import com.alumni.teachers.common.BizException;
import com.alumni.teachers.common.ErrorCode;
import com.alumni.teachers.entity.TeacherSyncLog;
import com.alumni.teachers.service.TeacherAdminService;
import com.alumni.teachers.sync.TeacherSyncService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 教师管理端接口：
 * <ul>
 *   <li>POST /api/v1/admin/teachers/sync —— 手动同步（仅 system_admin，SecurityConfig 控制）</li>
 *   <li>GET  /api/v1/admin/teachers —— 教师列表（school_admin / system_admin）</li>
 *   <li>GET  /api/v1/admin/teachers/sync-logs —— 同步记录（仅 system_admin）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/teachers")
public class TeacherAdminController {

    private final TeacherSyncService syncService;
    private final TeacherAdminService adminService;

    public TeacherAdminController(TeacherSyncService syncService, TeacherAdminService adminService) {
        this.syncService = syncService;
        this.adminService = adminService;
    }

    /** POST /api/v1/admin/teachers/sync —— 手动触发同步（仅超管；重入 409） */
    @PostMapping("/sync")
    public ApiResponse<TeacherSyncService.SyncResult> sync(@AuthenticationPrincipal AuthPrincipal operator,
                                                           HttpServletRequest request) {
        return ApiResponse.ok(syncService.sync(TeacherSyncLog.TRIGGER_MANUAL,
                operator.userId(), operator.role(), clientIp(request)));
    }

    /** GET /api/v1/admin/teachers?college_id=&major_id=&status=&keyword=&page=&page_size= */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(name = "college_id", required = false) Long collegeId,
                                                 @RequestParam(name = "major_id", required = false) Long majorId,
                                                 @RequestParam(name = "status", required = false) String status,
                                                 @RequestParam(name = "keyword", required = false) String keyword,
                                                 @RequestParam(name = "page", required = false) String page,
                                                 @RequestParam(name = "page_size", required = false) String pageSize) {
        return ApiResponse.ok(adminService.list(collegeId, majorId, status, keyword,
                parsePositiveInt(page, 1, "page"), parsePositiveInt(pageSize, 10, "page_size")));
    }

    /** GET /api/v1/admin/teachers/sync-logs?page=&page_size= —— 同步记录（仅超管） */
    @GetMapping("/sync-logs")
    public ApiResponse<Map<String, Object>> syncLogs(@RequestParam(name = "page", required = false) String page,
                                                     @RequestParam(name = "page_size", required = false) String pageSize) {
        return ApiResponse.ok(adminService.syncLogs(
                parsePositiveInt(page, 1, "page"), parsePositiveInt(pageSize, 10, "page_size")));
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

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
