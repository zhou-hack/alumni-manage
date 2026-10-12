package com.alumni.consents.repository;

import com.alumni.consents.entity.Showcase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowcaseRepository extends JpaRepository<Showcase, Long> {

    long deleteByArchiveId(Long archiveId);
}
