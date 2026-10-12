package com.alumni.showcase.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 授权记录（撤回=删行并联动下架）。created_at 由数据库默认值生成。
 */
@Getter
@Setter
@Entity(name = "showcaseConsent")
@Table(name = "consents")
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "archive_id", nullable = false)
    private Long archiveId;

    /** screen（大屏）/site（站点，预留） */
    @Column(name = "scope", nullable = false, length = 16)
    private String scope;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
