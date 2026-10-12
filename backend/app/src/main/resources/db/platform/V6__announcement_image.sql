-- ============================================================================
-- V6：公告图片静态资源表（编辑器图片不再 base64 内嵌正文，上传后按 URL 引用）
--   文件落盘：{storage.root}/announcement/{uuid}.{ext}
--   访问：GET /api/v1/announcements/images/{uuid}（公开，公告为公开内容）
-- ============================================================================
CREATE TABLE IF NOT EXISTS announcement_image (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid         VARCHAR(36)  NOT NULL COMMENT '对外资源标识（URL 使用，不暴露自增 ID）',
    filename     VARCHAR(255) NOT NULL COMMENT '原始文件名',
    content_type VARCHAR(64)  NOT NULL COMMENT 'image/jpeg 等',
    size_bytes   BIGINT UNSIGNED NOT NULL,
    created_by   BIGINT UNSIGNED NOT NULL COMMENT '上传人（管理员）',
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_announcement_image_uuid (uuid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '公告图片静态资源';
