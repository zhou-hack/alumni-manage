-- ============================================================================
-- 校友管理平台 数据库架构 v4（全量 DDL · 单一事实来源 · 对齐 Flyway V11）
-- 依据：《[定稿]API规范》v1.2 + P1 扩展（M34 三级审核 / M13 智能标签 / M14 消息通知 /
--        M15 细粒度权限 / M16 安全隐私与审计）+ 增量迁移（V3~V11）
-- 地位：全平台唯一结构基准。模块目录禁止再建 DDL 文件；
--       改表流程 = 改本文件 + 增量迁移脚本（幂等守卫）→ 评审 → 灰度 → 生产
-- 用法：mysql -u<运维账号> -p < schema.sql
-- 约定：
--   1. 单库 alumni_platform，utf8mb4 / InnoDB，主键 BIGINT UNSIGNED 自增
--   2. 时间统一 UTC（TIMESTAMP(6)）：连接串 serverTimezone=UTC，会话 SET time_zone='+00:00'
--   3. 跨模块连接：业务表统一 FK 到 users.id；附件 (biz_type, ref_id)、审核 (biz_type, biz_id)
--      多态关联不设外键（引用完整性由应用层保证）
--   4. 外键默认 RESTRICT，删除一律逻辑化/审核化，保审计链路
-- 同步说明：
--   本文件合并了 Flyway V1~V11 全部 DDL，与后端 app 模块迁移脚本完全一致。
--   已纳入本版：
--     V1  基础表 + seed（学院/专业/荣誉类别字典、hero_slide 默认图）
--     V2  P1 扩展：users.status / consents.version+consent_text_hash / 拼音列 /
--            counselor_scopes / tags / tag_bindings / notifications /
--            notification_subscriptions / audit_logs / user_change_logs /
--            field_permissions + 默认种子 / consent_texts + 默认版本 1
--     V3  archives.status / honors.status 列宽 16 → 24（容纳 pending_counselor）
--     V4  hero_slide.placement（hero/campus 两栏位）+ 联合索引
--     V5  announcements.content TEXT → MEDIUMTEXT
--     V6  announcement_image 公告图静态资源表
--     V7  announcements.cover_image 封面图列
--     V8  wall_message.show_name 是否公开姓名
--     V9  honors 团队字段（team_id/member_names/created_by/red_doc_url）+ honor_team_members
--     V10 alumni_base.id_card 改可空（注册申报制）+ activate_manual_requests
--     V11 honors 团队公示行（is_team_record/team_role）+ honor_drafts + honor_draft_members
-- ============================================================================

CREATE DATABASE IF NOT EXISTS alumni_platform
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE alumni_platform;
SET time_zone = '+00:00';
SET NAMES utf8mb4;

-- ============================================================================
-- 第二章 Auth（与现行 alumni_auth 开发库结构一致，合库直接迁移数据）
-- ============================================================================

CREATE TABLE alumni_base (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    student_id  VARCHAR(32)  NOT NULL COMMENT '学号',
    real_name   VARCHAR(64)  NOT NULL COMMENT '真实姓名',
    id_card     VARCHAR(32)  NULL COMMENT '身份证号（明文存储，仅用于激活核验；申报制注册时为空）',
    phone       VARCHAR(16)  NOT NULL COMMENT '校友库登记手机号',
    college     VARCHAR(64)  DEFAULT NULL COMMENT '学院（文本，对齐 dicts college.label）',
    major       VARCHAR(64)  DEFAULT NULL COMMENT '专业',
    grad_year   INT          NOT NULL COMMENT '毕业年份',
    real_name_pinyin      VARCHAR(128) DEFAULT NULL COMMENT '[P1/M13] 姓名全拼（小写无声调，预生成）',
    real_name_pinyin_abbr VARCHAR(64)  DEFAULT NULL COMMENT '[P1/M13] 姓名拼音首字母（预生成）',
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_alumni_base_student_id (student_id),
    KEY idx_alumni_base_college_year (college, grad_year),
    KEY idx_alumni_base_real_name (real_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '校友库（激活核验数据源，批量导入目标表）';

CREATE TABLE users (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid           VARCHAR(36)  NOT NULL COMMENT '对外对接 UUID（v4，创建时生成，不可变；内部关联仍用 id）',
    alumni_base_id BIGINT UNSIGNED NOT NULL COMMENT '关联校友库 ID',
    student_id     VARCHAR(32)  NOT NULL,
    phone          VARCHAR(16)  NOT NULL COMMENT '登录手机号（激活时可换绑）',
    nickname       VARCHAR(64)  NOT NULL,
    role           VARCHAR(32)  NOT NULL COMMENT 'student/alumni/counselor/college_admin/school_admin/system_admin',
    password_hash  VARCHAR(100) NOT NULL COMMENT 'BCrypt(SHA-256(原文)) 双层散列',
    status         VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT '[P1/M15] active/suspended（停权：Token 即刻作废，登录 403）',
    created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_uuid (uuid),
    UNIQUE KEY uk_users_student_id (student_id),
    UNIQUE KEY uk_users_phone (phone),
    UNIQUE KEY uk_users_alumni_base_id (alumni_base_id),
    KEY idx_users_role (role),
    CONSTRAINT fk_users_alumni_base FOREIGN KEY (alumni_base_id) REFERENCES alumni_base (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '平台账号';

CREATE TABLE refresh_tokens (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    token_hash VARCHAR(64)    NOT NULL COMMENT 'SHA-256(refresh_token)，不落明文',
    expires_at TIMESTAMP(6)   NOT NULL,
    revoked    TINYINT(1)     NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_user (user_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '刷新令牌（轮换制）';

-- 注：短信验证码 / verify_token / reset_token 存 Redis（限时一次性，已落地），不落库。

-- ============================================================================
-- 第三章 Dicts 基础字典
-- ============================================================================

CREATE TABLE dicts (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    type       VARCHAR(32) NOT NULL COMMENT 'college/major/grad_year/honor_category，随业务扩充',
    parent_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '父级字典项（major 归属 college；其余类型为 NULL）',
    label      VARCHAR(64) NOT NULL COMMENT '展示文本',
    label_pinyin      VARCHAR(128) DEFAULT NULL COMMENT '[P1/M13] 展示文本全拼（预生成）',
    label_pinyin_abbr VARCHAR(64)  DEFAULT NULL COMMENT '[P1/M13] 展示文本拼音首字母（预生成）',
    value      VARCHAR(64) NOT NULL COMMENT '存储值',
    sort       INT         NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dicts_type_value (type, value),
    KEY idx_dicts_type_sort (type, sort),
    KEY idx_dicts_parent (parent_id),
    CONSTRAINT fk_dicts_parent FOREIGN KEY (parent_id) REFERENCES dicts (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '基础字典（major 层级归属 college；被引用项禁止删除：409 RESOURCE_REFERENCED）';

-- ============================================================================
-- 第六章 Attachments 附件（跨模块复用组件）
-- ============================================================================

CREATE TABLE attachments (
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

CREATE TABLE attachment_audit_log (
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

-- ============================================================================
-- 第四章 Archives 校友档案
-- ============================================================================

CREATE TABLE archives (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '归属校友',
    title         VARCHAR(128) NOT NULL,
    title_pinyin      VARCHAR(192) DEFAULT NULL COMMENT '[P1/M13] 标题全拼（预生成）',
    title_pinyin_abbr VARCHAR(96)  DEFAULT NULL COMMENT '[P1/M13] 标题拼音首字母（预生成）',
    category      VARCHAR(32)  NOT NULL COMMENT 'award/experience/certificate……自由扩展',
    content       TEXT         DEFAULT NULL,
    remark        VARCHAR(255) DEFAULT NULL,
    status        VARCHAR(24)  NOT NULL DEFAULT 'draft'
                  COMMENT 'draft/pending_counselor/pending_first/pending_second/approved/rejected/deleting/deleted（P1 起新提交强制进 pending_counselor，无导员覆盖自动跳过）',
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

-- ============================================================================
-- 第五章 Honors 校友荣誉（独立结构化资源，统计口径唯一来源）
-- ============================================================================

CREATE TABLE honors (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    archive_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '可关联档案（非 Archive 的 category）',
    title       VARCHAR(128) NOT NULL,
    title_pinyin      VARCHAR(192) DEFAULT NULL COMMENT '[P1/M13] 标题全拼（预生成）',
    title_pinyin_abbr VARCHAR(96)  DEFAULT NULL COMMENT '[P1/M13] 标题拼音首字母（预生成）',
    category    VARCHAR(32)  NOT NULL COMMENT 'scholarship/competition……对齐 honor_category 字典',
    level       VARCHAR(32)  NOT NULL COMMENT 'national/provincial/school……',
    awarded_at  DATE         NOT NULL COMMENT '纯日期 YYYY-MM-DD',
    team_id     VARCHAR(36)  DEFAULT NULL COMMENT '团队荣誉批次 UUID（同批成员共享；单人提交为 NULL）',
    is_team_record TINYINT(1) NOT NULL DEFAULT 0 COMMENT '[M03 扩展] 1=团队荣誉公示行（团队入库时自动生成、挂在提交人名下）',
    team_role   VARCHAR(16)  DEFAULT NULL COMMENT '[M03 扩展] leader/member；公示行固定 leader，个人行固定 member；单人荣誉为 NULL',
    member_names VARCHAR(1000) DEFAULT NULL COMMENT '团队荣誉原始成员名单文本（人名解析依据，超长截断）',
    created_by  BIGINT UNSIGNED DEFAULT NULL COMMENT '提交人 users.id（单人提交=本人；团队/导入=操作者）',
    red_doc_url VARCHAR(512) DEFAULT NULL COMMENT '红头文件/官方文件链接（附件预览地址或外部 URL）',
    status      VARCHAR(24)  NOT NULL DEFAULT 'pending_counselor'
                COMMENT 'pending_counselor/pending_first/pending_second/approved/rejected（走 Reviews 通道；P1 起新提交强制三级，无导员覆盖自动跳过初审）',
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_honors_user (user_id),
    KEY idx_honors_team (team_id),
    KEY idx_honors_is_team (is_team_record),
    KEY idx_honors_cat_level (category, level),
    KEY idx_honors_status (status),
    KEY idx_honors_awarded (awarded_at),
    CONSTRAINT fk_honors_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_honors_archive FOREIGN KEY (archive_id) REFERENCES archives (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '校友荣誉（统计接口唯一数据源）';

CREATE TABLE honor_team_members (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    team_id       VARCHAR(36)  NOT NULL COMMENT '团队批次 UUID（关联 honors.team_id）',
    name_raw      VARCHAR(64)  NOT NULL COMMENT '解析出的原始人名',
    alumni_base_id BIGINT UNSIGNED DEFAULT NULL COMMENT '命中校友库 alumni_base.id（未匹配为 NULL）',
    user_id       BIGINT UNSIGNED DEFAULT NULL COMMENT '命中已激活账号 users.id',
    honor_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '生成的荣誉 honors.id（matched 才有）',
    match_status  VARCHAR(16)  NOT NULL COMMENT 'matched/duplicated/no_account/ambiguous/unmatched',
    reason        VARCHAR(255) DEFAULT NULL COMMENT '未匹配/歧义原因（ambiguous 存候选摘要）',
    created_by    BIGINT UNSIGNED DEFAULT NULL COMMENT '提交人 users.id（团队详情可见性校验用）',
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_htm_team (team_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '[M03] 团队荣誉成员匹配留证（人名识别结果，全部人名含未匹配入表）';

-- ============================================================================
-- 第七章 Reviews 审核中心（全业务共用通道）
-- ============================================================================

CREATE TABLE reviews (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    biz_type     VARCHAR(32)  NOT NULL COMMENT 'archive/honor/archive_delete/activate_manual',
    biz_id       BIGINT UNSIGNED DEFAULT NULL COMMENT '业务记录 ID（多态，不设外键）',
    submitter_id BIGINT UNSIGNED DEFAULT NULL COMMENT '业务提交人（审核日志对本人可见用）',
    stage        VARCHAR(16)  NOT NULL DEFAULT 'first' COMMENT '[P1] counselor/first/second（activate_manual 仅 first 单级）',
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

CREATE TABLE review_logs (
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

-- ============================================================================
-- 第九章 Consents 隐私授权
-- ============================================================================

CREATE TABLE consents (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    archive_id BIGINT UNSIGNED NOT NULL,
    scope      VARCHAR(16)     NOT NULL COMMENT 'screen（大屏）/site（站点，预留）',
    version           INT          DEFAULT NULL COMMENT '[P1/M16] 授权文本版本号（留证可追溯当时版本）',
    consent_text_hash VARCHAR(64)  DEFAULT NULL COMMENT '[P1/M16] 授权文本 SHA-256 摘要',
    created_at TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_consents_archive_scope (archive_id, scope),
    KEY idx_consents_user (user_id),
    CONSTRAINT fk_consents_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_consents_archive FOREIGN KEY (archive_id) REFERENCES archives (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '授权记录（撤回=删行并联动下架）';

-- ============================================================================
-- 第八章 Showcase / Screen 上下架与大屏
-- ============================================================================

CREATE TABLE showcase (
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

-- ============================================================================
-- 第十二章 Drafts 草稿（服务端兜底，版本号乐观锁）
-- ============================================================================

CREATE TABLE drafts (
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

-- ============================================================================
-- 第十一章 Imports 批量导入（异步任务）
-- ============================================================================

CREATE TABLE import_tasks (
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

CREATE TABLE import_errors (
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

-- ============================================================================
-- [扩展] Announcements 公告管理（API 规范十二章之外的扩展模块 [M13]）
-- 仅管理员（college_admin/school_admin）发布/编辑/撤下/删除；所有登录角色可读，
-- 学生端仅见 status=published。
-- ============================================================================

CREATE TABLE announcements (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title        VARCHAR(128)    NOT NULL COMMENT '公告标题',
    content      MEDIUMTEXT      NOT NULL COMMENT '公告内容（支持 Markdown，上限 10MB；V5 扩容）',
    cover_image  VARCHAR(512)    NULL COMMENT '封面图（静态资源 URL，可空；V7 新增）',
    status       VARCHAR(16)     NOT NULL DEFAULT 'draft' COMMENT 'draft/published/withdrawn',
    published_by BIGINT UNSIGNED DEFAULT NULL COMMENT '发布人（最近一次发布操作者）',
    published_at TIMESTAMP(6)    DEFAULT NULL COMMENT '最近一次发布时间（撤下不清除，重新发布覆盖）',
    created_by   BIGINT UNSIGNED NOT NULL COMMENT '创建人',
    created_at   TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_announcements_status (status, published_at),
    CONSTRAINT fk_announcements_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_announcements_publisher FOREIGN KEY (published_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '公告（draft→published↔withdrawn）';

-- ============================================================================
-- 第十章 Stats：不建表。overview/honors/colleges 全部为聚合查询：
--   校友数=COUNT(users)，待审数=COUNT(reviews WHERE status='pending')，
--   荣誉分布=GROUP BY honors(category,level)，
--   学院排行=archives ⋈ users ⋈ alumni_base GROUP BY alumni_base.college
-- ============================================================================

-- ============================================================================
-- [扩展] Outstanding 优秀校友展示位（扩展模块 [M14]）
-- 运营精选内容：school_admin 人工挑选并维护（寄语/时间线/代表荣誉均为编辑数据，
-- 不从业务表推导）；一人仅一个精选位（uk_outstanding_user）。
-- 公开端点 GET /alumni/outstanding 仅返回 status=published，按 display_order 升序。
-- ============================================================================

CREATE TABLE outstanding_alumni (
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

-- ============================================================================
-- [扩展] Directory 校友档案索引（扩展模块 [M15]）：不建表，纯聚合检索。
-- 公开名录 = users ⋈ alumni_base ⋈ archives(approved) ⋈ showcase(上架) ⋈ consents(screen)
--   —— 红线口径与 /screen/feed 完全一致：三项条件任一不满足，该校友天然不出现在结果集。
-- 荣誉仅出 honors.status='approved'；展示增强（照片/当前身份/编辑时间线）LEFT JOIN
--   outstanding_alumni(status='published')。
-- ============================================================================

-- ============================================================================
-- [扩展] Wall 校友留言墙（扩展模块 [M16]）
-- UGC 走审核：登录用户提交（一律 pending），school_admin 审核通过后公开可见。
-- 姓名/届次/学院不落库——展示时经 user_id ⋈ users ⋈ alumni_base 联查（改档案自动生效）。
-- 公开列表仅 status='approved'，置顶优先、时间倒序；ip_addr 仅风控用，任何接口不返回前端。
-- ============================================================================

CREATE TABLE wall_message (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '留言人 users.id（登录态取，客户端不可传）',
    content     VARCHAR(120)   NOT NULL COMMENT '留言正文（前端限 40 字，DB 留冗余至 120）',
    show_name   TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '[V8] 是否在公开墙显示姓名（0 匿名 / 1 显示）',
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

-- ============================================================================
-- ============================ P1 阶段扩展 ===================================
-- 依据：《[定稿]API规范P1》v1.0（M34 三级审核 / M13 智能标签与检索 /
--        M14 消息通知 / M15 细粒度权限与交接 / M16 安全隐私与审计）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- P1 [M34] counselor_scopes 导员管辖范围
-- 管辖范围 = 本学院内 届别 ∪ 专业 ∪ 指定学号 三维度并集（每行一个维度值，三列恰一非空）。
-- 导员账号本身为 users(role='counselor')，创建时配套生成 alumni_base 桩行（工号式 student_id）。
-- ----------------------------------------------------------------------------
CREATE TABLE counselor_scopes (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    counselor_id BIGINT UNSIGNED NOT NULL COMMENT '导员 users.id（role=counselor）',
    college_id   BIGINT UNSIGNED NOT NULL COMMENT '所属学院 dicts.id（type=college，创建时固定，防越院）',
    grad_year    INT             DEFAULT NULL COMMENT '管辖届别（维度之一，可空）',
    major_id     BIGINT UNSIGNED DEFAULT NULL COMMENT '管辖专业 dicts.id（type=major，可空）',
    student_id   VARCHAR(32)     DEFAULT NULL COMMENT '指定学号（可空）',
    created_at   TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_cs_counselor (counselor_id),
    KEY idx_cs_college_year (college_id, grad_year),
    KEY idx_cs_major (major_id),
    KEY idx_cs_student (student_id),
    CONSTRAINT fk_cs_counselor FOREIGN KEY (counselor_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '[M34] 导员管辖范围（届别∪专业∪学号并集，至少一项非空）';

-- ----------------------------------------------------------------------------
-- P1 [M13] tags / tag_bindings 标签体系（域隔离 honor/archive/alumni，一物多标 ≤20）
-- ----------------------------------------------------------------------------
CREATE TABLE tags (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name       VARCHAR(32)  NOT NULL COMMENT '标签名',
    `group`    VARCHAR(16)  NOT NULL COMMENT '标签域 honor/archive/alumni（域隔离：同域重名 409 TAG_DUPLICATED）',
    sort       INT          NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人 school_admin',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tags_group_name (`group`, name),
    CONSTRAINT fk_tags_creator FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '[M13] 标签（域隔离）';

CREATE TABLE tag_bindings (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tag_id     BIGINT UNSIGNED NOT NULL,
    biz_type   VARCHAR(16)     NOT NULL COMMENT 'honor/archive/alumni（与标签域一一对应，跨域 400）',
    biz_id     BIGINT UNSIGNED NOT NULL COMMENT '业务记录 ID（多态，不设外键）',
    created_at TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_binding (tag_id, biz_type, biz_id),
    KEY idx_tb_biz (biz_type, biz_id),
    CONSTRAINT fk_tb_tag FOREIGN KEY (tag_id) REFERENCES tags (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '[M13] 标签绑定（PUT 全量覆盖式，单对象 ≤20）';

-- ----------------------------------------------------------------------------
-- P1 [M14] notifications 站内消息 / notification_subscriptions 外部订阅
-- content 由服务端拼装，红线：不得包含学号（昵称+业务标题）。
-- ----------------------------------------------------------------------------
CREATE TABLE notifications (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL COMMENT '接收人',
    type       VARCHAR(16)     NOT NULL COMMENT 'review/announcement/todo',
    title      VARCHAR(64)     NOT NULL,
    content    VARCHAR(500)    NOT NULL COMMENT '服务端拼装；不含学号',
    biz_type   VARCHAR(32)     DEFAULT NULL,
    biz_id     BIGINT UNSIGNED DEFAULT NULL,
    is_read    TINYINT(1)      NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_ntf_user_read (user_id, is_read),
    KEY idx_ntf_user_created (user_id, created_at),
    CONSTRAINT fk_ntf_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '[M14] 站内消息';

CREATE TABLE notification_subscriptions (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    channel    VARCHAR(8)      NOT NULL COMMENT 'sms/email',
    enabled    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '默认全关（最小打扰原则）',
    scenes     JSON            DEFAULT NULL COMMENT '订阅场景 ["review_result","announcement"]',
    address    VARCHAR(128)    DEFAULT NULL COMMENT 'email 地址（sms 沿用账号绑定手机号）',
    created_at TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_nsub_user_channel (user_id, channel),
    CONSTRAINT fk_nsub_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '[M14] 外部消息订阅（短信/邮件，可退订即时生效）';

-- ----------------------------------------------------------------------------
-- P1 [M16] audit_logs 审计日志（只追加，不可篡改：无任何修改/删除接口，保留 ≥180 天）
-- ----------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人（系统动作为 NULL）',
    user_role  VARCHAR(32)     DEFAULT NULL,
    action     VARCHAR(48)     NOT NULL COMMENT 'login/login_failed/logout/export/attachment_delete/field_permission_change/user_suspend/user_resume/user_handover/consent_grant/consent_withdraw/dict_change/tag_change/counselor_scope_change',
    biz_type   VARCHAR(32)     DEFAULT NULL,
    biz_id     BIGINT UNSIGNED DEFAULT NULL,
    detail     JSON            DEFAULT NULL,
    ip         VARCHAR(45)     DEFAULT NULL,
    created_at TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_audit_user (user_id),
    KEY idx_audit_action (action),
    KEY idx_audit_biz (biz_type, biz_id),
    KEY idx_audit_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '[M16] 审计日志（INSERT only；导出限流单账号 5 次/小时）';

-- ----------------------------------------------------------------------------
-- P1 [M15] user_change_logs 账号变更留痕 / field_permissions 角色—字段可见性矩阵
-- ----------------------------------------------------------------------------
CREATE TABLE user_change_logs (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '被操作账号',
    action      VARCHAR(32)     NOT NULL COMMENT 'suspend/resume/handover/scope_change',
    operator_id BIGINT UNSIGNED NOT NULL COMMENT '操作人',
    detail      JSON            DEFAULT NULL COMMENT '如 {"target_user_id":15,"reason":"..."}',
    created_at  TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_ucl_user (user_id),
    CONSTRAINT fk_ucl_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ucl_operator FOREIGN KEY (operator_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '[M15] 账号变更留痕';

CREATE TABLE field_permissions (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role       VARCHAR(32) NOT NULL COMMENT 'student/alumni/counselor/college_admin/school_admin/system_admin',
    field      VARCHAR(32) NOT NULL COMMENT 'phone/id_card/email/home_address/work_place/emergency_contact（白名单）',
    visibility VARCHAR(8)  NOT NULL COMMENT 'visible/masked/hidden',
    updated_by BIGINT UNSIGNED DEFAULT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_fp_role_field (role, field)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '[M15] 角色—字段可见性矩阵（出参裁剪在服务端序列化阶段执行；变更写审计日志）';

-- 预置默认值：phone 全角色 masked；student/alumni 互不可见 id_card（hidden）；其余 visible
INSERT INTO field_permissions (role, field, visibility) VALUES
('student',       'phone', 'masked'), ('student',       'id_card', 'hidden'),
('alumni',        'phone', 'masked'), ('alumni',        'id_card', 'hidden'),
('counselor',     'phone', 'masked'), ('counselor',     'id_card', 'visible'),
('college_admin', 'phone', 'masked'), ('college_admin', 'id_card', 'visible'),
('school_admin',  'phone', 'masked'), ('school_admin',  'id_card', 'visible'),
('system_admin',  'phone', 'masked'), ('system_admin',  'id_card', 'visible');

-- ----------------------------------------------------------------------------
-- P1 [M16] consent_texts 授权文本版本化（文本变更必须升版本；旧授权记录保留原版本留证）
-- ----------------------------------------------------------------------------
CREATE TABLE consent_texts (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    version    INT          NOT NULL COMMENT '版本号（单调递增）',
    content    TEXT         NOT NULL COMMENT '授权文本全文（前端授权弹窗展示）',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_consent_texts_version (version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '[M16] 授权文本（版本化）';

INSERT INTO consent_texts (version, content) VALUES
(1, '本人授权校友管理平台在授权范围内（大屏展示/站点展示）展示本人档案信息。授权可随时撤回，撤回后立即停止展示。');

-- ----------------------------------------------------------------------------
-- P1 存量表列扩展（本文件为全量基准，直接改写 CREATE TABLE 定义）：
--   users            + status VARCHAR(16) NOT NULL DEFAULT 'active'（active/suspended）
--   consents         + version INT / consent_text_hash VARCHAR(64)（授权版本化留证）
--   alumni_base      + real_name_pinyin / real_name_pinyin_abbr（拼音预生成列 [M13]）
--                    + id_card 改 NULL（注册申报制）
--   archives/honors  + title_pinyin / title_pinyin_abbr；status 列宽 16 → 24（容纳 pending_counselor）
--   honors           + team_id / is_team_record / team_role / member_names / created_by / red_doc_url
--   dicts            + label_pinyin / label_pinyin_abbr
--   reviews.stage    取值扩展 counselor/first/second（导员初审环节）
--   announcements    + content 改 MEDIUMTEXT（V5）+ cover_image（V7）
--   wall_message     + show_name（V8）
--   hero_slide       + placement（V4）
-- 说明：上述列已合入上方对应 CREATE TABLE 定义；增量升级见 app 平台迁移脚本（幂等守卫）。

-- ============================================================================
-- V1/V4 [M05] hero_slide 门户首页大图轮播
--   同一后台模块（/admin/hero/slides）按 placement 筛选维护：hero 首屏 / campus 美丽校园
-- ============================================================================
CREATE TABLE hero_slide (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title         VARCHAR(100)   NOT NULL COMMENT '主标题',
    subtitle      VARCHAR(200)   DEFAULT NULL COMMENT '副标题',
    image         MEDIUMTEXT     DEFAULT NULL COMMENT '轮播图（base64 data URL 或 URL）',
    display_order INT            NOT NULL DEFAULT 0 COMMENT '展示顺序，小者靠前',
    placement     VARCHAR(20)    NOT NULL DEFAULT 'hero' COMMENT '[V4] 展示位置：hero 首屏轮播 / campus 美丽校园',
    status        VARCHAR(20)    NOT NULL DEFAULT 'published' COMMENT 'published=展示 withdrawn=隐藏',
    created_at    TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_hero_slide_placement (placement, status, display_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '门户首页大图轮播';

-- 默认两张轮播图（与 V1 seed 一致；存在则不重复插入）
INSERT INTO hero_slide (title, subtitle, image, display_order, placement, status)
SELECT t.title, t.subtitle, t.image, t.display_order, 'hero', 'published'
FROM (
    SELECT '桃李天下 · 薪火相传' AS title, '欢迎全球校友回家看看' AS subtitle,
           '/bgcsvg/front.jpg' AS image, 0 AS display_order
    UNION ALL
    SELECT '城堡长灯 · 静候归人', '无论走多远，这里永远为你亮灯', '/bgcsvg/side.jpg', 1
) t
WHERE NOT EXISTS (SELECT 1 FROM hero_slide);

-- ============================================================================
-- V6 [M13] announcement_image 公告图片静态资源表
--   公告编辑器图片不再 base64 内嵌正文，上传后按 URL 引用
--   访问：GET /api/v1/announcements/images/{uuid}（公开，公告为公开内容）
-- ============================================================================
CREATE TABLE announcement_image (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid         VARCHAR(36)     NOT NULL COMMENT '对外资源标识（URL 使用，不暴露自增 ID）',
    filename     VARCHAR(255)    NOT NULL COMMENT '原始文件名',
    content_type VARCHAR(64)     NOT NULL COMMENT 'image/jpeg 等',
    size_bytes   BIGINT UNSIGNED NOT NULL,
    created_by   BIGINT UNSIGNED NOT NULL COMMENT '上传人（管理员）',
    created_at   TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_announcement_image_uuid (uuid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '公告图片静态资源';

-- ============================================================================
-- V10 [注册申报制] activate_manual_requests 注册人工审核申报单
--   背景：学校未提供校友库数据，激活三要素核验无数据源；注册改为「申报 + 人工审核」制：
--     用户提交学号/姓名/手机号/学籍信息 → 账号 pending（登录 403）→ reviews(activate_manual) 人工审核
--     → 通过 active / 驳回 rejected（可重新提交）。
-- ============================================================================
CREATE TABLE activate_manual_requests (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '关联平台账号（注册时先建 pending 账号）',
    student_id    VARCHAR(32)     NOT NULL COMMENT '申报学号',
    real_name     VARCHAR(64)     NOT NULL COMMENT '申报姓名',
    contact_phone VARCHAR(16)     NOT NULL COMMENT '联系电话（即注册手机号）',
    note          VARCHAR(500)    DEFAULT NULL COMMENT '申请说明（选填）',
    advisor_note  VARCHAR(255)    DEFAULT NULL COMMENT '智能审核建议（AI 审核预留；Mock 恒为转人工）',
    created_at    TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_amr_user (user_id),
    CONSTRAINT fk_amr_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '注册人工审核申报单（reviews.biz_type=activate_manual 的业务表）';

-- ============================================================================
-- V11 [M03 扩展 2026-09-25] honor_drafts 荣誉证书解析草稿
--   人工填写 / 后续 OCR 自动填充；草稿确认后才转写 honor_team_members，确保留证链路完整
-- ============================================================================
CREATE TABLE honor_drafts (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    attachment_id BIGINT UNSIGNED DEFAULT NULL COMMENT '关联 attachments.id（证书图/扫描件；可空，纯表单录入时为空）',
    operator_id   BIGINT UNSIGNED NOT NULL COMMENT '草稿创建人 users.id',
    kind          VARCHAR(16)     NOT NULL DEFAULT 'solo' COMMENT 'solo=单人 / team=团队',
    title         VARCHAR(128)    DEFAULT NULL COMMENT '奖项名（人工录入或后续 OCR 抽取）',
    category      VARCHAR(32)     DEFAULT NULL COMMENT '荣誉类别 honor_category.value',
    level         VARCHAR(32)     DEFAULT NULL COMMENT '级别 national/provincial/city/school',
    awarded_at    DATE            DEFAULT NULL COMMENT '获奖日期 YYYY-MM-DD',
    college_name  VARCHAR(64)     DEFAULT NULL COMMENT '学院名（OCR/人工）',
    red_doc_url   VARCHAR(512)    DEFAULT NULL COMMENT '红头文件链接',
    member_names  TEXT            DEFAULT NULL COMMENT '原始成员名单（团队草稿使用；分隔符：、，,;；/空白）',
    parsed_json   JSON            DEFAULT NULL COMMENT '结构化抽取结果（OCR 阶段填充；人工阶段为 NULL）',
    status        VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending/confirmed/discarded',
    created_at    TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_draft_operator (operator_id),
    KEY idx_draft_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '[M03 扩展] 荣誉证书解析草稿（人工录入 + 后续 OCR 抽取接入）';

CREATE TABLE honor_draft_members (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    draft_id      BIGINT UNSIGNED NOT NULL COMMENT 'honor_drafts.id',
    name_raw      VARCHAR(64)     NOT NULL COMMENT '解析出的原始人名（人工编辑后可能变化）',
    alumni_base_id BIGINT UNSIGNED DEFAULT NULL COMMENT '命中校友库 alumni_base.id（人工选择 2 位）',
    user_id       BIGINT UNSIGNED DEFAULT NULL COMMENT '命中已激活账号 users.id',
    match_status  VARCHAR(16)     NOT NULL COMMENT 'matched/duplicated/no_account/ambiguous/unmatched/manual',
    reason        VARCHAR(255)    DEFAULT NULL COMMENT '未匹配/歧义原因',
    created_at    TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_hdm_draft (draft_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '[M03 扩展] 草稿阶段人名匹配留证；确认后转写 honor_team_members';
-- ----------------------------------------------------------------------------
