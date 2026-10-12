package com.alumni.showcase.repository;

import com.alumni.showcase.entity.Consent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    boolean existsByArchiveIdAndScope(Long archiveId, String scope);
}
