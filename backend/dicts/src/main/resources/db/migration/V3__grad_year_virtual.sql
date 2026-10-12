-- grad_year 改为虚拟类型（按当前年动态生成），删除落库种子行
-- 年份是可枚举数据，不是业务主数据：GET /dicts/grad_year 由服务动态出参
DELETE FROM dicts WHERE type = 'grad_year';
