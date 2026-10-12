package com.alumni.attachments.service;

import com.alumni.attachments.common.BizException;
import com.alumni.attachments.common.ErrorCode;
import com.alumni.attachments.dto.AttachmentDtos.BatchResult;
import com.alumni.attachments.dto.AttachmentDtos.FailedItem;
import com.alumni.attachments.dto.AttachmentDtos.SuccessItem;
import com.alumni.attachments.dto.AttachmentDtos.UploadedFile;
import com.alumni.attachments.entity.Attachment;
import com.alumni.attachments.entity.AttachmentAuditLog;
import com.alumni.attachments.repository.AttachmentAuditLogRepository;
import com.alumni.attachments.repository.AttachmentRepository;
import com.alumni.common.security.AuthPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 附件核心业务：统一上传 / 预览（权限继承）/ 删除（审计）。
 * 本期允许类型：图片（jpg/png/webp）+ PDF；Office 暂缓。
 */
@Service
public class AttachmentService {

    private static final Pattern BIZ_TYPE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,31}$");
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> ALLOWED = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "png", "image/png", "webp", "image/webp",
            "pdf", "application/pdf");

    private final AttachmentRepository attachmentRepository;
    private final AttachmentAuditLogRepository auditLogRepository;
    private final FileStorageService storageService;
    private final ImageProcessService imageProcessService;
    private final PreviewPermissionService permissionService;
    private final int batchLimit;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             AttachmentAuditLogRepository auditLogRepository,
                             FileStorageService storageService,
                             ImageProcessService imageProcessService,
                             PreviewPermissionService permissionService,
                             @Value("${app.storage.batch-limit:5}") int batchLimit) {
        this.attachmentRepository = attachmentRepository;
        this.auditLogRepository = auditLogRepository;
        this.storageService = storageService;
        this.imageProcessService = imageProcessService;
        this.permissionService = permissionService;
        this.batchLimit = batchLimit;
    }

    // ---------- 上传 ----------

    /** 统一上传：单文件失败直接 400；多文件坏行不阻断好行（success/failed 分组返回） */
    @Transactional
    public Object upload(List<MultipartFile> files, String bizType, String remark, AuthPrincipal uploader) {
        if (!BIZ_TYPE_PATTERN.matcher(bizType).matches()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "biz_type 格式错误（小写 snake_case）");
        }
        if (files == null || files.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "file 不能为空");
        }
        if (files.size() > batchLimit) {
            throw new BizException(ErrorCode.BATCH_LIMIT_EXCEEDED, "单次最多上传 " + batchLimit + " 个文件");
        }
        boolean single = files.size() == 1;
        List<SuccessItem> success = new ArrayList<>();
        List<FailedItem> failed = new ArrayList<>();

        for (MultipartFile file : files) {
            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
            try {
                UploadedFile uploaded = saveOne(file, bizType, remark, uploader);
                success.add(new SuccessItem(uploaded.id(), uploaded.url(), uploaded.name()));
                if (single) {
                    return uploaded;
                }
            } catch (UploadRejectException e) {
                if (single) {
                    throw new BizException(e.errorCode, e.getMessage());
                }
                failed.add(new FailedItem(originalName, e.getMessage()));
            } catch (IOException e) {
                if (single) {
                    throw new BizException(ErrorCode.INTERNAL_ERROR, "文件读取失败");
                }
                failed.add(new FailedItem(originalName, "文件读取失败"));
            }
        }
        return new BatchResult(success, failed);
    }

    private UploadedFile saveOne(MultipartFile file, String bizType, String remark,
                                 AuthPrincipal uploader) throws IOException {
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
        String ext = extOf(originalName);
        String mime = ALLOWED.get(ext);
        if (mime == null) {
            throw new UploadRejectException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "文件类型不允许（仅支持 jpg/png/webp/pdf）");
        }
        byte[] data = file.getBytes();
        if (data.length > MAX_FILE_BYTES) {
            throw new UploadRejectException(ErrorCode.FILE_TOO_LARGE, "文件大小超限（单文件 ≤10MB）");
        }
        // 内容嗅探：扩展名与真实内容必须一致（防伪装文件入库）
        if ("application/pdf".equals(mime) && !startsWithPdfMagic(data)) {
            throw new UploadRejectException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "PDF 文件内容不合法");
        }
        // 图片服务端压缩；PDF 原样存储
        if (mime.startsWith("image/")) {
            try {
                var processed = imageProcessService.process(data, mime);
                data = processed.data();
                ext = processed.ext();
                mime = processed.mime();
            } catch (IllegalStateException e) {
                throw new UploadRejectException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "图片内容无法解析或已损坏");
            }
        }
        String url = storageService.save(bizType, ext, data);

        Attachment attachment = new Attachment();
        attachment.setBizType(bizType);
        attachment.setStatus("unreferenced");
        attachment.setUrl(url);
        attachment.setName(originalName);
        attachment.setSize((long) data.length);
        attachment.setMime(mime);
        attachment.setUploaderId(uploader.userId());
        attachment.setRemark(remark);
        try {
            attachmentRepository.save(attachment);
        } catch (RuntimeException e) {
            // 补偿：DB 写失败时删除已落盘文件，避免无主文件残留
            storageService.deleteQuietly(url);
            throw e;
        }
        return new UploadedFile(attachment.getId(), bizType, url, originalName,
                attachment.getSize(), mime);
    }

    // ---------- 预览 ----------

    public record PreviewContent(Resource resource, String mime, String name,
                                 long size, long lastModified, boolean publicScope) {
    }

    /** 预览：权限按业务归属继承（PUBLIC 免认证 / NEED_LOGIN 401 / DENY 403）。
     *  文件流式直出（FileSystemResource，不整读入内存）；publicScope 供控制器决定缓存策略。 */
    public PreviewContent preview(Long id, AuthPrincipal user) {
        Attachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "附件不存在"));
        PreviewPermissionService.Decision decision = permissionService.decide(attachment, user);
        switch (decision) {
            case NEED_LOGIN -> throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或 Token 已过期");
            case DENY -> throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
            default -> { }
        }
        Path path = storageService.resolve(attachment.getUrl());
        try {
            return new PreviewContent(new FileSystemResource(path),
                    attachment.getMime(), attachment.getName(), attachment.getSize(),
                    Files.getLastModifiedTime(path).toMillis(),
                    decision == PreviewPermissionService.Decision.PUBLIC);
        } catch (IOException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件文件不存在");
        }
    }

    // ---------- 删除 ----------

    /** 删除（仅审核角色）：被引用 409；每次删除写审计日志（保留 ≥180 天） */
    @Transactional
    public void delete(Long id, String reason, AuthPrincipal operator, String clientIp) {
        Attachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "附件不存在"));
        if (attachment.getRefId() != null) {
            throw new BizException(ErrorCode.RESOURCE_REFERENCED, "附件已被引用，禁止删除");
        }
        AttachmentAuditLog audit = new AttachmentAuditLog();
        audit.setOperatorId(operator.userId());
        audit.setAttachmentId(attachment.getId());
        audit.setFilename(attachment.getName());
        audit.setBizType(attachment.getBizType());
        audit.setIp(clientIp);
        audit.setReason(reason);
        auditLogRepository.save(audit);

        attachmentRepository.delete(attachment);
        storageService.deleteQuietly(attachment.getUrl());
    }

    // ---------- 内部 ----------

    private String extOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    /** PDF 魔数嗅探：合法 PDF 以 "%PDF-" 开头 */
    private boolean startsWithPdfMagic(byte[] data) {
        byte[] magic = {'%', 'P', 'D', 'F', '-'};
        if (data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /** 单文件上传拒绝（内部区分业务码，单文件直抛、多文件入 failed 列表） */
    private static class UploadRejectException extends RuntimeException {
        final ErrorCode errorCode;

        UploadRejectException(ErrorCode errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
    }
}
