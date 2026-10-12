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
 * 公告图片静态资源（文件落盘 {storage.root}/announcement/{uuid}.{ext}，本表存元数据）。
 */
@Getter
@Setter
@Entity
@Table(name = "announcement_image")
public class AnnouncementImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对外资源标识（URL 使用） */
    @Column(nullable = false, length = 36)
    private String uuid;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
