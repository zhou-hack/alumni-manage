package com.alumni.notifications.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * 外部消息订阅（[M14] notification_subscriptions 表）：(user_id, channel) 唯一，一行一渠道。
 * 默认全关（最小打扰原则）；订阅/退订即时生效。created_at / updated_at 由数据库维护。
 */
@Getter
@Setter
@Entity
@Table(name = "notification_subscriptions")
public class NotificationSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** sms / email */
    @Column(nullable = false, length = 8)
    private String channel;

    @Column(nullable = false)
    private Boolean enabled = false;

    /** 订阅场景 ["review_result","announcement"]（JSON 列） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> scenes;

    /** email 地址（sms 沿用账号绑定手机号，不存地址） */
    @Column(length = 128)
    private String address;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
