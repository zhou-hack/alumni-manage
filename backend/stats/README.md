# alumni-stats 统计模块

校友管理平台 - 统计模块（《[定稿]API规范》第十章 Stats）。
Spring Boot 3.5.5 + Spring Data JPA + Flyway，与 `backend/auth` 模块同构，可独立运行。

## 接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/v1/stats/overview` | 总量看板：校友数/档案数/荣誉数/待审数 | college_admin / school_admin |
| GET | `/api/v1/stats/honors` | 荣誉分布（按类别/级别，**仅统计 Honor 表**） | college_admin / school_admin |
| GET | `/api/v1/stats/colleges?limit=8` | 学院排行（档案数 Top N） | college_admin / school_admin |

**统计口径**（`backend/db/schema.sql` 第十章，本模块不建表，全部为聚合查询）：

- 校友数 = `COUNT(users)`，档案数 = `COUNT(archives)`，荣誉数 = `COUNT(honors)`，
  待审数 = `COUNT(reviews WHERE status='pending')`
- 荣誉分布 = `GROUP BY honors(category, level)`，按数量降序
- 学院排行 = `archives ⋈ users ⋈ alumni_base GROUP BY alumni_base.college`，
  `LEFT JOIN dicts(type='college')` 取字典 ID，按档案数降序取 Top N

**limit 参数**：未传默认 8；合法区间 1~100，越界（0 / 101 / 负数）或非整数返回
`400 INVALID_PARAMETER`。

## 技术栈与结构

```
src/main/java/com/alumni/stats/
├── StatsApplication.java             # 启动类（端口 8081）
├── common/                           # ApiResponse / ErrorCode / BizException /
│                                     #   GlobalExceptionHandler / RequestIdFilter / RequestIdHolder
├── security/                         # SecurityConfig（stats/** 仅 COLLEGE_ADMIN/SCHOOL_ADMIN、CORS）/
│                                     #   JwtService / JwtAuthFilter / AuthPrincipal
├── entity/                           # User / Archive / Honor / Review（只读统计，部分列映射）
├── repository/                       # UserRepository / ArchiveRepository /
│                                     #   HonorRepository（JPQL 分组 + KeyCountProjection）/
│                                     #   ReviewRepository（countByStatus）/
│                                     #   CollegeRankRepository（native query + CollegeRankProjection）
├── service/StatsService.java         # overview / honors / colleges（limit 校验）
└── controller/StatsController.java
src/main/resources/
├── application.yml
└── db/migration/.gitkeep             # stats 无自有表，目录占位
```

- JWT 与 auth 模块同一密钥（`app.jwt.secret`，环境变量 `JWT_SECRET` 覆盖），token 跨模块互通。
- 统一响应 `{code, message, data, request_id, timestamp}`，Jackson 全模块 snake_case。

## 运行

前置：MySQL 8，库 `alumni_platform` 已建（users / alumni_base / archives / honors / reviews / dicts
表已存在，见 `backend/db/schema.sql`）。本模块无自有表，Flyway 仅做基线登记。

```bash
mvn spring-boot:run
# 或
mvn -DskipTests package && java -jar target/alumni-stats-0.1.0.jar
```

环境变量：`DB_USER`（默认 root）、`DB_PASSWORD`（默认 123456）、`JWT_SECRET`。

## 测试

```bash
mvn test
```

`StatsServiceTest`（11 个用例，Mockito mock Repository，无数据库依赖）覆盖：
overview 四项计数组装与待审口径（status='pending'）、honors 双维度聚合与空表、
colleges 默认 limit=8、limit 边界（1 / 100 合法）、limit 越界（0 / 101 / 负数 → INVALID_PARAMETER）、
排行结果按序透传。
