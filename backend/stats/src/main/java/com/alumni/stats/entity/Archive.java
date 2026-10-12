package com.alumni.stats.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 校友档案（统计只读，仅映射聚合查询所需列）。
 */
@Getter
@Setter
@Entity(name = "statsArchive")
@Table(name = "archives")
public class Archive {

    @Id
    private Long id;

    /** 归属校友（学院排行经 users + alumni_base 关联取学院） */
    @Column(name = "user_id", nullable = false)
    private Long userId;
}
