package com.alumni.drafts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * 草稿（服务端兜底，版本号乐观锁）：(user_id, biz_type) 唯一，一行一业务草稿。
 * payload 为前端表单原文（title/content/attachment_ids 等），键名原样保留。
 */
@Getter
@Setter
@Entity
@Table(name = "drafts")
public class Draft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** archive/……（随业务扩充） */
    @Column(name = "biz_type", nullable = false, length = 32)
    private String bizType;

    /** 乐观锁：提交版本 = 服务端版本才保存并 +1 */
    @Column(name = "version", nullable = false)
    private Integer version;

    /** 草稿内容（JSON 列，Map 键即前端字段名，不走 SNAKE_CASE 转换） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private Map<String, Object> payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
