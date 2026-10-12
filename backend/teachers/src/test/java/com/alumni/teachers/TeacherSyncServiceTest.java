package com.alumni.teachers;

import com.alumni.teachers.common.BizException;
import com.alumni.teachers.common.ErrorCode;
import com.alumni.teachers.entity.PersonalDataBackup;
import com.alumni.teachers.entity.Teacher;
import com.alumni.teachers.entity.TeacherSyncLog;
import com.alumni.teachers.repository.PersonalDataBackupRepository;
import com.alumni.teachers.repository.TeacherRepository;
import com.alumni.teachers.repository.TeacherSyncLogRepository;
import com.alumni.teachers.sync.TeacherDataSource;
import com.alumni.teachers.sync.TeacherFieldMapping;
import com.alumni.teachers.sync.TeacherOrgResolver;
import com.alumni.teachers.sync.TeacherSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TeacherSyncService 单元测试（纯 Mockito + ResourcelessTransactionManager 空事务管理器，不起容器）。
 * 覆盖：拉取失败不清库 / 空数据中止 / 成功 upsert+置停用+快照+7天清理 / 并发锁重入 409 / 组织映射命中与落空。
 */
@ExtendWith(MockitoExtension.class)
class TeacherSyncServiceTest {

    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private PersonalDataBackupRepository backupRepository;
    @Mock
    private TeacherSyncLogRepository syncLogRepository;
    @Mock
    private TeacherDataSource dataSource;
    @Mock
    private TeacherOrgResolver orgResolver;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations valueOperations;

    private TeacherSyncService syncService;

    /** 空事务管理器桩：TransactionTemplate 回调直接执行，不产生真实事务（纯单测用） */
    private static final PlatformTransactionManager NOOP_TX = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    };

    @BeforeEach
    void setUp() {
        syncService = new TeacherSyncService(teacherRepository, backupRepository, syncLogRepository,
                dataSource, orgResolver, jdbcTemplate, redis, new ObjectMapper(), NOOP_TX);
    }

    private void lockAcquired() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("teacher:sync:lock"), anyString(), any())).thenReturn(true);
    }

    private Map<String, Object> row(String no, String name, String college, String major) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(TeacherFieldMapping.TEACHER_NO, no);
        row.put(TeacherFieldMapping.NAME, name);
        row.put(TeacherFieldMapping.COLLEGE, college);
        row.put(TeacherFieldMapping.MAJOR, major);
        return row;
    }

    private Teacher teacher(String no, String status) {
        Teacher t = new Teacher();
        t.setTeacherNo(no);
        t.setName("姓名" + no);
        t.setStatus(status);
        return t;
    }

    @Test
    void 拉取失败_不清库_记失败日志() {
        lockAcquired();
        when(dataSource.fetchTeachers()).thenThrow(new BizException(ErrorCode.UPSTREAM_ERROR, "内网教师接口拉取失败"));

        TeacherSyncService.SyncResult result = syncService.sync(TeacherSyncLog.TRIGGER_SCHEDULED, null, null, null);

        assertEquals(TeacherSyncLog.RESULT_FAILED, result.result());
        assertEquals("内网教师接口拉取失败", result.errorMessage());
        verify(teacherRepository, never()).saveAll(anyList());
        verify(backupRepository, never()).saveAll(anyList());
        ArgumentCaptor<TeacherSyncLog> captor = ArgumentCaptor.forClass(TeacherSyncLog.class);
        verify(syncLogRepository).save(captor.capture());
        assertEquals(TeacherSyncLog.RESULT_FAILED, captor.getValue().getResult());
        verify(redis).delete("teacher:sync:lock");
    }

    @Test
    void 空数据_中止同步保护本地() {
        lockAcquired();
        when(dataSource.fetchTeachers()).thenReturn(List.of());

        TeacherSyncService.SyncResult result = syncService.sync(TeacherSyncLog.TRIGGER_SCHEDULED, null, null, null);

        assertEquals(TeacherSyncLog.RESULT_FAILED, result.result());
        assertTrue(result.errorMessage().contains("空数据"));
        verify(teacherRepository, never()).saveAll(anyList());
    }

    @Test
    void 成功同步_新增更新置停用_快照与七天清理() {
        lockAcquired();
        // 拉取：T001（库中已有，更新）+ T003（新增）；库中 T002 active 但本次未出现（置停用）
        when(dataSource.fetchTeachers()).thenReturn(List.of(
                row("T001", "王建国", "数据产业学院", "大数据技术"),
                row("T003", "张伟", "信息工程学院", "计算机应用技术")));
        when(orgResolver.resolve(any(), any())).thenReturn(new TeacherOrgResolver.OrgIds(1L, 11L));
        Teacher existing1 = teacher("T001", Teacher.STATUS_ACTIVE);
        Teacher existing2 = teacher("T002", Teacher.STATUS_ACTIVE);
        when(teacherRepository.findAll()).thenReturn(new ArrayList<>(List.of(existing1, existing2)));

        TeacherSyncService.SyncResult result = syncService.sync(TeacherSyncLog.TRIGGER_MANUAL, 2L, "system_admin", "127.0.0.1");

        assertEquals(TeacherSyncLog.RESULT_SUCCESS, result.result());
        assertEquals(2, result.fetched());
        assertEquals(1, result.added());
        assertEquals(1, result.updated());
        assertEquals(1, result.disabled());
        assertEquals(Teacher.STATUS_DISABLED, existing2.getStatus());
        assertEquals(Teacher.STATUS_ACTIVE, existing1.getStatus());
        // 快照：findAll 全量行写入 personal_data_backup
        ArgumentCaptor<List<PersonalDataBackup>> snapshotCaptor = ArgumentCaptor.forClass(List.class);
        verify(backupRepository).saveAll(snapshotCaptor.capture());
        assertEquals(2, snapshotCaptor.getValue().size());
        assertEquals("teachers", snapshotCaptor.getValue().get(0).getSourceTable());
        // 7 天滚动清理
        verify(backupRepository).deleteOlderThan(any());
        // 主事务保存 3 行（更新 1 + 新增 1 + 停用 1）
        ArgumentCaptor<List<Teacher>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(teacherRepository).saveAll(saveCaptor.capture());
        assertEquals(3, saveCaptor.getValue().size());
        // 手动触发写审计
        verify(jdbcTemplate).update(contains("teacher_sync"), eq(2L), eq("system_admin"), anyString(), eq("127.0.0.1"));
        verify(redis).delete("teacher:sync:lock");
    }

    @Test
    void 并发重入_锁未获取_抛409() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("teacher:sync:lock"), anyString(), any())).thenReturn(false);

        BizException e = assertThrows(BizException.class,
                () -> syncService.sync(TeacherSyncLog.TRIGGER_MANUAL, 2L, "system_admin", null));
        assertEquals(ErrorCode.INVALID_STATE, e.getErrorCode());
        verifyNoInteractions(dataSource);
    }

    @Test
    void 缺工号或姓名的行_跳过不阻断() {
        lockAcquired();
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put(TeacherFieldMapping.NAME, "缺工号");
        when(dataSource.fetchTeachers()).thenReturn(List.of(bad, row("T001", "王建国", "数据产业学院", "大数据技术")));
        when(orgResolver.resolve(any(), any())).thenReturn(new TeacherOrgResolver.OrgIds(null, null));
        when(teacherRepository.findAll()).thenReturn(new ArrayList<>());

        TeacherSyncService.SyncResult result = syncService.sync(TeacherSyncLog.TRIGGER_SCHEDULED, null, null, null);

        assertEquals(TeacherSyncLog.RESULT_SUCCESS, result.result());
        assertEquals(1, result.fetched());
        assertEquals(1, result.added());
    }

    @Test
    void 组织映射_命中与落空() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TeacherOrgResolver resolver = new TeacherOrgResolver(jdbc);
        // 命中：college -> 1，major -> 11
        when(jdbc.queryForList(contains("type = 'college'"), eq(Long.class), any(Object.class)))
                .thenReturn(List.of(1L));
        when(jdbc.queryForList(contains("type = 'major'"), eq(Long.class), any(Object.class), any(Object.class)))
                .thenReturn(List.of(11L));
        TeacherOrgResolver.OrgIds hit = resolver.resolve("数据产业学院", "大数据技术");
        assertEquals(1L, hit.collegeId());
        assertEquals(11L, hit.majorId());

        // 落空：college 查无 -> 全部 null
        when(jdbc.queryForList(contains("type = 'college'"), eq(Long.class), any(Object.class)))
                .thenReturn(List.of());
        TeacherOrgResolver.OrgIds miss = resolver.resolve("不存在的学院", "不存在的专业");
        assertNull(miss.collegeId());
        assertNull(miss.majorId());
    }
}
