-- ============================================================================
-- 第七章 Reviews 审核中心（全业务共用通道）
-- DDL 与 backend/db/schema.sql 保持一致（加 IF NOT EXISTS 保证可重复执行）
-- ============================================================================

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
