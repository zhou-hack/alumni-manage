-- ============================================================================
-- V14：校友照片收集管理（导员审核 + 标签 + 推送相册）
-- ----------------------------------------------------------------------------
-- 数据流：
--   1) 上传：校友在个人中心选标签上传照片 → photo_collect(status=pending)
--   2) 审核：辅导员/校级审核通过 → status=approved（进入相册库）
--                驳回 → status=rejected（校友可重新提交）
--   3) 推送：超管/校级在后台批量推送给特定校友/群体 → 写 photo_push_log + 发 notifications
--   4) 个人中心：本人只能看自己提交的；通过审核的自动展示在「我的相册」
--
-- 标签字段：JSON 数组存 tag 编码（前端展示时再字典翻译），便于复用现有 dicts 表
-- ============================================================================

CREATE TABLE IF NOT EXISTS photo_collect (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL COMMENT '提交人 users.id',
    attachment_id BIGINT       NOT NULL COMMENT '附件 attachments.id（复用上传组件）',
    title         VARCHAR(128) DEFAULT NULL COMMENT '照片标题（校友自填）',
    caption       VARCHAR(500) DEFAULT NULL COMMENT '照片说明',
    -- 标签 JSON 数组：["graduation_2020","sports_meet"]；前端按 dicts.photo_tag 翻译
    tags          JSON         NOT NULL COMMENT '标签列表',
    -- 状态机：pending 待审核 / approved 通过 / rejected 驳回 / pushed 已推送
    status        VARCHAR(16)  NOT NULL DEFAULT 'pending',
    reject_reason VARCHAR(255) DEFAULT NULL COMMENT '驳回原因',
    reviewed_by   BIGINT       DEFAULT NULL COMMENT '审核人 users.id',
    reviewed_at   DATETIME(6)  DEFAULT NULL,
    -- 推送统计：被推送给多少人（推送完成后回填）
    pushed_count  INT          NOT NULL DEFAULT 0,
    last_push_at  DATETIME(6)  DEFAULT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_photo_user (user_id),
    KEY idx_photo_status (status),
    KEY idx_photo_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '校友照片收集（提交 → 审核 → 推送相册）';

CREATE TABLE IF NOT EXISTS photo_push_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    photo_id      BIGINT       NOT NULL COMMENT '被推送的 photo_collect.id',
    -- 推送对象：all_users / college / role / single_user
    target_type   VARCHAR(32)  NOT NULL,
    target_id     VARCHAR(64)  DEFAULT NULL COMMENT 'college_id 或 role 或 user_id',
    -- 实际触达数（统计用）
    recipients    INT          NOT NULL DEFAULT 0,
    operator_id   BIGINT       NOT NULL COMMENT '执行推送的管理员',
    note          VARCHAR(255) DEFAULT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_push_photo (photo_id),
    KEY idx_push_operator (operator_id),
    KEY idx_push_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '照片推送日志（谁推、推给谁、触达多少人）';

-- 在 attachments 表索引上扩展：让按 biz_type='photo_collect' + ref_id 查询更高效
-- 不强制：若索引已包含 ref_id 则跳过
SET @sql_add_index = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attachments'
                  AND INDEX_NAME = 'idx_attachments_biz_ref'),
        'SELECT 1',
        'CREATE INDEX idx_attachments_biz_ref ON attachments(biz_type, ref_id)'
    )
);
PREPARE stmt FROM @sql_add_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
