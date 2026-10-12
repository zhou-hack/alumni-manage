package com.alumni.auth.repository;

import com.alumni.auth.entity.AlumniBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlumniBaseRepository extends JpaRepository<AlumniBase, Long> {

    /** 激活三要素核验：学号 + 姓名 + 身份证号同时匹配 */
    Optional<AlumniBase> findByStudentIdAndRealNameAndIdCard(String studentId, String realName, String idCard);
    Optional<AlumniBase> findByStudentId(String studentId);
}