package com.alumni.counselors.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 校友库。导员账号配套桩行：student_id 取 "T"+手机号（工号式，保证唯一），
 * id_card 空串、grad_year=0、college 取所属学院字典 label。
 */
@Getter
@Setter
@Entity(name = "counselorsAlumniBase")
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
