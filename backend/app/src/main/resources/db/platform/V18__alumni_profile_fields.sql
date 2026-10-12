-- ============================================================================
-- V18: 校友库扩展展示字段（荣誉大屏"校友风采/长廊"完整闭环）
-- ----------------------------------------------------------------------------
-- 业务背景：
--   HonorsScreenService.loadAlumni() 当前将 photo/industry/intro/quote/motto
--   写死为空字符串（注释：数据库无此字段，前端降级），导致大屏校友风采始终
--   缺照片 / 缺简介 / 缺座右铭。本升级补齐这 5 个字段：
--     photo_url — 头像/照片 URL（走 attachments 模块上传，返回 URL 写入此列）
--     industry  — 当前所在行业 / 单位
--     intro     — 个人简介（多行，< 500 字）
--     quote     — 人生信条 / 引用
--     motto     — 座右铭
--
-- 写入权限：
--   - 校级 / 系统管理员：可改任意校友的档案
--   - 校友本人：可改自己的档案（service 层校验 user.alumni_base_id == target.id）
--   - 其他角色：仅查看
--
-- 设计要点：
--   - 列全部可空（兼容现有数据）
--   - 不新增表（避免跨模块 join），仅在 alumni_base 表上扩展
--   - 长字符串列采用 utf8mb4 默认 4 字节 / 字符，500 字 ≈ 2KB 安全
--
-- 幂等化（V19 也会做相同动作）：列已存在则跳过；
-- 因为存在手工先跑 / V19 重放的可能，必须幂等。
-- ============================================================================

DROP PROCEDURE IF EXISTS platform_v18_alumni_profile_fields;
CREATE PROCEDURE platform_v18_alumni_profile_fields()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumni_base' AND COLUMN_NAME = 'photo_url') THEN
        ALTER TABLE alumni_base
            ADD COLUMN photo_url VARCHAR(512) DEFAULT NULL
                COMMENT '校友头像/照片 URL（走 /api/v1/attachments 上传后回填）' AFTER grad_year,
            ADD COLUMN industry  VARCHAR(64)  DEFAULT NULL
                COMMENT '当前所在行业 / 单位' AFTER photo_url,
            ADD COLUMN intro     VARCHAR(500) DEFAULT NULL
                COMMENT '个人简介（多行）' AFTER industry,
            ADD COLUMN quote     VARCHAR(200) DEFAULT NULL
                COMMENT '人生信条 / 引言' AFTER intro,
            ADD COLUMN motto     VARCHAR(200) DEFAULT NULL
                COMMENT '座右铭' AFTER quote;
    END IF;
END;
CALL platform_v18_alumni_profile_fields();
DROP PROCEDURE platform_v18_alumni_profile_fields;

-- 索引：常用筛选"已上传照片"的校友用于大屏精选（同样幂等）
DROP PROCEDURE IF EXISTS platform_v18_alumni_photo_idx;
CREATE PROCEDURE platform_v18_alumni_photo_idx()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumni_base' AND INDEX_NAME = 'idx_alumni_base_photo_url') THEN
        CREATE INDEX idx_alumni_base_photo_url ON alumni_base (photo_url);
    END IF;
END;
CALL platform_v18_alumni_photo_idx();
DROP PROCEDURE platform_v18_alumni_photo_idx;