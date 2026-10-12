package com.alumni.archives.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 校友档案（单行 version 计数；版本历史经 reviews/review_logs 追溯）。
 */
@Getter
@Setter
@Entity
@Table(name = "archives")
public class Archive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    /** [P1/M13] 标题全拼（创建/修改时经 PinyinUtils 预生成） */
    @Column(name = "title_pinyin", length = 192)
    private String titlePinyin;

    /** [P1/M13] 标题拼音首字母（创建/修改时经 PinyinUtils 预生成） */
    @Column(name = "title_pinyin_abbr", length = 96)
    private String titlePinyinAbbr;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "remark")
    private String remark;

    /** draft/pending_counselor/pending_first/pending_second/approved/rejected/deleting/deleted */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "draft";

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    /** delete-request 前的原状态（archive_delete 驳回恢复用） */
    @Column(name = "prev_status", length = 16)
    private String prevStatus;

    /** Java 侧即时填值（同事务 detail 出参非 null）；DB 列默认值仍作双保险 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
