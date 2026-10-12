package com.alumni.teachers.sync;

import com.alumni.teachers.entity.TeacherSyncLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 教师定时同步入口（app.teachers.sync.enabled=true 时装配；cron 走配置，默认每日 02:00）。
 * 异常仅记日志——失败结果已落 sync_logs，下一个周期自然重试。
 */
@Component
@ConditionalOnProperty(name = "app.teachers.sync.enabled", havingValue = "true")
public class TeacherSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(TeacherSyncScheduler.class);

    private final TeacherSyncService syncService;

    public TeacherSyncScheduler(TeacherSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(cron = "${app.teachers.sync.cron}")
    public void sync() {
        try {
            TeacherSyncService.SyncResult result = syncService.sync(TeacherSyncLog.TRIGGER_SCHEDULED, null, null, null);
            log.info("教师定时同步结束：batch={} result={}", result.batchId(), result.result());
        } catch (Exception e) {
            log.error("教师定时同步异常", e);
        }
    }
}
