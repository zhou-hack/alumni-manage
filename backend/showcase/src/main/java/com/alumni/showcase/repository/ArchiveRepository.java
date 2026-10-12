package com.alumni.showcase.repository;

import com.alumni.showcase.entity.Archive;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveRepository extends JpaRepository<Archive, Long> {
}
