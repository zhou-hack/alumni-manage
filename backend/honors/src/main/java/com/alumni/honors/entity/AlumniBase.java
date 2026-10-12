package com.alumni.honors.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 校友库（本模块仅读取列表筛选所需列）。
 * college 为文本，与 dicts（type='college'）的 label 对齐。
 */
@Getter
@Setter
@Entity(name = "honorsAlumniBase")
@Table(name = "alumni_base")
public class AlumniBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false, length = 32)
    private String studentId;

    @Column(name = "real_name", nullable = false, length = 64)
    private String realName;

    /** [P1/M13] 姓名全拼（预生成，列表 keyword 拼音容错用） */
    @Column(name = "real_name_pinyin", length = 128)
    private String realNamePinyin;

    /** [P1/M13] 姓名拼音首字母（预生成，列表 keyword 拼音容错用） */
    @Column(name = "real_name_pinyin_abbr", length = 64)
    private String realNamePinyinAbbr;

    /** 文本学院名，对齐 dicts college.label */
    @Column(name = "college", length = 64)
    private String college;

    @Column(name = "grad_year", nullable = false)
    private Integer gradYear;
}
