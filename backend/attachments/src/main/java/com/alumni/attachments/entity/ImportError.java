package com.alumni.attachments.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 导入错误行报告（行号 + 学号 + 原因）。
 */
@Getter
@Setter
@Entity
@Table(name = "import_errors")
public class ImportError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_id", nullable = false)
    private Long importId;

    @Column(name = "row_no", nullable = false)
    private Integer rowNo;

    @Column(name = "student_id", length = 32)
    private String studentId;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
