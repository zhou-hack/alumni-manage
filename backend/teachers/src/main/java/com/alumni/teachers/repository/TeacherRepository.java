package com.alumni.teachers.repository;

import com.alumni.teachers.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    /** 同步 upsert 唯一键查询 */
    Optional<Teacher> findByTeacherNo(String teacherNo);

    /** 取某状态全部记录（同步置停用比对 / 快照全量） */
    List<Teacher> findByStatus(String status);
}
