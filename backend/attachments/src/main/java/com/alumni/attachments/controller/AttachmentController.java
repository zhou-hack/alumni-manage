package com.alumni.attachments.controller;

import com.alumni.attachments.common.ApiResponse;
import com.alumni.attachments.dto.AttachmentDtos.DeleteRequest;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.attachments.service.AttachmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 附件接口（Base URL: /api/v1，见 API 规范第六章）。
 * 跨模块复用组件：biz_type 区分业务归属。
 */
@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /** 统一上传（multipart/form-data，多文件传多个 file 字段，批量 ≤5） */
    @PostMapping
    public ResponseEntity<ApiResponse<Object>> upload(
            @RequestParam("file") List<MultipartFile> files,
            @RequestParam("biz_type") String bizType,
            @RequestParam(value = "remark", required = false) String remark,
            @AuthenticationPrincipal AuthPrincipal principal) {
        Object result = attachmentService.upload(files, bizType, remark, principal);
        // 单文件 201（资源创建成功）；批量 200（success/failed 分组）
        boolean single = files.size() == 1;
        return ResponseEntity.status(single ? 201 : 200).body(ApiResponse.ok(result));
    }

    /** 预览：图片/PDF 直出流；权限按业务归属继承（公开内容免认证）。
     *  缓存策略：PUBLIC 附件 url 为 uuid 不可变 → immutable 强缓存 + ETag/Last-Modified 协商缓存，
     *  并通过 X-Accel-Expires 授权 nginx proxy_cache 共享缓存；非公开一律 no-store。 */
    @GetMapping("/{id}/preview")
    public ResponseEntity<org.springframework.core.io.Resource> preview(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal,
            ServletWebRequest webRequest) {
        AttachmentService.PreviewContent content = attachmentService.preview(id, principal);
        String etag = "\"" + id + "-" + content.size() + "-" + content.lastModified() + "\"";
        if (webRequest.checkNotModified(etag, content.lastModified())) {
            return null; // 304 Not Modified
        }
        String contentDisposition = "inline; filename*=UTF-8''"
                + java.net.URLEncoder.encode(content.name(), StandardCharsets.UTF_8).replace("+", "%20");
        CacheControl cacheControl = content.publicScope()
                ? CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()
                : CacheControl.noStore();
        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .eTag(etag)
                .lastModified(content.lastModified())
                .contentType(MediaType.parseMediaType(content.mime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                // nginx proxy_cache 按此头决定缓存时长（0 = 不缓存，保护 NEED_LOGIN/ALLOW 内容）
                .header("X-Accel-Expires", content.publicScope() ? "31536000" : "0")
                .body(content.resource());
    }

    /** 删除（仅 college_admin / school_admin；被引用 409；写审计日志） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @RequestBody(required = false) DeleteRequest req,
                                    @AuthenticationPrincipal AuthPrincipal principal,
                                    HttpServletRequest request) {
        String reason = req != null ? req.reason() : null;
        attachmentService.delete(id, reason, principal, clientIp(request));
        return ApiResponse.ok(null);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
