-- ============================================================================
-- V17: 荣誉大屏上下架重构
-- ----------------------------------------------------------------------------
-- 业务背景：
--   现有 honors.status 字段语义是「审核状态」(pending_counselor/.../approved/rejected)，
--   与「是否在前台大屏展示」语义不同。一个荣誉可能在审核库被驳回，
--   也要能在归档大屏展示；反之通过审核的荣誉，也可能临时下架维护。
--
-- 改造方案：
--   1. 在 honors 表新增 screen_status 字段（独立列），枚举：
--      online   — 前台大屏可见
--      offline  — 前台大屏不可见（默认；老数据默认填充）
--   2. 新增 honor_screen_log 表，记录每一次上下架变更（含操作人/原因/前后状态）
--   3. honor_screen_log 写入由应用层统一处理（不允许直接 UPDATE honors.screen_status）
--
-- 设计要点：
--   - screen_status 与 status 完全解耦，SQL 触发器抓 status='approved' 也会被允许上下架
--   - 大屏前台接口（screen 接口）只返回 screen_status='online' 的数据
--   - 后台管理页对全量 honors 做管理，screen_status 字段支持筛选/批量/单条切换
--   - 操作日志保留所有变更以满足审计需求
-- ============================================================================

-- 1) honors 加列（默认 offline，老数据安全）
ALTER TABLE honors
    ADD COLUMN screen_status VARCHAR(16) NOT NULL DEFAULT 'offline'
        COMMENT '大屏上下架状态：online 可见 / offline 不可见（默认；与审核 status 独立）' AFTER status;

CREATE INDEX idx_honors_screen_status ON honors (screen_status);

-- 2) 上下架操作日志表
CREATE TABLE IF NOT EXISTS honor_screen_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    honor_id        BIGINT       NOT NULL COMMENT '关联 honors.id',
    action          VARCHAR(16)  NOT NULL COMMENT '操作类型：online 上架 / offline 下架 / batch_online 批量上架 / batch_offline 批量下架',
    before_status   VARCHAR(16)  NOT NULL COMMENT '变更前 screen_status',
    after_status     VARCHAR(16)  NOT NULL COMMENT '变更后 screen_status',
    reason          VARCHAR(500) DEFAULT NULL COMMENT '操作原因/理由（可空）',
    operator_id     BIGINT       DEFAULT NULL COMMENT '操作人 users.id（admin）',
    operator_name   VARCHAR(64)  DEFAULT NULL COMMENT '操作人姓名（冗余存，避免 user 被删后丢审计线索）',
    operator_role   VARCHAR(32)  DEFAULT NULL COMMENT '操作人角色（college_admin/school_admin/system_admin）',
    client_ip       VARCHAR(64)  DEFAULT NULL COMMENT '操作 IP（X-Forwarded-For 优先）',
    batch_size      INT          DEFAULT NULL COMMENT '批量操作时的影响行数（单条操作为 1）',
    request_id      VARCHAR(64)  DEFAULT NULL COMMENT '关联 RequestId（用于日志串联）',
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '变更时间',

    PRIMARY KEY (id),
    KEY idx_honor_screen_log_honor (honor_id),
    KEY idx_honor_screen_log_action (action),
    KEY idx_honor_screen_log_operator (operator_id),
    KEY idx_honor_screen_log_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '荣誉大屏上下架变更日志（审计）';

-- 3) 历史兼容：现有 honors 表默认 screen_status='offline'
--   管理员首次在后台主动点「上架」才会进入大屏；老数据不自动上线，避免出现内容误展示