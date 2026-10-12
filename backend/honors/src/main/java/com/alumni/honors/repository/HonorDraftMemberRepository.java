package com.alumni.honors.repository;

import com.alumni.honors.entity.HonorDraftMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HonorDraftMemberRepository extends JpaRepository<HonorDraftMember, Long> {
    List<HonorDraftMember> findByDraftIdOrderByIdAsc(Long draftId);

    void deleteByDraftId(Long draftId);
}