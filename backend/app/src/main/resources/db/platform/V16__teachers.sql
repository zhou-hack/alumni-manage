-- V13：教师数据接入（内网同步 + 7 天快照备份）
-- 背景：教师数据按组织架构「院系 → 专业」接入（教师唯一归属一个专业，一对一；班级/个人层级本期不服务教师）。
--   数据源为学校内网 REST 中间接口，以云端为准全量覆盖：新增/更新按工号 upsert，云端消失的教师置 disabled（软停用，不物理删）。
--   每次同步前将 teachers 全量快照到 personal_data_backup（行 JSON），留存 7 天滚动删除。
-- 1) teachers：教师库（college_id/major_id 关联 dicts.id 但不设外键，映射失败不阻断同步；college_name/major_name 存原文快照兜底展示）
-- 2) personal_data_backup：个人数据同步前全量快照（行 JSON 对表结构演进免疫；7 天滚动）
-- 3) teacher_sync_logs：同步记录（定时/手动、成败、增改停计数），供管理端展示

CREATE TABLE IF NOT EXISTS teachers (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    teacher_no      VARCHAR(32)  NOT NULL COMMENT '工号（同步 upsert 唯一键）',
    name            VARCHAR(64)  NOT NULL COMMENT '姓名',
    college_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '院系 dicts.id（映射失败为 NULL，无外键）',
    major_id        BIGINT UNSIGNED DEFAULT NULL COMMENT '专业 dicts.id（教师唯一归属一个专业；映射失败为 NULL）',
    college_name    VARCHAR(64)  DEFAULT NULL COMMENT '院系原文快照（内网数据，展示兜底）',
    major_name      VARCHAR(64)  DEFAULT NULL COMMENT '专业原文快照（内网数据，展示兜底）',
    title           VARCHAR(64)  DEFAULT NULL COMMENT '职称（选填）',
    status          VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT 'active/disabled（云端删除置 disabled，不物理删）',
    external_id     VARCHAR(64)  DEFAULT NULL COMMENT '内网系统外部 ID',
    last_sync_batch VARCHAR(36)  DEFAULT NULL COMMENT '最近同步批次 UUID',
    last_synced_at  TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最近同步时间',
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_teachers_teacher_no (teacher_no),
    KEY idx_teachers_college_major (college_id, major_id),
    KEY idx_teachers_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '教师库（内网同步，云端为准全量覆盖）';

CREATE TABLE IF NOT EXISTS personal_data_backup (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    source_table  VARCHAR(64)    NOT NULL COMMENT '来源表（本期恒 teachers）',
    snapshot_date DATE           NOT NULL COMMENT '快照日期（留存 7 天滚动删除）',
    batch_id      VARCHAR(36)    NOT NULL COMMENT '同步批次 UUID',
    row_id        BIGINT UNSIGNED NOT NULL COMMENT '来源表行主键',
    row_json      JSON           NOT NULL COMMENT '行全量 JSON 快照',
    created_at    TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_pdb_source_date (source_table, snapshot_date),
    KEY idx_pdb_batch (batch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '个人数据同步前全量快照（7 天滚动）';

CREATE TABLE IF NOT EXISTS teacher_sync_logs (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    batch_id       VARCHAR(36)    NOT NULL COMMENT '同步批次 UUID',
    trigger_type   VARCHAR(16)    NOT NULL COMMENT 'scheduled/manual',
    operator_id    BIGINT UNSIGNED DEFAULT NULL COMMENT '手动触发人 users.id（定时为 NULL）',
    result         VARCHAR(16)    NOT NULL COMMENT 'success/failed',
    fetched_count  INT            NOT NULL DEFAULT 0 COMMENT '拉取条数',
    added_count    INT            NOT NULL DEFAULT 0,
    updated_count  INT            NOT NULL DEFAULT 0,
    disabled_count INT            NOT NULL DEFAULT 0,
    error_message  VARCHAR(500)   DEFAULT NULL COMMENT '失败原因',
    started_at     TIMESTAMP(6)   NOT NULL COMMENT '同步开始时间',
    finished_at    TIMESTAMP(6)   NULL DEFAULT NULL COMMENT '同步结束时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tsl_batch (batch_id),
    KEY idx_tsl_started (started_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '教师同步记录';
