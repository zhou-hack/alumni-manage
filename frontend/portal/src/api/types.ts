// 类型与错误码：严格对齐《[定稿]API规范.md》（字段 snake_case）

export type Role = 'student' | 'alumni' | 'counselor' | 'college_admin' | 'school_admin' | 'system_admin';

/** 统一响应信封（1.3） */
export interface ApiEnvelope<T> {
  code: string;
  message: string;
  data: T;
  request_id: string;
  timestamp: number;
}

/** 分页 data 结构（1.4） */
export interface Paginated<T> {
  list: T[];
  total: number;
  page: number;
  page_size: number;
}

export interface UserBrief {
  id: number;
  uuid?: string;
  student_id: string;
  nickname: string;
  /** 学籍真实姓名（users 表已有；后端 /auth/me 返回该字段后前端即优先展示） */
  real_name?: string | null;
  role: Role;
  phone?: string;
  created_at?: string;
}

export interface AuthTokens {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  user: UserBrief;
}

/** 档案状态机（4.1） */
export type ArchiveStatus =
  | 'draft'
  | 'pending_first'
  | 'pending_second'
  | 'approved'
  | 'rejected'
  | 'deleting'
  | 'deleted';

export interface Archive {
  id: number;
  owner_student_id: string;
  title: string;
  category: string;
  content: string;
  remark?: string;
  attachment_ids: number[];
  /** 附件对象列表（详情接口返回；列表接口可能缺省） */
  attachments?: Attachment[];
  status: ArchiveStatus;
  reject_reason?: string | null;
  version: number;
  consent_id?: number | null;
  published: boolean;
  created_at: string;
  updated_at: string;
}

export interface Honor {
  id: number;
  /** 归属用户 id（后端返回；校友端凭它与 /auth/me 的 id 比对过滤本人数据） */
  user_id?: number;
  archive_id?: number | null;
  owner_student_id: string;
  title: string;
  category: string;
  level: string;
  awarded_at: string; // YYYY-MM-DD
  /** 授予单位（选填） */
  granted_by?: string | null;
  /** 审核状态（复用档案状态机，部分接口返回） */
  status?: ArchiveStatus;
  reject_reason?: string | null;
  attachment_ids: number[];
  created_at: string;
}

export interface Attachment {
  id: number;
  biz_type: string;
  status: 'unreferenced' | 'referenced';
  name: string;
  url: string;
  size: number;
  mime: string;
  created_at: string;
  referenced_at?: string | null;
}

export type ReviewBizType = 'archive' | 'honor' | 'archive_delete' | 'activate_manual';

export interface Review {
  id: number;
  biz_type: ReviewBizType;
  biz_id: number;
  stage: 'first' | 'second';
  status: 'pending' | 'approved' | 'rejected';
  reviewer_id: number | null;
  reason: string | null;
  created_at: string;
  updated_at: string;
}

export interface Consent {
  id: number;
  archive_id: number;
  scope: 'screen' | 'site';
  owner_student_id: string;
  revoked: boolean;
  created_at: string;
}

export type DictType = 'college' | 'major' | 'grad_year' | 'honor_category' | 'photo_tag';

export interface DictItem {
  id: number;
  label: string;
  value: string;
  sort: number;
}

/** 我的相册照片（photo_collect；后端 PhotoDtos.PhotoItem，snake_case 由 request 层透传） */
export interface PhotoItem {
  id: number;
  user_id: number;
  attachment_id: number;
  attachment_url: string | null;
  attachment_name: string | null;
  title: string | null;
  caption: string | null;
  tags: string[];
  status: 'pending' | 'approved' | 'rejected' | 'pushed' | string;
  reject_reason: string | null;
  reviewed_by: number | null;
  reviewed_at: string | null;
  pushed_count: number;
  last_push_at: string | null;
  created_at: string;
  updated_at: string;
}

export interface ImportTask {
  id: number;
  total: number;
  success: number;
  failed: number;
  status: 'processing' | 'done';
  created_at: string;
}

export interface ImportErrorRow {
  row: number;
  student_id: string;
  reason: string;
}

export interface Draft {
  id: number;
  biz_type: string;
  owner_student_id: string;
  version: number;
  payload: unknown;
  updated_at: string;
}

export interface ScreenFeedItem {
  id: number;
  title: string;
  category: string;
  owner: string;
  college: string;
  grad_year: number;
  cover_url: string;
}

/* ===== 门户前台公开接口类型（公告 / 优秀校友 / 公开名录 / 留言墙；snake_case 对齐后端 DTO） ===== */

/** 公告列表项（GET /announcements，仅 published） */
export interface AnnouncementItem {
  id: number;
  title: string;
  content: string;
  cover_image?: string | null;
  status: 'draft' | 'published' | 'withdrawn' | string;
  published_by?: number | null;
  published_at?: string | null;
  created_by?: number | null;
  created_at?: string | null;
  updated_at?: string | null;
}

/** 优秀校友公开列表项（GET /alumni/outstanding，仅 published） */
export interface OutstandingItem {
  student_id: string;
  name: string;
  photo_url?: string | null;
  message?: string | null;
  college?: string | null;
  grad_year?: number | null;
  current?: string | null;
  /** JSON 透传结构：[{ year, event }] */
  timeline?: Record<string, string>[] | null;
  /** JSON 透传结构：[{ title, year, level }]（无 category 时前端归"奖项"） */
  awards?: Record<string, string>[] | null;
  featured: boolean;
}

/** 优秀校友公开列表响应 data */
export interface OutstandingListResult {
  list: OutstandingItem[];
  total: number;
}

/** 公开名录列表项（GET /alumni/directory，approved + 上架 + 授权） */
export interface DirectoryItem {
  student_id: string;
  name: string;
  photo_url?: string | null;
  college?: string | null;
  grad_year?: number | null;
  current?: string | null;
  top_honor?: string | null;
  archive_count: number;
}

/** 公开档案详情内的公开档案条目 */
export interface DirectoryPublicArchive {
  id: number;
  title: string;
  category: string;
  published_at?: string | null;
}

/** 公开档案详情（GET /alumni/directory/{studentId}，优秀校友与名录两模块弹窗共用） */
export interface DirectoryDetail {
  student_id: string;
  name: string;
  photo_url?: string | null;
  college?: string | null;
  grad_year?: number | null;
  current?: string | null;
  message?: string | null;
  timeline?: Record<string, string>[] | null;
  awards?: Record<string, string>[] | null;
  public_archives?: DirectoryPublicArchive[] | null;
}

/** 留言墙公开列表项（GET /wall/messages，仅 approved，置顶优先时间倒序） */
export interface WallPublicItem {
  id: number;
  user_id?: number | null;
  name: string;
  /** 后端已拼好"届"，如 2023届 */
  grad_year?: string | null;
  college?: string | null;
  content: string;
  is_pinned: boolean;
  created_at: string;
}

/** 我的留言项（GET /wall/messages/mine；POST /wall/messages 响应） */
export interface WallMyItem {
  id: number;
  content: string;
  show_name?: boolean | null;
  status: 'pending' | 'approved' | 'rejected' | string;
  review_note?: string | null;
  created_at: string;
  reviewed_at?: string | null;
  /** 软删除时间戳：非空 = 已撤回，可「重新发送」 */
  deleted_at?: string | null;
}

/** 第十三章 错误码表（HTTP 状态 + 业务 code） */
export const ERR = {
  OK: { status: 200, code: 'OK' },
  INVALID_PARAMETER: { status: 400, code: 'INVALID_PARAMETER' },
  AUTH_VERIFY_FAILED: { status: 400, code: 'AUTH_VERIFY_FAILED' },
  AUTH_CODE_INVALID: { status: 400, code: 'AUTH_CODE_INVALID' },
  FILE_TOO_LARGE: { status: 400, code: 'FILE_TOO_LARGE' },
  FILE_TYPE_NOT_ALLOWED: { status: 400, code: 'FILE_TYPE_NOT_ALLOWED' },
  BATCH_LIMIT_EXCEEDED: { status: 400, code: 'BATCH_LIMIT_EXCEEDED' },
  UNAUTHORIZED: { status: 401, code: 'UNAUTHORIZED' },
  FORBIDDEN: { status: 403, code: 'FORBIDDEN' },
  NOT_FOUND: { status: 404, code: 'NOT_FOUND' },
  INVALID_STATE: { status: 409, code: 'INVALID_STATE' },
  RESOURCE_REFERENCED: { status: 409, code: 'RESOURCE_REFERENCED' },
  AUTH_ALREADY_ACTIVATED: { status: 409, code: 'AUTH_ALREADY_ACTIVATED' },
  DRAFT_VERSION_CONFLICT: { status: 409, code: 'DRAFT_VERSION_CONFLICT' },
  RATE_LIMITED: { status: 429, code: 'RATE_LIMITED' },
  INTERNAL_ERROR: { status: 500, code: 'INTERNAL_ERROR' },
  UPSTREAM_UNAVAILABLE: { status: 502, code: 'UPSTREAM_UNAVAILABLE' }
} as const;

export type ErrKey = keyof typeof ERR;
