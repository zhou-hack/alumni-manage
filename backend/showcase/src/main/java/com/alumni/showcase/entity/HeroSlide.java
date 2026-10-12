package com.alumni.showcase.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 门户首页大图轮播位（首页 Hero，school_admin / system_admin 维护）。
 * image 存 base64 data URL（前端压缩 ≤500KB）或图片 URL，MEDIUMTEXT。
 */
@Getter
@Setter
@Entity
@Table(name = "hero_slide")
public class HeroSlide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 主标题（如 城堡长灯 · 静候归人） */
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    /** 副标题（如 无论走多远，这里永远为你亮灯） */
    @Column(name = "subtitle", length = 200)
    private String subtitle;

    /** 轮播图（base64 data URL 或 URL）；NULL 时门户端跳过该张 */
    @Column(name = "image", columnDefinition = "MEDIUMTEXT")
    private String image;

    /** 展示顺序，小者靠前 */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    /** published=展示 withdrawn=隐藏 */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 展示位置：hero=首屏轮播（默认） / campus=美丽校园模块 */
    @Column(name = "placement", nullable = false, length = 20)
    private String placement;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;
}
