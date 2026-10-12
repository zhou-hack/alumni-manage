-- ============================================================================
-- V5：announcements.content 扩容 TEXT(64KB) → MEDIUMTEXT(16MB)
--   公告正文支持 Markdown 图文混排，上限放宽至 10MB（前端校验 10 * 1024 * 1024 字符）
-- ============================================================================
ALTER TABLE announcements
    MODIFY COLUMN content MEDIUMTEXT NOT NULL COMMENT '公告内容（支持 Markdown，上限 10MB）';
