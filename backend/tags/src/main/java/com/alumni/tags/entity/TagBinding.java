package com.alumni.tags.entity;

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
 * 标签绑定（uk(tag_id, biz_type, biz_id)；biz_id 多态不设外键）。
 * created_at 由数据库默认值生成。
 */
@Getter
@Setter
@Entity
@Table(name = "tag_bindings")
public class TagBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    /** honor/archive/alumni（与标签域一一对应，跨域 400） */
    @Column(name = "biz_type", nullable = false, length = 16)
    private String bizType;

    @Column(name = "biz_id", nullable = false)
    private Long bizId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
