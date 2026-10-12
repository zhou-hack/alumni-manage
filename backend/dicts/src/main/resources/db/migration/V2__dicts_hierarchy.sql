-- dicts 层级化：专业归属学院（parent_id 自引用）
-- 规则：major.parent_id → college.id；其余类型 parent_id 为 NULL

-- 幂等：schema.sql v3 已含 parent_id（DBA 手动建库流程）时跳过，否则补建（Flyway 自建流程）
-- MySQL 8.x 无 ADD COLUMN IF NOT EXISTS，经 information_schema 判定；列/索引/外键同源，查列即可代表
DROP PROCEDURE IF EXISTS dicts_add_parent_id;
CREATE PROCEDURE dicts_add_parent_id()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dicts' AND COLUMN_NAME = 'parent_id') THEN
        ALTER TABLE dicts
            ADD COLUMN parent_id BIGINT UNSIGNED NULL COMMENT '父级字典项（major 归属 college）' AFTER type,
            ADD KEY idx_dicts_parent (parent_id),
            ADD CONSTRAINT fk_dicts_parent FOREIGN KEY (parent_id) REFERENCES dicts (id);
    END IF;
END;
CALL dicts_add_parent_id();
DROP PROCEDURE dicts_add_parent_id;

-- 存量专业归位
UPDATE dicts m JOIN dicts c ON c.type = 'college' AND c.value = 'CS'  SET m.parent_id = c.id WHERE m.type = 'major' AND m.value IN ('SE', 'CS');
UPDATE dicts m JOIN dicts c ON c.type = 'college' AND c.value = 'EE'  SET m.parent_id = c.id WHERE m.type = 'major' AND m.value IN ('EE');
UPDATE dicts m JOIN dicts c ON c.type = 'college' AND c.value = 'ME'  SET m.parent_id = c.id WHERE m.type = 'major' AND m.value IN ('ME');
UPDATE dicts m JOIN dicts c ON c.type = 'college' AND c.value = 'EM'  SET m.parent_id = c.id WHERE m.type = 'major' AND m.value IN ('FIN', 'ACCT');
UPDATE dicts m JOIN dicts c ON c.type = 'college' AND c.value = 'FL'  SET m.parent_id = c.id WHERE m.type = 'major' AND m.value IN ('ENG');
UPDATE dicts m JOIN dicts c ON c.type = 'college' AND c.value = 'SCI' SET m.parent_id = c.id WHERE m.type = 'major' AND m.value IN ('MATH', 'PHYS');
UPDATE dicts m JOIN dicts c ON c.type = 'college' AND c.value = 'LIT' SET m.parent_id = c.id WHERE m.type = 'major' AND m.value IN ('CL');

-- 每个学院补足第二专业
INSERT INTO dicts (type, parent_id, label, value, sort)
SELECT 'major', c.id, '通信工程', 'CE', 2 FROM dicts c WHERE c.type = 'college' AND c.value = 'EE';
INSERT INTO dicts (type, parent_id, label, value, sort)
SELECT 'major', c.id, '车辆工程', 'VE', 2 FROM dicts c WHERE c.type = 'college' AND c.value = 'ME';
INSERT INTO dicts (type, parent_id, label, value, sort)
SELECT 'major', c.id, '日语', 'JP', 2 FROM dicts c WHERE c.type = 'college' AND c.value = 'FL';
INSERT INTO dicts (type, parent_id, label, value, sort)
SELECT 'major', c.id, '新闻学', 'JOUR', 2 FROM dicts c WHERE c.type = 'college' AND c.value = 'LIT';
