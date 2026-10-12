package com.alumni.attachments.repository;

import com.alumni.attachments.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /** 孤儿附件：unreferenced 且早于指定时间（定时清理用） */
    List<Attachment> findByStatusAndCreatedAtBefore(String status, Instant before);
}
