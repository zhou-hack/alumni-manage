package com.alumni.admin.entity;

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
import java.util.Map;

/**
 * [M16] 审计日志（audit_logs）：只追加，不可篡改（无任何修改/删除接口）。
 * created_at 由数据库默认值生成。
 */
@Getter
@Setter
@Entity(name = "adminAuditLog")
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作人（系统动作为 NULL） */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_role", length = 32)
    private String userRole;

    /** field_permission_change/user_suspend/user_resume/user_handover/…… */
    @Column(name = "action", nullable = false, length = 48)
    private String action;

    @Column(name = "biz_type", length = 32)
    private String bizType;

    @Column(name = "biz_id")
    private Long bizId;

    /** 细节（JSON 列，键名原样保留） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", columnDefinition = "json")
    private Map<String, Object> detail;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
