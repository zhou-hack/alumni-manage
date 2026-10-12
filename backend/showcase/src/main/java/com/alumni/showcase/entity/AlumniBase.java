package com.alumni.showcase.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 校友库（V18 扩展：大屏展示字段 photo_url / industry / intro / quote / motto）。
 *
 * <p>字段语义：
 * <ul>
 *   <li>photo_url — 头像/照片 URL（建议通过 /api/v1/attachments 上传后回填）</li>
 *   <li>industry — 当前所在行业 / 单位</li>
 *   <li>intro — 个人简介（多行）</li>
 *   <li>quote — 人生信条 / 引言</li>
 *   <li>motto — 座右铭</li>
 * </ul>
 */
@Getter
@Setter
@Entity(name = "showcaseAlumniBase")
@Table(name = "alumni_base")
public class AlumniBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 学号（V1 已有） */
    @Column(name = "student_id", length = 32)
    private String studentId;

    /** 真实姓名（V1 已有） */
    @Column(name = "real_name", length = 64)
    private String realName;

    /** 学院（文本，对齐 dicts college.label） */
    @Column(name = "college", length = 64)
    private String college;

    @Column(name = "grad_year", nullable = false)
    private Integer gradYear;

    // === V18：大屏展示字段 ===

    @Column(name = "photo_url", length = 512)
    private String photoUrl;

    @Column(name = "industry", length = 64)
    private String industry;

    @Column(name = "intro", length = 500)
    private String intro;

    @Column(name = "quote", length = 200)
    private String quote;

    @Column(name = "motto", length = 200)
    private String motto;
}
