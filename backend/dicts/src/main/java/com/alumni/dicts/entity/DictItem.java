package com.alumni.dicts.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 基础字典项（college/major/grad_year/honor_category，随业务扩充）。
 */
@Getter
@Setter
@Entity
@Table(name = "dicts")
public class DictItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type", nullable = false, length = 32)
    private String type;

    /** 父级字典项 ID（major 归属 college；其余类型为 NULL） */
    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "label", nullable = false, length = 64)
    private String label;

    @Column(name = "value", nullable = false, length = 64)
    private String value;

    @Column(name = "sort", nullable = false)
    private Integer sort;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, updatable = false, insertable = false)
    private Instant updatedAt;
}
