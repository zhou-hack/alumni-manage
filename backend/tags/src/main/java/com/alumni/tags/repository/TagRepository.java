package com.alumni.tags.repository;

import com.alumni.tags.entity.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TagRepository extends JpaRepository<Tag, Long> {

    /** 标签列表：按 sort,id 升序（可选域过滤 + name 模糊） */
    Page<Tag> findAllByOrderBySortAscIdAsc(Pageable pageable);

    Page<Tag> findByTagGroupOrderBySortAscIdAsc(String tagGroup, Pageable pageable);

    Page<Tag> findByNameContainingOrderBySortAscIdAsc(String keyword, Pageable pageable);

    Page<Tag> findByTagGroupAndNameContainingOrderBySortAscIdAsc(String tagGroup, String keyword, Pageable pageable);

    /** 同域重名检查（创建） */
    boolean existsByTagGroupAndName(String tagGroup, String name);

    /** 同域重名检查（改名，排除自身） */
    boolean existsByTagGroupAndNameAndIdNot(String tagGroup, String name, Long id);

    List<Tag> findByIdIn(Collection<Long> ids);
}
