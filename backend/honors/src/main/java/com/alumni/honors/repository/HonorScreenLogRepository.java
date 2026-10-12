package com.alumni.honors.repository;

import com.alumni.honors.entity.HonorScreenLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HonorScreenLogRepository
        extends JpaRepository<HonorScreenLog, Long>, JpaSpecificationExecutor<HonorScreenLog> {

    /** Specification 动态查询：honor_id / action / 时间范围 / 操作人等 */
    default Page<HonorScreenLog> pageBySpec(Specification<HonorScreenLog> spec, Pageable pageable) {
        return findAll(spec, pageable);
    }
}