package com.alumni.showcase.repository;

import com.alumni.showcase.entity.AlumniBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * alumni_base 表的扩展字段 Repository（V18 · 大屏展示字段）。
 *
 * <p>注意：showcase 模块的 AlumniBase 实体只声明 V18 字段（photo/industry/intro/quote/motto），
 * 其他模块的 AlumniBase 不含这些字段——这不影响 JPA，因为它们只在自己模块内使用
 * 实体，且本模块的 update 走原生 SQL，绕开跨模块实体不一致问题。</p>
 */
@Repository
public interface AlumniBaseRepository extends JpaRepository<AlumniBase, Long> {

    /**
     * 更新大屏展示字段（photo_url/industry/intro/quote/motto）。
     * 用原生 SQL 而非 save()，避免 Hibernate dirty checking 把全表字段重新写一次。
     * 仅写入非空字段：null = 不改；空串 = 显式清空。
     */
    @Modifying
    @Query(value = "UPDATE alumni_base SET " +
            "photo_url = :photoUrl, " +
            "industry  = :industry, " +
            "intro     = :intro, " +
            "quote     = :quote, " +
            "motto     = :motto " +
            "WHERE id = :id", nativeQuery = true)
    int updateProfileFields(@Param("id") long id,
                           @Param("photoUrl") String photoUrl,
                           @Param("industry") String industry,
                           @Param("intro") String intro,
                           @Param("quote") String quote,
                           @Param("motto") String motto);
}
