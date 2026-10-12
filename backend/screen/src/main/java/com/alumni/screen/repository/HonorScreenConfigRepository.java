package com.alumni.screen.repository;

import com.alumni.screen.entity.HonorScreenConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HonorScreenConfigRepository extends JpaRepository<HonorScreenConfig, Long> {

    /** 当前生效配置（status='active'，按 updated_at desc 取最新一条） */
    Optional<HonorScreenConfig> findFirstByStatusOrderByUpdatedAtDesc(String status);

    /** 按状态分页 */
    Page<HonorScreenConfig> findByStatus(String status, Pageable pageable);

    /** 按 name 查重 */
    Optional<HonorScreenConfig> findByName(String name);

    /** 多状态查询（active + draft） */
    List<HonorScreenConfig> findByStatusIn(List<String> statuses);
}
