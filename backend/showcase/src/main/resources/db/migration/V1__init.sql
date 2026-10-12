-- ============================================================================
-- 第九章 Consents 授权记录
-- ============================================================================

CREATE TABLE IF NOT EXISTS consents (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    archive_id BIGINT UNSIGNED NOT NULL,
    scope      VARCHAR(16)     NOT NULL COMMENT 'screen（大屏）/site（站点，预留）',
    created_at TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_consents_archive_scope (archive_id, scope),
    KEY idx_consents_user (user_id),
    CONSTRAINT fk_consents_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_consents_archive FOREIGN KEY (archive_id) REFERENCES archives (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '授权记录（撤回=删行并联动下架）';

-- ============================================================================
-- 第八章 Showcase / Screen 上下架与大屏
-- ============================================================================

CREATE TABLE IF NOT EXISTS showcase (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    archive_id          BIGINT UNSIGNED NOT NULL COMMENT '上架条件：archives.status=approved 且 consent 有效',
    cover_attachment_id BIGINT UNSIGNED DEFAULT NULL COMMENT '发布时快照封面（取首个已引用图片附件）',
    published_by        BIGINT UNSIGNED NOT NULL COMMENT 'school_admin',
    published_at        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_showcase_archive (archive_id),
    CONSTRAINT fk_showcase_archive FOREIGN KEY (archive_id) REFERENCES archives (id),
    CONSTRAINT fk_showcase_cover FOREIGN KEY (cover_attachment_id) REFERENCES attachments (id),
    CONSTRAINT fk_showcase_publisher FOREIGN KEY (published_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '上架记录（行存在=上架中，下架=删行；consent 撤回联动删除）';
