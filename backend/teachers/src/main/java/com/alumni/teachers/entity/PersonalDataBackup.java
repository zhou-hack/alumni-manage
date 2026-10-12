package com.alumni.teachers.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 个人数据同步前全量快照（行 JSON，对表结构演进免疫）。
 * 每次教师同步前将 teachers 全量快照入库；snapshot_date 留存 7 天，同步成功后滚动删除更早快照。
 */
@Getter
@Setter
@Entity
@Table(name = "personal_data_backup")
public class PersonalDataBackup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 来源表（本期恒 teachers） */
    @Column(name = "source_table", nullable = false, length = 64)
    private String sourceTable;

    /** 快照日期（7 天滚动删除依据） */
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    /** 同步批次 UUID */
    @Column(name = "batch_id", nullable = false, length = 36)
    private String batchId;

    /** 来源表行主键 */
    @Column(name = "row_id", nullable = false)
    private Long rowId;

    /** 行全量 JSON 快照 */
    @Column(name = "row_json", nullable = false, columnDefinition = "JSON")
    private String rowJson;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
