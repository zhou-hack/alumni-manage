package com.alumni.attachments.controller;

import com.alumni.attachments.common.ApiResponse;
import com.alumni.attachments.dto.ImportDtos.Created;
import com.alumni.attachments.dto.ImportDtos.ErrorList;
import com.alumni.attachments.dto.ImportDtos.Result;
import com.alumni.attachments.dto.ImportDtos.TaskList;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.attachments.service.ImportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 批量导入接口（API 规范第十一章；挂载 attachments 模块，URL 保持规范冻结路径）。
 * 全部接口仅 college_admin（SecurityConfig 拦截）。
 */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    /** 下载导入模板（xlsx，数据 sheet + 填写说明 sheet） */
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] content = importService.template();
        String disposition = "attachment; filename*=UTF-8''"
                + URLEncoder.encode("alumni_import_template.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(content);
    }

    /** 上传表格创建导入任务（异步处理，立即返回 import_id，前端轮询结果） */
    @PostMapping
    public ApiResponse<Created> create(@RequestParam("file") MultipartFile file,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(importService.create(file, principal));
    }

    /** 导入任务列表（仅本人上传的任务，按创建时间倒序；用于批量导入页"历史任务"面板） */
    @GetMapping
    public ApiResponse<TaskList> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(importService.list(principal));
    }

    /** 导入结果 */
    @GetMapping("/{id}")
    public ApiResponse<Result> result(@PathVariable Long id,
                                      @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(importService.result(id, principal));
    }

    /** 错误行报告（行号+学号+原因） */
    @GetMapping("/{id}/errors")
    public ApiResponse<ErrorList> errors(@PathVariable Long id,
                                         @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(importService.errors(id, principal));
    }
}
