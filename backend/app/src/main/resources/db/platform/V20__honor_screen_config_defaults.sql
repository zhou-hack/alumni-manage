-- ============================================================================
-- V20: 荣誉大屏配置 — 空值字段兜底
-- ----------------------------------------------------------------------------
-- 修复遗留 bug：之前通过 PUT /admin/screen/config/{id} 更新时，部分必填字段
-- 在客户端为 undefined 被 Jackson 序列化为 null，导致 DB 中存入了空字符串，
-- 后续更新时 @NotBlank 校验失败，表现为「修改布局报 500 / 主题色无法更改」。
--
-- 兜底策略（仅修复空字符串 / NULL 的非法状态值，不覆盖已有合法值）：
--   layout        → 'grid-3'
--   theme_color   → 'tech'
--   sort_by       → 'awarded_at_desc'
--   status        → 'draft'   （避免 NULL 状态）
-- ============================================================================

UPDATE honor_screen_config
SET layout = 'grid-3'
WHERE layout IS NULL OR layout = '';

UPDATE honor_screen_config
SET theme_color = 'tech'
WHERE theme_color IS NULL OR theme_color = '';

UPDATE honor_screen_config
SET sort_by = 'awarded_at_desc'
WHERE sort_by IS NULL OR sort_by = '';

UPDATE honor_screen_config
SET status = 'draft'
WHERE status IS NULL OR status = '';

UPDATE honor_screen_config
SET refresh_sec = 30
WHERE refresh_sec IS NULL;

UPDATE honor_screen_config
SET max_items = 50
WHERE max_items IS NULL;