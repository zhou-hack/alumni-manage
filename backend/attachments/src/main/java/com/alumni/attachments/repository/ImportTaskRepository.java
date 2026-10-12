package com.alumni.attachments.repository;

import com.alumni.attachments.entity.ImportTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportTaskRepository extends JpaRepository<ImportTask, Long> {
    /** 当前用户上传过的导入任务，按创建时间倒序 */
    List<ImportTask> findByUploaderIdOrderByCreatedAtDesc(Long uploaderId);
}
