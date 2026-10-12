package com.alumni.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 校友库（激活三要素核验数据源）。
 * 身份证号按项目约定明文存储，仅用于激活比对，任何出参不得携带。
 */
@Getter
@Setter
@Entity
@Table(name = "alumni_base")
public class AlumniBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false, unique = true, length = 32)
    private String studentId;

    @Column(name = "real_name", nullable = false, length = 64)
    private String realName;

    @Column(name = "id_card", nullable = false, length = 32)
    private String idCard;

    @Column(name = "phone", nullable = false, length = 16)
    private String phone;

    @Column(name = "college", length = 64)
    private String college;

    @Column(name = "major", length = 64)
    private String major;

    @Column(name = "grad_year", nullable = false)
    private Integer gradYear;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
