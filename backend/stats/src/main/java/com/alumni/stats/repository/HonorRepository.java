package com.alumni.stats.repository;

import com.alumni.stats.entity.Honor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 荣誉分布：仅统计 Honor 表（规范第五章），按类别 / 级别两个维度分组计数。
 */
public interface HonorRepository extends JpaRepository<Honor, Long> {

    /** 按类别分组计数，数量降序 */
    @Query("SELECT h.category AS k, COUNT(h) AS cnt FROM statsHonor h GROUP BY h.category ORDER BY cnt DESC")
    List<KeyCountProjection> countGroupByCategory();

    /** 按级别分组计数，数量降序 */
    @Query("SELECT h.level AS k, COUNT(h) AS cnt FROM statsHonor h GROUP BY h.level ORDER BY cnt DESC")
    List<KeyCountProjection> countGroupByLevel();
}
