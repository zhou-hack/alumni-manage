package com.alumni.attachments.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 附件（跨模块复用）：上传后为 unreferenced，业务提交时由业务方回填 ref_id 转为 referenced。
 */
@Getter
@Setter
@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "biz_type", nullable = false, length = 32)
    private String bizType;

    /** 业务记录 ID，NULL=未引用（孤儿附件定时清理依据） */
    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "url", nullable = false, length = 255)
    private String url;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "size", nullable = false)
    private Long size;

    @Column(name = "mime", nullable = false, length = 64)
    private String mime;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "referenced_at")
    private Instant referencedAt;
}
