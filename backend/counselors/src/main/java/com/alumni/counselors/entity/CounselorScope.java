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
 * [M34] 导员管辖范围：本学院内 届别 ∪ 专业 ∪ 指定学号 三维度并集
 * （每行一个维度值，grad_year / major_id / student_id 恰一非空；college_id 创建时固定，防越院）。
 */
@Getter
@Setter
@Entity
@Table(name = "counselor_scopes")
public class CounselorScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 导员 users.id（role=counselor） */
    @Column(name = "counselor_id", nullable = false)
    private Long counselorId;

    /** 所属学院 dicts.id（type=college） */
    @Column(name = "college_id", nullable = false)
    private Long collegeId;

    /** 管辖届别（维度之一，可空） */
    @Column(name = "grad_year")
    private Integer gradYear;

    /** 管辖专业 dicts.id（type=major，可空） */
    @Column(name = "major_id")
    private Long majorId;

    /** 指定学号（可空） */
    @Column(name = "student_id", length = 32)
    private String studentId;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
