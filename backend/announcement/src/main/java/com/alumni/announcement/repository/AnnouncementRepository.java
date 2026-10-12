package com.alumni.announcement.repository;

import com.alumni.announcement.entity.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /** 按状态筛选（status 为 null 时查全部），按 id 倒序（最近优先） */
    Page<Announcement> findByStatusOrderByIdDesc(String status, Pageable pageable);

    Page<Announcement> findAllByOrderByIdDesc(Pageable pageable);
}
