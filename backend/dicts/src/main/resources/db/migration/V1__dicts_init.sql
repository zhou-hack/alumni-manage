-- dicts 模块初始化：建表（与 backend/db/schema.sql v3 定义一致）+ 默认种子数据
-- 共享单库约定：本模块迁移历史记录于 flyway_history_dicts，与其他模块互不影响

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

-- 毕业年份（value 即年份，保证可读性；grad_year 引用检查按 value 比对 alumni_base.grad_year）
INSERT INTO dicts (type, label, value, sort) VALUES
('grad_year', '2015', '2015', 1),
('grad_year', '2016', '2016', 2),
('grad_year', '2017', '2017', 3),
('grad_year', '2018', '2018', 4),
('grad_year', '2019', '2019', 5),
('grad_year', '2020', '2020', 6),
('grad_year', '2021', '2021', 7),
('grad_year', '2022', '2022', 8),
('grad_year', '2023', '2023', 9),
('grad_year', '2024', '2024', 10),
('grad_year', '2025', '2025', 11),
('grad_year', '2026', '2026', 12),
('grad_year', '2027', '2027', 13),
('grad_year', '2028', '2028', 14),
('grad_year', '2029', '2029', 15),
('grad_year', '2030', '2030', 16)
ON DUPLICATE KEY UPDATE label = VALUES(label), sort = VALUES(sort);

-- 荣誉类别
INSERT INTO dicts (type, label, value, sort) VALUES
('honor_category', '奖学金',   'scholarship', 1),
('honor_category', '学科竞赛', 'competition', 2),
('honor_category', '科研成果', 'research',    3),
('honor_category', '其他',     'other',       4)
ON DUPLICATE KEY UPDATE label = VALUES(label), sort = VALUES(sort);
