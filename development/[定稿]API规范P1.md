# 校友管理平台 P1 API 接口文档（需求稿）

> 版本：v1.0 Draft P1 阶段
> 维护人：后端开发者
> 需求依据：《校友管理平台项目需求文档（PRD）V2.2》第 6.3 节 P1 功能
> 上游规范：《[定稿]API规范P0.md》（本文**不重复**通用约定，仅列差异与新增）
> 设计风格：RESTful（资源导向 + 标准 HTTP 方法 + 标准 HTTP 状态码）

**变更流程**：与 P0 相同——联调开始前冻结；任何变更须 ① 群里说明 → ② 修改本文档 → ③ 前后端双方确认 → ④ breaking change 时升级版本号。

---

## P1 范围与模块映射

| PRD 模块 | 功能 | 对应章节 |
|---|---|---|
| M34 | 三级审核（导员→院系→校级）与导员管理 | 第二章 |
| M13 | 智能标签与增强检索（拼音/容错/联想） | 第三章 |
| M14 | 消息通知（站内 + 外部订阅） | 第四章 |
| M15 | 细粒度权限与账号交接 | 第五章 |
| M16 | 安全、隐私与审计 | 第六章 |
| M17 | 大屏屏幕自适应 | 第七章（前端为主，基本无新增接口） |

---

## 一、通用约定（相对 P0 的差异）

### 1.1 角色变更

P0 规范 1.2 节中 `counselor`（导员）"不参与审核流转"——**P1 起该约定作废**：

> `counselor`（导员）：P1 起参与审核流转，负责所带学生提交资料的**初审**（第一环节）。管辖范围由校级管理员配置，越范围不可见、不可审核。

其余角色与认证方式、Token 有效期均不变。

### 1.2 新增错误码

在 P0 第十三章错误码表基础上**追加**：

| code | HTTP | 含义 |
|---|---|---|
| `TAG_DUPLICATED` | 409 | 同域标签重名 |
| `SCOPE_REQUIRED` | 400 | 导员账号缺少管辖范围配置 |
| `HANDOVER_INVALID` | 409 | 账号交接目标无效（目标不存在 / 与原账号相同 / 角色不匹配） |
| `EXPORT_LIMIT_EXCEEDED` | 429 | 审计日志导出频率超限 |

> 通用错误码（INVALID_PARAMETER / UNAUTHORIZED / FORBIDDEN / NOT_FOUND / INVALID_STATE / RESOURCE_REFERENCED / RATE_LIMITED 等）沿用 P0 表，不重复登记。

---

## 二、三级审核与导员管理（M34）

### 2.1 审核流扩展

**P1 流程**：提交 → 待导员初审 → 待院系复审 → 待校级终审 → 通过入库 / 驳回重提（重提回到导员初审）。

**业务状态机扩展**（archives / honors 共用）：

```
draft → pending_counselor → pending_first → pending_second → approved
              │                  │               │
              └────────── rejected ←────────────┘   重提 → pending_counselor
```

**强制开启与兼容策略**（已决策：全校强制三级审核，不做学院自选）：

- P1 上线后**所有新提交一律进入 `pending_counselor`**
- **无导员覆盖兜底**：提交人未命中任何在岗导员管辖范围时，`pending_counselor` 环节自动跳过，直接进入 `pending_first`（由院系管理员初审），流程不卡死
- **存量在途单据**（`pending_first` / `pending_second`）：继续按 P0 两级流程走完，不做迁移
- P0 驳回重提的单据（原两级流程）：重提回 `pending_first`；P1 上线后新驳回的单据重提一律回 `pending_counselor`

### 2.2 Reviews 接口变化（沿用 P0 第七章路径）

| 变更点 | 说明 |
|---|---|
| `stage` 取值 | 扩展为 `counselor` / `first` / `second` |
| `GET /reviews` | 导员调用时**强制** `stage=counselor` 且按管辖范围过滤（越范围数据不可见）；院系/校级管理员可继续按 `stage` 筛选 |
| `POST /reviews/{id}/approve` | 导员仅可处理 `stage=counselor` 的单据；通过后单据流转至 `pending_first`；越范围操作 `403` + `FORBIDDEN` |
| `POST /reviews/{id}/reject` | 三级流程中任一层级驳回均置业务 `rejected`，`reason` 必填（沿用 P0） |
| `activate_manual` 等单级审核 | 不受影响，仍为 `first` 单级 |

**新增消息联动**：审核通过/驳回时服务端自动生成站内通知（见第四章 4.1），驳回通知携带 `reason`。

### 2.3 导员管理接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/counselors` | 导员列表（`?college_id=&keyword=&status=&page=`） | school_admin |
| POST | `/counselors` | 创建导员账号 | school_admin |
| GET | `/counselors/{id}` | 导员详情（含管辖范围） | school_admin |
| PATCH | `/counselors/{id}` | 修改信息 / 管辖范围 | school_admin |
| POST | `/counselors/{id}/disable` | 停用（在途待办回退至院系初审） | school_admin |
| POST | `/counselors/{id}/enable` | 启用 | school_admin |
| POST | `/counselors/{id}/password/reset` | 重置密码（散列值，规则同 1.7） | school_admin |

**POST /counselors** 请求：

```json
{
  "real_name": "王导员",
  "phone": "13900002000",
  "college_id": 3,
  "password": "3a7bd3e2360c...",
  "scope": {
    "grad_years": [2024, 2025],
    "major_ids": [1, 2],
    "student_ids": ["2021010003"]
  }
}
```

- `password` 为前端 SHA-256 散列值（同 P0 1.7 节）
- `scope` **必填**：管辖范围 = 本学院内按**届别 ∪ 专业 ∪ 指定学号**三个维度的并集；`grad_years` / `major_ids` / `student_ids` 至少一项非空，否则 `400` + `SCOPE_REQUIRED`
- `scope` 为空对象且不传任何维度 → 拒绝创建（防止无范围导员越权看到全院数据）

响应 `201`：

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "id": 9,
    "real_name": "王导员",
    "phone": "139****2000",
    "college_id": 3,
    "role": "counselor",
    "scope": { "grad_years": [2024, 2025], "major_ids": [1, 2], "student_ids": ["2021010003"] },
    "status": "active",
    "created_at": "2026-10-08T02:56:00Z"
  },
  "request_id": "...",
  "timestamp": 1758112800000
}
```

**范围校验规则**（服务端执行）：

- 提交人学号按 `alumni_base` 解析届别/专业，命中导员任一维度即归属其管辖
- 导员查看/审核非管辖单据：列表不可见，直接调用返回 `403` + `FORBIDDEN`（服务端校验，不依赖前端）

**停用语义**：`disable` 后账号即刻无法登录（Token 全部作废）；其名下 `pending_counselor` 在途单据自动回退为 `pending_first`（避免流程卡死），回退动作写入审计日志。

---

## 三、智能标签与增强检索（M13）

### 3.1 标签体系（标签域隔离、一物多标）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/tags` | 标签列表（`?group=&keyword=&page=`） | 登录 |
| POST | `/tags` | 创建标签 | school_admin |
| PATCH | `/tags/{id}` | 改名 / 调整排序 | school_admin |
| DELETE | `/tags/{id}` | 删除（被引用时 `409` + `RESOURCE_REFERENCED`） | school_admin |

**POST /tags** 请求：

```json
{ "name": "学霸", "group": "honor", "sort": 1 }
```

- `group`（标签域）取值：`honor` / `archive` / `alumni`，**域隔离**：同域内重名 `409` + `TAG_DUPLICATED`，跨域同名允许
- 绑定关系受域约束：荣誉只能绑 `honor` 域标签，绑定跨域标签 `400` + `INVALID_PARAMETER`

### 3.2 标签绑定（全量覆盖式）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| PUT | `/tags/bindings/{biz_type}/{biz_id}` | 设置标签（覆盖式） | school_admin / college_admin |
| GET | `/tags/bindings/{biz_type}/{biz_id}` | 查询已绑标签 | 登录 |

**PUT /tags/bindings/honor/12** 请求：

```json
{ "tag_ids": [1, 5, 9] }
```

- 一个对象可绑多个标签（一物多标）；单次上限 20 个，超限 `400` + `BATCH_LIMIT_EXCEEDED`
- `biz_type` 取值：`honor` / `archive` / `alumni`（与标签域一一对应）

### 3.3 增强检索（拼音 / 同音容错 / 关键词联想）

**GET /search**（聚合检索，登录）

```
/search?keyword=zhangsan&types=honor,alumni&college_id=3&grad_year=2025&tag_ids=1,5&page=1&page_size=20
```

响应 `200`：

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "list": [
      {
        "type": "honor",
        "id": 12,
        "title": "国家奖学金",
        "owner": "张三",
        "college": "计算机学院",
        "grad_year": 2025,
        "tags": ["学霸"],
        "status": "approved",
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

**服务端行为**：

- **拼音容错**：对姓名、荣誉标题、学院名等索引字段预生成拼音列（全拼 + 首字母），`keyword=zhangsan` / `zs` 均可命中"张三"；同音字（如"张/章"）命中
- **数据范围**：student / alumni 仅返回 `approved` 且本人可见内容；审核角色可加 `include_all=true` 检索在审数据
- `types` 不传默认全部（`honor,archive,alumni`）

**GET /search/suggest**（关键词联想，登录）

```
/search/suggest?keyword=国家&limit=10
```

响应：`data: { "list": [ { "text": "国家奖学金", "type": "honor", "count": 37 } ] }`

- 联想词来源：荣誉标题、标签名、校友姓名（脱敏规则不变）、学院名
- `limit` 默认 10，最大 20

> 现有列表接口（`/archives?keyword=`、`/honors?keyword=`）同步升级支持拼音容错，参数不变。

---

## 四、消息通知（M14）

### 4.1 站内消息

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/notifications` | 我的消息（`?type=&unread=true&page=`） | 登录 |
| GET | `/notifications/unread-count` | 未读数（红点） | 登录 |
| POST | `/notifications/{id}/read` | 标记已读 | 本人 |
| POST | `/notifications/read-all` | 全部已读（可带 `?type=`） | 本人 |

**消息结构**：

```json
{
  "id": 1,
  "type": "review",
  "title": "审核通过",
  "content": "您的荣誉《国家奖学金》已通过校级终审",
  "biz_type": "honor",
  "biz_id": 12,
  "read": false,
  "created_at": "2026-09-17T02:56:00Z"
}
```

- `type` 取值：`review`（审核结果）/ `announcement`（公告）/ `todo`（待办提醒）
- `content` 由服务端拼装，**不得包含学号**（昵称 + 业务标题）

**GET /notifications/unread-count** 响应：

```json
{ "total": 4, "by_type": { "review": 3, "announcement": 1 } }
```

**事件源（服务端自动生成，无手动发送接口）**：

| 事件 | 触发时机 | 接收人 |
|---|---|---|
| 审核通过 | 任一层级 approve 落库 | 提交人 |
| 审核驳回 | 任一层级 reject 落库（含 `reason`） | 提交人 |
| 公告发布 | announcement 置为 `published` | 全体登录用户 |
| 待办提醒 | 管理端有待审超 48h 单据（每日定时汇总一条） | 对应审核角色 |

### 4.2 外部消息订阅（短信 / 邮件，可订阅退订）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/notification-subscriptions/my` | 我的订阅配置 | 登录 |
| PUT | `/notification-subscriptions` | 更新订阅 | 登录 |

**PUT /notification-subscriptions** 请求：

```json
{
  "sms": { "enabled": true, "scenes": ["review_result", "announcement"] },
  "email": { "enabled": false, "address": "alumni@example.com" }
}
```

- `scenes` 取值：`review_result`（审核结果）/ `announcement`（公告）
- **默认全关**（最小打扰原则）；订阅 / 退订即时生效
- 外部消息内容与站内一致，**不暴露学号**，收件人仅显示昵称；手机号沿用账号绑定号（脱敏展示）
- 短信复用现有 SMS 通道（同 P0 限流规则）；邮件为新增外部依赖，发送失败仅记日志、不重试
- 公告短信推送按用户订阅过滤，**不提供**管理员批量群发接口（P1 范围外）

---

## 五、细粒度权限与账号交接（M15）

### 5.1 字段级权限（角色—字段可见性矩阵）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/admin/field-permissions` | 查看各角色字段可见性矩阵 | school_admin / system_admin |
| PUT | `/admin/field-permissions/{role}` | 更新某角色的字段可见性 | system_admin |

**PUT /admin/field-permissions/counselor** 请求：

```json
{
  "fields": {
    "phone": "masked",
    "id_card": "hidden",
    "home_address": "hidden",
    "honor_title": "visible"
  }
}
```

- 可见性取值：`visible`（明文）/ `masked`（脱敏）/ `hidden`（不出参）
- 可配置字段白名单：`phone` / `id_card` / `email` / `home_address` / `work_place` / `emergency_contact`
- 配置变更**写审计日志**；出参裁剪在服务端序列化阶段执行，越权字段不进入响应体
- 预置默认值：student/alumni 互相不可见 `id_card`；`phone` 全角色默认 `masked`

### 5.2 账号管理（停权 / 交接 / 留痕）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/admin/users` | 用户列表（`?role=&status=&keyword=&college_id=&page=`） | school_admin / system_admin |
| POST | `/admin/users/{id}/suspend` | 停权 | system_admin |
| POST | `/admin/users/{id}/resume` | 恢复 | system_admin |
| POST | `/admin/users/{id}/handover` | 账号交接 | system_admin |
| GET | `/admin/users/{id}/change-logs` | 变更留痕（停权/恢复/交接/改范围） | system_admin |

**POST /admin/users/{id}/suspend** 请求：`{ "reason": "离岗" }`

- 停权后 `status=suspended`：**所有已签发 Token 即刻作废**，登录返回 `403` + `FORBIDDEN`
- `reason` 必填，写入审计日志
- system_admin 不可停权自己（`409` + `INVALID_STATE`）

**POST /admin/users/{id}/handover** 请求：

```json
{ "target_user_id": 15, "reason": "原管理员毕业离岗，移交给继任者" }
```

- 语义：**原账号停权，数据范围与在途待办转移给目标账号**
- 目标账号须与原账号**同角色**且状态正常，否则 `409` + `HANDOVER_INVALID`
- **跨学院限制**（已决策）：`counselor` / `college_admin` **不得跨学院交接**（目标须同学院同角色），违反返回 `409` + `HANDOVER_INVALID`（提示"导员/院系管理员交接仅限本院"）；`school_admin` / `system_admin` 可跨学院
- 在途待办（reviews 中 `reviewer_id` 指向原账号的 pending 单据）自动改派目标账号，改派留痕
- 交接完成返回 `200`：`data: { "transferred_reviews": 3, "origin_status": "suspended" }`

**GET /admin/users/{id}/change-logs** 响应：

```json
{
  "list": [
    { "id": 7, "action": "handover", "operator_id": 2, "detail": { "target_user_id": 15, "reason": "..." }, "created_at": "2026-10-08T02:56:00Z" }
  ],
  "total": 1, "page": 1, "page_size": 20
}
```

> 账号到人原则：交接后原账号保留（历史记录归属不变），仅登录能力停用；不删除任何用户数据。

---

## 六、安全、隐私与审计（M16）

### 6.1 审计日志（只追加，不可篡改）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/audit-logs` | 审计日志查询 | system_admin（及授权审计人员角色） |
| GET | `/audit-logs/actions` | 动作字典（筛选下拉用） | 同上 |
| GET | `/audit-logs/export` | 导出（xlsx，异步任务） | 同上 |

**GET /audit-logs** 筛选参数：`user_id` / `action` / `biz_type` / `date_from` / `date_to` / `page`

**记录结构**：

```json
{
  "id": 1001,
  "user_id": 6,
  "user_role": "college_admin",
  "action": "attachment_delete",
  "biz_type": "attachment",
  "biz_id": 101,
  "detail": { "file_name": "证书.jpg", "reason": "误传" },
  "ip": "10.2.3.4",
  "created_at": "2026-09-17T02:56:00Z"
}
```

**`action` 枚举（P1 登记）**：

| action | 场景 |
|---|---|
| `login` / `login_failed` / `logout` | 认证事件 |
| `export` | 审计日志导出、数据导出 |
| `attachment_delete` | 附件删除（P0 已有独立表，P1 统一并入） |
| `field_permission_change` | 字段权限矩阵变更 |
| `user_suspend` / `user_resume` / `user_handover` | 账号管理 |
| `consent_grant` / `consent_withdraw` | 授权与撤回 |
| `dict_change` / `tag_change` / `counselor_scope_change` | 配置类变更 |

**不可篡改保证**：

- 表只允许 INSERT，服务端不提供任何修改 / 删除接口
- 保留 ≥ 180 天，到期归档不删除
- **导出本身也写审计日志**（`action=export`）；导出限流：单账号 5 次/小时，超限 `429` + `EXPORT_LIMIT_EXCEEDED`

### 6.2 隐私合规与授权留证

**授权版本化**（consents 扩展，沿用 P0 第九章路径）：

| 变更点 | 说明 |
|---|---|
| `POST /consents` | 请求 / 响应新增 `version`（当前授权文本版本号，服务端下发） |
| `GET /consents/my` | 返回每条授权的 `version`、`consent_text_hash`（授权文本 SHA-256 摘要）、`granted_at` |
| 撤回 | 沿用 `DELETE /consents/{id}`，撤回即时生效并写审计日志（`consent_withdraw`） |

**GET /consents/my** 响应示例：

```json
{
  "list": [
    {
      "id": 3,
      "archive_id": 12,
      "scope": "screen",
      "version": 2,
      "consent_text_hash": "9f2c1a7e...",
      "granted_at": "2026-09-17T02:56:00Z",
      "withdrawn_at": null
    }
  ]
}
```

- **授权文本变更必须升版本**：文本更新后已授权记录保持原 `version`（留证可追溯当时版本），新授权使用新版本号
- 当前授权文本：`GET /consents/text`（登录，返回 `version` + 全文），前端在授权弹窗展示

---

## 七、大屏屏幕自适应（M17）

| 项 | 说明 |
|---|---|
| 前端 | rem / vw 布局 + `ResizeObserver`，1920×1080 与 4K 无溢出截断 |
| 后端 | **无新增接口**；`GET /screen/feed` 结构不变，封面沿用 ≤1280px 压缩图，4K 场景由前端 CSS 拉伸处理 |
| 可选扩展 | `GET /screen/feed?resolution=2k`（预留参数，P1 不实现，供 P2 高清媒体评估） |

验收：1080P / 2K / 4K 三档分辨率下无横向滚动条、文字不截断（前端验收项，非接口项）。

---

## 八、数据库表设计概要

> 供开发参考；DDL 统一落 `backend/db/schema.sql` + Flyway 迁移脚本（app 平台目录），模块目录不持有 DDL。

| 表 | 用途 | 关键字段 |
|---|---|---|
| `counselor_scopes` | 导员管辖范围 | counselor_id, college_id, grad_year, major_id, student_id |
| `tags` | 标签（域隔离） | id, name, `group`, sort, created_by；uk(group, name) |
| `tag_bindings` | 标签绑定 | tag_id, biz_type, biz_id；uk(tag_id, biz_type, biz_id) |
| `notifications` | 站内消息 | id, user_id, type, title, content, biz_type, biz_id, read, created_at；idx(user_id, read) |
| `notification_subscriptions` | 外部订阅 | user_id, channel(sms/email), scenes(JSON), enabled |
| `audit_logs` | 审计日志（只追加） | id, user_id, user_role, action, biz_type, biz_id, detail(JSON), ip, created_at |
| `user_change_logs` | 账号变更留痕 | id, user_id, action, operator_id, detail(JSON), created_at |
| `field_permissions` | 角色—字段可见性 | role, field, visibility(visible/masked/hidden) |
| `consents`（扩展） | 授权版本化 | + version, consent_text_hash |
| `users`（扩展） | 账号状态 | + status(active/suspended) |

---

## 九、接口总览（P1 新增部分）

```
/api/v1
├── /admin
│   ├── /field-permissions/{role}          GET / PUT          [system_admin]
│   └── /users
│       ├──                                GET                [school_admin+]
│       └── /{id}/suspend | /resume | /handover | /change-logs
├── /counselors                            GET / POST
│   └── /{id} | /{id}/disable | /{id}/enable | /{id}/password/reset
├── /tags
│   ├── /{id}                              PATCH / DELETE
│   └── /bindings/{biz_type}/{biz_id}      PUT / GET
├── /search                                GET（聚合检索）
│   └── /suggest                           GET（联想）
├── /notifications
│   ├── /unread-count | /read-all          GET / POST
│   └── /{id}/read                         POST
├── /notification-subscriptions/my | (PUT) GET / PUT
├── /audit-logs
│   ├── /actions | /export                 GET
└── /consents/text                         GET（授权文本）
```

**P0 存量接口的变更清单**（不新增路径，仅行为扩展）：

| 接口 | 变更 |
|---|---|
| `GET /reviews` | stage 支持 `counselor`；导员强制范围过滤 |
| `POST /reviews/{id}/approve` / `reject` | 支持 counselor 环节流转；P1 新驳回单据重提一律回 `pending_counselor` |
| `GET /archives` / `/honors` | `keyword` 支持拼音容错 |
| `POST /consents` / `GET /consents/my` | 新增 version / consent_text_hash |
| archives / honors 状态机 | 新增 `pending_counselor` 状态（全校新提交强制进入） |

---

## 十、已确认决策与遗留问题

### 10.1 已确认决策（2026-09-20）

| 编号 | 问题 | 决策 |
|---|---|---|
| P1-Q1 | 导员初审开启方式 | **全校强制开启**三级审核，不做学院自选；无导员覆盖的提交自动跳过初审环节，直接由院系管理员初审 |
| P1-Q3 | 拼音索引方案 | **确认 MySQL 预生成拼音列**（全拼 + 首字母），不引入 ES |
| P1-Q4 | 审计人员独立角色 | **不设独立角色**，由 system_admin 代行审计查询 / 导出 |
| P1-Q6 | 账号交接跨学院 | **导员与院系管理员不得跨学院**交接（目标须同学院同角色）；school_admin / system_admin 可跨 |

### 10.2 遗留问题（联调前冻结）

| 编号 | 问题 | 确认方 |
|---|---|---|
| P1-Q2 | 邮件通道的 SMTP 资源与发件域名 | 信息化 |
| P1-Q5 | 待办超时提醒阈值（当前设计：48h，每日一条） | 学工 |
