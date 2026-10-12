# 校友管理平台 API 接口文档

> 版本：v1.2 Final P0阶段
> 维护人：后端开发者
> 通用规范依据：《项目全生命周期规划》第三章《后端 API 统一接口规范》
> 设计风格：RESTful（资源导向 + 标准 HTTP 方法 + 标准 HTTP 状态码）

**变更流程**：API v1 在前后端联调开始前冻结。任何接口变更必须：① 群里说明 → ② 修改本文档 → ③ 前后端双方确认 → ④ 涉及 breaking change 时升级版本号（/api/v2）。

---

## 一、通用约定

### 1.1 Base URL 与路径规范

| 项 | 约定 |
|---|---|
| Base URL | `http(s)://{host}/api/v1` |
| 路径风格 | 小写复数名词、`kebab-case`；资源标识用 `{id}` |
| 字段命名 | `snake_case` |
| HTTP 方法 | GET 查询 / POST 创建或动作 / PATCH 局部更新 / DELETE 删除 |

> 文档中所有路径均为**相对于 Base URL 的相对路径**。
> 例如 `POST /auth/login` 实际地址为 `http(s)://{host}/api/v1/auth/login`。
> 禁止出现 `/api/auth/login`、`/api/v1/auth/login` 等重复前缀写法。

### 1.2 认证

| 项 | 约定 |
|---|---|
| 认证头 | `Authorization: Bearer <access_token>` |
| access_token | 有效期 **2 小时** |
| refresh_token | 有效期 **7 天**，刷新时轮换（旧 token 即刻作废） |
| 免认证接口 | `/auth/**`（除 `/auth/logout`、`/auth/me`）、`/screen/**`，其余一律鉴权 |
| 角色 | `student`（学生）/ `alumni`（校友，已毕业）/ `counselor`（导员）/ `college_admin`（院系管理员）/ `school_admin`（学校管理员）/ `system_admin`（系统管理员） |

> 角色说明：
> - `student` 与 `alumni` 为**同一权限级别**，仅毕业状态不同：激活时按校友库毕业信息自动标记，未毕业为 `student`，已毕业为 `alumni`
> - `counselor`（导员）：可查看所带班级/年级学生的档案提交情况，**不参与审核流转**（审核仅 `college_admin` / `school_admin`）
> - `system_admin`（系统管理员）：负责账号管理、字典维护、系统配置等运维操作，**不参与业务审核流转**
> - 本文档中"审核角色"= `college_admin` / `school_admin`；"校友端"= `student` / `alumni`

### 1.3 统一响应

成功响应：

```json
{
  "code": "OK",
  "message": "ok",
  "data": {},
  "request_id": "9f2c1a7e-3b...",
  "timestamp": 1758112800000
}
```

失败响应：

```json
{
  "code": "AUTH_VERIFY_FAILED",
  "message": "信息不匹配",
  "data": null,
  "request_id": "9f2c1a7e-3b...",
  "timestamp": 1758112800000
}
```

- **HTTP 状态码表达 HTTP 层结果，`code` 为稳定的业务错误码**（见第十三章错误码表）
- `message` 为可直接展示的用户提示；**前端不得通过 `message` 判断业务状态，只能依据 HTTP 状态码 + `code`**
- `request_id` 用于日志追踪与排查
- `timestamp` 为服务端响应时间的 **Unix 毫秒时间戳**

**HTTP 状态码语义表**：

| HTTP | 含义 | 典型场景 |
|---|---|---|
| 200 | 请求成功 | 查询 / 更新 / 动作成功 |
| 201 | 资源创建成功 | POST 创建资源 |
| 400 | 请求参数错误 | 参数缺失 / 格式错误 / 业务校验失败 |
| 401 | 未认证 | 未登录、Token 无效或过期 |
| 403 | 无权限 | 已登录但角色 / 数据范围不允许 |
| 404 | 资源不存在 | id 不存在 |
| 409 | 冲突 | 状态不允许 / 重复提交 / 版本冲突 / 资源被引用 |
| 429 | 触发限流 | 频率超限 |
| 500 | 服务器内部错误 | 未捕获异常 |
| 502 | 网关 / 上游服务不可用 | 依赖服务故障 |

> 前端拦截器示例：`401` → 静默刷新一次 Token；`403` → 提示无权限；`409` → 按 `code` 区分冲突类型。

### 1.4 分页 / 排序 / 筛选

请求：

```
?page=1&page_size=20&sort=-created_at
```

- `page`：页码，从 1 开始
- `page_size`：每页数量，默认 20，最大 100
- `sort`：排序字段，`-` 前缀表示降序

响应（`data` 结构）：

```json
{ "list": [], "total": 137, "page": 1, "page_size": 20 }
```

筛选：资源字段直接作为 query 参数，如 `?college_id=3&grad_year=2022`。

### 1.5 时间格式

| 用途 | 格式 | 示例 |
|---|---|---|
| 业务时间字段（created_at 等） | **ISO 8601 UTC** | `2026-09-17T02:56:00Z` |
| 响应体 `timestamp` | Unix 毫秒时间戳 | `1758112800000` |
| 纯日期字段（awarded_at 等） | `YYYY-MM-DD` | `2025-10-01` |

- 服务端、数据库统一保存 UTC，前端根据用户本地时区转换显示
- **禁止**在 API 中使用 `2026-09-17 10:56:00` 这类带时区歧义的字符串

### 1.6 敏感字段

手机号 / 身份证出参一律服务端脱敏（`138****1234`）。

### 1.7 密码传输与存储

- **禁止明文传输密码**：所有涉及密码的接口（登录、激活设置密码等），前端必须先将密码做 **SHA-256 散列（hex 小写，64 字符）** 后再上传
- 请求体中的 `password` 字段一律为散列值，例如 `"password": "3a7bd3e2360c..."`；服务端**拒绝接收明显不符合 64 位 hex 格式的值**（`400` + `INVALID_PARAMETER`）
- 服务端收到散列值后，再用 **BCrypt 二次散列**入库；比对时同样先 SHA-256 再 BCrypt 校验
- 双层防护：HTTPS 保护传输链路，前端散列保证明文不出浏览器、不进服务端日志
- 密码强度要求（≥8 位、含字母+数字）由**前端在散列之前**校验原始密码

---

## 二、认证 Auth（B1）

### 2.1 首次激活（三要素核验 + 手机绑定）

**激活主流程**：

| 步骤 | 方法 | 路径 | 说明 |
|---|---|---|---|
| ① | POST | `/auth/activate/verify` | 学号+姓名+身份证号与校友库比对 |
| ② | POST | `/auth/activate/sms` | 发送验证码（可同步更换手机号） |
| ③ | POST | `/auth/activate/confirm` | 验证码 + 设置密码，完成激活 |

**核验不通过的兜底**：无任何因素匹配时，走人工核验通道（见 2.1.4），管理员人工比对身份后开通激活资格。

#### 2.1.1 POST /auth/activate/verify（三要素比对）

请求：

```json
{ "student_id": "2021000001", "real_name": "测试校友001", "id_card": "110101**********" }
```

匹配成功响应 `200`：

```json
{
  "code": "OK",
  "message": "ok",
  "data": { "verify_token": "v_tok_xxx", "expires_in": 600, "masked_phone": "138****1234" },
  "request_id": "...",
  "timestamp": 1758112800000
}
```

- `verify_token` 10 分钟有效，服务端绑定学号，后续步骤必须携带
- `masked_phone` 为校友库中登记的手机号（脱敏），校友可直接使用该号接收验证码，也可在下一步更换新手机号
- 限流：单 IP 10 次/小时 + 单学号 5 次/天（触发返回 `429` + `RATE_LIMITED`）

无匹配响应 `400` + `AUTH_VERIFY_FAILED`（不指明具体字段）：

```json
{
  "code": "AUTH_VERIFY_FAILED",
  "message": "信息不匹配，如确认本人信息无误，可提交人工核验",
  "data": { "can_manual_request": true },
  "request_id": "...",
  "timestamp": 1758112800000
}
```

#### 2.1.2 POST /auth/activate/sms（发验证码，可换绑手机号）

请求：

```json
{ "verify_token": "v_tok_xxx", "new_phone": "13900001111" }
```

- `new_phone` 选填：不传 → 验证码发到库内登记手机号；传入 → 发到新手机号，激活成功后账号绑定新号（换绑以验证码通过为准，确保本人持有新号）

#### 2.1.3 POST /auth/activate/confirm（完成激活）

请求：

```json
{ "verify_token": "v_tok_xxx", "sms_code": "123456", "password": "3a7bd3e2360c..." }
```

响应：同登录（见 2.2），直接签发令牌。

- 学号已激活：`409` + `AUTH_ALREADY_ACTIVATED`
- 验证码错误或过期：`400` + `AUTH_CODE_INVALID`
- `password` 为前端 SHA-256 散列值（见 1.7）；前端在散列前校验原始密码 ≥8 位且含字母+数字

#### 2.1.4 POST /auth/activate/manual-request（人工核验申请）

适用场景：三要素与校友库无任何匹配（如库内信息缺失、身份证升位、姓名生僻字录入差异等），校友自认信息无误时提交人工核验。

请求：

```json
{
  "student_id": "2021000001",
  "real_name": "测试校友001",
  "id_card": "110101**********",
  "contact_phone": "13900001111",
  "note": "身份证 15 位升 18 位，旧号已停用",
  "attachment_ids": [103]
}
```

- `attachment_ids`：学生证 / 毕业证 / 身份证照片等证明材料（走附件服务，`biz_type=activate`）
- 提交成功返回 `201`：`data: { "request_id_no": 56 }`，并生成一条 Review 记录（`biz_type=activate_manual`，见第七章）
- 管理员在审核中心人工比对：通过 → 服务端为该学号开通激活资格（校友重新走 2.1.1 即可匹配通过，或直接由管理员后台开通账号）；驳回 → 填写原因，前端轮询 / 登录页提示
- 同一学号存在待处理申请时重复提交：`409` + `INVALID_STATE`

### 2.2 登录

**POST /auth/login**（密码登录）

请求：

```json
{ "phone": "13800138000", "password": "3a7bd3e2360c..." }
```

> `password` 为前端 SHA-256 散列值（见 1.7），禁止明文上传。

响应 `200`：

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "access_token": "eyJ...",
    "refresh_token": "r_tok_xxx",
    "expires_in": 7200,
    "user": { "id": 1, "student_id": "2021000001", "nickname": "测试校友001", "role": "alumni" }
  },
  "request_id": "...",
  "timestamp": 1758112800000
}
```

**POST /auth/login/sms**（验证码登录）：`{ "phone", "sms_code" }`，响应同上。

**POST /auth/sms**（通用发码）：`{ "phone", "scene": "login" }`

- 6 位验证码，5 分钟有效，同号 10 分钟最多 3 条，验证成功后立即失效
- `scene`：`login`（验证码登录）/ `reset_password`（找回密码）

### 2.3 忘记密码（手机号 + SMS + 短时 reset_token）

| 步骤 | 方法 | 路径 | 说明 |
|---|---|---|---|
| ① | POST | `/auth/sms` | 发送验证码，`scene=reset_password` |
| ② | POST | `/auth/password/reset-token` | 校验验证码，签发 5 分钟 `reset_token` |
| ③ | POST | `/auth/password/reset` | 凭 `reset_token` + 新密码完成重置 |

> 设计要点：`/auth/password/reset` **不直接接受"手机号+验证码"调用**，必须先通过 ② 用验证码换取短时 `reset_token`（5 分钟、绑定手机号、一次性）。缺少或失效的 `reset_token` 直接 `401`，防止恶意脚本绕过验证码暴力调用重置接口。

**② POST /auth/password/reset-token**

请求：

```json
{ "phone": "13800138000", "sms_code": "123456" }
```

响应 `200`：

```json
{
  "code": "OK",
  "message": "ok",
  "data": { "reset_token": "rst_tok_xxx", "expires_in": 300 },
  "request_id": "...",
  "timestamp": 1758112800000
}
```

- `reset_token`：5 分钟有效，服务端绑定该手机号，**仅可用于 ③，一次性使用**（重置成功即作废）
- 验证码错误或过期：`400` + `AUTH_CODE_INVALID`；手机号未注册：`404` + `NOT_FOUND`（提示"该手机号未注册"）

**③ POST /auth/password/reset**

请求头：`Authorization: Bearer <reset_token>`

请求：

```json
{ "new_password": "3a7bd3e2360c..." }
```

- `new_password` 为前端 SHA-256 散列值（见 1.7），前端散列前校验强度
- `reset_token` 缺失 / 过期 / 已使用：`401` + `UNAUTHORIZED`（前端重新走 ①）
- 重置成功返回 `200`：该账号**所有已签发 Token 全部作废**（强制重新登录），前端跳登录页

### 2.4 令牌与当前用户

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/auth/refresh` | 刷新令牌（旧 refresh_token 即刻作废） | 公开（凭 refresh_token） |
| POST | `/auth/logout` | 登出，refresh_token 作废 | 登录 |
| GET | `/auth/me` | 当前用户信息 | 登录 |

**POST /auth/refresh**

请求：`{ "refresh_token": "r_tok_xxx" }`

响应 `200`：

```json
{
  "code": "OK",
  "message": "ok",
  "data": { "access_token": "eyJ...", "refresh_token": "r_tok_new", "expires_in": 7200 },
  "request_id": "...",
  "timestamp": 1758112800000
}
```

- Token 过期统一返回 `401` + `UNAUTHORIZED`
- 前端收到 `401` 后仅允许自动刷新一次，仍失败则跳转登录页

---

## 三、字典 Dicts（B1）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/dicts/{type}` | 按类型取字典项（college / major / grad_year / honor_category） | 登录 |
| POST | `/dicts/{type}` | 新增字典项 | school_admin |
| PATCH | `/dicts/{type}/{id}` | 修改 | school_admin |
| DELETE | `/dicts/{type}/{id}` | 删除 | school_admin |

**GET /dicts/college** 响应 `200`：

```json
{
  "code": "OK",
  "message": "ok",
  "data": [ { "id": 1, "label": "计算机学院", "value": "CS", "sort": 1 } ],
  "request_id": "...",
  "timestamp": 1758112800000
}
```

- 被引用的字典项禁止删除：`409` + `RESOURCE_REFERENCED`

---

## 四、档案 Archives（B2）

> 档案 = 校友提交的各类资料（award / experience / certificate 等 category 自由扩展）。
> 与荣誉（Honors）的关系：**Honor 是独立结构化资源**（含 level、awarded_at 等统计字段），可通过 `archive_id` 关联档案，但**不是 Archive 的一种 category**。统计接口只统计 Honor 表。

### 4.1 状态机

```
draft(草稿) → pending_first(待初审) → pending_second(待复核) → approved(通过)
                    │                        │
                    └──── rejected(驳回) ←───┘   驳回可重提：生成 version+1 新版本

任意状态 → deleting(删除审核中) → deleted(已删除) / 驳回恢复原状态
```

### 4.2 接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/archives` | 创建档案 | student / alumni |
| GET | `/archives` | 管理端列表（组合筛选） | 审核角色 |
| GET | `/archives/my` | 我的记录（含状态+驳回原因） | student / alumni |
| GET | `/archives/{id}` | 详情（本人或审核角色） | 登录 |
| PATCH | `/archives/{id}` | 修改（仅 draft / rejected 状态可改） | 本人 |
| POST | `/archives/{id}/resubmit` | 驳回重提，生成 version+1 | 本人 |
| POST | `/archives/{id}/delete-request` | **发起删除申请**（非立即删除） | 本人 |

**POST /archives** 请求：

```json
{
  "title": "全国大学生程序设计竞赛金奖",
  "category": "award",
  "content": "获奖经历描述...",
  "remark": "统一备注",
  "attachment_ids": [101, 102],
  "submit": true
}
```

- `submit=false` 存草稿；`submit=true` 进入 `pending_first`（返回 `201`）

**GET /archives** 筛选参数：`keyword`（学号/姓名）、`college_id`、`grad_year`、`category`、`status`、`date_from`、`date_to`

**POST /archives/{id}/delete-request**

- 语义：**创建删除审核申请**，不是立即删除；档案进入 `deleting` 状态，原内容保留
- 审核通过 → `deleted`（关联附件释放为 unreferenced）；驳回 → 恢复原状态，驳回原因回显
- 删除审核走 Reviews 通道（见第七章，`biz_type=archive_delete`）
- `deleting` 状态不可编辑、不可重提；重复发起返回 `409` + `INVALID_STATE`

**GET /archives/my** 响应 `200`：

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "list": [
      {
        "id": 12,
        "title": "...",
        "category": "award",
        "status": "rejected",
        "reject_reason": "证书照片不清晰，请重新上传",
        "version": 2,
        "created_at": "2026-09-17T02:56:00Z"
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

---

## 五、荣誉 Honors（B2）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/honors` | 创建荣誉（可通过 `archive_id` 关联档案） | student / alumni |
| GET | `/honors` | 列表（筛选参数同档案） | 登录 |
| GET | `/honors/{id}` | 详情 | 登录 |
| PATCH | `/honors/{id}` | 修改（未进入审核前） | 本人 |

请求体：

```json
{
  "archive_id": 12,
  "title": "国家奖学金",
  "category": "scholarship",
  "level": "national",
  "awarded_at": "2025-10-01",
  "attachment_ids": []
}
```

---

## 六、附件 Attachments（B1）

> 附件服务为**跨模块复用组件**，通过 `biz_type` 区分业务归属（`archive` / `honor` / `avatar` 等，随新模块扩充）。

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/attachments` | 统一上传（`multipart/form-data`，支持多文件） | 登录 |
| GET | `/attachments/{id}/preview` | 预览（图片/PDF 直出流） | 按业务归属继承 |
| DELETE | `/attachments/{id}` | 删除（仅未被引用附件） | college_admin / school_admin |

### 6.1 POST /attachments

请求（`multipart/form-data`）：

| 字段 | 必填 | 说明 |
|---|---|---|
| `file` | 是 | 文件，多文件传多个 `file` 字段 |
| `biz_type` | 是 | 业务归属 |
| `remark` | 否 | 统一备注（批量上传时多文件共享） |

单文件响应 `201`：

```json
{
  "code": "OK",
  "message": "ok",
  "data": { "id": 101, "biz_type": "archive", "url": "/files/archive/2026/09/xx.jpg", "name": "证书.jpg", "size": 204800, "mime": "image/jpeg" },
  "request_id": "...",
  "timestamp": 1758112800000
}
```

多文件响应：

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "success": [ { "id": 101, "url": "...", "name": "证书.jpg" } ],
    "failed": [ { "name": "a.exe", "reason": "文件类型不允许" } ]
  },
  "request_id": "...",
  "timestamp": 1758112800000
}
```

**限制与处理**：

- 文档类单文件 ≤ 10MB（超限 `400` + `FILE_TOO_LARGE`）；类型不允许 `400` + `FILE_TYPE_NOT_ALLOWED`；批量数量超限 `400` + `BATCH_LIMIT_EXCEEDED`
- 图片服务端处理：**最长边压缩至 ≤ 1280px**（保持原始宽高比），压缩后目标 ≤ 2MB（优先保尺寸后压质量）；原图不留存
- 上传成功后状态为 `unreferenced`，业务提交时通过 `attachment_ids` 建立引用（转为 `referenced`）
- **孤儿附件清理：`unreferenced` 状态超过 24 小时的附件由服务端定时任务自动删除**

附件表关键字段：

```
attachments
├── id
├── biz_type
├── status        (unreferenced / referenced)
├── created_at
└── referenced_at
```

### 6.2 GET /attachments/{id}/preview

- 图片、PDF 直接输出流；**本期不支持 Office 文档（doc/xls/docx/xlsx）在线预览**，前端引导下载或提示不支持
- 权限按业务归属继承：未审核档案的附件仅本人 + 审核角色可见；大屏公开内容附件免认证
- 不存在 `404` + `NOT_FOUND`；无权限 `403` + `FORBIDDEN`

### 6.3 DELETE /attachments/{id}

- 仅 `college_admin` / `school_admin` 可调用，校友本人无权删除（防误删在审材料）
- 已被引用附件禁止删除：`409` + `RESOURCE_REFERENCED`
- **审计**：每次删除写 `attachment_audit_log`（操作人、附件 ID、文件名、biz_type、IP、时间、删除原因），保留 ≥180 天

---

## 七、审核 Reviews（B2）

> **Review 是独立资源**，`{review_id}` 为审核记录 ID，非业务 ID。档案审核、荣誉审核、删除审核等全部共用 Reviews 通道，审核逻辑集中、可统一测试。

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/reviews` | 待办列表（学院看 first，校级看 second） | 审核角色 |
| GET | `/reviews/{review_id}` | 审核详情 | 审核角色 |
| POST | `/reviews/{review_id}/approve` | 通过 | 审核角色 |
| POST | `/reviews/{review_id}/reject` | 驳回（`reason` 必填） | 审核角色 |
| GET | `/reviews/{review_id}/logs` | 审核日志（谁/何时/动作/原因） | 审核角色 + 本人 |

Review 数据结构：

```json
{
  "id": 88,
  "biz_type": "archive",
  "biz_id": 12,
  "stage": "first",
  "status": "pending",
  "reviewer_id": null,
  "reason": null,
  "created_at": "2026-09-17T02:56:00Z",
  "updated_at": "2026-09-17T02:56:00Z"
}
```

- `biz_type`：`archive`（档案审核）/ `honor`（荣誉审核）/ `archive_delete`（删除审核）/ `activate_manual`（激活人工核验，见 2.1.4）
- `stage`：`first`（初审）/ `second`（复核）
- 流转：初审通过 → `pending_second`；复核通过 → 业务状态置 `approved`；驳回 → `rejected`
- 例外：`activate_manual`（人工核验）为**单级审核**，仅 `first` 一级，通过即完成

**POST /reviews/{review_id}/reject** 请求：

```json
{ "reason": "证书照片不清晰，请重新上传" }
```

- 状态不允许该操作（如已复核完成再初审）：`409` + `INVALID_STATE`

---

## 八、上下架与大屏 Showcase / Screen（B1）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/showcase/{archive_id}/publish` | 上架 | school_admin |
| POST | `/showcase/{archive_id}/unpublish` | 下架 | school_admin |
| GET | `/screen/feed` | 大屏轮播数据（免认证） | 公开 |
| GET | `/screen/feed?college_id=3` | 按学院筛选 | 公开 |

**上架条件**：

- Archive 状态必须为 `approved`
- 必须存在有效 `consent_id`

**GET /screen/feed** 响应 `200`：

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "list": [
      {
        "id": 12,
        "title": "...",
        "category": "award",
        "owner": "测试校友001",
        "college": "计算机学院",
        "grad_year": 2025,
        "cover_url": "/files/xx.jpg"
      }
    ]
  },
  "request_id": "...",
  "timestamp": 1758112800000
}
```

- 大屏接口仅返回 `approved` + 已上架 + consent 有效的数据
- 未授权资料**绝不出现在**此接口——越权红线

---

## 九、授权 Consents（B1）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/consents` | 创建授权（生成 consent_id） | student / alumni |
| GET | `/consents/my` | 我的授权记录 | student / alumni |
| DELETE | `/consents/{id}` | 撤回授权（已上架内容同步自动下架） | 本人 |

请求：

```json
{ "archive_id": 12, "scope": "screen" }
```

- `scope` 预留：`screen`（大屏）/ `site`（站点）

---

## 十、统计 Stats（B1）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/stats/overview` | 总量看板：校友数/档案数/荣誉数/待审数 |
| GET | `/stats/honors` | 荣誉分布（按类别/级别，**仅统计 Honor 表**） |
| GET | `/stats/colleges` | 学院排行（档案数 Top N，`?limit=8`） |

权限：审核角色及以上。

---

## 十一、批量导入 Imports（B2）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/imports/template` | 下载导入模板（xlsx，附填写说明） | college_admin |
| POST | `/imports` | 上传表格创建导入任务（csv / xls / xlsx），异步处理 | college_admin |
| GET | `/imports/{id}` | 导入结果 | college_admin |
| GET | `/imports/{id}/errors` | 错误行报告（行号+原因） | college_admin |

**POST /imports**

- `multipart/form-data`，字段名 `file`；按扩展名+内容嗅探识别格式，csv 要求 UTF-8
- 文件 ≤ 10MB，单次 ≤ 2000 行（超限 `400` + `FILE_TOO_LARGE` / `BATCH_LIMIT_EXCEEDED`）
- 异步处理：立即返回 `202` 风格响应（HTTP 200 + 任务已受理），前端轮询 `GET /imports/{id}`

响应：

```json
{
  "code": "OK",
  "message": "ok",
  "data": { "import_id": 88 },
  "request_id": "...",
  "timestamp": 1758112800000
}
```

**GET /imports/{id}** 响应：`data: { "total": 100, "success": 96, "failed": 4 }`

- 坏行不阻断好行：格式错误的行记入错误报告，合法行正常入库

**GET /imports/{id}/errors** 响应：`data: { "list": [ { "row": 5, "student_id": "2021000099", "reason": "学号重复" } ] }`

---

## 十二、草稿 Drafts（B2）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/drafts/sync` | 草稿同步（前端定时调用） |
| GET | `/drafts/my?biz_type=archive` | 我的草稿列表（换设备恢复用） |
| DELETE | `/drafts/{id}` | 删除 |

### 12.1 同步机制（localStorage 为主，服务端兜底）

- **主存储在前端 localStorage**：表单每次变更即写本地，断网不丢
- **每 1 分钟定时调 `POST /drafts/sync`**（本地有变更才发请求）
- 冲突策略：**版本号乐观锁**（不使用客户端时间戳 LWW——客户端时间不可信）

**POST /drafts/sync** 请求：

```json
{
  "biz_type": "archive",
  "version": 3,
  "payload": { "title": "...", "content": "...", "attachment_ids": [101] }
}
```

- 服务端保存当前版本号；提交版本 = 服务端版本 → 保存并 `version+1`
- 提交版本 < 服务端版本 → `409` + `DRAFT_VERSION_CONFLICT`，响应 `data` 携带服务端最新草稿与版本号，前端覆盖本地

冲突响应：

```json
{
  "code": "DRAFT_VERSION_CONFLICT",
  "message": "草稿已被其他设备修改",
  "data": { "version": 4, "payload": { "title": "...", "content": "..." } },
  "request_id": "...",
  "timestamp": 1758112800000
}
```

正式提交成功后：前端清除对应 localStorage 项，服务端草稿自动删除。

---

## 十三、错误码表

| code | HTTP | 含义 |
|---|---|---|
| `OK` | 200 / 201 | 成功 |
| `INVALID_PARAMETER` | 400 | 参数缺失或格式错误 |
| `AUTH_VERIFY_FAILED` | 400 | 三要素核验失败（不指明字段） |
| `AUTH_CODE_INVALID` | 400 | 验证码错误或过期 |
| `FILE_TOO_LARGE` | 400 | 文件大小超限 |
| `FILE_TYPE_NOT_ALLOWED` | 400 | 文件类型不允许 |
| `BATCH_LIMIT_EXCEEDED` | 400 | 批量数量超限 |
| `UNAUTHORIZED` | 401 | 未登录 / Token 无效或过期 |
| `FORBIDDEN` | 403 | 已登录但无权限（越权） |
| `NOT_FOUND` | 404 | 资源不存在 |
| `INVALID_STATE` | 409 | 当前状态不允许该操作 |
| `RESOURCE_REFERENCED` | 409 | 资源被引用，禁止删除 |
| `AUTH_ALREADY_ACTIVATED` | 409 | 该学号已激活 |
| `DRAFT_VERSION_CONFLICT` | 409 | 草稿版本冲突 |
| `RATE_LIMITED` | 429 | 触发限流 |
| `INTERNAL_ERROR` | 500 | 服务器内部错误 |
| `UPSTREAM_UNAVAILABLE` | 502 | 网关 / 上游服务不可用 |

> 新增错误码必须先在本表登记再使用，禁止私自杜撰。

---

## 附：接口总览

```
/api/v1
├── /auth
│   ├── /activate/verify|sms|confirm|manual-request
│   ├── /login | /login/sms | /sms
│   ├── /password/reset-token | /password/reset
│   ├── /refresh | /logout | /me
├── /dicts/{type}
├── /archives
│   └── /{id}/resubmit | /{id}/delete-request
├── /honors
├── /attachments
│   └── /{id}/preview
├── /reviews
│   └── /{review_id}/approve | /reject | /logs
├── /showcase/{archive_id}/publish | /unpublish
├── /screen/feed
├── /consents
├── /stats/overview | /honors | /colleges
├── /imports
│   └── /template | /{id} | /{id}/errors
└── /drafts
    └── /sync | /my
```
