-- ============================================================================
-- V10：荣誉证书解析草稿 + 团队记录（[M03 扩展 2026-09-25]）
--   1) 加列 honors.is_team_record / team_role：团队荣誉入库时挂提交人名下作"团队公示行"
--   2) 新表 honor_drafts：证书 / 红头文件解析草稿（人工填写 / 后续 OCR 自动填充）
--   3) 新表 honor_draft_members：草稿阶段的人名匹配留证（独立于 honor_team_members，
--       草稿确认后才转写 honor_team_members，确保留证链路完整）
-- ============================================================================
DROP PROCEDURE IF EXISTS platform_v10_alter;
CREATE PROCEDURE platform_v10_alter()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'honors' AND COLUMN_NAME = 'is_team_record') THEN
        ALTER TABLE honors
            ADD COLUMN is_team_record TINYINT(1) NOT NULL DEFAULT 0
                COMMENT '1=团队荣誉公示行（团队入库时自动生成、挂在提交人名下）' AFTER team_id,
            ADD COLUMN team_role VARCHAR(16) DEFAULT NULL
                COMMENT 'leader/member；公示行固定 leader，个人行固定 member' AFTER is_team_record,
            ADD KEY idx_honors_is_team (is_team_record);
    END IF;
END;
CALL platform_v10_alter();
DROP PROCEDURE platform_v10_alter;

CREATE TABLE IF NOT EXISTS honor_drafts (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    attachment_id BIGINT UNSIGNED DEFAULT NULL COMMENT '关联 attachments.id（证书图/扫描件；可空，纯表单录入时为空）',
    operator_id   BIGINT UNSIGNED NOT NULL COMMENT '草稿创建人 users.id',
    kind          VARCHAR(16)    NOT NULL DEFAULT 'solo' COMMENT 'solo=单人 / team=团队',
    title         VARCHAR(128)   DEFAULT NULL COMMENT '奖项名（人工录入或后续 OCR 抽取）',
    category      VARCHAR(32)    DEFAULT NULL COMMENT '荣誉类别 honor_category.value',
    level         VARCHAR(32)    DEFAULT NULL COMMENT '级别 national/provincial/city/school',
    awarded_at    DATE           DEFAULT NULL COMMENT '获奖日期 YYYY-MM-DD',
    college_name  VARCHAR(64)    DEFAULT NULL COMMENT '学院名（OCR/人工）',
    red_doc_url   VARCHAR(512)   DEFAULT NULL COMMENT '红头文件链接',
    member_names  TEXT           DEFAULT NULL COMMENT '原始成员名单（团队草稿使用；分隔符：、，,;；/空白）',
    parsed_json   JSON               NULL COMMENT '结构化抽取结果（OCR 阶段填充；人工阶段为 NULL）',
    status        VARCHAR(16)    NOT NULL DEFAULT 'pending' COMMENT 'pending/confirmed/discarded',
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_draft_operator (operator_id),
    KEY idx_draft_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '[M03 扩展] 荣誉证书解析草稿（人工录入 + 后续 OCR 抽取接入）';

CREATE TABLE IF NOT EXISTS honor_draft_members (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    draft_id      BIGINT UNSIGNED NOT NULL COMMENT 'honor_drafts.id',
    name_raw      VARCHAR(64)    NOT NULL COMMENT '解析出的原始人名（人工编辑后可能变化）',
    alumni_base_id BIGINT UNSIGNED DEFAULT NULL COMMENT '命中校友库 alumni_base.id（人工选择 2 位）',
    user_id       BIGINT UNSIGNED DEFAULT NULL COMMENT '命中已激活账号 users.id',
    match_status  VARCHAR(16)    NOT NULL COMMENT 'matched/duplicated/no_account/ambiguous/unmatched/manual',
    reason        VARCHAR(255)   DEFAULT NULL COMMENT '未匹配/歧义原因',
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_hdm_draft (draft_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '[M03 扩展] 草稿阶段人名匹配留证；确认后转写 honor_team_members';