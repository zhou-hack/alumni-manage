package com.alumni.audit.entity;

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
 * 审计日志（[M16]，对齐 db/schema.sql P1 段 audit_logs 表）。
 *
 * <p>只追加：服务端不提供任何修改/删除路径；created_at 由数据库默认值
 * CURRENT_TIMESTAMP(6) 生成。</p>
 */
@Getter
@Setter
@Entity
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

    /** login/login_failed/logout/export/attachment_delete/field_permission_change/
     *  user_suspend/user_resume/user_handover/consent_grant/consent_withdraw/
     *  dict_change/tag_change/counselor_scope_change/todo_remind */
    @Column(nullable = false, length = 48)
    private String action;

    @Column(name = "biz_type", length = 32)
    private String bizType;

    @Column(name = "biz_id")
    private Long bizId;

    /** 明细（JSON 列，键值原样透传，不走 SNAKE_CASE 转换） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> detail;

    @Column(length = 45)
    private String ip;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
