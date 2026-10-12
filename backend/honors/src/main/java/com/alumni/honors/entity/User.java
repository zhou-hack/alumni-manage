package com.alumni.honors.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 平台账号（本模块仅读取列表筛选所需列）。
 * alumniBase 为只读关联，用于 keyword 匹配 real_name、college/grad_year 筛选。
 */
@Getter
@Setter
@Entity(name = "honorsUser")
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alumni_base_id", nullable = false)
    private Long alumniBaseId;

    @Column(name = "student_id", nullable = false, length = 32)
    private String studentId;

    @Column(name = "nickname", nullable = false, length = 64)
    private String nickname;

    /** 只读关联：不由此维护外键 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumni_base_id", insertable = false, updatable = false)
    private AlumniBase alumniBase;
}
