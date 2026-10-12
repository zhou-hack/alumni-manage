package com.alumni.outstanding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 字典项（本模块仅读取 college 字典的 id/label/value，用于精选位学院展示与反查）。
 */
@Getter
@Setter
@Entity(name = "outstandingDict")
@Table(name = "dicts")
public class DictItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** college/major/grad_year/honor_category */
    @Column(nullable = false, length = 32)
    private String type;

    /** 展示文本（对齐 alumni_base.college） */
    @Column(nullable = false, length = 64)
    private String label;

    @Column(nullable = false, length = 64)
    private String value;
}
