package com.alumni.announcement.controller;

import com.alumni.announcement.common.ApiResponse;
import com.alumni.announcement.entity.AnnouncementImage;
import com.alumni.announcement.service.AnnouncementImageService;
import com.alumni.common.security.AuthPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 公告图片静态资源控制器（路径相对于 /api/v1）：
 *
 * <ul>
 *   <li>POST /announcements/images        —— 上传（multipart file；管理员），返回 url 供正文标记引用</li>
 *   <li>GET  /announcements/images/{uuid} —— 公开读取（公告为公开内容，免认证，长缓存）</li>
 * </ul>
 */
@RestController
public class AnnouncementImageController {

    private final AnnouncementImageService imageService;

    public AnnouncementImageController(AnnouncementImageService imageService) {
        this.imageService = imageService;
    }

    /** 上传图片：multipart/form-data，字段名 file */
    @PostMapping("/api/v1/announcements/images")
    public ApiResponse<Map<String, Object>> upload(@AuthenticationPrincipal AuthPrincipal operator,
                                                   @RequestParam("file") MultipartFile file) {
        AnnouncementImage image = imageService.store(operator, file);
        return ApiResponse.ok(Map.of(
                "id", image.getId(),
                "uuid", image.getUuid(),
                "url", AnnouncementImageService.urlOf(image),
                "filename", image.getFilename(),
                "size", image.getSizeBytes()));
    }

    /** 公开读取图片字节（按 content_type 输出，缓存 30 天） */
    @GetMapping("/api/v1/announcements/images/{uuid}")
    public ResponseEntity<byte[]> read(@PathVariable String uuid) {
        AnnouncementImageService.Loaded loaded = imageService.load(uuid);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(loaded.meta().getContentType()))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(loaded.bytes());
    }
}
