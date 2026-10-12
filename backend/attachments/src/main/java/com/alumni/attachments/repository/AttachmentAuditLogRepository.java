package com.alumni.attachments.repository;

import com.alumni.attachments.entity.AttachmentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentAuditLogRepository extends JpaRepository<AttachmentAuditLog, Long> {
}
