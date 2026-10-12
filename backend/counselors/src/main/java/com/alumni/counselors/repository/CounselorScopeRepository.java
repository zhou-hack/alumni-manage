package com.alumni.counselors.repository;

import com.alumni.counselors.entity.CounselorScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CounselorScopeRepository extends JpaRepository<CounselorScope, Long> {

    /** 某导员全部管辖范围行（聚合出参用） */
    List<CounselorScope> findByCounselorIdOrderById(Long counselorId);

    /** 全量替换前清空旧范围 */
    void deleteByCounselorId(Long counselorId);

    /** 仅调整所属学院时同步既有范围行（防越院：范围行学院与账号学院保持一致） */
    @Modifying
    @Query("UPDATE CounselorScope s SET s.collegeId = :collegeId WHERE s.counselorId = :counselorId")
    int updateCollegeIdByCounselorId(@Param("counselorId") Long counselorId, @Param("collegeId") Long collegeId);
}
