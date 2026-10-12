-- ===========================================================================
-- V2：P1 阶段扩展（《[定稿]API规范P1》：M34/M13/M14/M15/M16；全部幂等）
-- 从 V1 拆出（V1 已应用于开发库，Flyway checksum 冻结，不允许再改）
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- P1 存量表列扩展（幂等存储过程守卫：列存在即跳过）
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS platform_p1_alter;
CREATE PROCEDURE platform_p1_alter()
BEGIN
    -- [M15] users.status（active/suspended）
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'status') THEN
        ALTER TABLE users ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'active'
            COMMENT '[P1/M15] active/suspended（停权：Token 即刻作废，登录 403）' AFTER password_hash;
    END IF;
    -- [M16] consents 授权版本化
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consents' AND COLUMN_NAME = 'version') THEN
        ALTER TABLE consents
            ADD COLUMN version INT DEFAULT NULL COMMENT '[P1/M16] 授权文本版本号' AFTER scope,
            ADD COLUMN consent_text_hash VARCHAR(64) DEFAULT NULL COMMENT '[P1/M16] 授权文本 SHA-256 摘要' AFTER version;
    END IF;
    -- [M13] alumni_base 拼音列
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumni_base' AND COLUMN_NAME = 'real_name_pinyin') THEN
        ALTER TABLE alumni_base
            ADD COLUMN real_name_pinyin VARCHAR(128) DEFAULT NULL COMMENT '[P1/M13] 姓名全拼' AFTER grad_year,
            ADD COLUMN real_name_pinyin_abbr VARCHAR(64) DEFAULT NULL COMMENT '[P1/M13] 姓名拼音首字母' AFTER real_name_pinyin;
    END IF;
    -- [M13] archives 拼音列
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'archives' AND COLUMN_NAME = 'title_pinyin') THEN
        ALTER TABLE archives
            ADD COLUMN title_pinyin VARCHAR(192) DEFAULT NULL COMMENT '[P1/M13] 标题全拼' AFTER title,
            ADD COLUMN title_pinyin_abbr VARCHAR(96) DEFAULT NULL COMMENT '[P1/M13] 标题拼音首字母' AFTER title_pinyin;
    END IF;
    -- [M13] honors 拼音列
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'honors' AND COLUMN_NAME = 'title_pinyin') THEN
        ALTER TABLE honors
            ADD COLUMN title_pinyin VARCHAR(192) DEFAULT NULL COMMENT '[P1/M13] 标题全拼' AFTER title,
            ADD COLUMN title_pinyin_abbr VARCHAR(96) DEFAULT NULL COMMENT '[P1/M13] 标题拼音首字母' AFTER title_pinyin;
    END IF;
    -- [M13] dicts 拼音列
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dicts' AND COLUMN_NAME = 'label_pinyin') THEN
        ALTER TABLE dicts
            ADD COLUMN label_pinyin VARCHAR(128) DEFAULT NULL COMMENT '[P1/M13] 展示文本全拼' AFTER label,
            ADD COLUMN label_pinyin_abbr VARCHAR(64) DEFAULT NULL COMMENT '[P1/M13] 展示文本拼音首字母' AFTER label_pinyin;
    END IF;
END;
CALL platform_p1_alter();
DROP PROCEDURE platform_p1_alter;

-- ---------------------------------------------------------------------------
-- [M34] counselor_scopes 导员管辖范围（届别∪专业∪学号并集，至少一项非空）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS counselor_scopes (
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

-- ---------------------------------------------------------------------------
-- [M13] tags / tag_bindings（域隔离，一物多标 ≤20）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tags (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name       VARCHAR(32)  NOT NULL COMMENT '标签名',
    `group`    VARCHAR(16)  NOT NULL COMMENT '标签域 honor/archive/alumni（同域重名 409 TAG_DUPLICATED）',
    sort       INT          NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人 school_admin',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tags_group_name (`group`, name),
    CONSTRAINT fk_tags_creator FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '[M13] 标签（域隔离）';

CREATE TABLE IF NOT EXISTS tag_bindings (
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

-- ---------------------------------------------------------------------------
-- [M14] notifications / notification_subscriptions（content 红线：不含学号）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
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

CREATE TABLE IF NOT EXISTS notification_subscriptions (
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

-- ---------------------------------------------------------------------------
-- [M16] audit_logs（INSERT only，不可篡改，保留 ≥180 天；导出限流 5 次/小时/账号）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人（系统动作为 NULL）',
    user_role  VARCHAR(32)     DEFAULT NULL,
    action     VARCHAR(48)     NOT NULL,
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
  COMMENT = '[M16] 审计日志（只追加；导出限流单账号 5 次/小时）';

-- ---------------------------------------------------------------------------
-- [M15] user_change_logs / field_permissions（角色—字段可见性矩阵）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_change_logs (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '被操作账号',
    action      VARCHAR(32)     NOT NULL COMMENT 'suspend/resume/handover/scope_change',
    operator_id BIGINT UNSIGNED NOT NULL COMMENT '操作人',
    detail      JSON            DEFAULT NULL,
    created_at  TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_ucl_user (user_id),
    CONSTRAINT fk_ucl_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ucl_operator FOREIGN KEY (operator_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '[M15] 账号变更留痕';

CREATE TABLE IF NOT EXISTS field_permissions (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role       VARCHAR(32) NOT NULL,
    field      VARCHAR(32) NOT NULL COMMENT 'phone/id_card/email/home_address/work_place/emergency_contact（白名单）',
    visibility VARCHAR(8)  NOT NULL COMMENT 'visible/masked/hidden',
    updated_by BIGINT UNSIGNED DEFAULT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_fp_role_field (role, field)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '[M15] 角色—字段可见性矩阵（变更写审计日志）';

-- 预置默认值（仅在表为空时播种，避免覆盖管理员已调整的配置）
INSERT INTO field_permissions (role, field, visibility)
SELECT t.role, t.field, t.visibility FROM (
    SELECT 'student' AS role, 'phone' AS field, 'masked' AS visibility
    UNION ALL SELECT 'student', 'id_card', 'hidden'
    UNION ALL SELECT 'alumni', 'phone', 'masked'
    UNION ALL SELECT 'alumni', 'id_card', 'hidden'
    UNION ALL SELECT 'counselor', 'phone', 'masked'
    UNION ALL SELECT 'counselor', 'id_card', 'visible'
    UNION ALL SELECT 'college_admin', 'phone', 'masked'
    UNION ALL SELECT 'college_admin', 'id_card', 'visible'
    UNION ALL SELECT 'school_admin', 'phone', 'masked'
    UNION ALL SELECT 'school_admin', 'id_card', 'visible'
    UNION ALL SELECT 'system_admin', 'phone', 'masked'
    UNION ALL SELECT 'system_admin', 'id_card', 'visible'
) t
WHERE NOT EXISTS (SELECT 1 FROM field_permissions);

-- ---------------------------------------------------------------------------
-- [M16] consent_texts 授权文本版本化（文本变更必须升版本）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS consent_texts (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    version    INT          NOT NULL COMMENT '版本号（单调递增）',
    content    TEXT         NOT NULL COMMENT '授权文本全文',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_consent_texts_version (version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '[M16] 授权文本（版本化）';

INSERT INTO consent_texts (version, content)
SELECT 1, '本人授权校友管理平台在授权范围内（大屏展示/站点展示）展示本人档案信息。授权可随时撤回，撤回后立即停止展示。'
WHERE NOT EXISTS (SELECT 1 FROM consent_texts);
