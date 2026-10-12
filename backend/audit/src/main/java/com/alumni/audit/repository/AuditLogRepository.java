package com.alumni.audit.repository;

import com.alumni.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 审计日志 Repository（只追加：仅查询与 save，无任何 UPDATE/DELETE 派生方法）。
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
}
