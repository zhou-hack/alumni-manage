package com.alumni.attachments.service;

import com.alumni.attachments.common.BizException;
import com.alumni.attachments.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 文件存储：{storage_root}/{biz_type}/yyyy/MM/{uuid}.{ext}
 * DB 存相对路径 /files/...，文件只经 preview 接口鉴权后流出。
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path root;

    public FileStorageService(@Value("${app.storage.root}") String rootDir) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
    }

    /** 保存字节到业务目录，返回 DB 存储的相对 url */
    public String save(String bizType, String ext, byte[] data) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        String dir = String.format("%s/%d/%02d", bizType, now.getYear(), now.getMonthValue());
        String filename = UUID.randomUUID() + "." + ext;
        Path target = root.resolve(dir).resolve(filename);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件写入失败");
        }
        return "/files/" + dir + "/" + filename;
    }

    /** 由 DB url 解析磁盘路径（防路径穿越） */
    public Path resolve(String url) {
        String relative = url.startsWith("/files/") ? url.substring(7) : url;
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        return path;
    }

    /**
     * 删除磁盘文件。失败仅告警不阻断：调用方（DB 删除补偿/记录删除）已先行处理 DB 侧，
     * 残留文件需巡检清理，不做静默吞没。
     */
    public void deleteQuietly(String url) {
        try {
            Files.deleteIfExists(resolve(url));
        } catch (Exception e) {
            log.warn("磁盘文件删除失败，文件可能残留：url={}，原因={}", url, e.getMessage());
        }
    }
}
