# alumni-consents 隐私授权模块

校友管理平台 - 隐私授权模块（《[定稿]API规范》第九章 Consents）。
Spring Boot 3.5.5 + Spring Data JPA + Flyway，与 `backend/auth`、`backend/showcase` 模块同构，可独立运行。

## 接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/v1/consents` | 创建授权（生成 consent_id），成功返回 `201` | student / alumni |
| GET | `/api/v1/consents/my` | 我的授权记录（created_at 倒序） | student / alumni |
| DELETE | `/api/v1/consents/{id}` | 撤回授权（screen 授权同步自动下架） | 本人 |

**POST /consents 请求体**：

```json
{ "archive_id": 12, "scope": "screen" }
```

- `scope` 预留：`screen`（大屏）/ `site`（站点）

**POST /consents 响应 `201`**：

```json
{
  "code": "OK",
  "message": "ok",
  "data": { "id": 501, "archive_id": 12, "scope": "screen", "created_at": "2026-09-18T02:56:00Z" },
  "request_id": "...",
  "timestamp": 1758112800000
}
```

**DELETE /consents/{id} 响应 `200`**：`data = { "id": 501, "deleted": true, "cascade_unpublished": true }`

## 业务规则

**创建授权**：

- 仅 student / alumni 角色，其他角色 `403 FORBIDDEN`
- `archive_id` 必填正整数、`scope` 必填且仅允许 `screen|site`，否则 `400 INVALID_PARAMETER`
- 档案不存在 → `404 NOT_FOUND`
- 档案必须属于当前用户（`archives.user_id == 当前用户 ID`），替他人档案授权 → `403 FORBIDDEN`（越权红线）
- 同一 `(archive_id, scope)` 已存在授权 → `409 INVALID_STATE`（`uk_consents_archive_scope` 唯一约束兜底并发，`DataIntegrityViolationException` 转 409）
- `created_at` 由数据库默认值生成，插入后回读返回（ISO 8601 UTC）

**我的授权**：仅 student / alumni；返回 `{list:[{id, archive_id, scope, created_at}]}`，按 `created_at` 倒序。

**撤回授权**：

- 需登录；授权记录不存在 → `404 NOT_FOUND`
- 仅授权归属人本人（`consents.user_id == 当前用户 ID`）可删，否则 `403 FORBIDDEN`（越权红线）
- 级联红线：撤回 `scope='screen'` 的授权时，**同事务**删除 showcase 表中该 `archive_id` 的上架记录（大屏立即下架），`cascade_unpublished` 标记是否实际下架了上架中的内容；`scope='site'` 不级联
- 删除授权 + 级联下架在同一个 `@Transactional` 中

## 技术栈与结构

```
src/main/java/com/alumni/consents/
├── ConsentsApplication.java          # 启动类（端口 8084）
├── common/                           # ApiResponse / ErrorCode / BizException /
│                                     #   GlobalExceptionHandler / RequestIdFilter / RequestIdHolder
├── security/                         # SecurityConfig（consents/** 需登录、CORS、401/403 JSON）/
│                                     #   JwtService / JwtAuthFilter / AuthPrincipal
├── dto/ConsentDtos.java              # CreateConsentRequest（@Valid 入参校验）
├── entity/                           # Consent（全列）/ Archive（id,userId,status）/ Showcase（id,archiveId）
├── repository/                       # ConsentRepository（existsByArchiveIdAndScope /
│                                     #   findByUserIdOrderByCreatedAtDesc）/ ArchiveRepository /
│                                     #   ShowcaseRepository（deleteByArchiveId）
├── service/ConsentService.java       # 角色校验、归属校验、重复兜底、级联下架
└── controller/ConsentController.java # POST 201 / GET /my / DELETE /{id}
src/main/resources/
├── application.yml
└── db/migration/V1__init.sql         # consents 建表（IF NOT EXISTS）
```

- JWT 与 auth 模块同一密钥（`app.jwt.secret`，环境变量 `JWT_SECRET` 覆盖），token 跨模块互通。
- 统一响应 `{code, message, data, request_id, timestamp}`，Jackson 全模块 snake_case。

## 运行

前置：MySQL 8，库 `alumni_platform` 已建（users / archives / showcase 等表已存在，
见 `backend/db/schema.sql`），模块启动时 Flyway 自动补建 consents 表。

```bash
mvn spring-boot:run
# 或
mvn -DskipTests package && java -jar target/alumni-consents-0.1.0.jar
```

环境变量：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_USER` | `root` | 数据库用户名 |
| `DB_PASSWORD` | `123456` | 数据库密码 |
| `JWT_SECRET` | 开发默认密钥 | HS256 密钥（base64 32 字节），生产必须覆盖 |

## 测试

```bash
mvn test
```

`ConsentServiceTest`（15 个用例，Mockito mock Repository，无数据库依赖）覆盖：
创建正常（数据完整）/ student 角色、非 student/alumni 403、archive_id 非法 400、scope 非法 400、
档案不存在 404、替他人档案授权 403、重复授权 409、并发唯一约束冲突 409、
我的授权 created_at 倒序、非 student/alumni 403、撤回 screen 级联下架、撤回 site 不级联、
撤回他人授权 403、撤回不存在 404。
