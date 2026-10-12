package com.alumni.honors.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.honors.common.ApiResponse;
import com.alumni.honors.common.RequestIdHolder;
import com.alumni.honors.dto.HonorAdminDtos.BatchScreenStatusRequest;
import com.alumni.honors.dto.HonorAdminDtos.ImportResult;
import com.alumni.honors.dto.HonorAdminDtos.PageResult;
import com.alumni.honors.dto.HonorAdminDtos.ScreenLogPage;
import com.alumni.honors.dto.HonorAdminDtos.ScreenStatusResult;
import com.alumni.honors.dto.HonorAdminDtos.SetScreenStatusRequest;
import com.alumni.honors.service.HonorAdminService;
import com.alumni.honors.service.HonorScreenAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * [M03] 荣誉管理端控制器（路径相对于 /api/v1，college_admin / school_admin / system_admin）：
 *
 * <ul>
 *   <li>GET  /admin/honors                  —— 分级列表（college_admin 仅本院，多条件筛选）</li>
 *   <li>GET  /admin/honors/export           —— 导出 xlsx（同列表筛选；限流 5 次/时/账号；自审计）</li>
 *   <li>GET  /admin/honors/import/template  —— 下载导入模板（xlsx）</li>
 *   <li>POST /admin/honors/import           —— 批量导入（同步 ≤500 行，坏行不阻断好行）</li>
 *   <li>POST /admin/honors/{id}/screen-status        —— [M17] 单条上下架</li>
 *   <li>POST /admin/honors/screen-status:batch       —— [M17] 批量上下架</li>
 *   <li>GET  /admin/honors/screen-logs               —— [M17] 上下架变更日志（分页）</li>
 * </ul>
 *
 * <p>URL 级角色限制见 common SecurityConfig；学院隔离与定级规则在 Service 层。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/honors")
public class HonorAdminController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final HonorAdminService honorAdminService;
    private final HonorScreenAdminService honorScreenAdminService;

    public HonorAdminController(HonorAdminService honorAdminService,
                                HonorScreenAdminService honorScreenAdminService) {
        this.honorAdminService = honorAdminService;
        this.honorScreenAdminService = honorScreenAdminService;
    }

    /** GET /api/v1/admin/honors —— 分级列表 */
    @GetMapping
    public ApiResponse<PageResult> list(
            @AuthenticationPrincipal AuthPrincipal operator,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "student_id", required = false) String studentId,
            @RequestParam(name = "college_id", required = false) Long collegeId,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "level", required = false) String level,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "screen_status", required = false) String screenStatus,
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "has_red_doc", required = false) Boolean hasRedDoc,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(honorAdminService.list(operator, keyword, studentId, collegeId,
                category, level, status, screenStatus, year, hasRedDoc, page, pageSize));
    }

    /** GET /api/v1/admin/honors/export —— 导出 xlsx（Content-Disposition attachment） */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal AuthPrincipal operator,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "student_id", required = false) String studentId,
            @RequestParam(name = "college_id", required = false) Long collegeId,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "level", required = false) String level,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "screen_status", required = false) String screenStatus,
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "has_red_doc", required = false) Boolean hasRedDoc,
            HttpServletRequest request) {
        byte[] bytes = honorAdminService.export(operator, keyword, studentId, collegeId,
                category, level, status, screenStatus, year, hasRedDoc, clientIp(request));
        String disposition = "attachment; filename*=UTF-8''"
                + URLEncoder.encode("honors.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(XLSX_MEDIA_TYPE)
                .body(bytes);
    }

    /** GET /api/v1/admin/honors/import/template —— 下载导入模板 */
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> importTemplate() {
        String disposition = "attachment; filename*=UTF-8''"
                + URLEncoder.encode("honor_import_template.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(XLSX_MEDIA_TYPE)
                .body(honorAdminService.importTemplate());
    }

    /** POST /api/v1/admin/honors/import —— 批量导入（multipart file） */
    @PostMapping("/import")
    public ApiResponse<ImportResult> importHonors(
            @AuthenticationPrincipal AuthPrincipal operator,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(honorAdminService.importHonors(operator, file));
    }

    /* ----------------------- [P1/M17] 上下架 ----------------------- */

    /** POST /api/v1/admin/honors/{id}/screen-status —— 单条上下架 */
    @PostMapping("/{id}/screen-status")
    public ApiResponse<ScreenStatusResult> setScreenStatus(
            @AuthenticationPrincipal AuthPrincipal operator,
            @PathVariable("id") Long honorId,
            @RequestBody(required = false) SetScreenStatusRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(honorScreenAdminService.setStatus(
                operator, honorId, body, clientIp(request), RequestIdHolder.get()));
    }

    /** POST /api/v1/admin/honors/screen-status:batch —— 批量上下架（Google API style 命名） */
    @PostMapping("/screen-status:batch")
    public ApiResponse<ScreenStatusResult> batchScreenStatus(
            @AuthenticationPrincipal AuthPrincipal operator,
            @RequestBody BatchScreenStatusRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(honorScreenAdminService.batchSet(
                operator, body, clientIp(request), RequestIdHolder.get()));
    }

    /** GET /api/v1/admin/honors/screen-logs —— 上下架变更日志 */
    @GetMapping("/screen-logs")
    public ApiResponse<ScreenLogPage> screenLogs(
            @AuthenticationPrincipal AuthPrincipal operator,
            @RequestParam(name = "honor_id", required = false) Long honorId,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "operator_role", required = false) String operatorRole,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(honorScreenAdminService.listLogs(
                operator, honorId, action, operatorRole, page, pageSize));
    }

    /** 提交 IP 提取（X-Forwarded-For 优先，导出/上下架审计用） */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
