package com.alumni.photo.repository;

import com.alumni.photo.entity.PhotoPushLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoPushLogRepository extends JpaRepository<PhotoPushLog, Long> {

    Page<PhotoPushLog> findAllByOrderByIdDesc(Pageable pageable);

    Page<PhotoPushLog> findByPhotoIdOrderByIdDesc(Long photoId, Pageable pageable);
}
