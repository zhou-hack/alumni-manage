package com.alumni.announcement.repository;

import com.alumni.announcement.entity.AnnouncementImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnnouncementImageRepository extends JpaRepository<AnnouncementImage, Long> {

    Optional<AnnouncementImage> findByUuid(String uuid);
}
