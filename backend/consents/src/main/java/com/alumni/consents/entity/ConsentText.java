package com.alumni.consents.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 授权文本（[P1/M16] 版本化：文本变更必须升版本，旧授权记录保留原版本留证）。
 * created_at 由数据库默认值生成。
 */
@Getter
@Setter
@Entity
@Table(name = "consent_texts")
public class ConsentText {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 版本号（单调递增，uk_consent_texts_version 唯一） */
    @Column(name = "version", nullable = false)
    private Integer version;

    /** 授权文本全文（前端授权弹窗展示） */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
