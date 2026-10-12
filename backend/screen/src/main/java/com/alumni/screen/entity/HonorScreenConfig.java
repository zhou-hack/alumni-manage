package com.alumni.screen.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 荣誉大屏展示配置（管理员在后台维护，portal H5 大屏读取 active 配置）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>show_fields 用 JSON 字符串存 Object（MySQL JSON 列），由 Service 层解析为 Map；
 *       不引入额外 JSON 依赖（Hibernate 6 自带 JsonType 仅在较新版本稳定，此处简化为 String）。</li>
 *   <li>name 字段预留多版本/多场景（如"开学季"、"毕业季"），当前业务只用 default；
 *       UNIQUE 约束保证 name 唯一。</li>
 *   <li>status='active' 表示当前生效；'draft' 草稿；'archived' 历史归档。</li>
 * </ul>
 */
@Getter
@Setter
@Entity
@Table(name = "honor_screen_config")
public class HonorScreenConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name = "default";

    @Column(nullable = false, length = 32)
    private String layout = "grid-3";

    @Column(name = "theme_color", nullable = false, length = 16)
    private String themeColor = "tech";

    @Column(name = "refresh_sec", nullable = false)
    private Integer refreshSec = 30;

    /** JSON 字符串：{"title":true,"level":true,...}；由 Service 层解析/序列化 */
    @Column(name = "show_fields", nullable = false, columnDefinition = "json")
    private String showFields;

    @Column(name = "sort_by", nullable = false, length = 32)
    private String sortBy = "awarded_at_desc";

    @Column(name = "max_items", nullable = false)
    private Integer maxItems = 50;

    @Column(nullable = false, length = 16)
    private String status = "draft";

    @Column(length = 255)
    private String remark;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
