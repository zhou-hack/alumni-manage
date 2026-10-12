package com.alumni.reviews.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 审核日志条目（谁 / 何时 / 动作 / 原因）。
 * 每次创建 / 通过 / 驳回都会写入一条日志。
 * created_at 由数据库管理（DEFAULT CURRENT_TIMESTAMP）。
 */
@Getter
@Setter
@Entity
@Table(name = "review_logs")
public class ReviewLog {

    /** 动作常量：创建 */
    public static final String ACTION_CREATE = "create";
    /** 动作常量：通过 */
    public static final String ACTION_APPROVE = "approve";
    /** 动作常量：驳回 */
    public static final String ACTION_REJECT = "reject";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    /** 操作人；NULL=系统动作（如自动提交生成） */
    @Column(name = "operator_id")
    private Long operatorId;

    /** 动作：create / approve / reject */
    @Column(name = "action", nullable = false, length = 16)
    private String action;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
