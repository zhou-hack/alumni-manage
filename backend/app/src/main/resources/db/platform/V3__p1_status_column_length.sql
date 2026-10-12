-- ===========================================================================
-- V3：P1 修复 —— archives/honors.status 列宽 16 → 24
-- 原因：P1 新状态 'pending_counselor' 为 17 字符，超出原 VARCHAR(16)（Data truncation）。
-- 幂等：MODIFY COLUMN 本身幂等，重复执行无副作用。
-- ===========================================================================
ALTER TABLE archives MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'draft'
    COMMENT 'draft/pending_counselor/pending_first/pending_second/approved/rejected/deleting/deleted（P1 起新提交强制进 pending_counselor，无导员覆盖自动跳过）';
ALTER TABLE honors MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'pending_counselor'
    COMMENT 'pending_counselor/pending_first/pending_second/approved/rejected（走 Reviews 通道；P1 起新提交强制三级，无导员覆盖自动跳过初审）';
