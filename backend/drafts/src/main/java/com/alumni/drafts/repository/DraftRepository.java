package com.alumni.drafts.repository;

import com.alumni.drafts.entity.Draft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DraftRepository extends JpaRepository<Draft, Long> {

    Optional<Draft> findByUserIdAndBizType(Long userId, String bizType);

    List<Draft> findByUserIdOrderByUpdatedAtDesc(Long userId);

    long deleteByUserIdAndBizType(Long userId, String bizType);
}
