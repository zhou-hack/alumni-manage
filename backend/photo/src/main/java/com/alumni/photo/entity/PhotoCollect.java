package com.alumni.photo.entity;

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
 * 校友照片收集记录（photo_collect）。
 *
 * <p>状态机：
 * <ul>
 *   <li>pending — 待辅导员/校级审核</li>
 *   <li>approved — 通过审核，可推送</li>
 *   <li>rejected — 驳回（记录原因），提交人可删除/重新提交</li>
 *   <li>pushed — 已推送过（pushed_count>0 至少一次）</li>
 * </ul>
 *
 * <p>tags 字段为 JSON 字符串（前端按 dicts.photo_tag 字典翻译），由 Service 序列化。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "photo_collect")
public class PhotoCollect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "attachment_id", nullable = false)
    private Long attachmentId;

    @Column(length = 128)
    private String title;

    @Column(length = 500)
    private String caption;

    /** JSON 数组字符串，如 ["graduation_2020","sports_meet"] */
    @Column(nullable = false, columnDefinition = "json")
    private String tags;

    @Column(nullable = false, length = 16)
    private String status = "pending";

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "pushed_count", nullable = false)
    private Integer pushedCount = 0;

    @Column(name = "last_push_at")
    private Instant lastPushAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
