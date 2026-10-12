// API 出口层：按《[定稿]API规范.md》章节组织，路径/权限/错误码全部对齐
// 页面调用示例：await api.auth.login({ phone, password: await sha256Hex(raw) })

import { request, USE_MOCK } from './client';
import type {
  AnnouncementItem, Archive, Attachment, AuthTokens, Consent, DictItem, DictType, DirectoryDetail,
  DirectoryItem, Draft, Honor, ImportErrorRow, OutstandingListResult, Paginated, PhotoItem, Review, ScreenFeedItem,
  UserBrief, WallMyItem, WallPublicItem
} from './types';

export * from './types';
export { sha256Hex, checkPasswordStrength, tokenStore, ApiError, BASE_URL, USE_MOCK } from './client';

/* ===== 二、认证 Auth（B1） ===== */
export const auth = {
  /** 2.1.1 三要素比对 */
  activateVerify: (body: { student_id: string; real_name: string; id_card: string }) =>
    request<{ verify_token: string; expires_in: number; masked_phone: string }>('/auth/activate/verify', { method: 'POST', body, public: true }),
  /** 2.1.2 发验证码（可换绑手机号） */
  activateSms: (body: { verify_token: string; new_phone?: string }) =>
    request<{ masked_phone: string; expires_in: number; dev_code?: string }>('/auth/activate/sms', { method: 'POST', body, public: true }),
  /** 2.1.3 完成激活 */
  activateConfirm: (body: { verify_token: string; sms_code: string; password: string }) =>
    request<AuthTokens>('/auth/activate/confirm', { method: 'POST', body, public: true }),
  /** 2.1.4 人工核验申请（预留兜底通道） */
  activateManualRequest: (body: { student_id: string; real_name: string; id_card: string; contact_phone: string; note?: string; attachment_ids?: number[] }) =>
    request<{ request_id_no: number }>('/auth/activate/manual-request', { method: 'POST', body, public: true }),
  /** 2.2 密码登录（账号为 11 位手机号时传 phone，否则传 username，如管理员 admin） */
  login: (body: { phone?: string; username?: string; password: string }) =>
    request<AuthTokens>('/auth/login', { method: 'POST', body, public: true }),
  /** 2.2 验证码登录 */
  loginSms: (body: { phone: string; sms_code: string }) =>
    request<AuthTokens>('/auth/login/sms', { method: 'POST', body, public: true }),
  /** 2.2 通用发码（scene: login / reset_password） */
  sms: (body: { phone: string; scene: 'login' | 'reset_password' }) =>
    request<{ masked_phone: string; expires_in: number; dev_code?: string }>('/auth/sms', { method: 'POST', body, public: true }),
  /** 2.3 ② 验证码换 reset_token */
  passwordResetToken: (body: { phone: string; sms_code: string }) =>
    request<{ reset_token: string; expires_in: number }>('/auth/password/reset-token', { method: 'POST', body, public: true }),
  /** 2.3 ③ 凭 reset_token 重置密码 */
  passwordReset: (body: { new_password: string }, resetToken: string) =>
    request<void>('/auth/password/reset', { method: 'POST', body, public: true, bearer: resetToken }),
  /** 2.4 刷新令牌 */
  refresh: (body: { refresh_token: string }) =>
    request<AuthTokens>('/auth/refresh', { method: 'POST', body, public: true }),
  /** 2.4 登出 */
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
  /** 2.4 当前用户 */
  me: () => request<UserBrief>('/auth/me')
};

/* ===== 三、字典 Dicts（B1） ===== */
export const dicts = {
  list: (type: DictType) => request<DictItem[]>(`/dicts/${type}`),
  create: (type: DictType, body: { label: string; value?: string; sort?: number }) =>
    request<DictItem>(`/dicts/${type}`, { method: 'POST', body }),
  update: (type: DictType, id: number, body: Partial<DictItem>) =>
    request<DictItem>(`/dicts/${type}/${id}`, { method: 'PATCH', body }),
  remove: (type: DictType, id: number) => request<{ deleted: boolean }>(`/dicts/${type}/${id}`, { method: 'DELETE' })
};

/* ===== 四、档案 Archives（B2） ===== */
export const archives = {
  create: (body: { title: string; category: string; content?: string; remark?: string; attachment_ids?: number[]; submit?: boolean }) =>
    request<Archive>('/archives', { method: 'POST', body }),
  list: (params: Record<string, string> = {}) =>
    request<Paginated<Archive>>('/archives?' + new URLSearchParams(params)),
  my: (params: Record<string, string> = {}) =>
    request<Paginated<Archive>>('/archives/my?' + new URLSearchParams(params)),
  detail: (id: number) => request<Archive>(`/archives/${id}`),
  update: (id: number, body: Partial<Pick<Archive, 'title' | 'category' | 'content' | 'remark' | 'attachment_ids'>> & { submit?: boolean }) =>
    request<Archive>(`/archives/${id}`, { method: 'PATCH', body }),
  resubmit: (id: number) => request<Archive>(`/archives/${id}/resubmit`, { method: 'POST' }),
  deleteRequest: (id: number) => request<Archive>(`/archives/${id}/delete-request`, { method: 'POST' })
};

/* ===== 五、荣誉 Honors（B2） ===== */
export const honors = {
  create: (body: { archive_id?: number; title: string; category: string; level?: string; awarded_at: string; granted_by?: string; attachment_ids?: number[] }) =>
    request<Honor>('/honors', { method: 'POST', body }),
  list: (params: Record<string, string> = {}) => request<Paginated<Honor>>('/honors?' + new URLSearchParams(params)),
  detail: (id: number) => request<Honor>(`/honors/${id}`),
  update: (id: number, body: Partial<Pick<Honor, 'title' | 'category' | 'level' | 'awarded_at'>>) =>
    request<Honor>(`/honors/${id}`, { method: 'PATCH', body })
};

/* ===== 六、附件 Attachments（B1） ===== */
export const attachments = {
  /**
   * 统一上传（API 规范 6.1）：multipart/form-data，多文件传多个 file 字段
   * 白名单 jpg/png/webp/pdf，单文件 ≤10MB，单次 ≤5 个（前端预校验见提交页）
   */
  upload: (opts: { biz_type: string; remark?: string; files: File[] }) => {
    if (USE_MOCK) {
      // mock 服务端按 JSON 元数据处理（无真实文件流）
      const meta = opts.files.map((f) => ({ name: f.name, size: f.size, mime: f.type }));
      return request<{ success: Partial<Attachment>[]; failed: { name: string; reason: string }[] }>('/attachments', {
        method: 'POST',
        body: { biz_type: opts.biz_type, remark: opts.remark, files: meta }
      });
    }
    const form = new FormData();
    opts.files.forEach((f) => form.append('file', f));
    form.append('biz_type', opts.biz_type);
    if (opts.remark) form.append('remark', opts.remark);
    return request<{ success: Partial<Attachment>[]; failed: { name: string; reason: string }[] }>('/attachments', {
      method: 'POST',
      form
    });
  },
  preview: (id: number) => request<Attachment>(`/attachments/${id}/preview`),
  remove: (id: number) => request<{ deleted: boolean }>(`/attachments/${id}`, { method: 'DELETE' })
};

/* ===== 七、审核 Reviews（B2） ===== */
export const reviews = {
  todo: (params: Record<string, string> = {}) => request<Paginated<Review>>('/reviews?' + new URLSearchParams(params)),
  detail: (reviewId: number) => request<Review>(`/reviews/${reviewId}`),
  approve: (reviewId: number) => request<Review>(`/reviews/${reviewId}/approve`, { method: 'POST' }),
  reject: (reviewId: number, body: { reason: string }) =>
    request<Review>(`/reviews/${reviewId}/reject`, { method: 'POST', body }),
  logs: (reviewId: number) => request<Review[]>(`/reviews/${reviewId}/logs`)
};

/* ===== 八、上下架与大屏 Showcase / Screen（B1） ===== */
export const showcase = {
  publish: (archiveId: number) => request<Archive>(`/showcase/${archiveId}/publish`, { method: 'POST' }),
  unpublish: (archiveId: number) => request<Archive>(`/showcase/${archiveId}/unpublish`, { method: 'POST' })
};
export const screen = {
  /** 大屏轮播数据（免认证） */
  feed: (collegeId?: number) =>
    request<{ list: ScreenFeedItem[] }>(`/screen/feed${collegeId ? `?college_id=${collegeId}` : ''}`, { public: true })
};

/* ===== 校友荣誉大屏（portal /dashboard）：单接口拉取 KPI + 4 张图表 + 荣誉榜单 + 杰出校友 + 当前生效大屏配置 ===== */
/** 当前生效大屏配置（来自 admin 后台荣誉大屏管理 → 大屏配置 → active 配置） */
export interface DashboardScreenConfig {
  name: string;
  layout: 'grid-3' | 'grid-4' | 'waterfall';
  themeColor: 'tech' | 'gold' | 'dark' | 'warm';
  refreshSec: number; // 自动刷新间隔（秒）；0=不自动刷新
  showFields: {
    title?: boolean;
    level?: boolean;
    category?: boolean;
    granted_by?: boolean;
    awarded_at?: boolean;
    owner?: boolean;
  };
  sortBy: 'awarded_at_desc' | 'level_asc' | 'pinned_first';
  maxItems: number; // 榜单最大条数
}
export interface DashboardHonorData {
  stats: {
    total_alumni: number;
    total_awards: number;
    college_count: number;
    grad_year_count: number;
  };
  honor_by_type: Array<{ type: string; name: string; value: number }>;
  industry_top: Array<{ name: string; value: number }>;
  province_dist: Array<{ name: string; value: number }>;
  honors: Array<{
    id: number;
    title: string;
    level: string;
    levelKey: string;
    year: number | null;
    alumnusName: string;
  }>;
  alumni: Array<{
    id: string;
    name: string;
    college: string;
    graduationYear: number;
    industry: string;
    intro: string;
    quote: string;
    motto: string;
    photo: string;
    highlight: string;
    awardCount: number;
    colorA: string;
    colorB: string;
  }>;
  /** 当前生效大屏配置（admin 端荣誉大屏管理 → 大屏配置 → active 配置） */
  screen_config: DashboardScreenConfig;
  updated_at: string;
}
export const dashboardHonor = () =>
  request<DashboardHonorData>('/dashboard-honor', { public: true });

/* ===== 首页轮播 Hero（公开，仅 published；后台 /admin/hero/slides 维护；placement=hero 首屏 / campus 美丽校园） ===== */
export const hero = {
  slides: (placement: 'hero' | 'campus' = 'hero') =>
    request<{ list: { id: number; title: string; subtitle: string | null; image: string | null; display_order: number; placement: string }[]; total: number }>(
      `/hero/slides?placement=${placement}`,
      { public: true }
    )
};

/* ===== 九、授权 Consents（B1） ===== */
export const consents = {
  create: (body: { archive_id: number; scope?: 'screen' | 'site' }) => request<Consent>('/consents', { method: 'POST', body }),
  my: () => request<Consent[]>('/consents/my'),
  revoke: (id: number) => request<Consent>(`/consents/${id}`, { method: 'DELETE' })
};

/* ===== 十、统计 Stats（B1） ===== */
export const stats = {
  /** GET /stats/overview —— 总量看板 */
  overview: () =>
    request<{
      total_alumni: number;
      total_archives: number;
      total_honors: number;
      pending_reviews: number;
      published_count: number;
    }>('/stats/overview'),
  /** GET /stats/honors/distribution?dimension=college|year|category|level —— [M03] 荣誉四维分布 */
  honors: (dimension?: 'college' | 'year' | 'category' | 'level') =>
    request<{ dimension: string; list: Array<{ key: string; count: number }> }>(
      `/stats/honors/distribution${dimension ? `?dimension=${dimension}` : ''}`
    ),
  /** GET /stats/colleges?limit=N —— 学院档案数排行（list 字段） */
  colleges: (limit = 8) =>
    request<{ list: { college_id: number | null; college_name: string | null; archive_count: number }[] }>(
      `/stats/colleges?limit=${limit}`
    )
};

/* ===== 十一-X 校友风采/座右铭（V18：自己管理大屏展示字段） ===== */
/**
 * 校友本人修改自己在荣誉大屏上展示的 5 个字段：
 *   photoUrl（走 attachments 上传）、industry / intro / quote / motto
 * 校级/系统管理员可改任意校友（PATCH /admin/alumni/{id}/profile）
 * 校友本人只能改自己（PATCH /me/profile，由 service 取 user.alumni_base_id）
 */
export const alumniProfile = {
  /** 校友读自己的资料（公开 GET /alumni/{id}/profile 也可走，但 self 用 /me 拿当前账号） */
  getMy: () => request<{
    id: number;
    realName: string;
    college: string;
    major: string;
    gradYear: number;
    photoUrl: string;
    industry: string;
    intro: string;
    quote: string;
    motto: string;
  }>('/me/profile'),
  /** 校友写自己的资料 */
  updateMy: (body: { photoUrl?: string; industry?: string; intro?: string; quote?: string; motto?: string }) =>
    request<{
      id: number;
      photoUrl: string;
      industry: string;
      intro: string;
      quote: string;
      motto: string;
    }>('/me/profile', { method: 'PATCH', body }),
};
export const imports = {
  template: () => request<{ url: string }>('/imports/template'),
  create: (body: { rows: { student_id: string; real_name: string; college_id?: number; grad_year?: number }[] }) =>
    request<{ import_id: number }>('/imports', { method: 'POST', body }),
  result: (id: number) => request<{ total: number; success: number; failed: number }>(`/imports/${id}`),
  errors: (id: number) => request<{ list: ImportErrorRow[] }>(`/imports/${id}/errors`)
};

/* ===== 十二、草稿 Drafts（B2） ===== */
export const drafts = {
  sync: (body: { biz_type: string; version: number; payload: unknown }) =>
    request<{ version: number }>('/drafts/sync', { method: 'POST', body }),
  my: (bizType?: string) => request<Draft[]>(`/drafts/my${bizType ? `?biz_type=${bizType}` : ''}`),
  remove: (id: number) => request<{ deleted: boolean }>(`/drafts/${id}`, { method: 'DELETE' })
};

/* ===== 门户前台公开：公告 Announcements ===== */
export const announcements = {
  /** 公告列表（免登录，仅 published） */
  list: (params: Record<string, string> = {}) =>
    request<Paginated<AnnouncementItem>>('/announcements?' + new URLSearchParams(params), { public: true }),
  /** 公告详情（免登录） */
  detail: (id: number | string) => request<AnnouncementItem>(`/announcements/${id}`, { public: true })
};

/* ===== 门户前台公开：优秀校友 / 公开档案名录 AlumniPublic ===== */
export const alumniPublic = {
  /** 优秀校友公开列表（免登录，首页环形展厅） */
  outstanding: (limit = 16) => request<OutstandingListResult>(`/alumni/outstanding?limit=${limit}`, { public: true }),
  /** 公开档案名录（免登录，approved + 上架 + 授权） */
  directory: (params: Record<string, string> = {}) =>
    request<Paginated<DirectoryItem>>('/alumni/directory?' + new URLSearchParams(params), { public: true }),
  /** 公开档案详情（免登录，名录/优秀校友弹窗共用） */
  directoryDetail: (studentId: string) =>
    request<DirectoryDetail>(`/alumni/directory/${encodeURIComponent(studentId)}`, { public: true })
};

/* ===== 留言墙 Wall ===== */
export const wall = {
  /** 公开列表（免登录，仅 approved，置顶优先时间倒序） */
  list: (params: Record<string, string> = {}) =>
    request<Paginated<WallPublicItem>>('/wall/messages?' + new URLSearchParams(params), { public: true }),
  /** 提交留言（需登录；新留言一律 pending 待审核；show_name=true 才公开显示姓名，缺省匿名） */
  create: (body: { content: string; show_name?: boolean }) => request<WallMyItem>('/wall/messages', { method: 'POST', body }),
  /** 我的留言（需登录，含 pending/rejected/hidden + 软删记录） */
  mine: () => request<{ total: number; list: WallMyItem[] }>('/wall/messages/mine'),
  /** 编辑自己的 pending/rejected 留言（重置为 pending 重新走审核） */
  update: (id: number, body: { content: string; show_name?: boolean }) =>
    request<WallMyItem>(`/wall/messages/${id}`, { method: 'PUT', body }),
  /** 重新发送已撤回的留言（基于原 content 重建一条 pending） */
  resubmit: (id: number) => request<WallMyItem>(`/wall/messages/${id}/resubmit`, { method: 'POST' }),
  /** 撤回自己的留言（软删除；保留原行，可重新发送） */
  remove: (id: number) => request<{ id: number; deleted: boolean }>(`/wall/messages/${id}`, { method: 'DELETE' })
};

/* ===== 我的相册 Photos（照片收集：用户提交 → 导员/校级审核 → 校级推送） ===== */
export const photos = {
  /** 提交照片：先经 attachments.upload(biz_type=photo_collect) 拿到 attachment_id；tags 为 dicts.photo_tag 编码数组 */
  submit: (body: { attachment_id: number; title?: string; caption?: string; tags?: string[] }) =>
    request<PhotoItem>('/photos/submit', { method: 'POST', body: { ...body, tags: body.tags ?? [] } }),
  /** 我的照片列表（status: pending/approved/rejected/pushed，缺省全部） */
  mine: (params: Record<string, string> = {}) =>
    request<Paginated<PhotoItem>>('/photos/mine?' + new URLSearchParams(params))
};

export const api = { auth, dicts, archives, honors, attachments, reviews, showcase, screen, dashboardHonor, hero, consents, stats, alumniProfile, imports, drafts, announcements, alumniPublic, wall, photos };
