package com.alumni.reviews.repository;

import com.alumni.reviews.entity.Archive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArchiveRepository extends JpaRepository<Archive, Long> {

    /** 审核状态同步：仅更新 status 列，其余列由档案模块维护 */
    @Modifying
    @Query("UPDATE reviewsArchive a SET a.status = :status WHERE a.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
