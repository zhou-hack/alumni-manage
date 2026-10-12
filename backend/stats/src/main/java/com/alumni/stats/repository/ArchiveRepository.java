package com.alumni.stats.repository;

import com.alumni.stats.entity.Archive;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 档案数 = COUNT(archives)，直接使用 JpaRepository 自带 count()。
 */
public interface ArchiveRepository extends JpaRepository<Archive, Long> {
}
