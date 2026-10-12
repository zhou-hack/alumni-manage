-- ============================================================================
-- 第五章 Honors 校友荣誉（独立结构化资源，统计口径唯一来源）
-- ============================================================================

CREATE TABLE IF NOT EXISTS honors (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    archive_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '可关联档案（非 Archive 的 category）',
    title       VARCHAR(128) NOT NULL,
    category    VARCHAR(32)  NOT NULL COMMENT 'scholarship/competition……对齐 honor_category 字典',
    level       VARCHAR(32)  NOT NULL COMMENT 'national/provincial/school……',
    awarded_at  DATE         NOT NULL COMMENT '纯日期 YYYY-MM-DD',
    status      VARCHAR(16)  NOT NULL DEFAULT 'pending_first'
                COMMENT 'pending_first/pending_second/approved/rejected（走 Reviews 通道）',
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_honors_user (user_id),
    KEY idx_honors_cat_level (category, level),
    KEY idx_honors_status (status),
    KEY idx_honors_awarded (awarded_at),
    CONSTRAINT fk_honors_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_honors_archive FOREIGN KEY (archive_id) REFERENCES archives (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '校友荣誉（统计接口唯一数据源）';
