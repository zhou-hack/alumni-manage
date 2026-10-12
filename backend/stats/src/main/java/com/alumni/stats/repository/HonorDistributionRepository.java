package com.alumni.stats.repository;

import com.alumni.stats.entity.Honor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * [M03] 荣誉分布四维度聚合（仅 approved；honors ⋈ users ⋈ alumni_base）：
 * college（按学院文本，需 join 学籍）/ year（授予年份）/ category / level。
 * 可选过滤：college_id（经 dicts 转学院文本）、year（授予年份）。
 */
public interface HonorDistributionRepository extends JpaRepository<Honor, Long> {

    String BASE = """
            FROM honors h
            JOIN users u ON u.id = h.user_id
            JOIN alumni_base ab ON ab.id = u.alumni_base_id
            WHERE h.status = 'approved'
              AND (:collegeId IS NULL OR ab.college = (SELECT d.label FROM dicts d
                                                       WHERE d.type = 'college' AND d.id = :collegeId))
              AND (:year IS NULL OR YEAR(h.awarded_at) = :year)
            """;

    @Query(value = "SELECT ab.college AS k, COUNT(h.id) AS cnt " + BASE
            + " GROUP BY ab.college ORDER BY cnt DESC LIMIT :limit", nativeQuery = true)
    List<KeyCountProjection> byCollege(@Param("collegeId") Long collegeId,
                                       @Param("year") Integer year,
                                       @Param("limit") int limit);

    @Query(value = "SELECT CAST(YEAR(h.awarded_at) AS CHAR) AS k, COUNT(h.id) AS cnt " + BASE
            + " GROUP BY CAST(YEAR(h.awarded_at) AS CHAR) ORDER BY k DESC LIMIT :limit", nativeQuery = true)
    List<KeyCountProjection> byYear(@Param("collegeId") Long collegeId,
                                    @Param("year") Integer year,
                                    @Param("limit") int limit);

    @Query(value = "SELECT h.category AS k, COUNT(h.id) AS cnt " + BASE
            + " GROUP BY h.category ORDER BY cnt DESC LIMIT :limit", nativeQuery = true)
    List<KeyCountProjection> byCategory(@Param("collegeId") Long collegeId,
                                        @Param("year") Integer year,
                                        @Param("limit") int limit);

    @Query(value = "SELECT h.level AS k, COUNT(h.id) AS cnt " + BASE
            + " GROUP BY h.level ORDER BY cnt DESC LIMIT :limit", nativeQuery = true)
    List<KeyCountProjection> byLevel(@Param("collegeId") Long collegeId,
                                     @Param("year") Integer year,
                                     @Param("limit") int limit);
}
