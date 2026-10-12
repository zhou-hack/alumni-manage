package com.alumni.outstanding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 优秀校友展示位（运营精选，draft → published ↔ withdrawn）。
 * 一人仅一个精选位（uk_outstanding_user）；寄语/时间线/代表荣誉均为管理员编辑的展示数据，
 * 不从业务表推导。
 */
@Getter
@Setter
@Entity
@Table(name = "outstanding_alumni")
public class OutstandingAlumni {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联 users.id（须为已激活校友 student/alumni） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 冗余学号，便于检索与关联档案索引 */
    @Column(name = "student_id", nullable = false, length = 32)
    private String studentId;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    /** 照片（base64 data URL 或 URL）；NULL 时前端渲染姓氏占位图（V8 扩容 MEDIUMTEXT） */
    @Column(name = "photo_url", columnDefinition = "MEDIUMTEXT")
    private String photoUrl;

    /** 一句话寄语 */
    @Column(length = 200)
    private String message;

    /** 关联字典 dicts.id（type=college）；公开列表经 JOIN dicts 取 label 展示 */
    @Column(name = "college_id")
    private Long collegeId;

    /** 毕业年份，如 2023 */
    @Column(name = "grad_year")
    private Integer gradYear;

    @Column(name = "current_title", length = 100)
    private String currentTitle;

    @Column(name = "current_city", length = 50)
    private String currentCity;

    /** 成长时间线 [{"year":"2020","event":"..."}]（JSON 列，键值原样透传，不走 SNAKE_CASE） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<Map<String, String>> timeline;

    /** 代表荣誉 [{"title":"...","year":"2022","level":"省级"}]（JSON 列） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<Map<String, String>> awards;

    /** 展示顺序，小者靠前 */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    /** 是否首页焦点推荐位 */
    @Column(name = "is_featured", nullable = false)
    private Boolean isFeatured;

    /** draft / published / withdrawn */
    @Column(nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
