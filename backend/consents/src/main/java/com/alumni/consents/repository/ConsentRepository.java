package com.alumni.consents.repository;

import com.alumni.consents.entity.Consent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    boolean existsByArchiveIdAndScope(Long archiveId, String scope);

    List<Consent> findByUserIdOrderByCreatedAtDesc(Long userId);
}
