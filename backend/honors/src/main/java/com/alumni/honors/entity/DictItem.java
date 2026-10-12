package com.alumni.honors.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 基础字典项（本模块只读）。
 * type='honor_category' 用于荣誉类别校验；type='college' 用于列表 college_id 筛选取 label。
 */
@Getter
@Setter
@Entity(name = "honorsDictItem")
@Table(name = "dicts")
public class DictItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @Column(name = "label", nullable = false, length = 64)
    private String label;

    @Column(name = "value", nullable = false, length = 64)
    private String value;
}
