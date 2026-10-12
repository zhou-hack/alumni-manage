package com.alumni.honors.repository;

import com.alumni.honors.entity.HonorTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** [M03] 团队荣誉成员匹配留证查询（按批次取全部人名识别结果） */
public interface HonorTeamMemberRepository extends JpaRepository<HonorTeamMember, Long> {

    /** 团队详情：按批次 ID 取全部成员留证行（写入顺序即 id 升序） */
    List<HonorTeamMember> findByTeamIdOrderByIdAsc(String teamId);

    /** 是否存在该批次（详情可见性/404 判定用） */
    boolean existsByTeamId(String teamId);
}
