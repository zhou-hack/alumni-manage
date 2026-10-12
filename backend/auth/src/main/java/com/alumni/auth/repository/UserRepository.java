package com.alumni.auth.repository;

import com.alumni.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByStudentId(String studentId);

    boolean existsByAlumniBaseId(Long alumniBaseId);

    boolean existsByPhone(String phone);

    boolean existsByStudentId(String studentId);

    /**
     * 人员管理分页检索（管理端）：按角色过滤 + 学号/昵称关键词包含匹配
     * + 学籍筛选（学院精确 / 专业包含 / 届别精确，联 alumni_base）。
     * role/keyword/college/major/gradYear 传 null 时跳过对应条件。
     *
     * <p>数据范围隔离：scopeCollege 非 null 时强制该院（college_admin）；
     * counselorId 非 null 时要求命中 counselor_scopes（同学院 + 届别∪专业∪学号任一，
     * 与 ReviewService.SCOPE_MATCH 同口径）。</p>
     */
    @Query(value = """
            SELECT u.* FROM users u
            JOIN alumni_base ab ON ab.id = u.alumni_base_id
            WHERE (:role IS NULL OR u.role = :role)
              AND (:keyword IS NULL
                   OR LOWER(u.student_id) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:college IS NULL OR ab.college = :college)
              AND (:major IS NULL OR LOWER(ab.major) LIKE LOWER(CONCAT('%', :major, '%')))
              AND (:gradYear IS NULL OR ab.grad_year = :gradYear)
              AND (:scopeCollege IS NULL OR ab.college = :scopeCollege)
              AND (:counselorId IS NULL OR EXISTS (
                    SELECT 1 FROM counselor_scopes cs
                    WHERE cs.counselor_id = :counselorId
                      AND ab.college = (SELECT d.label FROM dicts d WHERE d.id = cs.college_id AND d.type = 'college')
                      AND (cs.grad_year = ab.grad_year
                           OR (cs.major_id IS NOT NULL
                               AND cs.major_id = (SELECT m.id FROM dicts m WHERE m.type = 'major' AND m.label = ab.major))
                           OR cs.student_id = ab.student_id)))
            """,
            countQuery = """
            SELECT COUNT(*) FROM users u
            JOIN alumni_base ab ON ab.id = u.alumni_base_id
            WHERE (:role IS NULL OR u.role = :role)
              AND (:keyword IS NULL
                   OR LOWER(u.student_id) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:college IS NULL OR ab.college = :college)
              AND (:major IS NULL OR LOWER(ab.major) LIKE LOWER(CONCAT('%', :major, '%')))
              AND (:gradYear IS NULL OR ab.grad_year = :gradYear)
              AND (:scopeCollege IS NULL OR ab.college = :scopeCollege)
              AND (:counselorId IS NULL OR EXISTS (
                    SELECT 1 FROM counselor_scopes cs
                    WHERE cs.counselor_id = :counselorId
                      AND ab.college = (SELECT d.label FROM dicts d WHERE d.id = cs.college_id AND d.type = 'college')
                      AND (cs.grad_year = ab.grad_year
                           OR (cs.major_id IS NOT NULL
                               AND cs.major_id = (SELECT m.id FROM dicts m WHERE m.type = 'major' AND m.label = ab.major))
                           OR cs.student_id = ab.student_id)))
            """,
            nativeQuery = true)
    Page<User> search(@Param("role") String role,
                      @Param("keyword") String keyword,
                      @Param("college") String college,
                      @Param("major") String major,
                      @Param("gradYear") Integer gradYear,
                      @Param("scopeCollege") String scopeCollege,
                      @Param("counselorId") Long counselorId,
                      Pageable pageable);
}
