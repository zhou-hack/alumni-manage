# 校友平台 API 综合测试报告

- 生成时间：2026/9/18 15:56:10
- 测试依据：《[定稿]API规范》第一~十一章（第十二章 Drafts 后端未实现，未纳入）
- 结果总览：**25 条用例，通过 25，失败 0，通过率 100%**

## 一、测试环境（模块地址）

| 模块 | Base URL |
|---|---|
| Auth 认证 | http://127.0.0.1:8080/api/v1 |
| Dicts 字典 | http://127.0.0.1:8081/api/v1 |
| Stats 统计 | http://127.0.0.1:8081/api/v1 |
| Reviews 审核 | http://127.0.0.1:8082/api/v1 |
| Attachments 附件 | http://127.0.0.1:8082/api/v1 |
| Imports 导入 | http://127.0.0.1:8082/api/v1 |
| Archives 档案 | http://127.0.0.1:8083/api/v1 |
| Showcase 上下架 | http://127.0.0.1:8083/api/v1 |
| Consents 授权 | http://127.0.0.1:8084/api/v1 |
| Honors 荣誉 | http://127.0.0.1:8085/api/v1 |

## 二、结果汇总

| 用例 | 模块 | 名称 | 期望 | 实际 | 耗时 | 结果 |
|---|---|---|---|---|---|---|
| S1 | stats | GET /stats/overview 无Token | HTTP 401 + UNAUTHORIZED | HTTP 401 UNAUTHORIZED | 16ms | ✔ 通过 |
| S2 | stats | GET /stats/overview 学生越权 | HTTP 403 + FORBIDDEN | HTTP 403 FORBIDDEN | 22ms | ✔ 通过 |
| S3 | stats | GET /stats/overview 管理员 | HTTP 200 + OK | HTTP 200 OK | 36ms | ✔ 通过 |
| S4 | stats | GET /stats/colleges?limit=8 | HTTP 200 + OK | HTTP 200 OK | 54ms | ✔ 通过 |
| S5 | stats | GET /stats/honors 荣誉分布 | HTTP 200 + OK | HTTP 200 OK | 21ms | ✔ 通过 |
| R1 | reviews | GET /reviews 无Token | HTTP 401 + UNAUTHORIZED | HTTP 401 UNAUTHORIZED | 18ms | ✔ 通过 |
| R2 | reviews | GET /reviews 学生越权 | HTTP 403 + FORBIDDEN | HTTP 403 FORBIDDEN | 50ms | ✔ 通过 |
| R3 | reviews | GET /reviews 初审待办(院系) | HTTP 200 + OK | HTTP 200 OK | 55ms | ✔ 通过 |
| R4 | reviews | GET /reviews 复核待办(校级) | HTTP 200 + OK | HTTP 200 OK | 17ms | ✔ 通过 |
| R5 | reviews | POST approve 不存在ID | HTTP 404 | HTTP 404 NOT_FOUND | 27ms | ✔ 通过 |
| R6 | reviews | POST reject 缺reason | HTTP 400/404 | HTTP 400 INVALID_PARAMETER | 18ms | ✔ 通过 |
| C1 | showcase | GET /screen/feed 免认证公开 | HTTP 200 + OK | HTTP 200 OK | 16ms | ✔ 通过 |
| C2 | showcase | POST publish 学生越权 | HTTP 403 + FORBIDDEN | HTTP 403 FORBIDDEN | 18ms | ✔ 通过 |
| C3 | showcase | POST unpublish 无Token | HTTP 401 | HTTP 401 UNAUTHORIZED | 17ms | ✔ 通过 |
| C4 | showcase | POST publish 不存在档案 | HTTP 400/404/409 | HTTP 404 NOT_FOUND | 51ms | ✔ 通过 |
| N1 | consents | GET /consents/my 学生本人 | HTTP 200 + OK | HTTP 200 OK | 176ms | ✔ 通过 |
| N2 | consents | GET /consents/my 无Token | HTTP 401 + UNAUTHORIZED | HTTP 401 UNAUTHORIZED | 15ms | ✔ 通过 |
| N3 | consents | POST /consents 非法scope | HTTP 400 | HTTP 400 INVALID_PARAMETER | 48ms | ✔ 通过 |
| N4 | consents | POST /consents 档案不存在 | HTTP 404 | HTTP 404 NOT_FOUND | 22ms | ✔ 通过 |
| N5 | consents | DELETE /consents/999999 不存在 | HTTP 404 | HTTP 404 NOT_FOUND | 19ms | ✔ 通过 |
| H1 | honors | GET /honors 已登录 | HTTP 200 + OK | HTTP 200 OK | 224ms | ✔ 通过 |
| H2 | honors | GET /honors 无Token | HTTP 401 | HTTP 401 UNAUTHORIZED | 17ms | ✔ 通过 |
| H3 | honors | GET /honors/999999 不存在 | HTTP 404 | HTTP 404 NOT_FOUND | 141ms | ✔ 通过 |
| H4 | honors | POST /honors 缺title | HTTP 400 | HTTP 400 INVALID_PARAMETER | 100ms | ✔ 通过 |
| H5 | honors | ⚠ 创建荣誉→重复创建应409（防重规则） | 首次201 → 同人同题同日重复409 INVALID_STATE | 首次201 → 重复409 INVALID_STATE | 100ms | ✔ 通过 |

## 三、请求 / 响应明细

### S1 GET /stats/overview 无Token

- 期望：HTTP 401 + UNAUTHORIZED
- 实际：HTTP 401 UNAUTHORIZED
- 结果：通过

**请求1**：`GET /stats/overview` → HTTP 401 · UNAUTHORIZED · 13ms · request_id=ba352a17-ab84-49c1-8a52-60f396ff0ce9

```json
{
  "code": "UNAUTHORIZED",
  "message": "未登录或 Token 已过期",
  "data": null,
  "request_id": "ba352a17-ab84-49c1-8a52-60f396ff0ce9",
  "timestamp": 1789718144657
}
```

### S2 GET /stats/overview 学生越权

- 期望：HTTP 403 + FORBIDDEN
- 实际：HTTP 403 FORBIDDEN
- 结果：通过

**请求1**：`GET /stats/overview` → HTTP 403 · FORBIDDEN · 18ms · request_id=c865908c-a9d7-4d5e-9f57-c33ae026e9e0

```json
{
  "code": "FORBIDDEN",
  "message": "无权限访问",
  "data": null,
  "request_id": "c865908c-a9d7-4d5e-9f57-c33ae026e9e0",
  "timestamp": 1789718144673
}
```

### S3 GET /stats/overview 管理员

- 期望：HTTP 200 + OK
- 实际：HTTP 200 OK
- 结果：通过

**请求1**：`GET /stats/overview` → HTTP 200 · OK · 34ms · request_id=2b552cca-97ee-4937-9464-b2e1b2f55dcc

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "total_alumni": 4,
    "total_archives": 3,
    "total_honors": 4,
    "pending_reviews": 20
  },
  "request_id": "2b552cca-97ee-4937-9464-b2e1b2f55dcc",
  "timestamp": 1789718144721
}
```

### S4 GET /stats/colleges?limit=8

- 期望：HTTP 200 + OK
- 实际：HTTP 200 OK
- 结果：通过

**请求1**：`GET /stats/colleges?limit=8` → HTTP 200 · OK · 52ms · request_id=8c9627e3-03b1-4814-849c-9792cc440882

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "list": [
      {
        "college_id": 1,
        "college_name": "计算机学院",
        "archive_count": 2
      },
      {
        "college_id": 2,
        "college_name": "电子信息学院",
        "archive_count": 1
      }
    ]
  },
  "request_id": "8c9627e3-03b1-4814-849c-9792cc440882",
  "timestamp": 1789718144778
}
```

### S5 GET /stats/honors 荣誉分布

- 期望：HTTP 200 + OK
- 实际：HTTP 200 OK
- 结果：通过

**请求1**：`GET /stats/honors` → HTTP 200 · OK · 17ms · request_id=a40d5497-1ec0-4d1c-a606-19bdecffd88f

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "by_category": [
      {
        "key": "scholarship",
        "count": 3
      },
      {
        "key": "competition",
        "count": 1
      }
    ],
    "by_level": [
      {
        "key": "provincial",
        "count": 2
      },
      {
        "key": "national",
        "count": 2
      }
    ]
  },
  "request_id": "a40d5497-1ec0-4d1c-a606-19bdecffd88f",
  "timestamp": 1789718144799
}
```

### R1 GET /reviews 无Token

- 期望：HTTP 401 + UNAUTHORIZED
- 实际：HTTP 401 UNAUTHORIZED
- 结果：通过

**请求1**：`GET /reviews` → HTTP 401 · UNAUTHORIZED · 15ms · request_id=50b44936-f74a-40bb-b074-c12e7813671b

```json
{
  "code": "UNAUTHORIZED",
  "message": "未登录或 Token 已过期",
  "data": null,
  "request_id": "50b44936-f74a-40bb-b074-c12e7813671b",
  "timestamp": 1789718144813
}
```

### R2 GET /reviews 学生越权

- 期望：HTTP 403 + FORBIDDEN
- 实际：HTTP 403 FORBIDDEN
- 结果：通过

**请求1**：`GET /reviews` → HTTP 403 · FORBIDDEN · 47ms · request_id=7e0f5080-90fc-46b0-9557-6e7f4bfd01cb

```json
{
  "code": "FORBIDDEN",
  "message": "无权限访问",
  "data": null,
  "request_id": "7e0f5080-90fc-46b0-9557-6e7f4bfd01cb",
  "timestamp": 1789718144875
}
```

### R3 GET /reviews 初审待办(院系)

- 期望：HTTP 200 + OK
- 实际：HTTP 200 OK
- 结果：通过

**请求1**：`GET /reviews` → HTTP 200 · OK · 54ms · request_id=f2821bd5-7130-4ea6-b113-c5a133e99d3b

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "list": [
      {
        "id": 24,
        "biz_type": "honor",
        "biz_id": 38,
        "submitter_id": 3,
        "stage": "first",
        "status": "pending",
        "reviewer_id": null,
        "reason": null,
        "created_at": "2026-09-18T06:58:53.755066Z",
        "updated_at": "2026-09-18T06:58:53.755066Z"
      },
      {
        "id": 23,
        "biz_type": "honor",
        "biz_id": 37,
        "submitter_id": 1,
        "stage": "first",
        "status": "pending",
        "reviewer_id": null,
        "reason": null,
        "created_at": "2026-09-18T06:55:34.912517Z",
        "updated_at": "2026-09-18T06:55:34.912517Z"
      },
      {
        "id": 22,
        "biz_type": "honor",
        "biz_id": 36,
        "submitter_id": 3,
        "stage": "first",
        "status": "pending",
        "reviewer_id": null,
        "reason": null,
        "created_at": "2026-09-18T06:55:34.883330Z",
        "updated_at": "2026-09-18T06:55:34.883330Z"
      },
      {
        "id": 21,
        "biz_type": "honor",
        "biz_id": 35,
        "submitter_id": 3,
        "stage": "first",
        "status": "pending",
        "reviewer_id": null,
        "reason": null,
        "created_at": "2026-09-18T06:55:34.863162Z",
        "updated_at": "2026-09-18T06:55:34.863162Z"
      },
      {
        "id": 20,
        "biz_type": "honor",
        "biz_id": 34,
        "submitter_id": 3,
        "stage": "first",
        "status": "pending",
        "reviewer_id": null,
        "reason": null,
        "created_at": "2026-09-18T06:55:34.646666Z",
        "updated_at": "2026-09-18T06:55:34.646666Z"
      },
      {
        "id": 19,
        "biz_type": "honor",
        "biz_id": 33,
        "submitter_id": 3,
        "stage": "first",
        "status": "pending",
        "reviewer_id": null,
        "reason": null,
        "created_at": "2026-09-18T06:49:57.721137Z",
        "updated_at": "2026-09-18T06:49:57
...（截断）
```

### R4 GET /reviews 复核待办(校级)

- 期望：HTTP 200 + OK
- 实际：HTTP 200 OK
- 结果：通过

**请求1**：`GET /reviews?stage=second` → HTTP 200 · OK · 15ms · request_id=040e5b9d-9fd1-40d4-a98d-65775baa66cf

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "list": [
      {
        "id": 4,
        "biz_type": "archive",
        "biz_id": 1,
        "submitter_id": 1,
        "stage": "second",
        "status": "pending",
        "reviewer_id": null,
        "reason": null,
        "created_at": "2026-09-17T13:43:33.885721Z",
        "updated_at": "2026-09-17T13:43:33.885721Z"
      },
      {
        "id": 1,
        "biz_type": "archive",
        "biz_id": 2,
        "submitter_id": 2,
        "stage": "second",
        "status": "pending",
        "reviewer_id": 1,
        "reason": null,
        "created_at": "2026-09-17T13:43:33.885721Z",
        "updated_at": "2026-09-18T02:20:00.778879Z"
      }
    ],
    "total": 2,
    "page": 1,
    "page_size": 20
  },
  "request_id": "040e5b9d-9fd1-40d4-a98d-65775baa66cf",
  "timestamp": 1789718144953
}
```

### R5 POST approve 不存在ID

- 期望：HTTP 404
- 实际：HTTP 404 NOT_FOUND
- 结果：通过

**请求1**：`POST /reviews/999999/approve` → HTTP 404 · NOT_FOUND · 25ms · request_id=9652382e-3b72-429e-a72f-bf1b9828720b

```json
{
  "code": "NOT_FOUND",
  "message": "审核记录不存在",
  "data": null,
  "request_id": "9652382e-3b72-429e-a72f-bf1b9828720b",
  "timestamp": 1789718144983
}
```

### R6 POST reject 缺reason

- 期望：HTTP 400/404
- 实际：HTTP 400 INVALID_PARAMETER
- 结果：通过

**请求1**：`POST /reviews/999999/reject` → HTTP 400 · INVALID_PARAMETER · 16ms · request_id=d5ed6672-d44e-4aae-ab9a-7670d805c2ac

```json
{
  "code": "INVALID_PARAMETER",
  "message": "驳回原因 reason 必填",
  "data": null,
  "request_id": "d5ed6672-d44e-4aae-ab9a-7670d805c2ac",
  "timestamp": 1789718145004
}
```

### C1 GET /screen/feed 免认证公开

- 期望：HTTP 200 + OK
- 实际：HTTP 200 OK
- 结果：通过

**请求1**：`GET /screen/feed` → HTTP 200 · OK · 13ms · request_id=ebdf568d-fa8e-46ef-9642-06879a826e3b

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "list": []
  },
  "request_id": "ebdf568d-fa8e-46ef-9642-06879a826e3b",
  "timestamp": 1789718145019
}
```

### C2 POST publish 学生越权

- 期望：HTTP 403 + FORBIDDEN
- 实际：HTTP 403 FORBIDDEN
- 结果：通过

**请求1**：`POST /showcase/1/publish` → HTTP 403 · FORBIDDEN · 14ms · request_id=978cee32-0df8-4948-8f62-c9225b15e48b

```json
{
  "code": "FORBIDDEN",
  "message": "无权限访问",
  "data": null,
  "request_id": "978cee32-0df8-4948-8f62-c9225b15e48b",
  "timestamp": 1789718145036
}
```

### C3 POST unpublish 无Token

- 期望：HTTP 401
- 实际：HTTP 401 UNAUTHORIZED
- 结果：通过

**请求1**：`POST /showcase/1/unpublish` → HTTP 401 · UNAUTHORIZED · 14ms · request_id=38561c6c-a6fd-4394-b9fb-ad1e2a0528fa

```json
{
  "code": "UNAUTHORIZED",
  "message": "未登录或 Token 已过期",
  "data": null,
  "request_id": "38561c6c-a6fd-4394-b9fb-ad1e2a0528fa",
  "timestamp": 1789718145052
}
```

### C4 POST publish 不存在档案

- 期望：HTTP 400/404/409
- 实际：HTTP 404 NOT_FOUND
- 结果：通过

**请求1**：`POST /showcase/999999/publish` → HTTP 404 · NOT_FOUND · 45ms · request_id=59501a89-e663-45a1-bd5c-9b366fd48c9a

```json
{
  "code": "NOT_FOUND",
  "message": "档案不存在",
  "data": null,
  "request_id": "59501a89-e663-45a1-bd5c-9b366fd48c9a",
  "timestamp": 1789718145113
}
```

### N1 GET /consents/my 学生本人

- 期望：HTTP 200 + OK
- 实际：HTTP 200 OK
- 结果：通过

**请求1**：`GET /consents/my` → HTTP 200 · OK · 149ms · request_id=c45f8f17-c5d4-41ab-8e70-a645bcba05f6

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "list": []
  },
  "request_id": "c45f8f17-c5d4-41ab-8e70-a645bcba05f6",
  "timestamp": 1789718145250
}
```

### N2 GET /consents/my 无Token

- 期望：HTTP 401 + UNAUTHORIZED
- 实际：HTTP 401 UNAUTHORIZED
- 结果：通过

**请求1**：`GET /consents/my` → HTTP 401 · UNAUTHORIZED · 12ms · request_id=bb52a2f7-2e66-48b8-88f6-460e6d735ff7

```json
{
  "code": "UNAUTHORIZED",
  "message": "未登录或 Token 已过期",
  "data": null,
  "request_id": "bb52a2f7-2e66-48b8-88f6-460e6d735ff7",
  "timestamp": 1789718145303
}
```

### N3 POST /consents 非法scope

- 期望：HTTP 400
- 实际：HTTP 400 INVALID_PARAMETER
- 结果：通过

**请求1**：`POST /consents` → HTTP 400 · INVALID_PARAMETER · 43ms · request_id=312f42b6-e490-42fe-9d10-fee5f00bb37e

```json
{
  "code": "INVALID_PARAMETER",
  "message": "scope 仅支持 screen/site",
  "data": null,
  "request_id": "312f42b6-e490-42fe-9d10-fee5f00bb37e",
  "timestamp": 1789718145357
}
```

### N4 POST /consents 档案不存在

- 期望：HTTP 404
- 实际：HTTP 404 NOT_FOUND
- 结果：通过

**请求1**：`POST /consents` → HTTP 404 · NOT_FOUND · 20ms · request_id=feb4bdd0-eb61-46b8-9488-a4223b7c0999

```json
{
  "code": "NOT_FOUND",
  "message": "档案不存在",
  "data": null,
  "request_id": "feb4bdd0-eb61-46b8-9488-a4223b7c0999",
  "timestamp": 1789718145383
}
```

### N5 DELETE /consents/999999 不存在

- 期望：HTTP 404
- 实际：HTTP 404 NOT_FOUND
- 结果：通过

**请求1**：`DELETE /consents/999999` → HTTP 404 · NOT_FOUND · 17ms · request_id=7aa2456c-6e1e-4e41-9043-abe040562124

```json
{
  "code": "NOT_FOUND",
  "message": "授权记录不存在",
  "data": null,
  "request_id": "7aa2456c-6e1e-4e41-9043-abe040562124",
  "timestamp": 1789718145407
}
```

### H1 GET /honors 已登录

- 期望：HTTP 200 + OK
- 实际：HTTP 200 OK
- 结果：通过

**请求1**：`GET /honors` → HTTP 200 · OK · 219ms · request_id=5d0e2ad1-b1af-4de4-95d0-9327ced2f71a

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "list": [
      {
        "id": 38,
        "user_id": 3,
        "archive_id": null,
        "title": "国家奖学金",
        "category": "scholarship",
        "level": "national",
        "awarded_at": "2025-10-01",
        "status": "pending_first",
        "created_at": "2026-09-18T06:58:53.754026Z",
        "updated_at": "2026-09-18T06:58:53.754026Z"
      },
      {
        "id": 3,
        "user_id": 1,
        "archive_id": 1,
        "title": "不允许",
        "category": "scholarship",
        "level": "provincial",
        "awarded_at": "2025-10-01",
        "status": "approved",
        "created_at": "2026-09-18T06:32:39.970319Z",
        "updated_at": "2026-09-18T06:33:37.789670Z"
      },
      {
        "id": 1,
        "user_id": 1,
        "archive_id": null,
        "title": "国家奖学金",
        "category": "scholarship",
        "level": "national",
        "awarded_at": "2024-10-01",
        "status": "approved",
        "created_at": "2026-09-17T13:43:33.883500Z",
        "updated_at": "2026-09-17T13:43:33.883500Z"
      },
      {
        "id": 2,
        "user_id": 3,
        "archive_id": null,
        "title": "蓝桥杯一等奖",
        "category": "competition",
        "level": "provincial",
        "awarded_at": "2025-04-01",
        "status": "pending_first",
        "created_at": "2026-09-17T13:43:33.883500Z",
        "updated_at": "2026-09-17T13:43:33.883500Z"
      }
    ],
    "total": 4,
    "page": 1,
    "page_size": 20
  },
  "request_id": "5d0e2ad1-b1af-4de4-95d0-9327ced2f71a",
  "timestamp": 1789718145610
}
```

### H2 GET /honors 无Token

- 期望：HTTP 401
- 实际：HTTP 401 UNAUTHORIZED
- 结果：通过

**请求1**：`GET /honors` → HTTP 401 · UNAUTHORIZED · 14ms · request_id=9bc736dd-b71a-4bf4-af6b-1f8adc15c686

```json
{
  "code": "UNAUTHORIZED",
  "message": "未登录或 Token 已过期",
  "data": null,
  "request_id": "9bc736dd-b71a-4bf4-af6b-1f8adc15c686",
  "timestamp": 1789718145642
}
```

### H3 GET /honors/999999 不存在

- 期望：HTTP 404
- 实际：HTTP 404 NOT_FOUND
- 结果：通过

**请求1**：`GET /honors/999999` → HTTP 404 · NOT_FOUND · 139ms · request_id=ca81cad0-4f63-41a6-b1d7-2afcaac7ab96

```json
{
  "code": "NOT_FOUND",
  "message": "荣誉不存在",
  "data": null,
  "request_id": "ca81cad0-4f63-41a6-b1d7-2afcaac7ab96",
  "timestamp": 1789718145795
}
```

### H4 POST /honors 缺title

- 期望：HTTP 400
- 实际：HTTP 400 INVALID_PARAMETER
- 结果：通过

**请求1**：`POST /honors` → HTTP 400 · INVALID_PARAMETER · 93ms · request_id=e0b79026-c423-4987-addf-b97d2bf8c565

```json
{
  "code": "INVALID_PARAMETER",
  "message": "title 不能为空",
  "data": null,
  "request_id": "e0b79026-c423-4987-addf-b97d2bf8c565",
  "timestamp": 1789718145894
}
```

### H5 ⚠ 创建荣誉→重复创建应409（防重规则）

- 期望：首次201 → 同人同题同日重复409 INVALID_STATE
- 实际：首次201 → 重复409 INVALID_STATE
- 结果：通过

**请求1**：`POST /honors (首次)` → HTTP 201 · OK · 82ms · request_id=60e5f935-d153-4e55-af7e-f9b696d22bd1

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "id": 39,
    "user_id": 3,
    "archive_id": null,
    "title": "综合测试荣誉145906",
    "category": "scholarship",
    "level": "national",
    "awarded_at": "2025-10-01",
    "status": "pending_first",
    "created_at": "2026-09-18T07:55:45.955598Z",
    "updated_at": "2026-09-18T07:55:45.955598Z"
  },
  "request_id": "60e5f935-d153-4e55-af7e-f9b696d22bd1",
  "timestamp": 1789718145985
}
```

**请求2**：`POST /honors (重复)` → HTTP 409 · INVALID_STATE · 15ms · request_id=44d42312-958f-46f0-b15e-5506043ec650

```json
{
  "code": "INVALID_STATE",
  "message": "相同荣誉已提交（待审核或已通过），请勿重复创建",
  "data": null,
  "request_id": "44d42312-958f-46f0-b15e-5506043ec650",
  "timestamp": 1789718145999
}
```

## 四、结果分析

本轮用例全部通过：认证拦截（401）、越权拦截（403）、参数校验（400）、资源不存在（404）、状态冲突（409）等错误处理路径均符合规范；查询类接口统一响应结构（code/message/data/request_id/timestamp）完整。
