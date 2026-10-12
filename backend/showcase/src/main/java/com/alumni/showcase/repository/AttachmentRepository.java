package com.alumni.showcase.repository;

import com.alumni.showcase.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /** 封面：首个已引用的图片附件（按引用时间、ID 升序取第一），无则返回 null */
    @Query(value = """
            SELECT a.id FROM attachments a
            WHERE a.biz_type = 'archive' AND a.ref_id = :archiveId
              AND a.status = 'referenced' AND a.mime LIKE 'image/%'
            ORDER BY a.referenced_at ASC, a.id ASC LIMIT 1
            """, nativeQuery = true)
    Long findCoverAttachmentId(@Param("archiveId") Long archiveId);
}
