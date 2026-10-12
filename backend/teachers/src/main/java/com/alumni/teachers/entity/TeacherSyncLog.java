package com.alumni.teachers.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 教师同步记录（定时/手动每次一条）：成败、拉取与增改停计数、失败原因。
 * started_at/finished_at 由同步流程写入（非数据库默认值，便于记录真实起止）。
 */
@Getter
@Setter
@Entity
@Table(name = "teacher_sync_logs")
public class TeacherSyncLog {

    public static final String TRIGGER_SCHEDULED = "scheduled";
    public static final String TRIGGER_MANUAL = "manual";
    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAILED = "failed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 同步批次 UUID */
    @Column(name = "batch_id", nullable = false, length = 36)
    private String batchId;

    /** scheduled / manual */
    @Column(name = "trigger_type", nullable = false, length = 16)
    private String triggerType;

    /** 手动触发人 users.id（定时为 NULL） */
    @Column(name = "operator_id")
    private Long operatorId;

    /** success / failed */
    @Column(name = "result", nullable = false, length = 16)
    private String result;

    @Column(name = "fetched_count", nullable = false)
    private Integer fetchedCount;

    @Column(name = "added_count", nullable = false)
    private Integer addedCount;

    @Column(name = "updated_count", nullable = false)
    private Integer updatedCount;

    @Column(name = "disabled_count", nullable = false)
    private Integer disabledCount;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
