-- archives 模块初始化：建表（与 backend/db/schema.sql v3 定义一致，含 prev_status 扩展列）
-- 共享单库约定：本模块迁移历史记录于 flyway_history_archives，与其他模块互不影响
-- prev_status：delete-request 前的原状态快照（archive_delete 审核驳回后懒恢复用；reviews 模块不回调业务）

CREATE TABLE IF NOT EXISTS archives (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '归属校友',
    title         VARCHAR(128) NOT NULL,
    category      VARCHAR(32)  NOT NULL COMMENT 'award/experience/certificate……自由扩展',
    content       TEXT         DEFAULT NULL,
    remark        VARCHAR(255) DEFAULT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'draft'
                  COMMENT 'draft/pending_first/pending_second/approved/rejected/deleting/deleted',
    version       INT          NOT NULL DEFAULT 1 COMMENT '驳回重提 version+1，版本历史见 review_logs',
    reject_reason VARCHAR(500) DEFAULT NULL COMMENT '最近一次驳回原因（回显用）',
    prev_status   VARCHAR(16)  DEFAULT NULL COMMENT 'delete-request 前的原状态（archive_delete 驳回恢复用）',
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_archives_user (user_id),
    KEY idx_archives_status_cat (status, category),
    KEY idx_archives_created (created_at),
    CONSTRAINT fk_archives_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '校友档案（keyword/college/grad_year 经 users+alumni_base 关联筛选）';

-- 已存在表补列（schema.sql v3 建库后老库演进；MySQL 8.x 无 ADD COLUMN IF NOT EXISTS，经 information_schema 判定）
DROP PROCEDURE IF EXISTS archives_add_prev_status;
CREATE PROCEDURE archives_add_prev_status()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'archives' AND COLUMN_NAME = 'prev_status') THEN
        ALTER TABLE archives
            ADD COLUMN prev_status VARCHAR(16) DEFAULT NULL COMMENT 'delete-request 前的原状态（archive_delete 驳回恢复用）' AFTER reject_reason;
    END IF;
END;
CALL archives_add_prev_status();
DROP PROCEDURE archives_add_prev_status;
