package com.alumni.showcase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 首页轮播 DTO 集（门户 Hero，school_admin / system_admin 维护）。
 * image 为 base64 data URL（前端 canvas 压缩 ≤5MB）或图片 URL。
 * 美丽校园（placement=campus）与首屏轮播（hero）共用同一上限。
 */
public final class HeroDtos {

    private HeroDtos() {
    }

    /** 图片上限：base64 约 750 万字符（≈5MB 原图） */
    static final int IMAGE_MAX = 7_500_000;

    /** 轮播项（公开 / 管理通用出参） */
    public record Item(
            Long id,
            String title,
            String subtitle,
            String image,
            Integer displayOrder,
            String status,
            String placement,
            java.time.Instant createdAt,
            java.time.Instant updatedAt) {
    }

    /** 新增请求（title 必填，其余可选；image 缺省由门户端回退默认图；placement 缺省 hero） */
    public record CreateRequest(
            @NotBlank(message = "title 不能为空") @Size(max = 100, message = "title 最长 100 字符") String title,
            @Size(max = 200, message = "subtitle 最长 200 字符") String subtitle,
            @Size(max = IMAGE_MAX, message = "image 超长（base64 照片约 ≤5MB 原图）") String image,
            Integer displayOrder,
            @Pattern(regexp = "^(published|withdrawn)$", message = "status 仅允许 published/withdrawn") String status,
            @Pattern(regexp = "^(hero|campus)$", message = "placement 仅允许 hero/campus") String placement) {
    }

    /** 修改请求（字段均可选，给了才改） */
    public record UpdateRequest(
            @Size(max = 100, message = "title 最长 100 字符") String title,
            @Size(max = 200, message = "subtitle 最长 200 字符") String subtitle,
            @Size(max = IMAGE_MAX, message = "image 超长（base64 照片约 ≤5MB 原图）") String image,
            Integer displayOrder,
            @Pattern(regexp = "^(published|withdrawn)$", message = "status 仅允许 published/withdrawn") String status,
            @Pattern(regexp = "^(hero|campus)$", message = "placement 仅允许 hero/campus") String placement) {
    }
}
