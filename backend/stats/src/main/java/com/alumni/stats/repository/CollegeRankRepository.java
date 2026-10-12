package com.alumni.stats.repository;

import com.alumni.stats.entity.Archive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 学院排行聚合查询（规范第十章口径）：
 * archives ⋈ users ⋈ alumni_base，按 alumni_base.college 分组取档案数 Top N，
 * 学院文本 LEFT JOIN dicts(type='college') 取字典 ID。
 */
public interface CollegeRankRepository extends JpaRepository<Archive, Long> {

    @Query(value = """
            SELECT d.id AS college_id, ab.college AS college_name, COUNT(a.id) AS archive_count
            FROM archives a
            JOIN users u ON u.id = a.user_id
            JOIN alumni_base ab ON ab.id = u.alumni_base_id
            LEFT JOIN dicts d ON d.type = 'college' AND d.label = ab.college
            GROUP BY ab.college, d.id
            ORDER BY archive_count DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<CollegeRankProjection> findCollegeRanks(@Param("limit") int limit);
}
