package com.alumni.photo.repository;

import com.alumni.photo.entity.PhotoCollect;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhotoCollectRepository extends JpaRepository<PhotoCollect, Long> {

    /** 用户自己的全部照片（含所有状态） */
    List<PhotoCollect> findByUserIdOrderByIdDesc(Long userId);

    /** 用户已审核通过的（相册展示用） */
    Page<PhotoCollect> findByUserIdAndStatusOrderByIdDesc(Long userId, String status, Pageable pageable);

    /** 审核队列：按 status 分页 */
    Page<PhotoCollect> findByStatusOrderByIdDesc(String status, Pageable pageable);

    /** 多状态筛选 */
    Page<PhotoCollect> findByStatusInOrderByIdDesc(List<String> statuses, Pageable pageable);
}
