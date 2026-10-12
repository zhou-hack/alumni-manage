package com.alumni.teachers.sync;

import com.alumni.teachers.common.BizException;
import com.alumni.teachers.common.ErrorCode;
import com.alumni.teachers.entity.PersonalDataBackup;
import com.alumni.teachers.entity.Teacher;
import com.alumni.teachers.entity.TeacherSyncLog;
import com.alumni.teachers.repository.PersonalDataBackupRepository;
import com.alumni.teachers.repository.TeacherRepository;
import com.alumni.teachers.repository.TeacherSyncLogRepository;
import com.alumni.teachers.sync.TeacherFieldMapping.TeacherRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

/**
 * 教师同步编排（云端为准全量覆盖）。三段式：
 * <ol>
 *   <li>事务外拉取内网数据——失败仅记 sync_logs(failed)，绝不清库；空数据同样中止保护本地</li>
 *   <li>REQUIRES_NEW 独立事务快照 teachers 全量到 personal_data_backup（主事务回滚也有备份）</li>
 *   <li>主事务按工号 upsert（新增/覆盖为 active）+ 云端消失置 disabled + 7 天滚动清理 + sync_logs(success)</li>
 * </ol>
 * Redis SETNX 锁防并发重入（手动重入 409）；手动触发追加 audit_logs。
 * 事务用 TransactionTemplate 编程式控制，规避自调用代理失效。
 */
@Service
public class TeacherSyncService {

    private static final Logger log = LoggerFactory.getLogger(TeacherSyncService.class);

    private static final String LOCK_KEY = "teacher:sync:lock";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);
    private static final String SOURCE_TABLE = "teachers";
    private static final int BACKUP_RETAIN_DAYS = 7;

    private final TeacherRepository teacherRepository;
    private final PersonalDataBackupRepository backupRepository;
    private final TeacherSyncLogRepository syncLogRepository;
    private final TeacherDataSource dataSource;
    private final TeacherOrgResolver orgResolver;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;
    private final TransactionTemplate txNewTemplate;

    public TeacherSyncService(TeacherRepository teacherRepository,
                              PersonalDataBackupRepository backupRepository,
                              TeacherSyncLogRepository syncLogRepository,
                              TeacherDataSource dataSource,
                              TeacherOrgResolver orgResolver,
                              JdbcTemplate jdbcTemplate,
                              StringRedisTemplate redis,
                              ObjectMapper objectMapper,
                              PlatformTransactionManager txManager) {
        this.teacherRepository = teacherRepository;
        this.backupRepository = backupRepository;
        this.syncLogRepository = syncLogRepository;
        this.dataSource = dataSource;
        this.orgResolver = orgResolver;
        this.jdbcTemplate = jdbcTemplate;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.txTemplate = new TransactionTemplate(txManager);
        this.txNewTemplate = new TransactionTemplate(txManager);
        this.txNewTemplate.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
    }

    /** 同步结果（手动触发直接回给前端） */
    public record SyncResult(String batchId, String result, int fetched, int added, int updated,
                             int disabled, String errorMessage) {
    }

    public SyncResult sync(String triggerType, Long operatorId, String operatorRole, String clientIp) {
        String batchId = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, batchId, LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BizException(ErrorCode.INVALID_STATE, "同步正在进行中，请稍后重试");
        }
        Instant startedAt = Instant.now();
        try {
            List<TeacherRecord> records;
            try {
                records = fetchRecords();
            } catch (Exception e) {
                String message = e instanceof BizException be ? be.getMessage() : "数据源拉取失败：" + e.getMessage();
                log.warn("教师同步拉取失败：batch={}", batchId, e);
                writeLog(batchId, triggerType, operatorId, TeacherSyncLog.RESULT_FAILED,
                        0, 0, 0, 0, message, startedAt);
                audit(triggerType, operatorId, operatorRole, clientIp,
                        detail(batchId, TeacherSyncLog.RESULT_FAILED, 0, 0, 0, 0));
                return new SyncResult(batchId, TeacherSyncLog.RESULT_FAILED, 0, 0, 0, 0, message);
            }

            // 快照：独立事务（REQUIRES_NEW），主事务回滚也保留备份
            txNewTemplate.executeWithoutResult(s -> snapshot(batchId));

            // 主事务：upsert + 置停用 + 7 天清理 + 成功日志
            int[] counts = txTemplate.execute(s -> {
                int[] c = applySync(batchId, records);
                backupRepository.deleteOlderThan(LocalDate.now().minusDays(BACKUP_RETAIN_DAYS));
                persistLog(batchId, triggerType, operatorId, TeacherSyncLog.RESULT_SUCCESS,
                        records.size(), c[0], c[1], c[2], null, startedAt);
                return c;
            });

            log.info("教师同步完成：batch={} fetched={} added={} updated={} disabled={}",
                    batchId, records.size(), counts[0], counts[1], counts[2]);
            audit(triggerType, operatorId, operatorRole, clientIp,
                    detail(batchId, TeacherSyncLog.RESULT_SUCCESS, records.size(), counts[0], counts[1], counts[2]));
            return new SyncResult(batchId, TeacherSyncLog.RESULT_SUCCESS,
                    records.size(), counts[0], counts[1], counts[2], null);
        } finally {
            redis.delete(LOCK_KEY);
        }
    }

    /** 拉取并抽取为 TeacherRecord；缺工号/姓名的行跳过；空数据中止（防接口异常清空本地） */
    private List<TeacherRecord> fetchRecords() {
        List<Map<String, Object>> rows = dataSource.fetchTeachers();
        List<TeacherRecord> records = new ArrayList<>();
        int skipped = 0;
        for (Map<String, Object> row : rows) {
            TeacherRecord record = TeacherRecord.from(row);
            if (record == null) {
                skipped++;
            } else {
                records.add(record);
            }
        }
        if (skipped > 0) {
            log.warn("教师同步：{} 行缺少工号/姓名已跳过", skipped);
        }
        if (records.isEmpty()) {
            throw new BizException(ErrorCode.UPSTREAM_ERROR, "上游返回空数据，已中止同步以保护本地数据");
        }
        return records;
    }

    /** 快照 teachers 全量（行 JSON） */
    private void snapshot(String batchId) {
        List<Teacher> all = teacherRepository.findAll();
        if (all.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        List<PersonalDataBackup> snapshots = new ArrayList<>(all.size());
        for (Teacher t : all) {
            PersonalDataBackup b = new PersonalDataBackup();
            b.setSourceTable(SOURCE_TABLE);
            b.setSnapshotDate(today);
            b.setBatchId(batchId);
            b.setRowId(t.getId());
            b.setRowJson(toRowJson(t));
            snapshots.add(b);
        }
        backupRepository.saveAll(snapshots);
        log.info("教师同步快照完成：batch={} rows={}", batchId, snapshots.size());
    }

    /** 主事务体：按工号 upsert + 云端消失置停用，返回 {added, updated, disabled} */
    private int[] applySync(String batchId, List<TeacherRecord> records) {
        Map<String, Teacher> remaining = teacherRepository.findAll().stream()
                .collect(Collectors.toMap(Teacher::getTeacherNo, Function.identity(), (a, b) -> a));
        Instant now = Instant.now();
        List<Teacher> touched = new ArrayList<>();
        int added = 0;
        int updated = 0;
        for (TeacherRecord record : records) {
            TeacherOrgResolver.OrgIds org = orgResolver.resolve(record.college(), record.major());
            Teacher teacher = remaining.remove(record.teacherNo());
            if (teacher == null) {
                teacher = new Teacher();
                teacher.setTeacherNo(record.teacherNo());
                added++;
            } else {
                updated++;
            }
            teacher.setName(record.name());
            teacher.setCollegeId(org.collegeId());
            teacher.setMajorId(org.majorId());
            teacher.setCollegeName(record.college());
            teacher.setMajorName(record.major());
            teacher.setTitle(record.title());
            teacher.setExternalId(record.externalId());
            teacher.setStatus(Teacher.STATUS_ACTIVE);
            teacher.setLastSyncBatch(batchId);
            teacher.setLastSyncedAt(now);
            touched.add(teacher);
        }
        int disabled = 0;
        for (Teacher missing : remaining.values()) {
            if (Teacher.STATUS_ACTIVE.equals(missing.getStatus())) {
                missing.setStatus(Teacher.STATUS_DISABLED);
                missing.setLastSyncBatch(batchId);
                missing.setLastSyncedAt(now);
                touched.add(missing);
                disabled++;
            }
        }
        teacherRepository.saveAll(touched);
        return new int[]{added, updated, disabled};
    }

    private String toRowJson(Teacher t) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", t.getId());
        row.put("teacher_no", t.getTeacherNo());
        row.put("name", t.getName());
        row.put("college_id", t.getCollegeId());
        row.put("major_id", t.getMajorId());
        row.put("college_name", t.getCollegeName());
        row.put("major_name", t.getMajorName());
        row.put("title", t.getTitle());
        row.put("status", t.getStatus());
        row.put("external_id", t.getExternalId());
        row.put("last_sync_batch", t.getLastSyncBatch());
        row.put("last_synced_at", t.getLastSyncedAt() == null ? null : t.getLastSyncedAt().toString());
        try {
            return objectMapper.writeValueAsString(row);
        } catch (Exception e) {
            throw new IllegalStateException("教师快照序列化失败", e);
        }
    }

    /** 独立事务写同步日志（拉取失败场景无外围事务） */
    private void writeLog(String batchId, String triggerType, Long operatorId, String result,
                          int fetched, int added, int updated, int disabled, String errorMessage,
                          Instant startedAt) {
        txNewTemplate.executeWithoutResult(
                s -> persistLog(batchId, triggerType, operatorId, result,
                        fetched, added, updated, disabled, errorMessage, startedAt));
    }

    private void persistLog(String batchId, String triggerType, Long operatorId, String result,
                            int fetched, int added, int updated, int disabled, String errorMessage,
                            Instant startedAt) {
        TeacherSyncLog entry = new TeacherSyncLog();
        entry.setBatchId(batchId);
        entry.setTriggerType(triggerType);
        entry.setOperatorId(operatorId);
        entry.setResult(result);
        entry.setFetchedCount(fetched);
        entry.setAddedCount(added);
        entry.setUpdatedCount(updated);
        entry.setDisabledCount(disabled);
        entry.setErrorMessage(errorMessage == null ? null
                : errorMessage.substring(0, Math.min(errorMessage.length(), 500)));
        entry.setStartedAt(startedAt);
        entry.setFinishedAt(Instant.now());
        syncLogRepository.save(entry);
    }

    /** 手动触发追加审计（失败不影响主流程） */
    private void audit(String triggerType, Long operatorId, String operatorRole, String clientIp, String detail) {
        if (!TeacherSyncLog.TRIGGER_MANUAL.equals(triggerType)) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO audit_logs (user_id, user_role, action, biz_type, biz_id, detail, ip)"
                            + " VALUES (?, ?, 'teacher_sync', 'teacher', NULL, ?, ?)",
                    operatorId, operatorRole, detail, clientIp);
        } catch (Exception e) {
            log.error("教师同步审计写入失败：operator={}", operatorId, e);
        }
    }

    private String detail(String batchId, String result, int fetched, int added, int updated, int disabled) {
        return "{\"batch_id\":\"" + batchId + "\",\"result\":\"" + result
                + "\",\"fetched\":" + fetched + ",\"added\":" + added
                + ",\"updated\":" + updated + ",\"disabled\":" + disabled + "}";
    }
}
