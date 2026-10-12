-- ============================================================================
-- 校友管理平台 · 单体合并迁移 V1（全量，唯一迁移脚本）
-- 由 12 个模块迁移脚本 + 历次增量迁移按外键依赖顺序合并而成，全部幂等：
--   auth V1/V2 + dicts V1/V2/V3 + attachments V1/V2 + archives V1
--   + reviews V1 + consents V1 + honors V1 + showcase V1
--   + announcements（原 V3）+ drafts（原 V4）+ outstanding（原 V5，已折入
--     原 V6 grad_year INT / 原 V8 photo_url MEDIUMTEXT 列修正）
--   + wall（原 V7）+ users.uuid 补列（原 V2，与 auth V2 段同源幂等）
-- 兼容两条部署路径：
--   a) schema.sql 先行建库（非空库，baseline 0 后本脚本全部 no-op）；
--   b) 全新空库，由本脚本一次性建全结构 + 种子。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- auth V1（幂等化：补 IF NOT EXISTS）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alumni_base (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    student_id  VARCHAR(32)  NOT NULL COMMENT '学号',
    real_name   VARCHAR(64)  NOT NULL COMMENT '真实姓名',
    id_card     VARCHAR(32)  NOT NULL COMMENT '身份证号（明文存储，仅用于激活核验）',
    phone       VARCHAR(16)  NOT NULL COMMENT '校友库登记手机号',
    college     VARCHAR(64)  DEFAULT NULL COMMENT '学院',
    major       VARCHAR(64)  DEFAULT NULL COMMENT '专业',
    grad_year   INT          NOT NULL COMMENT '毕业年份',
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_alumni_base_student_id (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '校友库（激活三要素核验数据源）';

CREATE TABLE IF NOT EXISTS users (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    alumni_base_id BIGINT UNSIGNED NOT NULL COMMENT '关联校友库 ID',
    student_id     VARCHAR(32)  NOT NULL,
    phone          VARCHAR(16)  NOT NULL COMMENT '登录手机号（激活时可换绑）',
    nickname       VARCHAR(64)  NOT NULL,
    role           VARCHAR(32)  NOT NULL COMMENT 'student/alumni/counselor/college_admin/school_admin/system_admin',
    password_hash  VARCHAR(100) NOT NULL COMMENT 'BCrypt(SHA-256(原文)) 双层散列',
    created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_student_id (student_id),
    UNIQUE KEY uk_users_phone (phone),
    UNIQUE KEY uk_users_alumni_base_id (alumni_base_id),
    CONSTRAINT fk_users_alumni_base FOREIGN KEY (alumni_base_id) REFERENCES alumni_base (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '平台账号';

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    token_hash VARCHAR(64)    NOT NULL COMMENT 'SHA-256(refresh_token)，不落明文',
    expires_at TIMESTAMP(6)   NOT NULL,
    revoked    TINYINT(1)      NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_user (user_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '刷新令牌（轮换制）';

-- auth V2：users.uuid（幂等化：列存在即跳过）
DROP PROCEDURE IF EXISTS platform_users_add_uuid;
CREATE PROCEDURE platform_users_add_uuid()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'uuid') THEN
        ALTER TABLE users ADD COLUMN uuid VARCHAR(36) NULL AFTER id;
        UPDATE users SET uuid = UUID() WHERE uuid IS NULL;
        ALTER TABLE users MODIFY COLUMN uuid VARCHAR(36) NOT NULL, ADD UNIQUE KEY uk_users_uuid (uuid);
    END IF;
END;
CALL platform_users_add_uuid();
DROP PROCEDURE platform_users_add_uuid;

-- ---------------------------------------------------------------------------
-- dicts V1：建表 + 种子
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dicts (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    type       VARCHAR(32) NOT NULL COMMENT 'college/major/grad_year/honor_category，随业务扩充',
    label      VARCHAR(64) NOT NULL COMMENT '展示文本',
    value      VARCHAR(64) NOT NULL COMMENT '存储值',
    sort       INT         NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dicts_type_value (type, value),
    KEY idx_dicts_type_sort (type, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '基础字典（被引用项禁止删除：409 RESOURCE_REFERENCED）';

-- 学院
INSERT INTO dicts (type, label, value, sort) VALUES
('college', '计算机学院',   'CS',  1),
('college', '电子信息学院', 'EE',  2),
('college', '机械工程学院', 'ME',  3),
('college', '经济管理学院', 'EM',  4),
('college', '外国语学院',   'FL',  5),
('college', '理学院',       'SCI', 6),
('college', '文学院',       'LIT', 7)
ON DUPLICATE KEY UPDATE label = VALUES(label), sort = VALUES(sort);

-- 专业
INSERT INTO dicts (type, label, value, sort) VALUES
('major', '软件工程',         'SE',   1),
('major', '计算机科学与技术', 'CS',   2),
('major', '电子信息工程',     'EE',   3),
('major', '机械工程',         'ME',   4),
('major', '金融学',           'FIN',  5),
('major', '英语',             'ENG',  6),
('major', '数学',             'MATH', 7),
('major', '物理学',           'PHYS', 8),
('major', '汉语言文学',       'CL',   9),
('major', '会计学',           'ACCT', 10)
ON DUPLICATE KEY UPDATE label = VALUES(label), sort = VALUES(sort);

-- 荣誉类别
INSERT INTO dicts (type, label, value, sort) VALUES
('honor_category', '奖学金',   'scholarship', 1),
('honor_category', '学科竞赛', 'competition', 2),
('honor_category', '科研成果', 'research',    3),
('honor_category', '其他',     'other',       4)
ON DUPLICATE KEY UPDATE label = VALUES(label), sort = VALUES(sort);

-- ---------------------------------------------------------------------------
-- dicts V2：层级化（parent_id 自引用，幂等存储过程）
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS platform_dicts_add_parent_id;
CREATE PROCEDURE platform_dicts_add_parent_id()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dicts' AND COLUMN_NAME = 'parent_id') THEN
        ALTER TABLE dicts
            ADD COLUMN parent_id BIGINT UNSIGNED NULL COMMENT '父级字典项（major 归属 college）' AFTER type,
            ADD KEY idx_dicts_parent (parent_id),
            ADD CONSTRAINT fk_dicts_parent FOREIGN KEY (parent_id) REFERENCES dicts (id);
    END IF;
END;
CALL platform_dicts_add_parent_id();
DROP PROCEDURE platform_dicts_add_parent_id;

-- 存量专业归位
UPDATE dicts m JOIN dicts c ON c.type = 'college' AND c.value = 'CS'  SET m.parent_id = c.id WHERE m.type = 'major' AND m.value IN ('SE', 'CS');
UPDATE dicts m JOIN dicts c ON c.type = 'college' AND c.value = 'EE'  SET m.parent_id = c.id WHERE m.type = 'major' AND m.value IN ('EE');
UPDATE dicts m JOIN dicts c ON c.type = 'college' AND c.value = 'ME'  SET m.parent_id = c.id WHERE m.type = 'major' AND m.value IN ('ME');
UPDATE dicts m JOIN dicts c ON c.type = 'college' AND c.value = 'EM'  SET m.parent_id = c.id WHERE m.type = 'major' AND m.value IN ('FIN', 'ACCT');
UPDATE dicts m JOIN dicts c ON c.type = 'college' AND c.value = 'FL'  SET m.parent_id = c.id WHERE m.type = 'major' AND m.value IN ('ENG');
UPDATE dicts m JOIN dicts c ON c.type = 'college' AND c.value = 'SCI' SET m.parent_id = c.id WHERE m.type = 'major' AND m.value IN ('MATH', 'PHYS');
UPDATE dicts m JOIN dicts c ON c.type = 'college' AND c.value = 'LIT' SET m.parent_id = c.id WHERE m.type = 'major' AND m.value IN ('CL');

-- 每个学院补足第二专业（幂等：唯一键 type+value 命中则只回填 parent_id）
INSERT INTO dicts (type, parent_id, label, value, sort)
SELECT 'major', c.id, '通信工程', 'CE', 2 FROM dicts c WHERE c.type = 'college' AND c.value = 'EE'
ON DUPLICATE KEY UPDATE parent_id = c.id;
INSERT INTO dicts (type, parent_id, label, value, sort)
SELECT 'major', c.id, '车辆工程', 'VE', 2 FROM dicts c WHERE c.type = 'college' AND c.value = 'ME'
ON DUPLICATE KEY UPDATE parent_id = c.id;
INSERT INTO dicts (type, parent_id, label, value, sort)
SELECT 'major', c.id, '日语', 'JP', 2 FROM dicts c WHERE c.type = 'college' AND c.value = 'FL'
ON DUPLICATE KEY UPDATE parent_id = c.id;
INSERT INTO dicts (type, parent_id, label, value, sort)
SELECT 'major', c.id, '新闻学', 'JOUR', 2 FROM dicts c WHERE c.type = 'college' AND c.value = 'LIT'
ON DUPLICATE KEY UPDATE parent_id = c.id;

-- dicts V3：grad_year 虚拟化（不落库，删除种子行）
DELETE FROM dicts WHERE type = 'grad_year';

-- ---------------------------------------------------------------------------
-- attachments V1：附件表 + 删除审计表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attachments (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    biz_type      VARCHAR(32)  NOT NULL COMMENT 'archive/honor/avatar/activate……目录隔离',
    ref_id        BIGINT UNSIGNED DEFAULT NULL COMMENT '业务记录 ID，NULL=未引用（孤儿附件定时清理依据）',
    status        VARCHAR(16)  NOT NULL DEFAULT 'unreferenced' COMMENT 'unreferenced/referenced',
    url           VARCHAR(255) NOT NULL COMMENT '相对路径 /files/{biz_type}/yyyy/MM/xx',
    name          VARCHAR(128) NOT NULL COMMENT '原始文件名',
    size          BIGINT       NOT NULL COMMENT '字节数（图片为压缩后）',
    mime          VARCHAR(64)  NOT NULL,
    uploader_id   BIGINT UNSIGNED NOT NULL,
    remark        VARCHAR(255) DEFAULT NULL,
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    referenced_at TIMESTAMP(6) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_attachments_biz_ref (biz_type, ref_id),
    KEY idx_attachments_orphan (status, created_at),
    CONSTRAINT fk_attachments_uploader FOREIGN KEY (uploader_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '附件（ref_id IS NOT NULL 即已引用；孤儿 24h 定时清理）';

CREATE TABLE IF NOT EXISTS attachment_audit_log (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    operator_id   BIGINT UNSIGNED NOT NULL COMMENT '操作人（college_admin/school_admin）',
    attachment_id BIGINT UNSIGNED NOT NULL,
    filename      VARCHAR(128) NOT NULL,
    biz_type      VARCHAR(32)  NOT NULL,
    ip            VARCHAR(45)  NOT NULL COMMENT '支持 IPv6',
    reason        VARCHAR(255) DEFAULT NULL COMMENT '删除原因',
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_attachment_audit_time (created_at),
    KEY idx_attachment_audit_operator (operator_id),
    CONSTRAINT fk_attachment_audit_operator FOREIGN KEY (operator_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '附件删除审计（只增不删，保留≥180天，建议按月归档）';

-- ---------------------------------------------------------------------------
-- attachments V2（imports）：任务表 + 错误行报告表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS import_tasks (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uploader_id BIGINT UNSIGNED NOT NULL COMMENT 'college_admin',
    filename    VARCHAR(128)    NOT NULL,
    file_size   BIGINT          NOT NULL COMMENT '字节数，≤10MB',
    status      VARCHAR(16)     NOT NULL DEFAULT 'processing' COMMENT 'processing/done',
    total       INT             NOT NULL DEFAULT 0,
    success     INT             NOT NULL DEFAULT 0,
    failed      INT             NOT NULL DEFAULT 0,
    created_at  TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finished_at TIMESTAMP(6)    DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_import_tasks_uploader (uploader_id),
    CONSTRAINT fk_import_tasks_uploader FOREIGN KEY (uploader_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '导入任务（坏行不阻断好行，合法行入 alumni_base）';

CREATE TABLE IF NOT EXISTS import_errors (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    import_id  BIGINT UNSIGNED NOT NULL,
    row_no     INT             NOT NULL COMMENT '原始行号',
    student_id VARCHAR(32)     DEFAULT NULL,
    reason     VARCHAR(255)    NOT NULL COMMENT '如：学号重复/格式错误',
    created_at TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_import_errors_task (import_id),
    CONSTRAINT fk_import_errors_task FOREIGN KEY (import_id) REFERENCES import_tasks (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '导入错误行报告';

-- ---------------------------------------------------------------------------
-- archives V1：档案表（含 prev_status 扩展列）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS archives (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '归属校友',
    title         VARCHAR(128) NOT NULL,
    category      VARCHAR(32)  NOT NULL COMMENT 'award/experience/certificate……自由扩展',
    content       TEXT         DEFAULT NULL,
    remark        VARCHAR(255) DEFAULT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'draft'
                  COMMENT 'draft/pending_first/pending_second/approved/rejected/deleting/deleted',
    version       INT          NOT NULL DEFAULT 1 COMMENT '驳回重提 version+1，版本历史见 review_logs',
    reject_reason VARCHAR(500) DEFAULT NULL COMMENT '最近一次驳回原因（回显用）',
    prev_status   VARCHAR(16)  DEFAULT NULL COMMENT 'delete-request 前的原状态（archive_delete 驳回恢复用）',
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_archives_user (user_id),
    KEY idx_archives_status_cat (status, category),
    KEY idx_archives_created (created_at),
    CONSTRAINT fk_archives_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '校友档案（keyword/college/grad_year 经 users+alumni_base 关联筛选）';

DROP PROCEDURE IF EXISTS platform_archives_add_prev_status;
CREATE PROCEDURE platform_archives_add_prev_status()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'archives' AND COLUMN_NAME = 'prev_status') THEN
        ALTER TABLE archives
            ADD COLUMN prev_status VARCHAR(16) DEFAULT NULL COMMENT 'delete-request 前的原状态（archive_delete 驳回恢复用）' AFTER reject_reason;
    END IF;
END;
CALL platform_archives_add_prev_status();
DROP PROCEDURE platform_archives_add_prev_status;

-- ---------------------------------------------------------------------------
-- reviews V1：审核中心
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reviews (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    biz_type     VARCHAR(32)  NOT NULL COMMENT 'archive/honor/archive_delete/activate_manual',
    biz_id       BIGINT UNSIGNED DEFAULT NULL COMMENT '业务记录 ID（多态，不设外键）',
    submitter_id BIGINT UNSIGNED DEFAULT NULL COMMENT '业务提交人（审核日志对本人可见用）',
    stage        VARCHAR(16)  NOT NULL DEFAULT 'first' COMMENT 'first/second（activate_manual 仅 first 单级）',
    status       VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
    reviewer_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '最近一次操作的审核人',
    reason       VARCHAR(500)  DEFAULT NULL COMMENT '驳回原因（reject 必填）',
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_reviews_todo (biz_type, stage, status),
    KEY idx_reviews_biz (biz_type, biz_id),
    KEY idx_reviews_submitter (submitter_id),
    CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_submitter FOREIGN KEY (submitter_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审核记录（学院看 first，校级看 second；Review 是独立资源）';

CREATE TABLE IF NOT EXISTS review_logs (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    review_id   BIGINT UNSIGNED NOT NULL,
    operator_id BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人；NULL=系统动作（如自动提交生成）',
    action      VARCHAR(16)  NOT NULL COMMENT 'create/approve/reject',
    reason      VARCHAR(500)  DEFAULT NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_review_logs_review (review_id),
    CONSTRAINT fk_review_logs_review FOREIGN KEY (review_id) REFERENCES reviews (id),
    CONSTRAINT fk_review_logs_operator FOREIGN KEY (operator_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审核日志（谁/何时/动作/原因，本人+审核角色可见）';

-- ---------------------------------------------------------------------------
-- consents V1：隐私授权
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS consents (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    archive_id BIGINT UNSIGNED NOT NULL,
    scope      VARCHAR(16)     NOT NULL COMMENT 'screen（大屏）/site（站点，预留）',
    created_at TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_consents_archive_scope (archive_id, scope),
    KEY idx_consents_user (user_id),
    CONSTRAINT fk_consents_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_consents_archive FOREIGN KEY (archive_id) REFERENCES archives (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '授权记录（撤回=删行并联动下架）';

-- ---------------------------------------------------------------------------
-- honors V1：校友荣誉
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS honors (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    archive_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '可关联档案（非 Archive 的 category）',
    title       VARCHAR(128) NOT NULL,
    category    VARCHAR(32)  NOT NULL COMMENT 'scholarship/competition……对齐 honor_category 字典',
    level       VARCHAR(32)  NOT NULL COMMENT 'national/provincial/school……',
    awarded_at  DATE         NOT NULL COMMENT '纯日期 YYYY-MM-DD',
    status      VARCHAR(16)  NOT NULL DEFAULT 'pending_first'
                COMMENT 'pending_first/pending_second/approved/rejected（走 Reviews 通道）',
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_honors_user (user_id),
    KEY idx_honors_cat_level (category, level),
    KEY idx_honors_status (status),
    KEY idx_honors_awarded (awarded_at),
    CONSTRAINT fk_honors_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_honors_archive FOREIGN KEY (archive_id) REFERENCES archives (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '校友荣誉（统计接口唯一数据源）';

-- ---------------------------------------------------------------------------
-- showcase V1：上架记录
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS showcase (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    archive_id          BIGINT UNSIGNED NOT NULL COMMENT '上架条件：archives.status=approved 且 consent 有效',
    cover_attachment_id BIGINT UNSIGNED DEFAULT NULL COMMENT '发布时快照封面（取首个已引用图片附件）',
    published_by        BIGINT UNSIGNED NOT NULL COMMENT 'school_admin',
    published_at        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_showcase_archive (archive_id),
    CONSTRAINT fk_showcase_archive FOREIGN KEY (archive_id) REFERENCES archives (id),
    CONSTRAINT fk_showcase_cover FOREIGN KEY (cover_attachment_id) REFERENCES attachments (id),
    CONSTRAINT fk_showcase_publisher FOREIGN KEY (published_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '上架记录（行存在=上架中，下架=删行；consent 撤回联动删除）';

-- ---------------------------------------------------------------------------
-- announcements（[M13] 扩展模块，幂等）
-- 权限：写=college_admin/school_admin；读=所有登录角色（非管理员强制只读 published）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS announcements (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title        VARCHAR(128)    NOT NULL COMMENT '公告标题',
    content      TEXT            NOT NULL COMMENT '公告内容',
    status       VARCHAR(16)     NOT NULL DEFAULT 'draft' COMMENT 'draft/published/withdrawn',
    published_by BIGINT UNSIGNED DEFAULT NULL COMMENT '发布人（最近一次发布操作者）',
    published_at TIMESTAMP(6)    DEFAULT NULL COMMENT '最近一次发布时间',
    created_by   BIGINT UNSIGNED NOT NULL COMMENT '创建人',
    created_at   TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_announcements_status (status, published_at),
    CONSTRAINT fk_announcements_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_announcements_publisher FOREIGN KEY (published_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '公告（draft→published↔withdrawn）';

-- ---------------------------------------------------------------------------
-- drafts（第十二章，幂等）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS drafts (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    biz_type   VARCHAR(32)     NOT NULL COMMENT 'archive/……',
    version    INT             NOT NULL DEFAULT 1 COMMENT '乐观锁：提交版本=服务端版本才保存并 +1',
    payload    JSON            NOT NULL COMMENT '草稿内容（标题/正文/attachment_ids 等）',
    created_at TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_drafts_user_biz (user_id, biz_type),
    CONSTRAINT fk_drafts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '草稿（正式提交成功后删除对应行）';

-- ---------------------------------------------------------------------------
-- outstanding（[M14] 扩展模块，幂等；已折入历次列修正：
--   grad_year INT（对齐 alumni_base.grad_year 口径）、photo_url MEDIUMTEXT（base64 照片））
-- 权限：写=仅 school_admin；读=公开端点 GET /alumni/outstanding 仅 published。
-- [M15] Directory 校友档案索引不建表（纯聚合检索），无迁移内容。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS outstanding_alumni (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '关联 users.id（须为已激活校友 student/alumni）',
    student_id    VARCHAR(32)    NOT NULL COMMENT '冗余学号，便于检索与关联档案索引',
    display_name  VARCHAR(50)    NOT NULL COMMENT '展示姓名',
    photo_url     MEDIUMTEXT     DEFAULT NULL COMMENT '照片（base64 data URL，前端压缩 ≤100KB；NULL 时前端渲染姓氏占位图）',
    message       VARCHAR(200)   DEFAULT NULL COMMENT '一句话寄语',
    college_id    BIGINT UNSIGNED DEFAULT NULL COMMENT '关联字典 dicts.id（type=college）',
    grad_year     INT            DEFAULT NULL COMMENT '毕业年份，如 2023（对齐 alumni_base.grad_year INT）',
    current_title VARCHAR(100)   DEFAULT NULL COMMENT '当前职位，如 前端开发工程师',
    current_city  VARCHAR(50)    DEFAULT NULL COMMENT '当前城市，如 福州',
    timeline      JSON           DEFAULT NULL COMMENT '成长时间线 [{"year":"2020","event":"..."}]',
    awards        JSON           DEFAULT NULL COMMENT '代表荣誉 [{"title":"...","year":"2022","level":"省级"}]',
    display_order INT            NOT NULL DEFAULT 0 COMMENT '展示顺序，小者靠前',
    is_featured   TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否首页焦点推荐位',
    status        VARCHAR(20)    NOT NULL DEFAULT 'draft' COMMENT 'draft/published/withdrawn',
    created_at    TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_outstanding_user (user_id),
    KEY idx_outstanding_status_order (status, display_order),
    KEY idx_outstanding_grad_year (grad_year),
    KEY idx_outstanding_student_id (student_id),
    CONSTRAINT fk_outstanding_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_outstanding_college FOREIGN KEY (college_id) REFERENCES dicts (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '优秀校友展示位（运营精选，school_admin 维护）';

-- ---------------------------------------------------------------------------
-- wall（[M16] 扩展模块，幂等）
-- 权限：公开列表 GET /wall/messages 免认证（仅 approved）；mine/POST/DELETE 登录；
--       /admin/wall/** 仅 school_admin。提交限流（60s/1 条、日/5 条）在服务层内存实现。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wall_message (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '留言人 users.id（登录态取，客户端不可传）',
    content     VARCHAR(120)   NOT NULL COMMENT '留言正文（前端限 40 字，DB 留冗余至 120）',
    status      VARCHAR(16)    NOT NULL DEFAULT 'pending' COMMENT 'pending=待审核 approved=已通过 rejected=已驳回',
    review_note VARCHAR(255)   DEFAULT NULL COMMENT '驳回原因（展示给留言人）',
    reviewed_by BIGINT UNSIGNED DEFAULT NULL COMMENT '审核人 users.id',
    reviewed_at TIMESTAMP(6)   DEFAULT NULL COMMENT '审核时间',
    is_pinned   TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '置顶（预留，0=普通）',
    ip_addr     VARCHAR(45)    DEFAULT NULL COMMENT '提交 IP（风控用，不返回前端）',
    created_at  TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_wall_status_created (status, created_at),
    KEY idx_wall_user (user_id),
    CONSTRAINT fk_wall_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_wall_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '校友留言墙';

-- ---------------------------------------------------------------------------
-- hero（门户首页大图轮播，幂等）
-- 权限：公开列表 GET /hero/slides 免认证（仅 published）；维护 /admin/hero/** 仅
--       school_admin / system_admin。图片 MEDIUMTEXT（base64 data URL，前端压缩 ≤500KB）。
-- 种子：与门户原硬编码两张默认图一致，仅在表为空时插入（避免重复播种）。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hero_slide (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title         VARCHAR(100)   NOT NULL COMMENT '主标题',
    subtitle      VARCHAR(200)   DEFAULT NULL COMMENT '副标题',
    image         MEDIUMTEXT     DEFAULT NULL COMMENT '轮播图（base64 data URL 或 URL）',
    display_order INT            NOT NULL DEFAULT 0 COMMENT '展示顺序，小者靠前',
    status        VARCHAR(20)    NOT NULL DEFAULT 'published' COMMENT 'published=展示 withdrawn=隐藏',
    created_at    TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_hero_status_order (status, display_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '门户首页大图轮播';

INSERT INTO hero_slide (title, subtitle, image, display_order, status)
SELECT t.title, t.subtitle, t.image, t.display_order, 'published'
FROM (
    SELECT '桃李天下 · 薪火相传' AS title, '欢迎全球校友回家看看' AS subtitle,
           '/bgcsvg/front.jpg' AS image, 0 AS display_order
    UNION ALL
    SELECT '城堡长灯 · 静候归人', '无论走多远，这里永远为你亮灯', '/bgcsvg/side.jpg', 1
) t
WHERE NOT EXISTS (SELECT 1 FROM hero_slide);

