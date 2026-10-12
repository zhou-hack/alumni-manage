package com.alumni.attachments.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 附件删除审计（只增不删，保留 ≥180 天）。
 */
@Getter
@Setter
@Entity
@Table(name = "attachment_audit_log")
public class AttachmentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "attachment_id", nullable = false)
    private Long attachmentId;

    @Column(name = "filename", nullable = false, length = 128)
    private String filename;

    @Column(name = "biz_type", nullable = false, length = 32)
    private String bizType;

    @Column(name = "ip", nullable = false, length = 45)
    private String ip;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
