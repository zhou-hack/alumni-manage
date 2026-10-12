-- ============================================================================
-- V4：hero_slide 增加展示位置（placement）
--   hero   = 门户首屏大图轮播（默认，存量数据归此）
--   campus = 首页「美丽校园」模块轮播
-- 同一后台模块（/admin/hero/slides）按位置筛选维护，无需独立后端模块。
-- ============================================================================
ALTER TABLE hero_slide
    ADD COLUMN placement VARCHAR(20) NOT NULL DEFAULT 'hero' COMMENT '展示位置：hero 首屏轮播 / campus 美丽校园' AFTER status;

ALTER TABLE hero_slide
    ADD INDEX idx_hero_slide_placement (placement, status, display_order);
