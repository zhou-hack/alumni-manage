package com.alumni.honors.repository;

import com.alumni.honors.entity.DictItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DictItemRepository extends JpaRepository<DictItem, Long> {

    /** 荣誉类别等字典值存在性校验 */
    boolean existsByTypeAndValue(String type, String value);

    /** 按类型与主键取字典项（college_id → label 转换用） */
    Optional<DictItem> findByTypeAndId(String type, Long id);
}
