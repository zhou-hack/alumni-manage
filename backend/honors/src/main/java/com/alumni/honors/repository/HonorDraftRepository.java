package com.alumni.honors.repository;

import com.alumni.honors.entity.HonorDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HonorDraftRepository extends JpaRepository<HonorDraft, Long>, JpaSpecificationExecutor<HonorDraft> {
}