package com.alumni.tags.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 标签（域隔离 honor/archive/alumni，uk(group, name)）。
 * created_at / updated_at 由数据库默认值生成。
 */
@Getter
@Setter
@Entity
@Table(name = "tags")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String name;

    /** 标签域 honor/archive/alumni（列名为 MySQL 保留字 group，反引号转义；Java 字段名避开 JPQL 关键字） */
    @Column(name = "`group`", nullable = false, length = 16)
    private String tagGroup;

    @Column(nullable = false)
    private Integer sort = 0;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
