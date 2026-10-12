package com.alumni.reviews.repository;

import com.alumni.reviews.entity.Honor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HonorRepository extends JpaRepository<Honor, Long> {

    /** 审核状态同步：仅更新 status 列，其余列由荣誉模块维护 */
    @Modifying
    @Query("UPDATE reviewsHonor h SET h.status = :status WHERE h.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
