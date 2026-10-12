-- 使用 root 或有权限的管理员账号执行一次即可
-- 作用：建库 + 创建认证模块专属账号 almuni 并授权

CREATE DATABASE IF NOT EXISTS alumni_auth
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'almuni'@'localhost' IDENTIFIED BY 'almuni_dev';
CREATE USER IF NOT EXISTS 'almuni'@'%' IDENTIFIED BY 'almuni_dev';

GRANT ALL PRIVILEGES ON alumni_auth.* TO 'almuni'@'localhost';
GRANT ALL PRIVILEGES ON alumni_auth.* TO 'almuni'@'%';

FLUSH PRIVILEGES;
