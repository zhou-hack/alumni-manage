-- 批量导入（规范第十一章）：任务表 + 错误行报告表
-- 挂载约定：/api/v1/imports 路由由 attachments 模块承载（上传同构）；原表文件存 attachments 行（biz_type=import）

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
