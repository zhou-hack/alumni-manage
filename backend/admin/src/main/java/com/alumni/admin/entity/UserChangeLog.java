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
 * [M15] 账号变更留痕（user_change_logs）：停权/恢复/交接/改范围，只追加。
 * created_at 由数据库默认值生成。
 */
@Getter
@Setter
@Entity
@Table(name = "user_change_logs")
public class UserChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 被操作账号 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** suspend/resume/handover/scope_change */
    @Column(name = "action", nullable = false, length = 32)
    private String action;

    /** 操作人 */
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    /** 变更细节，如 {"target_user_id":15,"reason":"..."}（JSON 列，键名原样保留） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", columnDefinition = "json")
    private Map<String, Object> detail;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
