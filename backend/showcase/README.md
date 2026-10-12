# alumni-showcase 展览馆/大屏模块

校友管理平台 - 上下架与大屏模块（《[定稿]API规范》第八章 Showcase / Screen）。
Spring Boot 3.5.5 + Spring Data JPA + Flyway，与 `backend/auth` 模块同构，可独立运行。

## 接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/v1/showcase/{archive_id}/publish` | 上架 | school_admin |
| POST | `/api/v1/showcase/{archive_id}/unpublish` | 下架 | school_admin |
| GET | `/api/v1/screen/feed` | 大屏轮播数据（免认证） | 公开 |
| GET | `/api/v1/screen/feed?college_id=3` | 按学院筛选 | 公开 |

**上架条件**（任一不满足即 409，档案不存在 404）：

- Archive 状态必须为 `approved`
- 必须存在有效 `consents(scope='screen')` 记录（提示"缺少有效大屏授权"）
- 未处于上架中（并发重复上架由 `uk_showcase_archive` 唯一约束兜底，转 409）

**大屏红线口径**：feed 由单条 SQL 强制 `showcase ⋈ archives(status='approved') ⋈ consents(scope='screen')`，
授权撤回后该行天然不再出现；封面取发布时快照（首个已引用图片附件）。

## 技术栈与结构

```
src/main/java/com/alumni/showcase/
├── ShowcaseApplication.java          # 启动类（端口 8083）
├── common/                           # ApiResponse / ErrorCode / BizException /
│                                     #   GlobalExceptionHandler / RequestIdFilter / RequestIdHolder
├── security/                         # SecurityConfig（feed 公开、showcase/** 仅 SCHOOL_ADMIN、CORS）/
│                                     #   JwtService / JwtAuthFilter / AuthPrincipal
├── entity/                           # Showcase / Consent / Archive / Attachment / User / AlumniBase
├── repository/                       # ShowcaseRepository（feed native query + FeedProjection）/
│                                     #   ConsentRepository / AttachmentRepository / ArchiveRepository
├── service/ShowcaseService.java      # 上架四重校验、角色二次校验、下架、feed
└── controller/ShowcaseController.java
src/main/resources/
├── application.yml
└── db/migration/V1__init.sql         # showcase / consents 建表（IF NOT EXISTS）
```

- JWT 与 auth 模块同一密钥（`app.jwt.secret`，环境变量 `JWT_SECRET` 覆盖），token 跨模块互通。
- 统一响应 `{code, message, data, request_id, timestamp}`，Jackson 全模块 snake_case。

## 运行

前置：MySQL 8，库 `alumni_platform` 已建（users / alumni_base / archives / attachments / dicts 表已存在，
见 `backend/db/schema.sql`），模块启动时 Flyway 自动补建 showcase / consents 两表。

```bash
mvn spring-boot:run
# 或
mvn -DskipTests package && java -jar target/alumni-showcase-0.1.0.jar
```

环境变量：`DB_USER`（默认 root）、`DB_PASSWORD`（默认 123456）、`JWT_SECRET`。

## 测试

```bash
mvn test
```

`ShowcaseServiceTest`（15 个用例，Mockito mock Repository，无数据库依赖）覆盖：
上架正常/无封面、档案不存在 404、未过审 409、无授权 409、重复上架 409、并发唯一约束冲突 409、
college_admin/校友 403、下架正常、未上架 404、feed 红线口径（撤回即隐藏）与学院筛选。
