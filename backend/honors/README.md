# alumni-honors 校友荣誉模块

校友管理平台 - 校友荣誉模块（《[定稿]API规范》第五章 Honors，统计接口唯一数据源）。
Spring Boot 3.5.5 + Spring Data JPA + Flyway，与 `backend/auth`、`backend/consents` 等模块同构，可独立运行。

## 接口

| 方法 | 路径 | 说明 | 权限 | 成功状态码 |
|---|---|---|---|---|
| POST | `/api/v1/honors` | 创建荣誉（生成荣誉记录 + Reviews 待审记录） | student / alumni | `201` |
| GET | `/api/v1/honors` | 荣誉列表（分页 + 多条件筛选） | 任意登录用户 | `200` |
| GET | `/api/v1/honors/{id}` | 荣誉详情（含 attachment_ids） | 任意登录用户 | `200` |
| PATCH | `/api/v1/honors/{id}` | 修改荣誉（未进入审核前，含附件换绑） | 本人 | `200` |

### POST /api/v1/honors 创建荣誉

**请求体**：

```json
{
  "archive_id": 12,
  "title": "国家奖学金",
  "category": "scholarship",
  "level": "national",
  "awarded_at": "2025-11-01",
  "attachment_ids": [7, 8]
}
```

- `archive_id` 可空（荣誉不一定挂在档案上）；`attachment_ids` 可空
- `awarded_at` 为纯日期 `YYYY-MM-DD`

**响应 `201`**：

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "id": 501,
    "user_id": 100,
    "archive_id": 12,
    "title": "国家奖学金",
    "category": "scholarship",
    "level": "national",
    "awarded_at": "2025-11-01",
    "status": "pending_first",
    "created_at": "2026-09-18T02:56:00Z",
    "updated_at": "2026-09-18T02:56:00Z"
  },
  "request_id": "...",
  "timestamp": 1758112800000
}
```

### GET /api/v1/honors 荣誉列表

**查询参数**（均可选）：

| 参数 | 说明 |
|---|---|
| `page` | 页码，默认 1，必须 ≥1 |
| `page_size` | 每页条数，默认 20，1~100 |
| `keyword` | 模糊匹配 `users.student_id` 或 `alumni_base.real_name` |
| `college_id` | 字典 `college` 项 ID（内部转 label 后匹配 `alumni_base.college`） |
| `grad_year` | 毕业年份，匹配 `alumni_base.grad_year` |
| `category` | 荣誉类别精确匹配 |
| `status` | `pending_first/pending_second/approved/rejected` 之一 |
| `date_from` / `date_to` | `awarded_at` 闭区间，`YYYY-MM-DD` |
| `sort` | 排序字段，`-` 前缀降序，默认 `-created_at`；允许 `created_at/awarded_at/title`，非法字段忽略用默认 |

**响应 `200`**：

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "list": [
      {
        "id": 501,
        "user_id": 100,
        "archive_id": 12,
        "title": "国家奖学金",
        "category": "scholarship",
        "level": "national",
        "awarded_at": "2025-11-01",
        "status": "pending_first",
        "created_at": "2026-09-18T02:56:00Z",
        "updated_at": "2026-09-18T02:56:00Z"
      }
    ],
    "total": 1,
    "page": 1,
    "page_size": 20
  },
  "request_id": "...",
  "timestamp": 1758112800000
}
```

### GET /api/v1/honors/{id} 荣誉详情

**响应 `200`**：`data` 为荣誉全部字段 + `attachment_ids`（该荣誉已引用附件的 ID 列表）：

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "id": 501,
    "user_id": 100,
    "archive_id": 12,
    "title": "国家奖学金",
    "category": "scholarship",
    "level": "national",
    "awarded_at": "2025-11-01",
    "status": "pending_first",
    "created_at": "2026-09-18T02:56:00Z",
    "updated_at": "2026-09-18T02:56:00Z",
    "attachment_ids": [7, 8]
  },
  "request_id": "...",
  "timestamp": 1758112800000
}
```

### PATCH /api/v1/honors/{id} 修改荣誉

**请求体**（全部字段可选，仅更新非 null 字段）：

```json
{
  "title": "全国大学生数学建模竞赛一等奖",
  "category": "competition",
  "level": "national",
  "awarded_at": "2025-09-01",
  "archive_id": 20,
  "attachment_ids": [9]
}
```

**响应 `200`**：`data` 为更新后的完整荣誉字段。

### 错误响应示例

**`400 INVALID_PARAMETER`**（category 不在 honor_category 字典 / 分页越界 / status 非法 / 日期格式错误）：

```json
{
  "code": "INVALID_PARAMETER",
  "message": "荣誉类别不存在",
  "data": null,
  "request_id": "...",
  "timestamp": 1758112800000
}
```

**`403 FORBIDDEN`**（非 student/alumni 创建 / 关联他人档案 / 引用他人附件 / 修改他人荣誉）：

```json
{ "code": "FORBIDDEN", "message": "只能修改本人荣誉", "data": null, "request_id": "...", "timestamp": 1758112800000 }
```

**`404 NOT_FOUND`**（荣誉 / 关联档案 / 附件不存在）：

```json
{ "code": "NOT_FOUND", "message": "荣誉不存在", "data": null, "request_id": "...", "timestamp": 1758112800000 }
```

**`409 INVALID_STATE`**（附件已被引用 / 审核已开始或已结束后修改）：

```json
{ "code": "INVALID_STATE", "message": "审核已开始或已结束，不可修改", "data": null, "request_id": "...", "timestamp": 1758112800000 }
```

## 业务规则

**状态机**：创建后 `status='pending_first'`，由 reviews 模块驱动双级审核
`pending_first → pending_second → approved/rejected`；本模块仅负责创建与未审前修改。

**创建荣誉**：

- 仅 student / alumni 角色，其他角色 `403 FORBIDDEN`「仅学生/校友可创建荣誉」
- `category` 必须存在于 dicts（`type='honor_category'`，value 匹配），否则 `400`「荣誉类别不存在」
- `archive_id` 非空时：档案不存在 `404`「关联档案不存在」；档案不归属当前用户 `403`「只能关联本人档案」
- `attachment_ids` 逐条校验：附件不存在 `404`「附件不存在: {id}」；非本人上传 `403`「只能引用本人上传的附件」；状态非 `unreferenced` `409`「附件已被引用: {id}」
- **业务幂等**：同用户 + 同标题 + 同获奖日期 的荣誉处于 `pending_first/pending_second/approved` 时禁止重复创建，`409 INVALID_STATE`「相同荣誉已提交（待审核或已通过），请勿重复创建」；`rejected` 状态不占用，允许重新提交
- 同一 `@Transactional` 内：保存荣誉（`status='pending_first'`）→ 附件逐条置 `referenced`（回填 `biz_type='honor'`、`ref_id`、`referenced_at`）→ 生成 Review（`biz_type='honor'`、`biz_id=荣誉ID`、`stage='first'`、`status='pending'`）→ 写 ReviewLog（`action='create'`、`operator_id=NULL` 系统动作）
- `created_at/updated_at` 由数据库默认值生成，插入后回读返回（ISO 8601 UTC）

**列表**：任意登录用户；JPA Specification 组合筛选，keyword/college/grad_year 走
`honors → users → alumni_base` 关联；`college_id` 先查 dicts（`type='college'`）取 label，
字典项不存在直接返回空列表；`status` 仅允许四态，否则 `400`；日期区间解析失败 `400`。

**详情**：任意登录用户；`id` 非正整数或不存在均 `404`「荣誉不存在」。

**修改荣誉**：

- 仅本人（`honors.user_id == 当前用户 ID`），否则 `403`「只能修改本人荣誉」（越权红线）
- 仅 `status='pending_first'`（审核尚未处理）可改，否则 `409`「审核已开始或已结束，不可修改」
- 逐字段更新非 null 字段，category 字典校验、archive 归属校验同创建
- `attachment_ids` 非 null 时：先释放该荣誉全部旧附件（`status='unreferenced'`、`ref_id/referenced_at` 置空，`biz_type` 保留 `'honor'`），再按创建规则校验并引用新附件，全程同一事务

## 技术栈与结构

```
src/main/java/com/alumni/honors/
├── HonorsApplication.java            # 启动类（端口 8085）
├── common/                           # ApiResponse / ErrorCode / BizException /
│                                     #   GlobalExceptionHandler / RequestIdFilter / RequestIdHolder
├── security/                         # SecurityConfig（honors/** 需登录、CORS、401/403 JSON）/
│                                     #   JwtService / JwtAuthFilter / AuthPrincipal
├── dto/HonorDtos.java                # CreateHonorRequest / UpdateHonorRequest（@Valid 入参校验）
├── entity/                           # Honor（全列 + 只读 user 关联）/ User / AlumniBase /
│                                     #   Archive / Attachment / Review / ReviewLog / DictItem
├── repository/                       # HonorRepository（JpaSpecificationExecutor）/ ReviewRepository /
│                                     #   ReviewLogRepository / AttachmentRepository /
│                                     #   ArchiveRepository / DictItemRepository
├── service/HonorService.java         # 角色/字典/归属/附件校验、附件引用与释放、
│                                     #   Review 生成、Specification 列表筛选
└── controller/HonorController.java   # POST 201 / GET 列表 / GET /{id} / PATCH /{id}
src/main/resources/
├── application.yml
└── db/migration/V1__init.sql         # honors 建表（IF NOT EXISTS）
```

- JWT 与 auth 模块同一密钥（`app.jwt.secret`，环境变量 `JWT_SECRET` 覆盖），token 跨模块互通。
- 统一响应 `{code, message, data, request_id, timestamp}`，Jackson 全模块 snake_case。

## 运行

前置：MySQL 8，库 `alumni_platform` 已建（users / alumni_base / archives / attachments /
reviews / review_logs / dicts 等表已存在，见 `backend/db/schema.sql`），
模块启动时 Flyway 自动补建 honors 表。

```bash
mvn spring-boot:run
# 或
mvn -DskipTests package && java -jar target/alumni-honors-0.1.0.jar
```

环境变量：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_USER` | `root` | 数据库用户名 |
| `DB_PASSWORD` | `123456` | 数据库密码 |
| `JWT_SECRET` | 开发默认密钥 | HS256 密钥（base64 32 字节），生产必须覆盖 |

服务端口：`8085`。

## 测试

```bash
mvn test
```

`HonorServiceTest`（20 个用例，Mockito mock Repository，无数据库依赖）覆盖：
创建正常（存荣誉+引用附件+建 Review+建系统日志）、非 student/alumni 403、category 不在字典 400、
档案不存在 404、关联他人档案 403、附件不存在 404、附件他人上传 403、附件已引用 409、
无档案无附件创建成功、列表 page/page_size 非法 400、status 筛选非法 400、
college_id 字典不存在返回空列表、列表正常分页（含排序映射）、详情不存在 404、
详情含 attachment_ids、修改非本人 403、非 pending_first 409、
修改成功（含附件换绑：旧释放新引用）、修改 category 非法 400。
