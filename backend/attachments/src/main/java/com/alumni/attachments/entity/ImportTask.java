package com.alumni.attachments.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 批量导入任务（规范第十一章；坏行不阻断好行，合法行入 alumni_base）。
 */
@Getter
@Setter
@Entity
@Table(name = "import_tasks")
public class ImportTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "filename", nullable = false, length = 128)
    private String filename;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** processing / done */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "processing";

    @Column(name = "total", nullable = false)
    private Integer total = 0;

    @Column(name = "success", nullable = false)
    private Integer success = 0;

    @Column(name = "failed", nullable = false)
    private Integer failed = 0;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
