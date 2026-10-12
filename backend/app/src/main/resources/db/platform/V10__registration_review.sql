-- V9：注册申请制（人工审核闭环）
-- 背景：学校未提供校友库数据，激活三要素核验无数据源；注册改为「申报 + 人工审核」制：
--   用户提交学号/姓名/手机号/学籍信息 → 账号 pending（登录 403）→ reviews(activate_manual) 人工审核
--   → 通过 active / 驳回 rejected（可重新提交）。
-- 1) alumni_base.id_card 改可空：申报制不收集身份证号，alumni_base 转为学籍信息载体
-- 2) activate_manual_requests：注册审核申报单（reviews.biz_id 指向本表）

ALTER TABLE alumni_base MODIFY id_card VARCHAR(32) NULL COMMENT '身份证号（明文存储，仅用于激活核验；申报制注册为空）';

CREATE TABLE IF NOT EXISTS activate_manual_requests (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '关联平台账号（注册时先建 pending 账号）',
    student_id    VARCHAR(32)  NOT NULL COMMENT '申报学号',
    real_name     VARCHAR(64)  NOT NULL COMMENT '申报姓名',
    contact_phone VARCHAR(16)  NOT NULL COMMENT '联系电话（即注册手机号）',
    note          VARCHAR(500) DEFAULT NULL COMMENT '申请说明（选填）',
    advisor_note  VARCHAR(255) DEFAULT NULL COMMENT '智能审核建议（AI 审核预留；Mock 恒为转人工）',
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_amr_user (user_id),
    CONSTRAINT fk_amr_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '注册人工审核申报单（reviews.biz_type=activate_manual 的业务表）';
