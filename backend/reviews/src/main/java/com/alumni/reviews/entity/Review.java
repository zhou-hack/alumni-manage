package com.alumni.reviews.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 审核记录。Review 是独立资源，id 为审核记录 ID（非业务 ID）。
 * 档案审核、荣誉审核、删除审核、激活人工核验共用本通道。
 * 时间列由数据库管理（DEFAULT / ON UPDATE CURRENT_TIMESTAMP）。
 */
@Getter
@Setter
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务类型：archive / honor / archive_delete / activate_manual */
    @Column(name = "biz_type", nullable = false, length = 32)
    private String bizType;

    /** 业务记录 ID（多态，不设外键） */
    @Column(name = "biz_id")
    private Long bizId;

    /** 业务提交人（审核日志对本人可见用） */
    @Column(name = "submitter_id")
    private Long submitterId;

    /** 审核阶段：first（初审）/ second（复核）；activate_manual 仅 first 单级 */
    @Column(name = "stage", nullable = false, length = 16)
    private String stage;

    /** 审核状态：pending / approved / rejected */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 最近一次操作的审核人 */
    @Column(name = "reviewer_id")
    private Long reviewerId;

    /** 驳回原因（reject 必填） */
    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, updatable = false, insertable = false)
    private Instant updatedAt;
}
