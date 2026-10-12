package com.alumni.admin.repository;

import com.alumni.admin.entity.UserChangeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserChangeLogRepository extends JpaRepository<UserChangeLog, Long> {

    /** 某账号变更留痕，created_at 倒序（最近优先） */
    Page<UserChangeLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
