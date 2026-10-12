package com.alumni.showcase.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 校友档案（本模块仅读取上架校验与 feed 所需列）。
 */
@Getter
@Setter
@Entity(name = "showcaseArchive")
@Table(name = "archives")
public class Archive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    /** draft/pending_first/pending_second/approved/rejected/deleting/deleted */
    @Column(name = "status", nullable = false, length = 16)
    private String status;
}
