-- ============================================================================
-- V12：留言墙软删除 + 自助恢复 (PRD 留言墙 P3-Q4)
--   - deleted_at NULL → 留言有效（pending/approved/rejected/hidden 四种状态）
--   - deleted_at 非 NULL → 留言人主动撤回（软删，保留行）
--   - 重新发送 (POST /wall/messages/{id}/resubmit) → 复制原留言内容生成一条新的 pending
--   - 编辑 (PUT /wall/messages/{id}) → 仅本人 + 状态=pending/rejected 可改
-- ============================================================================
ALTER TABLE wall_message
    ADD COLUMN deleted_at DATETIME(6) NULL COMMENT '软删除时间（NULL=有效；非 NULL=留言人撤回/删除）' AFTER reviewed_at;