package com.alumni.notifications.entity;

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
 * 站内消息（[M14] notifications 表）。created_at 由数据库默认值生成。
 *
 * <p>content 由服务端拼装，红线：不得包含学号。is_read 为 TINYINT(1)，
 * 属性命名 read（Jackson SNAKE_CASE 出参 "read"，与《API 规范》示例一致）。</p>
 *
 * <p>本模块不提供手动发送接口；review/announcement 类消息由对应业务模块共享库直写生成。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 接收人 users.id */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** review / announcement / todo */
    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, length = 64)
    private String title;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "biz_type", length = 32)
    private String bizType;

    @Column(name = "biz_id")
    private Long bizId;

    /** 已读标记（DB 列 is_read TINYINT(1)） */
    @Column(name = "is_read", nullable = false)
    private Boolean read = false;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
