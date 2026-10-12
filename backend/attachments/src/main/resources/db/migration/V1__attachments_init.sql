-- attachments 模块初始化：附件表 + 删除审计表（与 backend/db/schema.sql v3 定义一致）

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
