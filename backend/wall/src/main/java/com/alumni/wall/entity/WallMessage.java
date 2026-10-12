package com.alumni.wall.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 校友留言（UGC，pending → approved / rejected，走管理员审核通道）。
 * 姓名/届次/学院不落库——展示时经 user_id ⋈ users ⋈ alumni_base 联查得出，
 * 用户改昵称/档案后留言墙自动更新，杜绝假身份。
 */
@Getter
@Setter
@Entity
@Table(name = "wall_message")
public class WallMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 留言人 users.id（登录态取，客户端不可传） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 留言正文（前端限 40 字，DB 留冗余至 120，防绕过前端直调接口） */
    @Column(nullable = false, length = 120)
    private String content;

    /** 是否在公开墙显示姓名（false=匿名，提交人勾选决定，默认匿名） */
    @Column(name = "show_name", nullable = false)
    private Boolean showName;

    /** pending=待审核 approved=已通过 rejected=已驳回 */
    @Column(nullable = false, length = 16)
    private String status;

    /** 驳回原因（展示给留言人） */
    @Column(name = "review_note", length = 255)
    private String reviewNote;

    /** 审核人 users.id */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** 置顶（预留，false=普通） */
    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned;

    /** 提交 IP（风控用，任何接口不返回给前端） */
    @Column(name = "ip_addr", length = 45)
    private String ipAddr;

    /** 软删除时间戳（NULL = 未删；非 NULL = 留言人主动撤回 / 删除）；
     *  软删保留行供「重新发送」时按 user_id + content 重建一条 pending 留言；
     *  管理端物理删除另走 adminDelete()。 */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
