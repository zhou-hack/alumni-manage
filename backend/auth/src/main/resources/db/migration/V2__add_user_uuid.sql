-- users 增加对外对接 UUID（不可变，激活时生成；存量数据回填）
ALTER TABLE users ADD COLUMN uuid VARCHAR(36) NULL AFTER id;
UPDATE users SET uuid = UUID() WHERE uuid IS NULL;
ALTER TABLE users MODIFY COLUMN uuid VARCHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_users_uuid (uuid);
