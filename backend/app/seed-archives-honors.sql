-- 第二步：基于已有 30 个学生，插入档案/荣誉/上架/授权/留言墙等
SET FOREIGN_KEY_CHECKS = 0;

-- 清理档案/荣誉/留言/公告/优秀校友/轮播/通知的 [DEMO]%（重新执行幂等）
DELETE FROM honor_screen_log WHERE honor_id IN (SELECT id FROM honors WHERE title LIKE '[DEMO]%');
DELETE FROM honors WHERE title LIKE '[DEMO]%';
DELETE FROM reviews WHERE biz_type='archive' AND biz_id IN (SELECT id FROM archives WHERE title LIKE '[DEMO]%');
DELETE FROM reviews WHERE biz_type='honor' AND biz_id IN (SELECT id FROM honors WHERE title LIKE '[DEMO]%');
DELETE FROM showcase WHERE archive_id IN (SELECT id FROM archives WHERE title LIKE '[DEMO]%');
DELETE FROM consents WHERE archive_id IN (SELECT id FROM archives WHERE title LIKE '[DEMO]%');
DELETE FROM archives WHERE title LIKE '[DEMO]%';
DELETE FROM wall_message WHERE content LIKE '[DEMO]%';
DELETE FROM outstanding_alumni WHERE display_name LIKE '[DEMO]%';
DELETE FROM hero_slide WHERE title LIKE '[DEMO]%';
DELETE FROM notifications WHERE title LIKE '[DEMO]%';
DELETE FROM announcements WHERE title LIKE '[DEMO]%';

SET FOREIGN_KEY_CHECKS = 1;

-- 档案（15 个学生各一条）
INSERT INTO archives (user_id, title, title_pinyin, title_pinyin_abbr, category, content, status, version, created_at, updated_at)
SELECT
    u.id,
    CONCAT('[DEMO] ', u.nickname, ' - 个人简介档案'),
    'demo',
    'demo',
    CASE (u.id % 4)
        WHEN 0 THEN 'award'
        WHEN 1 THEN 'certificate'
        WHEN 2 THEN 'experience'
        ELSE 'career'
    END,
    CONCAT('本档案由 [DEMO] 脚本生成。我是 ', u.nickname, '，来自 ', ab.college, ' ', ab.major, ' 专业，', ab.grad_year, ' 届。'),
    CASE WHEN u.id % 3 = 0 THEN 'approved' WHEN u.id % 3 = 1 THEN 'pending_first' ELSE 'draft' END,
    1,
    NOW(),
    NOW()
FROM users u
JOIN alumni_base ab ON u.alumni_base_id = ab.id
WHERE u.student_id LIKE '205000%'
ORDER BY u.id
LIMIT 15;

-- 荣誉（15 个学生各一条）
INSERT INTO honors (user_id, title, title_pinyin, title_pinyin_abbr, category, level, awarded_at, status, screen_status, created_at, updated_at)
SELECT
    u.id,
    CONCAT('[DEMO] ', u.nickname, ' 获 ',
        CASE (u.id % 4)
            WHEN 0 THEN '校级奖学金'
            WHEN 1 THEN '编程大赛一等奖'
            WHEN 2 THEN '优秀学生干部'
            ELSE '社会实践先进个人'
        END),
    'demo',
    'demo',
    CASE (u.id % 4) WHEN 0 THEN 'scholarship' WHEN 1 THEN 'competition' WHEN 2 THEN 'other' ELSE 'other' END,
    CASE (u.id % 3) WHEN 0 THEN 'national' WHEN 1 THEN 'provincial' ELSE 'school' END,
    DATE_SUB(CURDATE(), INTERVAL (u.id * 30) DAY),
    CASE WHEN u.id % 4 IN (0, 2) THEN 'approved' WHEN u.id % 4 = 1 THEN 'pending_first' ELSE 'rejected' END,
    CASE WHEN u.id % 3 = 0 THEN 'online' ELSE 'offline' END,
    NOW(),
    NOW()
FROM users u
WHERE u.student_id LIKE '205000%'
ORDER BY u.id
LIMIT 15;

-- 公告
INSERT INTO announcements (title, content, status, published_by, published_at, created_by, created_at, updated_at) VALUES
('[DEMO] 校友会 2026 年会报名启动', '亲爱的校友：2026 年校友年会将于 11 月 12 日在校本部举行，欢迎各位校友踊跃报名。报名截止日期：10 月 30 日。详情请联系校友办。', 'published', 24, NOW(), 24, NOW(), NOW()),
('[DEMO] 关于征集优秀校友事迹材料的通知', '为弘扬优秀校友精神，学校拟编写《校友风采录》，现面向全校征集优秀校友事迹材料。请各学院组织推荐，于 11 月 20 日前提交校友办。', 'published', 24, NOW(), 24, NOW(), NOW()),
('[DEMO] 校友信息系统升级公告', '校友信息系统将于本周五 22:00 - 24:00 进行升级维护，期间可能短暂不可用，请大家提前安排访问时间。', 'published', 24, NOW(), 24, NOW(), NOW()),
('[DEMO] 校友捐赠倡议书（草稿）', '母校即将迎来建校 70 周年，现发起校友捐赠倡议，所得款项将用于改善校园基础设施。', 'draft', NULL, NULL, 24, NOW(), NOW()),
('[DEMO] 校友通讯录更新（草稿）', '为保持校友通讯录的时效性，请各位校友及时更新个人信息和联系方式。', 'draft', NULL, NULL, 24, NOW(), NOW());

-- 留言墙（5 approved + 2 pending）
INSERT INTO wall_message (user_id, content, show_name, status, review_note, reviewed_by, reviewed_at, is_pinned, created_at, updated_at) VALUES
(25, '[DEMO] 感谢母校的培养，希望学弟学妹们珍惜在校时光！', 1, 'approved', '审核通过', 24, NOW(), 1, NOW(), NOW()),
(26, '[DEMO] 校友会组织的活动非常有意义，认识了不同届的校友。', 1, 'approved', '审核通过', 24, NOW(), 0, NOW(), NOW()),
(27, '[DEMO] 希望能看到更多校友的就业分享。', 0, 'approved', '审核通过', 24, NOW(), 0, NOW(), NOW()),
(28, '[DEMO] 建议学校多组织跨行业的校友交流。', 1, 'approved', '审核通过', 24, NOW(), 0, NOW(), NOW()),
(29, '[DEMO] 校友信息系统做得越来越好了。', 1, 'approved', '审核通过', 24, NOW(), 0, NOW(), NOW()),
(30, '[DEMO] 请问校友卡什么时候可以申请？', 1, 'pending', NULL, NULL, NULL, 0, NOW(), NOW()),
(31, '[DEMO] 建议开设校友导师计划。', 1, 'pending', NULL, NULL, NULL, 0, NOW(), NOW());

-- 优秀校友
INSERT INTO outstanding_alumni (user_id, student_id, display_name, message, college_id, grad_year, current_title, current_city, timeline, awards, display_order, is_featured, status, created_at, updated_at) VALUES
(26, (SELECT student_id FROM users WHERE id=26), '[DEMO] 张明 - 知名游戏制作人', '从游戏开发专业起步，如今已成为业内知名的游戏制作人。', 103, 2018, '某游戏公司创意总监', '上海',
    JSON_ARRAY(JSON_OBJECT('year','2014','event','入学游戏产业学院'), JSON_OBJECT('year','2018','event','毕业并加入游戏公司'), JSON_OBJECT('year','2022','event','晋升创意总监')),
    JSON_ARRAY(JSON_OBJECT('title','校级优秀毕业生','year','2018','level','校级'), JSON_OBJECT('title','省级游戏设计大赛金奖','year','2017','level','省级')),
    1, 1, 'published', NOW(), NOW()),
(28, (SELECT student_id FROM users WHERE id=28), '[DEMO] 李华 - 影视传媒公司创始人', '数字媒体艺术专业出身，毕业后创立影视制作公司。', 104, 2019, '某影视传媒公司 CEO', '北京',
    JSON_ARRAY(JSON_OBJECT('year','2015','event','入学数字影视传媒产业学院'), JSON_OBJECT('year','2019','event','创立影视公司')),
    JSON_ARRAY(JSON_OBJECT('title','优秀青年校友','year','2023','level','校级')),
    2, 0, 'published', NOW(), NOW()),
(30, (SELECT student_id FROM users WHERE id=30), '[DEMO] 王强 - 数据科学家', '数据科学与大数据技术专业，毕业后深耕数据领域。', 106, 2020, '某互联网大厂高级数据科学家', '深圳',
    JSON_ARRAY(JSON_OBJECT('year','2016','event','入学数据产业学院'), JSON_OBJECT('year','2020','event','加入头部互联网公司'), JSON_OBJECT('year','2024','event','晋升高级数据科学家')),
    JSON_ARRAY(JSON_OBJECT('title','全国大学生数学建模竞赛一等奖','year','2019','level','国家级')),
    3, 0, 'published', NOW(), NOW());

-- 荣誉墙上架（给前 5 个 approved 档案）
INSERT INTO showcase (archive_id, published_by, published_at)
SELECT a.id, 24, NOW()
FROM archives a
WHERE a.title LIKE '[DEMO]%' AND a.status = 'approved'
ORDER BY a.id
LIMIT 5;

-- 大屏授权 consents（screen）
INSERT INTO consents (user_id, archive_id, scope, version, consent_text_hash)
SELECT
    a.user_id,
    a.id,
    'screen',
    1,
    SHA2('demo-consent-text-v1', 256)
FROM archives a
JOIN showcase s ON s.archive_id = a.id
WHERE a.title LIKE '[DEMO]%'
LIMIT 5;

-- 首页轮播
INSERT INTO hero_slide (title, subtitle, image, display_order, status, placement, created_at, updated_at) VALUES
('[DEMO] 校友会 2026 年会启动', '欢迎校友回家', NULL, 1, 'published', 'hero', NOW(), NOW()),
('[DEMO] 优秀校友风采展', '记录每一份精彩', NULL, 2, 'published', 'hero', NOW(), NOW()),
('[DEMO] 校友信息系统升级', '更好的用户体验', NULL, 3, 'published', 'hero', NOW(), NOW());

-- 站内通知（前 10 个学生）
INSERT INTO notifications (user_id, type, title, content, is_read, created_at)
SELECT
    u.id,
    'system',
    '[DEMO] 欢迎使用校友管理平台',
    CONCAT('亲爱的 ', u.nickname, '，您的账号已创建，初始密码 123456，请尽快登录并修改密码。'),
    0,
    NOW()
FROM users u
WHERE u.student_id LIKE '205000%'
LIMIT 10;

-- 字典补充
INSERT IGNORE INTO dicts (type, label, value, sort) VALUES
('honor_level', '国际级', 'international', 0),
('honor_level', '国家级', 'national', 1),
('honor_level', '省级', 'provincial', 2),
('honor_level', '市级', 'city', 3),
('honor_level', '校级', 'school', 4),
('honor_level', '院级', 'college', 5);

-- 统计
SELECT '学生账号' AS 模块, COUNT(*) AS 新增 FROM users WHERE student_id LIKE '205000%'
UNION ALL SELECT '档案', COUNT(*) FROM archives WHERE title LIKE '[DEMO]%'
UNION ALL SELECT '荣誉', COUNT(*) FROM honors WHERE title LIKE '[DEMO]%'
UNION ALL SELECT '公告', COUNT(*) FROM announcements WHERE title LIKE '[DEMO]%'
UNION ALL SELECT '留言', COUNT(*) FROM wall_message WHERE content LIKE '[DEMO]%'
UNION ALL SELECT '优秀校友', COUNT(*) FROM outstanding_alumni WHERE display_name LIKE '[DEMO]%'
UNION ALL SELECT '荣誉墙', COUNT(*) FROM showcase WHERE archive_id IN (SELECT id FROM archives WHERE title LIKE '[DEMO]%')
UNION ALL SELECT '首页轮播', COUNT(*) FROM hero_slide WHERE title LIKE '[DEMO]%'
UNION ALL SELECT '通知', COUNT(*) FROM notifications WHERE title LIKE '[DEMO]%'
UNION ALL SELECT '大屏授权', COUNT(*) FROM consents WHERE consent_text_hash = SHA2('demo-consent-text-v1', 256);
