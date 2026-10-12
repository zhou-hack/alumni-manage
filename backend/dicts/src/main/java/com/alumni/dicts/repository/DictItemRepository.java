package com.alumni.dicts.repository;

import com.alumni.dicts.entity.DictItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DictItemRepository extends JpaRepository<DictItem, Long> {

    List<DictItem> findByTypeOrderBySortAscIdAsc(String type);

    List<DictItem> findByTypeAndParentIdOrderBySortAscIdAsc(String type, Long parentId);

    long countByParentId(Long parentId);

    boolean existsByTypeAndValue(String type, String value);

    boolean existsByTypeAndValueAndIdNot(String type, String value, Long id);

    boolean existsByType(String type);
}
