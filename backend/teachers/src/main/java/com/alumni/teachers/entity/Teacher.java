package com.alumni.teachers.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 教师库（内网同步，云端为准全量覆盖）。
 * 组织架构：教师唯一归属一个专业（一对一）；college_id/major_id 关联 dicts.id 但不设外键，
 * 映射失败时保留 NULL + 原文快照（college_name/major_name），不阻断同步。
 * 云端删除的教师置 status=disabled 软停用，不物理删（保留历史关联）。
 * 时间列 created_at/updated_at 由数据库管理；last_synced_at 由同步流程写入。
 */
@Getter
@Setter
@Entity
@Table(name = "teachers")
public class Teacher {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DISABLED = "disabled";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 工号（同步 upsert 唯一键） */
    @Column(name = "teacher_no", nullable = false, length = 32)
    private String teacherNo;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 院系 dicts.id（映射失败为 NULL，无外键） */
    @Column(name = "college_id")
    private Long collegeId;

    /** 专业 dicts.id（教师唯一归属一个专业；映射失败为 NULL） */
    @Column(name = "major_id")
    private Long majorId;

    /** 院系原文快照（内网数据，展示兜底） */
    @Column(name = "college_name", length = 64)
    private String collegeName;

    /** 专业原文快照（内网数据，展示兜底） */
    @Column(name = "major_name", length = 64)
    private String majorName;

    /** 职称（选填） */
    @Column(name = "title", length = 64)
    private String title;

    /** active / disabled */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 内网系统外部 ID */
    @Column(name = "external_id", length = 64)
    private String externalId;

    /** 最近同步批次 UUID */
    @Column(name = "last_sync_batch", length = 36)
    private String lastSyncBatch;

    /** 最近同步时间（同步流程写入） */
    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, updatable = false, insertable = false)
    private Instant updatedAt;
}
