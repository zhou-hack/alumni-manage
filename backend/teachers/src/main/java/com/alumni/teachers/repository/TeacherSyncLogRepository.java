package com.alumni.teachers.repository;

import com.alumni.teachers.entity.TeacherSyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherSyncLogRepository extends JpaRepository<TeacherSyncLog, Long> {

    /** 同步记录分页（最新在前） */
    Page<TeacherSyncLog> findAllByOrderByStartedAtDescIdDesc(Pageable pageable);
}
