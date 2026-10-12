-- 校友管理平台 认证模块 - 初始建表
-- 时间统一 UTC，字符集 utf8mb4

CREATE TABLE alumni_base (
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

CREATE TABLE users (
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
