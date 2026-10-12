package com.alumni.stats.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 校友荣誉（统计只读；规范第五章明确 Honor 是统计接口唯一数据源）。
 */
@Getter
@Setter
@Entity(name = "statsHonor")
@Table(name = "honors")
public class Honor {

    @Id
    private Long id;

    /** 荣誉类别（scholarship/competition……对齐 honor_category 字典） */
    @Column(name = "category", nullable = false, length = 32)
    private String category;

    /** 荣誉级别（national/provincial/school……） */
    @Column(name = "level", nullable = false, length = 32)
    private String level;
}
