-- ============================================================================
-- V13: 荣誉大屏管理 — honor_screen_config 表
-- ----------------------------------------------------------------------------
-- 用途：管理员在大屏后台配置大屏展示参数（布局/主色/刷新频率/显示字段等）。
-- 数据特征：
--   - 同一时刻只允许一条「激活」配置（status='active'），其余为历史版本/草稿
--   - 单一全局配置场景：name='default'，UNIQUE 约束保证
--   - 配置字段用 JSON 存，避免频繁 DDL；具体字段由前端 Schema 决定
--
-- 与荣誉数据的关系：
--   本表只存"展示配置"，实际荣誉数据来源仍为 honors 表（CRUD 走现有 honors 模块）
-- ============================================================================

CREATE TABLE IF NOT EXISTS honor_screen_config (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name         VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT '配置名（预留多版本/多场景，当前业务只用 default）',
    layout       VARCHAR(32)  NOT NULL DEFAULT 'grid-3' COMMENT '大屏布局：grid-3 三列网格 / grid-4 四列网格 / waterfall 瀑布流',
    theme_color  VARCHAR(16)  NOT NULL DEFAULT 'tech' COMMENT '主色预设：tech 蓝青 / gold 金色 / dark 深色 / warm 暖色',
    refresh_sec  INT          NOT NULL DEFAULT 30 COMMENT '自动刷新间隔（秒）；0=手动刷新',
    show_fields  JSON         NOT NULL COMMENT '显示字段开关：{ title, level, category, granted_by, awarded_at, owner } 等',
    sort_by      VARCHAR(32)  NOT NULL DEFAULT 'awarded_at_desc' COMMENT '默认排序：awarded_at_desc / level_asc / pinned_first',
    max_items    INT          NOT NULL DEFAULT 50 COMMENT '大屏最大展示条数（1~200）',
    status       VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT '状态：active 当前生效 / draft 草稿 / archived 历史',
    remark       VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_by   BIGINT       DEFAULT NULL COMMENT '创建人 users.id（管理员）',
    updated_by   BIGINT       DEFAULT NULL COMMENT '最后修改人 users.id',
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_honor_screen_config_name (name),
    KEY idx_honor_screen_config_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '荣誉大屏展示配置（管理员在后台维护，portal H5 大屏读取生效项）';

-- 初始化一条默认配置，避免前端首次进入出现「未配置」空态
INSERT INTO honor_screen_config (name, layout, theme_color, refresh_sec, show_fields, sort_by, max_items, status, remark)
VALUES (
    'default',
    'grid-3',
    'tech',
    30,
    JSON_OBJECT('title', true, 'level', true, 'category', true, 'granted_by', true, 'awarded_at', true, 'owner', false),
    'awarded_at_desc',
    50,
    'active',
    '系统初始化默认配置'
)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);
