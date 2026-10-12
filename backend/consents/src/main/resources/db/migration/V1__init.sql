-- ============================================================================
-- 第九章 Consents 隐私授权
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
