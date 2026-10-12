package com.alumni.announcement.service;

import com.alumni.announcement.common.BizException;
import com.alumni.announcement.common.ErrorCode;
import com.alumni.announcement.entity.AnnouncementImage;
import com.alumni.announcement.repository.AnnouncementImageRepository;
import com.alumni.common.security.AuthPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 公告图片静态资源：上传落盘 {storage.root}/announcement/{uuid}.{ext}，按 UUID 公开读取。
 *
 * <p>与 attachments 模块（业务附件，鉴权预览）隔离：公告为公开内容，图片 URL 免认证可读，
 * 正文只存 [[alumni-img:URL|宽度]] 标记，不再内嵌 base64。</p>
 */
@Service
public class AnnouncementImageService {

    /** 允许的图片类型 → 扩展名 */
    private static final Map<String, String> TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp");
    /** 单张上限：10MB */
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    /** 写操作限定角色（与公告写一致） */
    private static final Set<String> MANAGER_ROLES = Set.of("college_admin", "school_admin", "system_admin");

    private final AnnouncementImageRepository repository;
    private final Path rootDir;

    public AnnouncementImageService(AnnouncementImageRepository repository,
                                    @Value("${app.storage.root:./storage}") String rootDir) {
        this.repository = repository;
        this.rootDir = Path.of(rootDir).toAbsolutePath().normalize();
    }

    /** 上传：校验类型/大小 → 落盘 → 登记元数据，返回对外 URL 路径 */
    public AnnouncementImage store(AuthPrincipal operator, MultipartFile file) {
        if (operator == null || !MANAGER_ROLES.contains(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可上传公告图片");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "文件不能为空");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String ext = TYPES.get(contentType);
        if (ext == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "仅支持 jpg/png/gif/webp 图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "图片超过 10MB 上限");
        }

        AnnouncementImage image = new AnnouncementImage();
        image.setUuid(UUID.randomUUID().toString());
        image.setFilename(file.getOriginalFilename() == null ? "image." + ext : file.getOriginalFilename());
        image.setContentType(contentType);
        image.setSizeBytes(file.getSize());
        image.setCreatedBy(operator.userId());

        try {
            Path dir = rootDir.resolve("announcement");
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(image.getUuid() + "." + ext), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "图片保存失败");
        }
        return repository.save(image);
    }

    /** 读取：元数据 + 文件字节 */
    public Loaded load(String uuid) {
        AnnouncementImage image = repository.findByUuid(uuid)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "图片不存在"));
        String ext = TYPES.get(image.getContentType());
        Path path = rootDir.resolve("announcement").resolve(image.getUuid() + "." + ext);
        try {
            return new Loaded(image, Files.readAllBytes(path));
        } catch (IOException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "图片文件不存在");
        }
    }

    /** 对外 URL 路径（正文标记引用） */
    public static String urlOf(AnnouncementImage image) {
        return "/api/v1/announcements/images/" + image.getUuid();
    }

    public record Loaded(AnnouncementImage meta, byte[] bytes) {
    }
}
