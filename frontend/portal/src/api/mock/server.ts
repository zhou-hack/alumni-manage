// 模拟服务端：完整实现《[定稿]API规范.md》全部接口（含预留），统一信封/状态机/错误码/权限
// 数据持久化于 localStorage（fr_api_db_v1），刷新不丢；接真实后端后本文件可整体移除

import { apiErr, isoNow, uuid } from '../client';
import type {
  ApiEnvelope, Archive, Attachment, Consent, DictItem, DictType, Draft, Honor,
  ImportTask, ImportErrorRow, Paginated, Review, Role, UserBrief
} from '../types';

/* ---------------- 数据结构与种子 ---------------- */

interface DbUser {
  id: number;
  student_id: string;
  real_name: string;
  nickname: string;
  phone: string;
  id_card: string; // 脱敏存储
  role: Role;
  password_sha: string;
  activated: boolean;
  college_id: number | null;
  grad_year: number | null;
}

interface Db {
  users: DbUser[];
  dicts: Record<DictType, DictItem[]>;
  archives: Archive[];
  honors: Honor[];
  attachments: Attachment[];
  reviews: Review[];
  consents: Consent[];
  imports: (ImportTask & { errors: ImportErrorRow[] })[];
  drafts: Draft[];
  sessions: Record<string, number>; // token -> user id
  verifyTokens: Record<string, { student_id: string; exp: number }>;
  resetTokens: Record<string, { phone: string; exp: number; used: boolean }>;
  seq: number;
}

const PW_SHA = '7d71b0d8b7bbdf8ef57fb0f461db792f0f4c6010684f782b70f5ce4676add427'; // SHA-256("Furuan2026")
const LS_KEY = 'fr_api_db_v1';

function seed(): Db {
  const t = isoNow();
  const users: DbUser[] = [
    { id: 1, student_id: '2021000001', real_name: '林子轩', nickname: '林子轩', phone: '13800001111', id_card: '3501**********01', role: 'alumni', password_sha: PW_SHA, activated: true, college_id: 1, grad_year: 2023 },
    { id: 2, student_id: '2022000002', real_name: '陈思远', nickname: '陈思远', phone: '13800002222', id_card: '3501**********02', role: 'alumni', password_sha: PW_SHA, activated: true, college_id: 2, grad_year: 2024 },
    { id: 3, student_id: '2024000003', real_name: '黄俊杰', nickname: '黄俊杰', phone: '13800003333', id_card: '3501**********03', role: 'student', password_sha: PW_SHA, activated: true, college_id: 3, grad_year: 2026 },
    { id: 4, student_id: '2020000004', real_name: '李欣怡', nickname: '李欣怡', phone: '13800004444', id_card: '3501**********04', role: 'alumni', password_sha: PW_SHA, activated: true, college_id: 4, grad_year: 2022 },
    { id: 5, student_id: '2025000006', real_name: '吴雨桐', nickname: '吴雨桐', phone: '13800006666', id_card: '3501**********06', role: 'student', password_sha: PW_SHA, activated: false, college_id: 1, grad_year: 2027 },
    { id: 6, student_id: 'A10001', real_name: '学院管理员', nickname: '游戏学院管理员', phone: '13900000001', id_card: '3501**********11', role: 'college_admin', password_sha: PW_SHA, activated: true, college_id: 1, grad_year: null },
    { id: 7, student_id: 'A10002', real_name: '校级管理员', nickname: '校级管理员', phone: '13900000002', id_card: '3501**********12', role: 'school_admin', password_sha: PW_SHA, activated: true, college_id: null, grad_year: null },
    { id: 8, student_id: 'A10003', real_name: '系统管理员', nickname: '系统管理员', phone: '13900000003', id_card: '3501**********13', role: 'system_admin', password_sha: PW_SHA, activated: true, college_id: null, grad_year: null },
    { id: 9, student_id: 'A10004', real_name: '导员', nickname: '导员', phone: '13900000004', id_card: '3501**********14', role: 'counselor', password_sha: PW_SHA, activated: true, college_id: 1, grad_year: null }
  ];
  const dicts: Record<DictType, DictItem[]> = {
    college: [
      { id: 1, label: '游戏产业学院', value: 'GAME', sort: 1 },
      { id: 2, label: '智能产业学院', value: 'SMART', sort: 2 },
      { id: 3, label: '数据产业学院', value: 'DATA', sort: 3 },
      { id: 4, label: '数字影视传媒产业学院', value: 'MEDIA', sort: 4 },
      { id: 5, label: '现代通信产业学院', value: 'COMM', sort: 5 },
      { id: 6, label: '数字金融产业学院', value: 'FIN', sort: 6 },
      { id: 7, label: '智能建造产业学院', value: 'BUILD', sort: 7 },
      { id: 8, label: '未来技术产业学院', value: 'FUTURE', sort: 8 }
    ],
    major: [
      { id: 1, label: '虚拟现实技术应用', value: 'VR', sort: 1 },
      { id: 2, label: '软件技术', value: 'SW', sort: 2 },
      { id: 3, label: '大数据技术', value: 'BD', sort: 3 },
      { id: 4, label: '动漫制作技术', value: 'ANIME', sort: 4 }
    ],
    grad_year: [2019, 2020, 2021, 2022, 2023, 2024, 2025, 2026].map((y, i) => ({ id: i + 1, label: `${y}届`, value: String(y), sort: i + 1 })),
    honor_category: [
      { id: 1, label: '奖学金', value: 'scholarship', sort: 1 },
      { id: 2, label: '三好学生', value: 'three_good', sort: 2 },
      { id: 3, label: '优秀干部', value: 'cadre', sort: 3 },
      { id: 4, label: '证书', value: 'certificate', sort: 4 },
      { id: 5, label: '奖项', value: 'award', sort: 5 },
      { id: 6, label: '社会贡献', value: 'social', sort: 6 }
    ],
    photo_tag: [
      { id: 1, label: '校园风景', value: 'campus', sort: 1 },
      { id: 2, label: '活动合影', value: 'activity', sort: 2 },
      { id: 3, label: '毕业纪念', value: 'graduation', sort: 3 }
    ]
  };
  const archives: Archive[] = [
    { id: 1, owner_student_id: '2021000001', title: '福建省VR应用开发大赛一等奖', category: 'award', content: '团队作品《海丝古航》获省级一等奖。', remark: '毕业前统一归档', attachment_ids: [1], status: 'approved', reject_reason: null, version: 1, consent_id: 1, published: true, created_at: t, updated_at: t },
    { id: 2, owner_student_id: '2022000002', title: '1+X Web前端开发职业技能等级证书（中级）', category: 'certificate', content: '中级证书扫描件归档。', attachment_ids: [2], status: 'pending_second', reject_reason: null, version: 1, consent_id: null, published: false, created_at: t, updated_at: t },
    { id: 3, owner_student_id: '2024000003', title: '数据建模训练营优秀学员', category: 'award', content: '校级训练营优秀学员。', attachment_ids: [], status: 'pending_first', reject_reason: null, version: 1, consent_id: null, published: false, created_at: t, updated_at: t },
    { id: 4, owner_student_id: '2020000004', title: '海峡两岸动漫设计大赛银奖', category: 'award', content: '个人参赛获银奖。', attachment_ids: [3], status: 'approved', reject_reason: null, version: 1, consent_id: 2, published: true, created_at: t, updated_at: t },
    { id: 5, owner_student_id: '2021000001', title: '网龙数字教育小镇实习证明', category: 'experience', content: 'VR内容生产实习6个月。', attachment_ids: [], status: 'rejected', reject_reason: '实习证明缺少单位盖章，请补充后重提', version: 1, consent_id: null, published: false, created_at: t, updated_at: t }
  ];
  const honors: Honor[] = [
    { id: 1, archive_id: 1, owner_student_id: '2021000001', title: '福建省VR应用开发大赛一等奖', category: 'award', level: 'provincial', awarded_at: '2023-05-20', attachment_ids: [1], created_at: t },
    { id: 2, archive_id: 4, owner_student_id: '2020000004', title: '海峡两岸动漫设计大赛银奖', category: 'award', level: 'provincial', awarded_at: '2022-09-30', attachment_ids: [3], created_at: t }
  ];
  const attachments: Attachment[] = [
    { id: 1, biz_type: 'archive', status: 'referenced', name: 'VR大赛获奖证书.jpg', url: '/files/archive/2026/09/1.jpg', size: 204800, mime: 'image/jpeg', created_at: t, referenced_at: t },
    { id: 2, biz_type: 'archive', status: 'referenced', name: '前端证书扫描件.pdf', url: '/files/archive/2026/09/2.pdf', size: 512000, mime: 'application/pdf', created_at: t, referenced_at: t },
    { id: 3, biz_type: 'archive', status: 'referenced', name: '动漫大赛获奖作品集.pdf', url: '/files/archive/2026/09/3.pdf', size: 812000, mime: 'application/pdf', created_at: t, referenced_at: t }
  ];
  const reviews: Review[] = [
    { id: 1, biz_type: 'archive', biz_id: 2, stage: 'second', status: 'pending', reviewer_id: null, reason: null, created_at: t, updated_at: t },
    { id: 2, biz_type: 'archive', biz_id: 3, stage: 'first', status: 'pending', reviewer_id: null, reason: null, created_at: t, updated_at: t }
  ];
  const consents: Consent[] = [
    { id: 1, archive_id: 1, scope: 'screen', owner_student_id: '2021000001', revoked: false, created_at: t },
    { id: 2, archive_id: 4, scope: 'screen', owner_student_id: '2020000004', revoked: false, created_at: t }
  ];
  return { users, dicts, archives, honors, attachments, reviews, consents, imports: [], drafts: [], sessions: {}, verifyTokens: {}, resetTokens: {}, seq: 100 };
}

function load(): Db {
  try {
    const raw = localStorage.getItem(LS_KEY);
    if (raw) return JSON.parse(raw) as Db;
  } catch {
    /* 损坏则重建 */
  }
  const db = seed();
  save(db);
  return db;
}
function save(db: Db) {
  localStorage.setItem(LS_KEY, JSON.stringify(db));
}

let db = load();
const next = () => ++db.seq;

/* ---------------- 工具 ---------------- */

const ok = <T,>(data: T, status = 200): ApiEnvelope<T> => ({ code: 'OK', message: 'ok', data, request_id: uuid(), timestamp: Date.now() });

function authUser(token: string | null): DbUser {
  if (!token) throw apiErr('UNAUTHORIZED', '未登录或Token已过期');
  const uid = db.sessions[token];
  const u = db.users.find((x) => x.id === uid);
  if (!u) throw apiErr('UNAUTHORIZED', '未登录或Token已过期');
  return u;
}
function requireRole(u: DbUser, roles: Role[]) {
  if (!roles.includes(u.role)) throw apiErr('FORBIDDEN', '无权限访问');
}
const isReviewRole = (r: Role) => r === 'college_admin' || r === 'school_admin';
const isAlumniSide = (r: Role) => r === 'student' || r === 'alumni';
const maskPhone = (p: string) => p.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');
const brief = (u: DbUser): UserBrief => ({ id: u.id, student_id: u.student_id, nickname: u.nickname, role: u.role });
const collegeLabel = (id: number | null) => db.dicts.college.find((c) => c.id === id)?.label ?? '—';

function issueTokens(u: DbUser) {
  const at = 'at_' + u.id + '_' + uuid();
  const rt = 'rt_' + u.id + '_' + uuid();
  db.sessions[at] = u.id;
  db.sessions[rt] = u.id;
  save(db);
  return { access_token: at, refresh_token: rt, expires_in: 7200, user: brief(u) };
}

function paginate<T>(list: T[], q: Record<string, string>): Paginated<T> {
  const page = Math.max(1, parseInt(q.page ?? '1', 10) || 1);
  const size = Math.min(100, Math.max(1, parseInt(q.page_size ?? '20', 10) || 20));
  const start = (page - 1) * size;
  return { list: list.slice(start, start + size), total: list.length, page, page_size: size };
}

/* ---------------- 路由分发 ---------------- */

type Ctx = { user: DbUser | null; q: Record<string, string>; body: any };

export function mockDispatch(method: string, rawPath: string, body: unknown, token: string | null): ApiEnvelope<unknown> {
  const [pathPart, queryPart] = rawPath.split('?');
  const seg = pathPart.split('/').filter(Boolean);
  const q: Record<string, string> = {};
  new URLSearchParams(queryPart ?? '').forEach((v, k) => (q[k] = v));
  const ctx: Ctx = { user: null, q, body: (body ?? {}) as any };

  // 免认证白名单（1.2）：/auth/**（除 logout、me）、/screen/**
  const publicPath =
    (seg[0] === 'auth' && !(seg[1] === 'logout' || seg[1] === 'me')) || seg[0] === 'screen';
  if (!publicPath) ctx.user = authUser(token);

  const r = route(method, seg, ctx, token);
  save(db);
  return r;
}

function route(method: string, s: string[], { user, q, body }: Ctx, token: string | null): ApiEnvelope<unknown> {
  /* ===== 二、Auth ===== */
  if (s[0] === 'auth') {
    if (method === 'POST' && s[1] === 'activate' && s[2] === 'verify') {
      const u = db.users.find((x) => x.student_id === body.student_id && x.real_name === body.real_name && x.id_card === body.id_card);
      if (!u) throw apiErr('AUTH_VERIFY_FAILED', '信息不匹配，如确认本人信息无误，可提交人工核验', { can_manual_request: true });
      if (u.activated) throw apiErr('AUTH_ALREADY_ACTIVATED', '该学号已激活');
      const vt = 'v_tok_' + uuid();
      db.verifyTokens[vt] = { student_id: u.student_id, exp: Date.now() + 10 * 60 * 1000 };
      return ok({ verify_token: vt, expires_in: 600, masked_phone: maskPhone(u.phone) });
    }
    if (method === 'POST' && s[1] === 'activate' && s[2] === 'sms') {
      const vt = db.verifyTokens[body.verify_token];
      if (!vt || vt.exp < Date.now()) throw apiErr('AUTH_CODE_INVALID', '核验凭证无效或已过期');
      if (body.new_phone) {
        const u = db.users.find((x) => x.student_id === vt.student_id);
        if (u) u.phone = body.new_phone;
      }
      return ok({ sent: true });
    }
    if (method === 'POST' && s[1] === 'activate' && s[2] === 'confirm') {
      const vt = db.verifyTokens[body.verify_token];
      if (!vt || vt.exp < Date.now()) throw apiErr('AUTH_CODE_INVALID', '核验凭证无效或已过期');
      if (body.sms_code !== '123456') throw apiErr('AUTH_CODE_INVALID', '验证码错误或过期');
      if (!/^[0-9a-f]{64}$/.test(body.password)) throw apiErr('INVALID_PARAMETER', '密码格式不合法');
      const u = db.users.find((x) => x.student_id === vt.student_id);
      if (!u) throw apiErr('NOT_FOUND', '账号不存在');
      if (u.activated) throw apiErr('AUTH_ALREADY_ACTIVATED', '该学号已激活');
      u.activated = true;
      u.password_sha = body.password;
      delete db.verifyTokens[body.verify_token];
      return ok(issueTokens(u));
    }
    if (method === 'POST' && s[1] === 'activate' && s[2] === 'manual-request') {
      const pending = db.reviews.some((rv) => rv.biz_type === 'activate_manual' && rv.status === 'pending' && rv.biz_id === db.users.find((x) => x.student_id === body.student_id)?.id);
      if (pending) throw apiErr('INVALID_STATE', '存在待处理的人工核验申请');
      const u = db.users.find((x) => x.student_id === body.student_id);
      db.reviews.push({ id: next(), biz_type: 'activate_manual', biz_id: u?.id ?? 0, stage: 'first', status: 'pending', reviewer_id: null, reason: null, created_at: isoNow(), updated_at: isoNow() });
      return ok({ request_id_no: db.seq }, 201);
    }
    if (method === 'POST' && s[1] === 'login' && s[2] === 'sms') {
      if (body.sms_code !== '123456') throw apiErr('AUTH_CODE_INVALID', '验证码错误或过期');
      const u = db.users.find((x) => x.phone === body.phone);
      if (!u) throw apiErr('NOT_FOUND', '该手机号未注册');
      return ok(issueTokens(u));
    }
    if (method === 'POST' && s[1] === 'login') {
      const u = db.users.find((x) => x.phone === body.phone);
      if (!u || u.password_sha !== body.password) throw apiErr('UNAUTHORIZED', '手机号或密码错误');
      if (!u.activated) throw apiErr('INVALID_STATE', '账号未激活，请先完成激活');
      return ok(issueTokens(u));
    }
    if (method === 'POST' && s[1] === 'sms') return ok({ sent: true, scene: body.scene });
    if (method === 'POST' && s[1] === 'password' && s[2] === 'reset-token') {
      if (body.sms_code !== '123456') throw apiErr('AUTH_CODE_INVALID', '验证码错误或过期');
      const u = db.users.find((x) => x.phone === body.phone);
      if (!u) throw apiErr('NOT_FOUND', '该手机号未注册');
      const rt = 'rst_tok_' + uuid();
      db.resetTokens[rt] = { phone: body.phone, exp: Date.now() + 5 * 60 * 1000, used: false };
      return ok({ reset_token: rt, expires_in: 300 });
    }
    if (method === 'POST' && s[1] === 'password' && s[2] === 'reset') {
      const rt = token; // Bearer <reset_token>（2.3 ③）
      const rec = rt ? db.resetTokens[rt] : undefined;
      if (!rec || rec.used || rec.exp < Date.now()) throw apiErr('UNAUTHORIZED', '重置凭证缺失或已失效');
      if (!/^[0-9a-f]{64}$/.test(body.new_password)) throw apiErr('INVALID_PARAMETER', '密码格式不合法');
      const u = db.users.find((x) => x.phone === rec.phone);
      if (!u) throw apiErr('NOT_FOUND', '账号不存在');
      u.password_sha = body.new_password;
      rec.used = true;
      // 所有已签发 Token 全部作废
      Object.keys(db.sessions).forEach((k) => {
        if (db.sessions[k] === u.id) delete db.sessions[k];
      });
      return ok({ reset: true });
    }
    if (method === 'POST' && s[1] === 'refresh') {
      const uid = db.sessions[body.refresh_token];
      if (!uid || !body.refresh_token?.startsWith('rt_')) throw apiErr('UNAUTHORIZED', 'Token无效或过期');
      delete db.sessions[body.refresh_token]; // 轮换：旧 token 即刻作废
      const u = db.users.find((x) => x.id === uid)!;
      return ok(issueTokens(u));
    }
    if (method === 'POST' && s[1] === 'logout') {
      if (token) delete db.sessions[token];
      return ok({ logged_out: true });
    }
    if (method === 'GET' && s[1] === 'me') return ok(brief(user!));
  }

  /* ===== 三、Dicts ===== */
  if (s[0] === 'dicts') {
    const type = s[1] as DictType;
    if (!db.dicts[type]) throw apiErr('NOT_FOUND', '字典类型不存在');
    if (method === 'GET') return ok(db.dicts[type]);
    requireRole(user!, ['school_admin', 'system_admin']);
    if (method === 'POST') {
      const item: DictItem = { id: next(), label: body.label, value: body.value ?? body.label, sort: body.sort ?? db.dicts[type].length + 1 };
      db.dicts[type].push(item);
      return ok(item, 201);
    }
    const id = Number(s[2]);
    const item = db.dicts[type].find((d) => d.id === id);
    if (!item) throw apiErr('NOT_FOUND', '字典项不存在');
    if (method === 'PATCH') {
      Object.assign(item, { label: body.label ?? item.label, value: body.value ?? item.value, sort: body.sort ?? item.sort });
      return ok(item);
    }
    if (method === 'DELETE') {
      const referenced =
        (type === 'college' && db.users.some((u) => u.college_id === id)) ||
        (type === 'grad_year' && db.users.some((u) => u.grad_year === Number(item.value)));
      if (referenced) throw apiErr('RESOURCE_REFERENCED', '字典项被引用，禁止删除');
      db.dicts[type] = db.dicts[type].filter((d) => d.id !== id);
      return ok({ deleted: true });
    }
  }

  /* ===== 四、Archives ===== */
  if (s[0] === 'archives') {
    if (method === 'POST' && s.length === 1) {
      requireRole(user!, ['student', 'alumni']);
      const a: Archive = {
        id: next(), owner_student_id: user!.student_id, title: body.title, category: body.category,
        content: body.content ?? '', remark: body.remark, attachment_ids: body.attachment_ids ?? [],
        status: body.submit ? 'pending_first' : 'draft', reject_reason: null, version: 1,
        consent_id: null, published: false, created_at: isoNow(), updated_at: isoNow()
      };
      db.archives.push(a);
      db.attachments.forEach((at) => {
        if (a.attachment_ids.includes(at.id)) { at.status = 'referenced'; at.referenced_at = isoNow(); }
      });
      if (a.status === 'pending_first')
        db.reviews.push({ id: next(), biz_type: 'archive', biz_id: a.id, stage: 'first', status: 'pending', reviewer_id: null, reason: null, created_at: isoNow(), updated_at: isoNow() });
      return ok(a, 201);
    }
    if (method === 'GET' && s[1] === 'my') {
      requireRole(user!, ['student', 'alumni']);
      const list = db.archives.filter((a) => a.owner_student_id === user!.student_id);
      return ok(paginate(list, q));
    }
    if (method === 'GET' && s.length === 1) {
      requireRole(user!, ['college_admin', 'school_admin', 'counselor']);
      let list = db.archives.filter((a) => a.status !== 'deleted');
      if (q.college_id) {
        const stu = new Set(db.users.filter((u) => u.college_id === Number(q.college_id)).map((u) => u.student_id));
        list = list.filter((a) => stu.has(a.owner_student_id));
      }
      if (q.status) list = list.filter((a) => a.status === q.status);
      if (q.category) list = list.filter((a) => a.category === q.category);
      if (q.grad_year) {
        const stu = new Set(db.users.filter((u) => u.grad_year === Number(q.grad_year)).map((u) => u.student_id));
        list = list.filter((a) => stu.has(a.owner_student_id));
      }
      if (q.keyword) list = list.filter((a) => a.title.includes(q.keyword) || a.owner_student_id.includes(q.keyword));
      return ok(paginate(list, q));
    }
    const id = Number(s[1]);
    const a = db.archives.find((x) => x.id === id);
    if (!a || a.status === 'deleted') throw apiErr('NOT_FOUND', '档案不存在');
    if (method === 'GET') {
      if (a.owner_student_id !== user!.student_id) requireRole(user!, ['college_admin', 'school_admin', 'counselor']);
      return ok(a);
    }
    if (a.owner_student_id !== user!.student_id) throw apiErr('FORBIDDEN', '仅本人可操作');
    if (method === 'PATCH') {
      if (!(a.status === 'draft' || a.status === 'rejected')) throw apiErr('INVALID_STATE', '当前状态不允许修改');
      Object.assign(a, { title: body.title ?? a.title, content: body.content ?? a.content, remark: body.remark ?? a.remark, attachment_ids: body.attachment_ids ?? a.attachment_ids, updated_at: isoNow() });
      return ok(a);
    }
    if (method === 'POST' && s[2] === 'resubmit') {
      if (a.status !== 'rejected') throw apiErr('INVALID_STATE', '仅驳回状态可重提');
      a.version += 1;
      a.status = 'pending_first';
      a.reject_reason = null;
      a.updated_at = isoNow();
      db.reviews.push({ id: next(), biz_type: 'archive', biz_id: a.id, stage: 'first', status: 'pending', reviewer_id: null, reason: null, created_at: isoNow(), updated_at: isoNow() });
      return ok(a);
    }
    if (method === 'POST' && s[2] === 'delete-request') {
      if (a.status === 'deleting') throw apiErr('INVALID_STATE', '删除审核进行中');
      const prev = a.status;
      a.status = 'deleting';
      db.reviews.push({ id: next(), biz_type: 'archive_delete', biz_id: a.id, stage: 'first', status: 'pending', reviewer_id: null, reason: JSON.stringify({ prev }), created_at: isoNow(), updated_at: isoNow() });
      return ok(a);
    }
  }

  /* ===== 五、Honors ===== */
  if (s[0] === 'honors') {
    if (method === 'POST') {
      requireRole(user!, ['student', 'alumni']);
      const h: Honor = { id: next(), archive_id: body.archive_id ?? null, owner_student_id: user!.student_id, title: body.title, category: body.category, level: body.level ?? 'school', awarded_at: body.awarded_at, attachment_ids: body.attachment_ids ?? [], created_at: isoNow() };
      db.honors.push(h);
      return ok(h, 201);
    }
    if (method === 'GET' && s.length === 1) {
      let list = db.honors;
      if (q.category) list = list.filter((h) => h.category === q.category);
      return ok(paginate(list, q));
    }
    const id = Number(s[1]);
    const h = db.honors.find((x) => x.id === id);
    if (!h) throw apiErr('NOT_FOUND', '荣誉不存在');
    if (method === 'GET') return ok(h);
    if (method === 'PATCH') {
      if (h.owner_student_id !== user!.student_id) throw apiErr('FORBIDDEN', '仅本人可操作');
      Object.assign(h, { title: body.title ?? h.title, category: body.category ?? h.category, level: body.level ?? h.level, awarded_at: body.awarded_at ?? h.awarded_at });
      return ok(h);
    }
  }

  /* ===== 六、Attachments ===== */
  if (s[0] === 'attachments') {
    if (method === 'POST') {
      const files: { name: string; size: number; mime: string }[] = body.files ?? [];
      if (files.length > 20) throw apiErr('BATCH_LIMIT_EXCEEDED', '批量数量超限');
      const allow = (m: string) => m.startsWith('image/') || m === 'application/pdf' || /officedocument|msword|excel|spreadsheet/.test(m);
      const success: Partial<Attachment>[] = [];
      const failed: { name: string; reason: string }[] = [];
      files.forEach((f) => {
        if (f.size > 10 * 1024 * 1024) return failed.push({ name: f.name, reason: '文件大小超限' });
        if (!allow(f.mime)) return failed.push({ name: f.name, reason: '文件类型不允许' });
        const at: Attachment = { id: next(), biz_type: body.biz_type ?? 'archive', status: 'unreferenced', name: f.name, url: `/files/${body.biz_type ?? 'archive'}/2026/09/${f.name}`, size: f.size, mime: f.mime, created_at: isoNow(), referenced_at: null };
        db.attachments.push(at);
        success.push({ id: at.id, url: at.url, name: at.name });
      });
      return ok({ success, failed }, 201);
    }
    const id = Number(s[1]);
    const at = db.attachments.find((x) => x.id === id);
    if (!at) throw apiErr('NOT_FOUND', '附件不存在');
    if (method === 'GET' && s[2] === 'preview') {
      if (at.status === 'unreferenced' && at.biz_type !== 'activate') requireRole(user!, ['student', 'alumni', 'college_admin', 'school_admin']);
      return ok(at);
    }
    if (method === 'DELETE') {
      requireRole(user!, ['college_admin', 'school_admin']);
      if (at.status === 'referenced') throw apiErr('RESOURCE_REFERENCED', '附件被引用，禁止删除');
      db.attachments = db.attachments.filter((x) => x.id !== id);
      return ok({ deleted: true });
    }
  }

  /* ===== 七、Reviews ===== */
  if (s[0] === 'reviews') {
    requireRole(user!, ['college_admin', 'school_admin', 'student', 'alumni']);
    if (method === 'GET' && s.length === 1) {
      requireRole(user!, ['college_admin', 'school_admin']);
      const stage = user!.role === 'college_admin' ? 'first' : 'second';
      const list = db.reviews.filter((r) => r.status === 'pending' && r.stage === stage && r.biz_type !== 'activate_manual')
        .concat(user!.role === 'school_admin' ? db.reviews.filter((r) => r.status === 'pending' && r.biz_type === 'activate_manual') : []);
      return ok(paginate(list, q));
    }
    const id = Number(s[1]);
    const rv = db.reviews.find((x) => x.id === id);
    if (!rv) throw apiErr('NOT_FOUND', '审核记录不存在');
    if (method === 'GET' && s.length === 2) return ok(rv);
    if (method === 'GET' && s[2] === 'logs') {
      const logs = db.reviews.filter((x) => x.biz_type === rv.biz_type && x.biz_id === rv.biz_id);
      return ok(logs);
    }
    requireRole(user!, ['college_admin', 'school_admin']);
    if (rv.status !== 'pending') throw apiErr('INVALID_STATE', '当前状态不允许该操作');
    const expectStage = user!.role === 'college_admin' ? 'first' : 'second';
    if (rv.stage !== expectStage) throw apiErr('FORBIDDEN', '层级不匹配');
    const target = rv.biz_type === 'archive' || rv.biz_type === 'archive_delete'
      ? db.archives.find((a) => a.id === rv.biz_id)
      : undefined;
    if (method === 'POST' && s[2] === 'approve') {
      rv.status = 'approved'; rv.reviewer_id = user!.id; rv.updated_at = isoNow();
      if (rv.biz_type === 'archive' && target) {
        target.status = rv.stage === 'first' ? 'pending_second' : 'approved';
        target.updated_at = isoNow();
        if (rv.stage === 'first')
          db.reviews.push({ id: next(), biz_type: 'archive', biz_id: target.id, stage: 'second', status: 'pending', reviewer_id: null, reason: null, created_at: isoNow(), updated_at: isoNow() });
      }
      if (rv.biz_type === 'archive_delete' && target) {
        target.status = 'deleted';
        db.attachments.forEach((at) => { if (target.attachment_ids.includes(at.id)) at.status = 'unreferenced'; });
      }
      if (rv.biz_type === 'activate_manual') {
        const u = db.users.find((x) => x.id === rv.biz_id);
        if (u) u.activated = true;
      }
      return ok(rv);
    }
    if (method === 'POST' && s[2] === 'reject') {
      if (!body.reason) throw apiErr('INVALID_PARAMETER', '驳回原因必填');
      // 删除审核：reason 初始存原状态（JSON），驳回时先取回再覆盖为驳回原因
      let prevStatus: string | null = null;
      if (rv.biz_type === 'archive_delete') {
        try {
          prevStatus = JSON.parse(rv.reason ?? 'null')?.prev ?? null;
        } catch {
          prevStatus = null;
        }
      }
      rv.status = 'rejected'; rv.reviewer_id = user!.id; rv.reason = body.reason; rv.updated_at = isoNow();
      if (rv.biz_type === 'archive' && target) { target.status = 'rejected'; target.reject_reason = body.reason; target.updated_at = isoNow(); }
      if (rv.biz_type === 'archive_delete' && target) target.status = (prevStatus ?? 'approved') as Archive['status'];
      return ok(rv);
    }
  }

  /* ===== 八、Showcase / Screen ===== */
  if (s[0] === 'showcase') {
    requireRole(user!, ['school_admin']);
    const id = Number(s[1]);
    const a = db.archives.find((x) => x.id === id);
    if (!a) throw apiErr('NOT_FOUND', '档案不存在');
    if (method === 'POST' && s[2] === 'publish') {
      if (a.status !== 'approved') throw apiErr('INVALID_STATE', '仅通过审核的档案可上架');
      const consent = db.consents.find((c) => c.archive_id === id && !c.revoked);
      if (!consent) throw apiErr('INVALID_STATE', '缺少有效授权');
      a.published = true;
      return ok(a);
    }
    if (method === 'POST' && s[2] === 'unpublish') {
      a.published = false;
      return ok(a);
    }
  }
  if (s[0] === 'screen' && s[1] === 'feed') {
    let list = db.archives.filter((a) => a.status === 'approved' && a.published && db.consents.some((c) => c.archive_id === a.id && !c.revoked));
    if (q.college_id) {
      const stu = new Set(db.users.filter((u) => u.college_id === Number(q.college_id)).map((u) => u.student_id));
      list = list.filter((a) => stu.has(a.owner_student_id));
    }
    return ok({
      list: list.map((a) => {
        const owner = db.users.find((u) => u.student_id === a.owner_student_id);
        return { id: a.id, title: a.title, category: a.category, owner: owner?.nickname ?? '—', college: collegeLabel(owner?.college_id ?? null), grad_year: owner?.grad_year ?? 0, cover_url: '' };
      })
    });
  }

  /* ===== 九、Consents ===== */
  if (s[0] === 'consents') {
    if (method === 'POST') {
      requireRole(user!, ['student', 'alumni']);
      const c: Consent = { id: next(), archive_id: body.archive_id, scope: body.scope ?? 'screen', owner_student_id: user!.student_id, revoked: false, created_at: isoNow() };
      db.consents.push(c);
      const a = db.archives.find((x) => x.id === body.archive_id);
      if (a) a.consent_id = c.id;
      return ok(c, 201);
    }
    if (method === 'GET' && s[1] === 'my') {
      requireRole(user!, ['student', 'alumni']);
      return ok(db.consents.filter((c) => c.owner_student_id === user!.student_id));
    }
    const id = Number(s[1]);
    const c = db.consents.find((x) => x.id === id);
    if (!c) throw apiErr('NOT_FOUND', '授权不存在');
    if (method === 'DELETE') {
      if (c.owner_student_id !== user!.student_id) throw apiErr('FORBIDDEN', '仅本人可撤回');
      c.revoked = true;
      const a = db.archives.find((x) => x.id === c.archive_id);
      if (a) { a.published = false; a.consent_id = null; } // 已上架同步自动下架
      return ok(c);
    }
  }

  /* ===== 十、Stats ===== */
  if (s[0] === 'stats') {
    requireRole(user!, ['college_admin', 'school_admin', 'system_admin']);
    if (s[1] === 'overview')
      return ok({
        alumni_count: db.users.filter((u) => isAlumniSide(u.role)).length,
        archive_count: db.archives.filter((a) => a.status !== 'deleted').length,
        honor_count: db.honors.length,
        pending_count: db.reviews.filter((r) => r.status === 'pending').length
      });
    if (s[1] === 'honors') {
      const byCat: Record<string, number> = {};
      db.honors.forEach((h) => (byCat[h.category] = (byCat[h.category] ?? 0) + 1));
      return ok(byCat);
    }
    if (s[1] === 'colleges') {
      const limit = Number(q.limit ?? 8);
      const byCollege: Record<number, number> = {};
      db.archives.filter((a) => a.status !== 'deleted').forEach((a) => {
        const u = db.users.find((x) => x.student_id === a.owner_student_id);
        if (u?.college_id) byCollege[u.college_id] = (byCollege[u.college_id] ?? 0) + 1;
      });
      return ok(
        Object.entries(byCollege)
          .map(([cid, count]) => ({ college: collegeLabel(Number(cid)), count }))
          .sort((a, b) => b.count - a.count)
          .slice(0, limit)
      );
    }
  }

  /* ===== 十一、Imports ===== */
  if (s[0] === 'imports') {
    requireRole(user!, ['college_admin']);
    if (method === 'GET' && s[1] === 'template') return ok({ url: '/templates/import-template.xlsx' });
    if (method === 'POST') {
      const rows: { student_id: string; real_name: string; college_id: number; grad_year: number }[] = body.rows ?? [];
      if (rows.length > 2000) throw apiErr('BATCH_LIMIT_EXCEEDED', '单次最多2000行');
      const errors: ImportErrorRow[] = [];
      let success = 0;
      rows.forEach((r, i) => {
        if (!r.student_id || !r.real_name) return errors.push({ row: i + 2, student_id: r.student_id ?? '', reason: '学号/姓名缺失' });
        if (db.users.some((u) => u.student_id === r.student_id)) return errors.push({ row: i + 2, student_id: r.student_id, reason: '学号重复' });
        db.users.push({ id: next(), student_id: r.student_id, real_name: r.real_name, nickname: r.real_name, phone: '', id_card: '', role: 'student', password_sha: PW_SHA, activated: false, college_id: r.college_id ?? null, grad_year: r.grad_year ?? null });
        success++;
      });
      const task = { id: next(), total: rows.length, success, failed: errors.length, status: 'done' as const, errors, created_at: isoNow() };
      db.imports.push(task);
      return ok({ import_id: task.id });
    }
    const id = Number(s[1]);
    const task = db.imports.find((x) => x.id === id);
    if (!task) throw apiErr('NOT_FOUND', '导入任务不存在');
    if (method === 'GET' && s.length === 2) return ok({ total: task.total, success: task.success, failed: task.failed });
    if (method === 'GET' && s[2] === 'errors') return ok({ list: task.errors });
  }

  /* ===== 十二、Drafts ===== */
  if (s[0] === 'drafts') {
    if (method === 'POST' && s[1] === 'sync') {
      const mine = db.drafts.find((d) => d.owner_student_id === user!.student_id && d.biz_type === body.biz_type);
      if (mine) {
        if (body.version < mine.version)
          throw apiErr('DRAFT_VERSION_CONFLICT', '草稿已被其他设备修改', { version: mine.version, payload: mine.payload });
        mine.payload = body.payload;
        mine.version += 1;
        mine.updated_at = isoNow();
        return ok({ version: mine.version });
      }
      const d: Draft = { id: next(), biz_type: body.biz_type, owner_student_id: user!.student_id, version: 1, payload: body.payload, updated_at: isoNow() };
      db.drafts.push(d);
      return ok({ version: 1 }, 201);
    }
    if (method === 'GET' && s[1] === 'my')
      return ok(db.drafts.filter((d) => d.owner_student_id === user!.student_id && (!q.biz_type || d.biz_type === q.biz_type)));
    const id = Number(s[1]);
    if (method === 'DELETE') {
      db.drafts = db.drafts.filter((d) => !(d.id === id && d.owner_student_id === user!.student_id));
      return ok({ deleted: true });
    }
  }

  throw apiErr('NOT_FOUND', '接口不存在');
}
