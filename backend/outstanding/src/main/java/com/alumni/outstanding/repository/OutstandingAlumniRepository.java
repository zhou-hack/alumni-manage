package com.alumni.outstanding.repository;

import com.alumni.outstanding.entity.OutstandingAlumni;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutstandingAlumniRepository extends JpaRepository<OutstandingAlumni, Long> {

    /** 一人仅一个精选位：重复添加判定依据 */
    boolean existsByUserId(Long userId);

    /** 公开列表：仅 published，按展示顺序升序 */
    List<OutstandingAlumni> findByStatusOrderByDisplayOrderAscIdAsc(String status);

    /** 管理列表：按状态筛选（status 为 null 时查全部），按展示顺序升序 */
    Page<OutstandingAlumni> findByStatusOrderByDisplayOrderAscIdAsc(String status, Pageable pageable);

    Page<OutstandingAlumni> findAllByOrderByDisplayOrderAscIdAsc(Pageable pageable);
}
