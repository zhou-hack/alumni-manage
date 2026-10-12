/api/v1
│
├── 第一章  通用规范（差异：counselor 参与审核流转；新增错误码 TAG_DUPLICATED / SCOPE_REQUIRED / HANDOVER_INVALID / EXPORT_LIMIT_EXCEEDED）
│          文件夹：backend/common（公共底座，无新模块——新错误码登记进 ErrorCode，
│          counselor 角色约定进统一安全栈 AuthPrincipal/SecurityConfig）
│
├── 第二章  /counselors        三级审核与导员管理（M34）
│          文件夹：backend/counselors
│          com.alumni.counselors/
│          ├── controller/CounselorController.java
│          ├── service/CounselorService.java
│          ├── repository/CounselorScopeRepository.java
│          ├── entity/CounselorScope.java
│          └── dto/CounselorDtos.java
│          （school_admin 创建/停启用/重置密码；scope = 届别∪专业∪学号，至少一项非空）
│          GET / POST /counselors，GET / PATCH /{id}，POST /{id}/disable|enable|password/reset
│          流程：提交 → pending_counselor → pending_first → pending_second → approved
│          兜底：无导员覆盖自动跳过初审环节；导员停用在途单回退院系初审
│
├── 第三章  /tags  /search     智能标签与增强检索（M13）
│          文件夹：backend/tags（一个模块承载两个路径前缀，检索聚合依赖标签域）
│          com.alumni.tags/
│          ├── controller/TagController.java          /tags + /tags/bindings/**
│          ├── controller/SearchController.java       /search + /search/suggest
│          ├── service/{TagService, SearchService}.java
│          ├── repository/{TagRepository, TagBindingRepository}.java
│          └── entity/{Tag, TagBinding}.java
│          /tags：标签域隔离（honor/archive/alumni），同域重名 409；school_admin 维护
│          /tags/bindings/{biz_type}/{biz_id}：PUT 全量覆盖式绑定，一物多标 ≤20
│          /search：聚合检索（拼音全拼+首字母容错，MySQL 预生成拼音列）
│          /search/suggest：关键词联想（标题/标签/姓名/学院，limit≤20）
│
├── 第四章  /notifications     消息通知（M14）
│          文件夹：backend/notifications（站内信与外部订阅同模块）
│          com.alumni.notifications/
│          ├── controller/NotificationController.java        /notifications/**
│          ├── controller/SubscriptionController.java        /notification-subscriptions/**
│          ├── service/{NotificationService, SubscriptionService}.java
│          ├── repository/{NotificationRepository, SubscriptionRepository}.java
│          ├── entity/{Notification, NotificationSubscription}.java
│          └── event/NotificationEventListener.java    监听审核/公告事件（服务端自动生成）
│          站内：GET /notifications + /unread-count，POST /{id}/read | /read-all
│          事件源：审核通过/驳回（含 reason）、公告发布、待办超 48h 提醒（服务端自动生成）
│          /notification-subscriptions：外部订阅（SMS/邮件），默认全关、可退订、不暴露学号
│
├── 第五章  /admin             细粒度权限与账号交接（M15）
│          文件夹：backend/admin（注意：/admin/wall 在 wall 模块、/admin/outstanding-alumni
│          在 outstanding 模块——沿用"/admin/* 归属业务模块"的既有约定，本模块只管权限与账号）
│          com.alumni.admin/
│          ├── controller/FieldPermissionController.java     /admin/field-permissions/**
│          ├── controller/AdminUserController.java           /admin/users/**
│          ├── service/{FieldPermissionService, AdminUserService}.java
│          ├── repository/{FieldPermissionRepository, UserChangeLogRepository}.java
│          └── entity/{FieldPermission, UserChangeLog}.java
│          /admin/field-permissions：角色—字段可见性矩阵（visible/masked/hidden）
│          /admin/users：GET 列表；POST /{id}/suspend | /resume | /handover | /change-logs
│          停权：Token 全部作废；交接：同角色转移在途待办并留痕
│          跨学院限制：counselor / college_admin 不得跨学院交接；校级/系统管理员可跨
│
├── 第六章  /audit-logs       安全、隐私与审计（M16）
│          文件夹：backend/audit（审计查询/导出独立模块；consents 版本化改造在原模块）
│          com.alumni.audit/
│          ├── controller/AuditLogController.java      /audit-logs + /actions + /export
│          ├── service/{AuditLogService, AuditExportService}.java
│          ├── repository/AuditLogRepository.java
│          └── entity/AuditLog.java
│          查询/导出（system_admin 代行，只追加不可篡改，保留≥180天）
│          /consents/text：授权文本（版本化留证）→ backend/consents（P0 模块扩展）
│          consents 扩展：version + consent_text_hash，撤回即时生效
│
├── 第七章  大屏屏幕自适应（M17，前端为主，无新增接口）
│          文件夹：无新后端模块；前端 alumni_frontend 调整 rem/vw + ResizeObserver
│
└── P0 存量接口变更（不新增路径，行为扩展——均改在原模块文件夹内）
       backend/reviews    GET /reviews                          stage 支持 counselor，导员强制范围过滤
                          POST /reviews/{id}/approve|reject     支持 counselor 环节；新驳回重提回 pending_counselor
       backend/archives   GET /archives                         keyword 支持拼音容错（预生成拼音列）
       backend/honors     GET /honors                           keyword 支持拼音容错（预生成拼音列）
       backend/consents   POST /consents / GET /consents/my     新增 version / consent_text_hash
       backend/archives   archives / honors 状态机              新增 pending_counselor（全校新提交强制进入）
       backend/db         schema.sql                            拼音列（全拼+首字母）、counselor_scopes、tags、
                          tag_bindings、notifications、notification_subscriptions、audit_logs、
                          user_change_logs、field_permissions、users.status、consents 版本化列
