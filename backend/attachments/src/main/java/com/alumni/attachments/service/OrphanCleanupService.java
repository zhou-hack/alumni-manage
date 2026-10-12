package com.alumni.attachments.service;

import com.alumni.attachments.entity.Attachment;
import com.alumni.attachments.repository.AttachmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 孤儿附件清理（API 规范 6.1）：unreferenced 状态超过 24 小时的附件定时删除（文件+记录）。
 */
@Service
public class OrphanCleanupService {

    private static final Logger log = LoggerFactory.getLogger(OrphanCleanupService.class);

    private final AttachmentRepository attachmentRepository;
    private final FileStorageService storageService;
    private final long retentionHours;

    public OrphanCleanupService(AttachmentRepository attachmentRepository,
                                FileStorageService storageService,
                                @Value("${app.storage.orphan-retention-hours:24}") long retentionHours) {
        this.attachmentRepository = attachmentRepository;
        this.storageService = storageService;
        this.retentionHours = retentionHours;
    }

    /** 每小时执行一次 */
    @Scheduled(fixedDelay = 3600_000, initialDelay = 60_000)
    @Transactional
    public void cleanup() {
        Instant before = Instant.now().minus(retentionHours, ChronoUnit.HOURS);
        List<Attachment> orphans = attachmentRepository.findByStatusAndCreatedAtBefore("unreferenced", before);
        if (orphans.isEmpty()) {
            return;
        }
        for (Attachment orphan : orphans) {
            storageService.deleteQuietly(orphan.getUrl());
            attachmentRepository.delete(orphan);
        }
        log.info("孤儿附件清理完成：删除 {} 个（unreferenced 超过 {}h）", orphans.size(), retentionHours);
    }
}
