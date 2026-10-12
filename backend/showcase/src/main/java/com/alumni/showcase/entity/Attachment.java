package com.alumni.showcase.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 附件（本模块仅读取封面选取所需列）。
 */
@Getter
@Setter
@Entity(name = "showcaseAttachment")
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** archive/honor/avatar/activate……目录隔离 */
    @Column(name = "biz_type", nullable = false, length = 32)
    private String bizType;

    /** 业务记录 ID，NULL=未引用 */
    @Column(name = "ref_id")
    private Long refId;

    /** unreferenced/referenced */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "mime", nullable = false, length = 64)
    private String mime;

    @Column(name = "referenced_at")
    private Instant referencedAt;
}
