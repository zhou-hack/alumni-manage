package com.alumni.stats.repository;

import com.alumni.stats.entity.Archive;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 上架数 = COUNT(showcase)，行存在即上架中（下架=删行，consent 撤回联动删除）。
 * 不引入 showcase 模块实体：以本模块 Archive 实体作 domain 类型（Spring Data 多模块
 * 严格模式下按实体归属认领 repository），仅暴露原生计数查询。
 */
public interface ShowcaseCountRepository extends Repository<Archive, Long> {

    @Query(value = "SELECT COUNT(*) FROM showcase", nativeQuery = true)
    long countAll();
}
