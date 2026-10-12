package com.alumni.tags.repository;

import com.alumni.tags.entity.TagBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TagBindingRepository extends JpaRepository<TagBinding, Long> {

    List<TagBinding> findByBizTypeAndBizId(String bizType, Long bizId);

    /** 删除标签前的引用检查 */
    long countByTagId(Long tagId);

    /** 全量覆盖式绑定：先删旧（同事务内与插入新绑定一起提交，幂等） */
    void deleteByBizTypeAndBizId(String bizType, Long bizId);
}
