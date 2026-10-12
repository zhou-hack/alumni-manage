package com.alumni.reviews.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 校友档案（仅映射审核状态同步所需的列）。
 */
@Getter
@Setter
@Entity(name = "reviewsArchive")
@Table(name = "archives")
public class Archive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** draft/pending_first/pending_second/approved/rejected/deleting/deleted */
    @Column(name = "status", nullable = false, length = 16)
    private String status;
}
