-- ============================================================================
-- V15：组织架构同步日志（手动触发的学生/老师数据拉取记录）
-- ----------------------------------------------------------------------------
-- 场景：
--   校级管理员在后台点「一键同步组织架构」 → 调用 SPI 适配器
--   → 同步来源：本期 MVP 是 Mock（读取 alumni_base 现有数据视为「组织」）
--   → 同步结果写入 org_sync_log（成功/失败条数、详情）
--   → 同步行为本身写 audit_logs
--
-- 设计：org_sync_log 单独表，便于前端展示「最近 20 次同步历史」；
--         audit_logs 仅保留触发事件（action=org_sync），方便审计检索。
-- ============================================================================

CREATE TABLE IF NOT EXISTS org_sync_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    -- 同步来源：alumni_base（读取现有 alumni_base 表作为组织基准）/ external_db（预留对接外部）
    source        VARCHAR(32)  NOT NULL,
    -- 同步类型：users（用户） / alumni_base（学籍） / dicts（字典：学院/专业）
    sync_type     VARCHAR(32)  NOT NULL,
    -- 同步模式：incremental（增量）/ full（全量）
    mode          VARCHAR(16)  NOT NULL DEFAULT 'incremental',
    -- 结果统计
    total         INT          NOT NULL DEFAULT 0 COMMENT '源端记录总数',
    inserted      INT          NOT NULL DEFAULT 0 COMMENT '新增',
    updated       INT          NOT NULL DEFAULT 0 COMMENT '更新',
    skipped       INT          NOT NULL DEFAULT 0 COMMENT '无变化跳过',
    failed        INT          NOT NULL DEFAULT 0 COMMENT '失败',
    -- 失败明细（截断存储）
    failures      JSON         DEFAULT NULL,
    -- 触发人
    operator_id   BIGINT       NOT NULL,
    started_at    DATETIME(6)  NOT NULL,
    finished_at   DATETIME(6)  DEFAULT NULL,
    -- 状态：running / success / failed / partial（部分成功）
    status        VARCHAR(16)  NOT NULL DEFAULT 'running',
    -- 备注
    remark        VARCHAR(255) DEFAULT NULL,

    PRIMARY KEY (id),
    KEY idx_orgsync_started (started_at),
    KEY idx_orgsync_operator (operator_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '组织架构同步日志（手动同步用户/学籍/字典）';
