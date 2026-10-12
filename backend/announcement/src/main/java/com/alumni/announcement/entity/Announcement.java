package com.alumni.announcement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 公告（draft → published ↔ withdrawn）。created_at 由数据库默认值生成。
 */
@Getter
@Setter
@Entity
@Table(name = "announcements")
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String title;

    /** 公告内容（支持 Markdown，上限 10MB；MEDIUMTEXT） */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    /** 封面图（静态资源 URL，可空） */
    @Column(name = "cover_image", length = 512)
    private String coverImage;

    /** draft / published / withdrawn */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
