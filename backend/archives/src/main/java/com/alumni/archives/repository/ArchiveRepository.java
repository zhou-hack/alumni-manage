package com.alumni.archives.repository;

import com.alumni.archives.entity.Archive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveRepository extends JpaRepository<Archive, Long> {

    Page<Archive> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
}
