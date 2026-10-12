-- ============================================================================
-- 第十二章 Drafts 草稿（服务端兜底，版本号乐观锁）
-- ============================================================================

CREATE TABLE IF NOT EXISTS drafts (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    biz_type   VARCHAR(32)     NOT NULL COMMENT 'archive/……',
    version    INT             NOT NULL DEFAULT 1 COMMENT '乐观锁：提交版本=服务端版本才保存并 +1',
    payload    JSON            NOT NULL COMMENT '草稿内容（标题/正文/attachment_ids 等）',
    created_at TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_drafts_user_biz (user_id, biz_type),
    CONSTRAINT fk_drafts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '草稿（正式提交成功后删除对应行）';
