-- ============================================================================
-- V19：学院/专业字典替换为「产业学院 8 个 + 27 个专业」+ 校友大屏展示字段(V18b)
-- ----------------------------------------------------------------------------
-- 业务背景：
--   原 V1 设定的 7 个学院（计算机/电子/机械/经济/外国语/理/文）是早期示例数据。
--   真实学校提供 `表格_20261001.xlsx` 给定 8 个产业学院 + 27 个专业（外加 2 个
--   公共学院：马克思主义/创新创业），本迁移按此口径更新字典与 alumni_base 字段。
--
-- 同时：
--   1) V18b：补 alumni_base 5 个大屏展示字段（photo_url / industry / intro / quote / motto）
--      （若 V18 已运行，列已存在，跳过）
--   2) 清理重复校友账号、修复 user 角色、删除测试荣誉
--   3) 给现有校友补充 V18 字段（5 位核心人物的简介/座右铭）
--   4) 校友库 college / major 字符串重映射（旧 label → 新 label）
--   5) 学院再分配：让 8 个学院分布均匀（演示账号 / 软件技术专业分流）
--   6) 新增 8 位"明星校友"+ 21 条荣誉，覆盖国际级/国家级/省部级/校级 4 个级别
--
-- 兼容：所有 UPDATE / DELETE 都按字符串 / id 比对，幂等可重放。
-- ============================================================================

-- ===== 1) V18b：alumni_base 5 个大屏展示字段（若已存在则跳过）=====
DROP PROCEDURE IF EXISTS v19_alumni_profile_fields;
CREATE PROCEDURE v19_alumni_profile_fields()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumni_base' AND COLUMN_NAME = 'photo_url') THEN
        ALTER TABLE alumni_base
            ADD COLUMN photo_url VARCHAR(512) DEFAULT NULL COMMENT '校友头像/照片 URL' AFTER grad_year,
            ADD COLUMN industry  VARCHAR(64)  DEFAULT NULL COMMENT '当前所在行业 / 单位' AFTER photo_url,
            ADD COLUMN intro     VARCHAR(500) DEFAULT NULL COMMENT '个人简介（多行）' AFTER industry,
            ADD COLUMN quote     VARCHAR(200) DEFAULT NULL COMMENT '人生信条 / 引言' AFTER intro,
            ADD COLUMN motto     VARCHAR(200) DEFAULT NULL COMMENT '座右铭' AFTER quote,
            ADD KEY idx_alumni_base_photo_url (photo_url);
    END IF;
END;
CALL v19_alumni_profile_fields();
DROP PROCEDURE v19_alumni_profile_fields;

-- ===== 2) 学院字典：清旧插新（dict 表 self-FK 必须先 major 再 college）=====
-- outstanding_alumni.college_id 外键（fk_outstanding_college）引用 dicts.id：
-- 先解除引用再删，末尾（第 13 节）按校友库 college 字符串重映射回新学院
UPDATE outstanding_alumni SET college_id = NULL WHERE college_id IS NOT NULL;
DELETE FROM dicts WHERE type = 'major';
DELETE FROM dicts WHERE type = 'college';

INSERT INTO dicts (type, label, value, sort) VALUES
('college', '游戏产业学院',         'COL01', 1),
('college', '数字影视传媒产业学院', 'COL02', 2),
('college', '现代通信产业学院',     'COL03', 3),
('college', '数据产业学院',         'COL04', 4),
('college', '智能产业学院',         'COL05', 5),
('college', '未来技术产业学院',     'COL06', 6),
('college', '马克思主义学院',         'COL07', 7),
('college', '创新创业学院',           'COL08', 8)
ON DUPLICATE KEY UPDATE label = VALUES(label), sort = VALUES(sort);

-- ===== 3) 专业字典：27 个挂到对应学院 =====
INSERT INTO dicts (type, parent_id, label, value, sort)
SELECT 'major', c.id, t.label, t.value, t.sort
FROM (
    SELECT 'COL01' AS college, '游戏艺术设计'          AS label, 'COL01-MJ01' AS value, 1 AS sort UNION ALL
    SELECT 'COL01',          '虚拟现实技术应用',        'COL01-MJ02', 2 UNION ALL
    SELECT 'COL01',          '动漫制作技术',            'COL01-MJ03', 3 UNION ALL
    SELECT 'COL01',          '动漫设计',                'COL01-MJ04', 4 UNION ALL
    SELECT 'COL01',          '视觉传达设计',            'COL01-MJ05', 5 UNION ALL

    SELECT 'COL02',          '网络直播与运营',          'COL02-MJ01', 1 UNION ALL
    SELECT 'COL02',          '融媒体技术与运营',        'COL02-MJ02', 2 UNION ALL

    SELECT 'COL03',          '现代通信技术',            'COL03-MJ01', 1 UNION ALL
    SELECT 'COL03',          '现代移动通信技术',        'COL03-MJ02', 2 UNION ALL
    SELECT 'COL03',          '智能互联网络技术',        'COL03-MJ03', 3 UNION ALL
    SELECT 'COL03',          '网络规划与优化技术',      'COL03-MJ04', 4 UNION ALL

    SELECT 'COL04',          '大数据技术',              'COL04-MJ01', 1 UNION ALL
    SELECT 'COL04',          '区块链技术应用',          'COL04-MJ02', 2 UNION ALL
    SELECT 'COL04',          '云计算技术应用',          'COL04-MJ03', 3 UNION ALL
    SELECT 'COL04',          '工业软件开发技术',        'COL04-MJ04', 4 UNION ALL

    SELECT 'COL05',          '软件技术',                'COL05-MJ01', 1 UNION ALL
    SELECT 'COL05',          '智能体技术应用',          'COL05-MJ02', 2 UNION ALL
    SELECT 'COL05',          '智能产品开发与应用',      'COL05-MJ03', 3 UNION ALL
    SELECT 'COL05',          '人工智能技术应用',        'COL05-MJ04', 4 UNION ALL
    SELECT 'COL05',          '信息安全技术应用',        'COL05-MJ05', 5 UNION ALL

    SELECT 'COL06',          '工业机器人技术',          'COL06-MJ01', 1 UNION ALL
    SELECT 'COL06',          '智能机器人技术',          'COL06-MJ02', 2 UNION ALL
    SELECT 'COL06',          '工业互联网应用',          'COL06-MJ03', 3 UNION ALL
    SELECT 'COL06',          '智能光电技术应用',        'COL06-MJ04', 4 UNION ALL
    SELECT 'COL06',          '电子产品检测技术',        'COL06-MJ05', 5 UNION ALL
    SELECT 'COL06',          '无人机应用技术',          'COL06-MJ06', 6 UNION ALL
    SELECT 'COL06',          '具身智能机器人技术',      'COL06-MJ07', 7
) t JOIN dicts c ON c.type = 'college' AND c.value = t.college
ON DUPLICATE KEY UPDATE label = VALUES(label), sort = VALUES(sort), parent_id = VALUES(parent_id);

-- ===== 4) 校友库字符串重映射（旧 label → 新 label）=====
UPDATE alumni_base SET college = '游戏产业学院'         WHERE college IN ('计算机学院','软件学院','机械学院');
UPDATE alumni_base SET college = '数字影视传媒产业学院' WHERE college IN ('电子信息学院','传媒学院');
UPDATE alumni_base SET college = '现代通信产业学院'     WHERE college IN ('机械工程学院');
UPDATE alumni_base SET college = '数据产业学院'         WHERE college IN ('经济管理学院','经济学院');
UPDATE alumni_base SET college = '智能产业学院'         WHERE college IN ('外国语学院');
UPDATE alumni_base SET college = '未来技术产业学院'     WHERE college IN ('理学院','数学学院');
UPDATE alumni_base SET college = '马克思主义学院'         WHERE college IN ('文学院');
UPDATE alumni_base SET college = '创新创业学院'           WHERE college IN ('信息中心','?????');

UPDATE alumni_base SET major = '软件技术'         WHERE major = '软件工程';
UPDATE alumni_base SET major = '人工智能技术应用' WHERE major = '计算机科学与技术';
UPDATE alumni_base SET major = '现代通信技术'     WHERE major = '通信工程';
UPDATE alumni_base SET major = '智能产品开发与应用' WHERE major = '电子信息工程';
UPDATE alumni_base SET major = '工业机器人技术'   WHERE major IN ('车辆工程','机械工程');

-- ===== 5) 学院再分配：让 8 个学院分布均匀 =====
UPDATE alumni_base SET college = '智能产业学院',     major='软件技术' WHERE id IN (1, 5, 6, 7);
UPDATE alumni_base SET college = '数据产业学院'         WHERE id = 2;
UPDATE alumni_base SET college = '现代通信产业学院'     WHERE id = 3;
UPDATE alumni_base SET college = '未来技术产业学院'     WHERE id = 4;
UPDATE alumni_base SET college = '数据产业学院',         major='工业软件开发技术' WHERE id IN (31, 33);
UPDATE alumni_base SET college = '未来技术产业学院',     major='具身智能机器人技术' WHERE id = 32;
UPDATE alumni_base SET college = '现代通信产业学院',     major='智能互联网络技术' WHERE id = 34;
UPDATE alumni_base SET college = '游戏产业学院',         major='动漫制作技术' WHERE id = 35;
UPDATE alumni_base SET college = '数字影视传媒产业学院', major='融媒体技术与运营' WHERE id = 36;
UPDATE alumni_base SET college = '马克思主义学院',         major='马克思主义理论' WHERE id = 37;
UPDATE alumni_base SET college = '游戏产业学院',         major='游戏艺术设计' WHERE id = 54;
UPDATE alumni_base SET college = '数据产业学院',         major='区块链技术应用' WHERE id = 55;
UPDATE alumni_base SET college = '数字影视传媒产业学院', major='融媒体技术与运营' WHERE id = 56;
UPDATE alumni_base SET college = '智能产业学院',         major='软件技术' WHERE id = 57;
UPDATE alumni_base SET college = '智能产业学院',         major='软件技术' WHERE id = 38;

-- ===== 6) 清理测试垃圾（如果存在）=====
-- 测试荣誉：综合测试/导入测试/英文标题/不允许 等
DELETE FROM honors WHERE title IN (
    '不允许',
    'P1测试奖学金',
    '导入测试荣誉A','导入测试荣誉B','导入免审荣誉C',
    'National Mathematical Modeling Contest 1st Prize'
) OR title LIKE '综合测试荣誉%' OR title LIKE '测试荣誉-mua%' OR title LIKE '测试荣誉-mub%';

-- 测试账号 alumni_base：id 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50
-- 这些 user.id 通常是 9-20，需先清关联
DELETE FROM review_logs WHERE operator_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM review_logs WHERE review_id IN (SELECT id FROM reviews WHERE submitter_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50));
DELETE FROM reviews     WHERE reviewer_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM reviews     WHERE submitter_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM refresh_tokens    WHERE user_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM notification_subscriptions WHERE user_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM notifications     WHERE user_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM activate_manual_requests WHERE user_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM user_change_logs  WHERE user_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM counselor_scopes  WHERE counselor_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM consents          WHERE user_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM wall_message      WHERE user_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM archives          WHERE user_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM drafts            WHERE user_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM import_tasks      WHERE uploader_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM showcase          WHERE published_by IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM announcements     WHERE created_by IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50)
                                  OR published_by IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM tags              WHERE created_by IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM attachment_audit_log WHERE operator_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM attachments       WHERE uploader_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM honors            WHERE user_id IN (SELECT id FROM users WHERE alumni_base_id BETWEEN 39 AND 50);
DELETE FROM users             WHERE alumni_base_id BETWEEN 39 AND 50;
DELETE FROM alumni_base       WHERE id BETWEEN 39 AND 50;

-- 修复陈雨薇乱码（如果存在）
UPDATE users        SET nickname   = '陈雨薇' WHERE id = 2;
UPDATE alumni_base  SET real_name  = '陈雨薇' WHERE id = 2;
-- 修正错角色（被误标的校友）
UPDATE users SET role = 'alumni' WHERE id IN (4, 7) AND role <> 'alumni';

-- ===== 7) 给核心 5 位校友补 V18 大屏展示字段 =====
UPDATE alumni_base SET
    photo_url = 'https://picsum.photos/seed/alumni1/240/240',
    industry  = '游戏开发',
    intro     = '2018 年起在腾讯互娱从事 3A 手游客户端开发，主导过 2 款月流水过亿项目的核心战斗系统设计。',
    quote     = '代码是写给人看的，顺便让机器执行。',
    motto     = 'Stay hungry, stay foolish.'
WHERE id = 1;

UPDATE alumni_base SET
    photo_url = 'https://picsum.photos/seed/alumni2/240/240',
    industry  = '互联网 / 大数据',
    intro     = '现任字节跳动数据平台资深架构师，主导日均千亿级数据流量的实时数仓建设。',
    quote     = '数据不会说谎，工程师要学会倾听。',
    motto     = '慢就是快。'
WHERE id = 2;

UPDATE alumni_base SET
    photo_url = 'https://picsum.photos/seed/alumni3/240/240',
    industry  = '通信 / 半导体',
    intro     = '华为 5G 无线协议高级工程师，参与 3GPP Release 17/18 标准制定，已发表专利 18 项。',
    quote     = '把每一行协议当作艺术品去打磨。',
    motto     = '凡事预则立，不预则废。'
WHERE id = 3;

UPDATE alumni_base SET
    photo_url = 'https://picsum.photos/seed/alumni4/240/240',
    industry  = '智能制造 / 机器人',
    intro     = '汇川技术工业机器人算法负责人，专注运动控制与机器视觉融合。',
    quote     = '让机器像人一样思考，但比人更可靠。',
    motto     = '让中国制造走向中国智造。'
WHERE id = 4;

UPDATE alumni_base SET
    photo_url = 'https://picsum.photos/seed/alumni5/240/240',
    industry  = '人工智能',
    intro     = '旷视科技算法研究员，专注多模态大模型的工业落地。',
    quote     = 'AI 不是替代人，而是放大人的能力。',
    motto     = 'Be the change you wish to see in the world.'
WHERE id = 5;

UPDATE alumni_base SET
    photo_url = 'https://picsum.photos/seed/alumni55/240/240',
    industry  = '教育/培训',
    motto     = '终身学习,终身成长'
WHERE id = 55;

-- ===== 8) 新增 8 位"明星校友"（每个学院 1 位）=====
-- 已有则跳过（按 student_id 唯一性）
INSERT IGNORE INTO alumni_base (student_id, real_name, phone, college, major, grad_year, photo_url, industry, intro, quote, motto, real_name_pinyin, real_name_pinyin_abbr) VALUES
('2018001001', '沈星然', '13900001001', '游戏产业学院', '游戏艺术设计', 2018,
    'https://picsum.photos/seed/xingran/240/240', '游戏 / 互动娱乐',
    '原画师出身，主导 3 款现象级手游美术风格设定，作品《云中阙》获 TGA 最佳艺术指导提名。',
    '每一帧都是与世界对话的方式。', '以美承载意义。', 'shenxingran', 'sxr'),
('2019002001', '陆子衿', '13900002001', '数字影视传媒产业学院', '融媒体技术与运营', 2019,
    'https://picsum.photos/seed/zijin/240/240', '影视 / 短视频',
    'B 站头部知识区 UP 主，全网粉丝 1200 万，主攻科技与人文的跨界解读。',
    '让复杂的世界，被简单地说清楚。', '内容是时间的玫瑰。', 'luzijin', 'lzj'),
('2017003001', '韩晨旭', '13900003001', '现代通信产业学院', '现代通信技术', 2017,
    'https://picsum.photos/seed/chenxu/240/240', '通信 / 半导体',
    '中兴通讯 5G 基带首席架构师，主导 R17 标准提案 6 项，授权发明专利 25 项。',
    '通信是文明的基础设施。', '以工程之名,行文明之事。', 'hanchenxu', 'hcx'),
('2018004001', '宋知微', '13900004001', '数据产业学院', '大数据技术', 2018,
    'https://picsum.photos/seed/zhiwei/240/240', '金融科技',
    '蚂蚁集团风控数据负责人，主导反欺诈模型在日均 10 亿笔交易中实时拦截风险。',
    '在数字洪流中识别真相。', '以数据之光,照风险之暗。', 'songzhiwei', 'szw'),
('2016005001', '唐亦凡', '13900005001', '智能产业学院', '软件技术', 2016,
    'https://picsum.photos/seed/yifan/240/240', '互联网 / 后端架构',
    '阿里云 P9 技术专家，主导过千万级 QPS 的电商大促稳定性保障。',
    '系统稳定是用户体验的最低门槛。', 'Code for the long run.', 'tangyifan', 'tyf'),
('2018006001', '邵怀瑾', '13900006001', '未来技术产业学院', '工业机器人技术', 2018,
    'https://picsum.photos/seed/huaijin/240/240', '工业自动化',
    '新松机器人算法工程师，主导协作机器人在 3C 行业的柔性产线落地。',
    '让机器读懂人,而不是让人迁就机器。', '让工厂长出灵魂。', 'shaohuaijin', 'shj'),
('2020007001', '韦明远', '13900007001', '马克思主义学院', '马克思主义理论', 2020,
    'https://picsum.photos/seed/mingyuan/240/240', '党政 / 教育',
    '高校思政课青年讲师，全国高校思政课教学展示特等奖获得者。',
    '理论只要说服,才能引领。', '让信仰点亮课堂。', 'weimingyuan', 'wmy'),
('2019008001', '夏听澜', '13900008001', '创新创业学院', '创新创业管理', 2019,
    'https://picsum.photos/seed/tinglan/240/240', '风险投资',
    '红杉资本中国基金副总裁，主投硬科技与 AI 应用,被投企业 3 家已上市。',
    '最好的投资是陪创业者穿越周期。', '慢变量,长主义。', 'xiatinglan', 'xtl');

-- ===== 9) 8 位 users（密码统一 12345678）=====
INSERT IGNORE INTO users (uuid, alumni_base_id, student_id, phone, nickname, role, password_hash, status)
SELECT UUID(), ab.id, ab.student_id, ab.phone, ab.real_name, 'alumni',
       '$2a$10$NrKoWB1OAmJ5PThq/98x/u3IAzfbyex6rq27TC3DWOORVnVfLq4Jy', 'active'
FROM alumni_base ab
WHERE ab.student_id IN ('2018001001','2019002001','2017003001','2018004001','2016005001','2018006001','2020007001','2019008001');

-- ===== 10) 21 条荣誉（8 位明星各 2 条 + 现有 5 位各 1 条；全部 approved + online）=====
-- 用户变量（通过 user.student_id 反查 user_id）
SET @u_xingran  = (SELECT id FROM users WHERE student_id='2018001001');
SET @u_zijin    = (SELECT id FROM users WHERE student_id='2019002001');
SET @u_chenxu   = (SELECT id FROM users WHERE student_id='2017003001');
SET @u_zhiwei   = (SELECT id FROM users WHERE student_id='2018004001');
SET @u_yifan    = (SELECT id FROM users WHERE student_id='2016005001');
SET @u_huaijin  = (SELECT id FROM users WHERE student_id='2018006001');
SET @u_mingyuan = (SELECT id FROM users WHERE student_id='2020007001');
SET @u_tinglan  = (SELECT id FROM users WHERE student_id='2019008001');
SET @u_linjia   = (SELECT id FROM users WHERE alumni_base_id=1);
SET @u_chenyu   = (SELECT id FROM users WHERE alumni_base_id=2);
SET @u_huang    = (SELECT id FROM users WHERE alumni_base_id=3);
SET @u_zhouya   = (SELECT id FROM users WHERE alumni_base_id=4);
SET @u_su       = (SELECT id FROM users WHERE alumni_base_id=5);

INSERT IGNORE INTO honors (user_id, title, category, level, awarded_at, status, screen_status) VALUES
(@u_xingran,  '中国游戏金翎奖最佳原画',          'competition', 'national',      '2020-11-15', 'approved', 'online'),
(@u_xingran,  'TGA 最佳艺术指导提名',            'research',    'international', '2023-12-08', 'approved', 'online'),
(@u_zijin,    'B 站百大 UP 主',                  'other',       'provincial',    '2021-12-30', 'approved', 'online'),
(@u_zijin,    '广电总局融媒体创新案例',           'research',    'national',      '2023-06-20', 'approved', 'online'),
(@u_chenxu,   '中国通信学会科学技术一等奖',       'research',    'national',      '2022-09-15', 'approved', 'online'),
(@u_chenxu,   '广东省五四青年奖章',               'other',       'provincial',    '2021-05-04', 'approved', 'online'),
(@u_zhiwei,   '中国数据挖掘大赛金奖',             'competition', 'national',      '2023-07-10', 'approved', 'online'),
(@u_zhiwei,   '阿里妈妈算法挑战赛冠军',           'competition', 'provincial',    '2020-11-22', 'approved', 'online'),
(@u_yifan,    '中国软件行业杰出工程师',           'research',    'national',      '2021-10-19', 'approved', 'online'),
(@u_yifan,    '阿里集团最佳技术贡献奖',           'other',       'school',        '2020-03-30', 'approved', 'online'),
(@u_huaijin,  '全国大学生机器人大赛一等奖',       'competition', 'national',      '2019-08-08', 'approved', 'online'),
(@u_huaijin,  '辽宁省科技进步二等奖',             'research',    'provincial',    '2023-04-12', 'approved', 'online'),
(@u_mingyuan, '全国高校思政课教学展示特等奖',     'competition', 'national',      '2024-06-18', 'approved', 'online'),
(@u_mingyuan, '国家奖学金',                       'scholarship', 'national',      '2022-11-20', 'approved', 'online'),
(@u_tinglan,  '福布斯中国 30 Under 30',           'other',       'national',      '2023-04-15', 'approved', 'online'),
(@u_tinglan,  '中国青年创新创业大赛金奖',         'competition', 'national',      '2020-12-05', 'approved', 'online'),
(@u_linjia,   '全国大学生数学建模竞赛一等奖',     'competition', 'national',      '2021-09-25', 'approved', 'online'),
(@u_chenyu,   '国家奖学金',                       'scholarship', 'national',      '2020-11-15', 'approved', 'online'),
(@u_huang,    '挑战杯全国大学生课外学术科技作品竞赛特等奖','competition','national','2022-10-30','approved','online'),
(@u_zhouya,   '全国大学生机械创新设计大赛一等奖', 'competition', 'national',      '2019-07-15', 'approved', 'online'),
(@u_su,       '国家奖学金',                       'scholarship', 'national',      '2023-11-15', 'approved', 'online');

-- ===== 11) 调整届别分布（让大屏"届别"卡片覆盖多年）=====
UPDATE alumni_base SET grad_year = 2020 WHERE id IN (5, 6, 7);
UPDATE alumni_base SET grad_year = 2024 WHERE id IN (31, 32, 33, 34);
UPDATE alumni_base SET grad_year = 2025 WHERE id IN (35, 36, 37);
UPDATE alumni_base SET grad_year = 2023 WHERE id = 1;
UPDATE alumni_base SET grad_year = 2021 WHERE id = 2;
UPDATE alumni_base SET grad_year = 2022 WHERE id = 3;
UPDATE alumni_base SET grad_year = 2019 WHERE id = 4;

-- ===== 12) 让团队荣誉（id 81/82/83）指向林嘉树（id 1）并上墙 =====
UPDATE honors
   SET user_id = (SELECT id FROM users WHERE alumni_base_id=1 LIMIT 1),
       title = '全国大学生数学建模竞赛一等奖(联合)',
       awarded_at = '2023-09-25',
       status = 'approved',
       screen_status = 'online'
 WHERE id IN (81, 82, 83);

-- ===== 13) outstanding_alumni.college_id 重映射到新学院字典 =====
-- 第 2 节删除旧字典前已置 NULL；此处按 user → alumni_base.college（新 label）回填
UPDATE outstanding_alumni oa
  JOIN users u ON u.id = oa.user_id
  JOIN alumni_base ab ON ab.id = u.alumni_base_id
  JOIN dicts d ON d.type = 'college' AND d.label = ab.college
   SET oa.college_id = d.id
 WHERE oa.college_id IS NULL;