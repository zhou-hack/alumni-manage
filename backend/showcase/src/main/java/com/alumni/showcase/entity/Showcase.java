package com.alumni.showcase.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 上架记录（行存在=上架中，下架=删行）。
 * published_at 由数据库默认值生成。
 */
@Getter
@Setter
@Entity
@Table(name = "showcase")
public class Showcase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "archive_id", nullable = false)
    private Long archiveId;

    @Column(name = "cover_attachment_id")
    private Long coverAttachmentId;

    @Column(name = "published_by", nullable = false)
    private Long publishedBy;

    @Column(name = "published_at", nullable = false, insertable = false, updatable = false)
    private Instant publishedAt;
}
