-- ============================================================================
-- V9：荣誉证书与资料管理扩展（团队荣誉 / 红头文件 / 提交人留痕）
--   1) honors 加列：team_id / member_names / created_by / red_doc_url（幂等守卫）
--   2) 新表 honor_team_members：团队荣誉成员匹配留证（全部人名入表，含未匹配）
-- ============================================================================
DROP PROCEDURE IF EXISTS platform_v9_alter;
CREATE PROCEDURE platform_v9_alter()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'honors' AND COLUMN_NAME = 'team_id') THEN
        ALTER TABLE honors
            ADD COLUMN team_id VARCHAR(36) DEFAULT NULL
                COMMENT '团队荣誉批次 UUID（同批成员共享；单人提交为 NULL）' AFTER archive_id,
            ADD COLUMN member_names VARCHAR(1000) DEFAULT NULL
                COMMENT '团队荣誉原始成员名单文本（人名解析依据，超长截断）' AFTER team_id,
            ADD COLUMN created_by BIGINT UNSIGNED DEFAULT NULL
                COMMENT '提交人 users.id（单人提交=本人；团队/导入=操作者）' AFTER member_names,
            ADD COLUMN red_doc_url VARCHAR(512) DEFAULT NULL
                COMMENT '红头文件/官方文件链接（附件预览地址或外部 URL）' AFTER created_by,
            ADD KEY idx_honors_team (team_id);
    END IF;
END;
CALL platform_v9_alter();
DROP PROCEDURE platform_v9_alter;

CREATE TABLE IF NOT EXISTS honor_team_members (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    team_id       VARCHAR(36)  NOT NULL COMMENT '团队批次 UUID（关联 honors.team_id）',
    name_raw      VARCHAR(64)  NOT NULL COMMENT '解析出的原始人名',
    alumni_base_id BIGINT UNSIGNED DEFAULT NULL COMMENT '命中校友库 alumni_base.id（未匹配为 NULL）',
    user_id       BIGINT UNSIGNED DEFAULT NULL COMMENT '命中已激活账号 users.id',
    honor_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '生成的荣誉 honors.id（matched 才有）',
    match_status  VARCHAR(16)  NOT NULL COMMENT 'matched/duplicated/no_account/ambiguous/unmatched',
    reason        VARCHAR(255) DEFAULT NULL COMMENT '未匹配/歧义原因（ambiguous 存候选摘要）',
    created_by    BIGINT UNSIGNED DEFAULT NULL COMMENT '提交人 users.id（团队详情可见性校验用）',
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_htm_team (team_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '[M03] 团队荣誉成员匹配留证（人名识别结果，全部人名含未匹配入表）';
