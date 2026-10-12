package com.alumni.orgsync.repository;

import com.alumni.orgsync.entity.OrgSyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgSyncLogRepository extends JpaRepository<OrgSyncLog, Long> {
    Page<OrgSyncLog> findAllByOrderByIdDesc(Pageable pageable);
}
