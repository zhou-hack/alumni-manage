# alumni-reviews 审核模块

校友管理平台审核中心（《[定稿]API规范》第七章）。Review 是独立资源，档案审核、荣誉审核、删除审核、激活人工核验全部共用本通道。

## 架构

Spring Boot 3.5.5 + Spring Data JPA + Flyway + Spring Security（JWT），与 auth 模块同构，可独立运行。

```
com.alumni.reviews
├── ReviewsApplication          启动类（端口 8082）
├── common/                     ApiResponse / ErrorCode / BizException / GlobalExceptionHandler / RequestIdFilter / RequestIdHolder
├── security/                   JwtService / JwtAuthFilter / AuthPrincipal / SecurityConfig
├── entity/                     Review / ReviewLog / Archive（仅 id+status）/ Honor（仅 id+status）
├── repository/                 ReviewRepository / ReviewLogRepository / ArchiveRepository / HonorRepository
├── service/ReviewService       审核状态机 + 角色-阶段分工 + 业务状态同步
└── controller/ReviewController 5 个 REST 端点
```

- 表结构由 Flyway 管理（`src/main/resources/db/migration/V1__init.sql`，与 `backend/db/schema.sql` 一致）；`jpa.hibernate.ddl-auto=validate` 只做校验。
- 时间列（created_at / updated_at）由数据库管理（`insertable=false updatable=false`），出参为 ISO 8601 UTC。
- JWT 与 auth 模块共用同一密钥（`app.jwt.secret`），本模块只校验不签发；`/api/v1/reviews/**` 一律要求登录，角色细粒度规则在 Service 层判定（日志接口允许提交人本人访问）。
- approve / reject 的"记录更新 + 业务表状态同步 + 审核日志写入"在同一 `@Transactional` 内完成。

## 状态机

| biz_type | 流转 |
|---|---|
| archive / honor / archive_delete（双级） | 初审通过 → stage=second（业务 pending_second）；复核通过 → approved（archive_delete → 业务 deleted）；任意阶段驳回 → rejected（业务 rejected，archive_delete 驳回由业务模块自行恢复） |
| activate_manual（单级） | first 通过即 approved，无业务表；college_admin / school_admin 均可处理 |

角色-阶段分工：college_admin 管 first（初审），school_admin 管 second（复核）。

## 接口

统一前缀 `/api/v1`，统一响应 `{code, message, data, request_id, timestamp}`，字段 snake_case。

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/reviews?biz_type=&status=&page=&page_size=` | 待办列表（学院看 first，校级看 second；status 默认 pending；page_size 默认 20、最大 100） | 审核角色 |
| GET | `/reviews/{review_id}` | 审核详情 | 审核角色 |
| POST | `/reviews/{review_id}/approve` | 通过 | 审核角色（阶段匹配） |
| POST | `/reviews/{review_id}/reject` | 驳回（body `{"reason":"..."}` 必填） | 审核角色（阶段匹配） |
| GET | `/reviews/{review_id}/logs` | 审核日志 | 审核角色 + 提交人本人 |

错误语义：非审核角色 403 FORBIDDEN；记录不存在 404 NOT_FOUND；已处理记录重复操作 409 INVALID_STATE；非法分页 / 状态参数、缺 reason 400 INVALID_PARAMETER；未登录或 Token 过期 401 UNAUTHORIZED。

## 构建 / 运行 / 测试

```powershell
# 编译
mvn -q -DskipTests compile
# 单元测试（14 个用例，Mockito mock repository，不依赖数据库）
mvn -q test
# 打包
mvn -q -DskipTests package
# 运行（需先启动 MySQL 并创建 alumni_platform 库，Flyway 自动建表）
java -jar target/alumni-reviews-0.1.0.jar
```

### 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_USER` | `root` | MySQL 用户名 |
| `DB_PASSWORD` | `123456` | MySQL 密码 |
| `JWT_SECRET` | 开发默认密钥 | JWT HS256 密钥（base64 32 字节），必须与 auth 模块一致，生产环境必须覆盖 |

数据源：`jdbc:mysql://127.0.0.1:3306/alumni_platform`（UTC 会话时区），服务端口 `8082`。
