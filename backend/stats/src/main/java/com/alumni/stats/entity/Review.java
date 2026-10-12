package com.alumni.stats.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 审核记录（统计只读，仅按 status 计数待审量）。
 */
@Getter
@Setter
@Entity(name = "statsReview")
@Table(name = "reviews")
public class Review {

    @Id
    private Long id;

    /** 审核状态：pending/approved/rejected */
    @Column(name = "status", nullable = false, length = 16)
    private String status;
}
