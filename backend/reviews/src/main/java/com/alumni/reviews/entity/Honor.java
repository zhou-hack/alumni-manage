package com.alumni.reviews.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 校友荣誉（仅映射审核状态同步所需的列）。
 */
@Getter
@Setter
@Entity(name = "reviewsHonor")
@Table(name = "honors")
public class Honor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** pending_first/pending_second/approved/rejected */
    @Column(name = "status", nullable = false, length = 16)
    private String status;
}
