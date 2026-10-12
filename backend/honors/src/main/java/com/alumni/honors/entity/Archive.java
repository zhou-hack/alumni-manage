package com.alumni.honors.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 校友档案（本模块仅读取存在性与归属校验所需列）。
 */
@Getter
@Setter
@Entity(name = "honorsArchive")
@Table(name = "archives")
public class Archive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** draft/pending_first/pending_second/approved/rejected/deleting/deleted */
    @Column(name = "status", nullable = false, length = 16)
    private String status;
}
