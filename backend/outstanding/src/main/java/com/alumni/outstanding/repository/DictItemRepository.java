package com.alumni.outstanding.repository;

import com.alumni.outstanding.entity.DictItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DictItemRepository extends JpaRepository<DictItem, Long> {

    /** college 字典项是否存在（创建/修改精选位时校验 college_id） */
    boolean existsByIdAndType(Long id, String type);

    /** 学院文本 → 字典项（创建精选位时从学籍 college 反查，对齐 alumni_base.college 文本） */
    Optional<DictItem> findByTypeAndLabel(String type, String label);

    List<DictItem> findAllByTypeOrderByIdAsc(String type);

    /**
     * 校友学籍信息（创建精选位时取默认值 + 校验用户身份）：
     * users ⋈ alumni_base，一次取 role/real_name/college/grad_year。
     */
    @Query(value = """
            SELECT u.id AS userId, u.student_id AS studentId, u.role AS role,
                   ab.real_name AS realName, ab.college AS college, ab.grad_year AS gradYear
            FROM users u
            JOIN alumni_base ab ON ab.id = u.alumni_base_id
            WHERE u.id = :userId
            """, nativeQuery = true)
    AlumnusInfoProjection findAlumnusInfo(@Param("userId") Long userId);

    /** 学籍信息投影（native，字段别名即 getter 名） */
    interface AlumnusInfoProjection {
        Long getUserId();

        String getStudentId();

        String getRole();

        String getRealName();

        String getCollege();

        Integer getGradYear();
    }
}
