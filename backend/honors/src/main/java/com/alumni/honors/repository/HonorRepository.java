package com.alumni.honors.repository;

import com.alumni.honors.entity.Honor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface HonorRepository extends JpaRepository<Honor, Long>, JpaSpecificationExecutor<Honor> {

    /** 幂等校验：同用户 + 同标题 + 同获奖日期 且状态处于指定集合的荣誉是否已存在 */
    boolean existsByUserIdAndTitleAndAwardedAtAndStatusIn(Long userId, String title,
                                                          LocalDate awardedAt, Collection<String> statuses);

    /** [M03] 团队详情：按批次取同批生成的全部荣誉 */
    List<Honor> findByTeamIdOrderByIdAsc(String teamId);
}
