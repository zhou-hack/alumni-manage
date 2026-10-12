package com.alumni.honors.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * [P1/M17] 荣誉大屏上下架变更日志（审计）。
 * 由 HonorScreenService.setStatus / batchSet 统一写入；不允许应用层直接 UPDATE honors.screen_status。
 */
@Getter
@Setter
@Entity(name = "honorsHonorScreenLog")
@Table(name = "honor_screen_log")
public class HonorScreenLog {

    /** 单条上架 */
    public static final String ACTION_ONLINE = "online";
    /** 单条下架 */
    public static final String ACTION_OFFLINE = "offline";
    /** 批量上架 */
    public static final String ACTION_BATCH_ONLINE = "batch_online";
    /** 批量下架 */
    public static final String ACTION_BATCH_OFFLINE = "batch_offline";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "honor_id", nullable = false)
    private Long honorId;

    @Column(name = "action", nullable = false, length = 16)
    private String action;

    @Column(name = "before_status", nullable = false, length = 16)
    private String beforeStatus;

    @Column(name = "after_status", nullable = false, length = 16)
    private String afterStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 64)
    private String operatorName;

    @Column(name = "operator_role", length = 32)
    private String operatorRole;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "batch_size")
    private Integer batchSize;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}