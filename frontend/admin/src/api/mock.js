/**
 * Mock 适配层 · 管理后台
 * 覆盖：认证（管理员登录）、字典、档案列表（管理端）、审核待办/通过/驳回/日志
 * 所有响应严格遵循《API 规范》统一响应结构 {code, message, data, request_id, timestamp}
 *
 * 演示账号（管理员）：用户名 admin，密码 Admin12345
 * 审核角色说明（API 规范 1.2）：审核仅 college_admin / school_admin；
 * 演示账号为 school_admin，可见初审（代院系）与复核全部待办。
 */
import { sha256Hex } from '@/utils/crypto'

const now = () => Date.now()
const rand = () => Math.random().toString(36).slice(2, 10)

const DEMO_PASSWORD = 'Admin12345'

const db = {
  user: {
    id: 90,
    student_id: 'ADMIN001',
    nickname: '校级管理员',
    role: 'school_admin',
    phone: '13900000001',
  },
  passwordHash: '',
  notifications: [
    { id: 1, type: 'review', title: '档案待初审', content: '档案《全国大学生程序设计竞赛金奖》已提交初审，请尽快处理', bizType: 'review', bizId: 101, read: false, createdAt: new Date(Date.now() - 600_000).toISOString() },
    { id: 2, type: 'honor', title: '荣誉证书已确认', content: '《国家奖学金》证书已确认并关联到你的资料库', bizType: 'honor', bizId: 88, read: false, createdAt: new Date(Date.now() - 3_600_000).toISOString() },
    { id: 3, type: 'announcement', title: '校友返校日通知', content: '10月15日为校友返校日，请各院系负责人做好接待工作', bizType: 'announcement', bizId: 12, read: true, createdAt: new Date(Date.now() - 86_400_000).toISOString() },
    { id: 4, type: 'system', title: '系统维护通知', content: '本周日凌晨 02:00 - 04:00 进行系统维护，期间可能无法登录', bizType: null, bizId: null, read: true, createdAt: new Date(Date.now() - 172_800_000).toISOString() },
  ],
  subscriptions: ['review', 'honor', 'announcement', 'system'],
  auditLogs: [
    { id: 1001, userId: 90, userRole: 'school_admin', action: 'review.approve', bizType: 'review', bizId: 201, detail: { archive_id: 12, reason: '材料齐全' }, ip: '127.0.0.1', createdAt: new Date(Date.now() - 3600_000).toISOString() },
    { id: 1002, userId: 6, userRole: 'college_admin', action: 'review.first_pass', bizType: 'review', bizId: 202, detail: { archive_id: 14, college: '计算机学院' }, ip: '192.168.1.20', createdAt: new Date(Date.now() - 7200_000).toISOString() },
    { id: 1003, userId: 99, userRole: 'system_admin', action: 'account.suspend', bizType: 'account', bizId: 31, detail: { reason: '长期未登录' }, ip: '127.0.0.1', createdAt: new Date(Date.now() - 86_400_000).toISOString() },
    { id: 1004, userId: 99, userRole: 'system_admin', action: 'account.handover', bizType: 'account', bizId: 31, detail: { from: 31, to: 32, transferred_reviews: 3 }, ip: '127.0.0.1', createdAt: new Date(Date.now() - 86_400_000 - 600_000).toISOString() },
    { id: 1005, userId: 12, userRole: 'alumni', action: 'archive.submit', bizType: 'archive', bizId: 12, detail: { title: '2026 个人资料' }, ip: '10.0.0.18', createdAt: new Date(Date.now() - 2 * 86_400_000).toISOString() },
    { id: 1006, userId: 12, userRole: 'alumni', action: 'auth.login', bizType: 'auth', bizId: null, detail: { phone: '139****0001' }, ip: '10.0.0.18', createdAt: new Date(Date.now() - 2 * 86_400_000 - 1200_000).toISOString() },
    { id: 1007, userId: 90, userRole: 'school_admin', action: 'auth.password_change', bizType: 'auth', bizId: 90, detail: null, ip: '127.0.0.1', createdAt: new Date(Date.now() - 3 * 86_400_000).toISOString() },
  ],
  auditActions: [
    { action: 'review.approve', label: '审核通过' },
    { action: 'review.reject', label: '审核驳回' },
    { action: 'review.first_pass', label: '初审通过' },
    { action: 'review.second_pass', label: '终审通过' },
    { action: 'account.suspend', label: '账号停权' },
    { action: 'account.resume', label: '账号恢复' },
    { action: 'account.handover', label: '账号交接' },
    { action: 'archive.submit', label: '资料提交' },
    { action: 'archive.update', label: '资料修改' },
    { action: 'auth.login', label: '登录' },
    { action: 'auth.password_change', label: '修改密码' },
  ],
  counselors: [
    { id: 201, realName: '张老师', phone: '139****0021', collegeId: 1, college: '计算机学院', role: 'counselor', status: 'active', createdAt: new Date(Date.now() - 30 * 86_400_000).toISOString() },
    { id: 202, realName: '李老师', phone: '139****0022', collegeId: 2, college: '经济管理学院', role: 'counselor', status: 'active', createdAt: new Date(Date.now() - 60 * 86_400_000).toISOString() },
    { id: 203, realName: '王老师', phone: '139****0023', collegeId: 1, college: '计算机学院', role: 'counselor', status: 'disabled', createdAt: new Date(Date.now() - 90 * 86_400_000).toISOString() },
  ],
  counselorScopes: {
    201: { gradYears: [2024, 2025, 2026], majorIds: [1], studentIds: [] },
    202: { gradYears: [], majorIds: [10, 11], studentIds: [] },
    203: { gradYears: [], majorIds: [], studentIds: ['2021000001'] },
  },
  tags: [
    { id: 1, name: '互联网', group: 'industry', sort: 10, createdBy: 90, createdAt: new Date(Date.now() - 60 * 86_400_000).toISOString(), updatedAt: new Date(Date.now() - 60 * 86_400_000).toISOString() },
    { id: 2, name: '金融', group: 'industry', sort: 20, createdBy: 90, createdAt: new Date(Date.now() - 60 * 86_400_000).toISOString(), updatedAt: new Date(Date.now() - 60 * 86_400_000).toISOString() },
    { id: 3, name: '教育', group: 'industry', sort: 30, createdBy: 90, createdAt: new Date(Date.now() - 60 * 86_400_000).toISOString(), updatedAt: new Date(Date.now() - 60 * 86_400_000).toISOString() },
    { id: 4, name: '篮球', group: 'interest', sort: 10, createdBy: 90, createdAt: new Date(Date.now() - 30 * 86_400_000).toISOString(), updatedAt: new Date(Date.now() - 30 * 86_400_000).toISOString() },
    { id: 5, name: '摄影', group: 'interest', sort: 20, createdBy: 90, createdAt: new Date(Date.now() - 30 * 86_400_000).toISOString(), updatedAt: new Date(Date.now() - 30 * 86_400_000).toISOString() },
    { id: 6, name: '2024 届', group: 'year', sort: 24, createdBy: 90, createdAt: new Date(Date.now() - 7 * 86_400_000).toISOString(), updatedAt: new Date(Date.now() - 7 * 86_400_000).toISOString() },
  ],
  tagBindings: {},
  drafts: [
    { id: 3001, userId: 12, bizType: 'archive', version: 3, payload: { title: '2026 个人学业总结', category: 'study', content: '本学期参与三个项目...' }, updatedAt: new Date(Date.now() - 7200_000).toISOString() },
    { id: 3002, userId: 12, bizType: 'archive', version: 1, payload: { title: '校园活动小记', category: 'experience', content: '参加了校庆志愿服务...' }, updatedAt: new Date(Date.now() - 3 * 86_400_000).toISOString() },
    { id: 3003, userId: 12, bizType: 'honor', version: 2, payload: { title: '校级数学竞赛二等奖', category: 'award', level: 'school' }, updatedAt: new Date(Date.now() - 7 * 86_400_000).toISOString() },
  ],
  accessTokens: {},
  refreshTokens: {},
  archives: [
    {
      id: 12,
      title: '全国大学生程序设计竞赛金奖',
      category: 'award',
      content: '2024 年参加全国大学生程序设计竞赛（ICPC 亚洲区域赛），团队获得金奖。',
      remark: '获奖证书与现场照片',
      status: 'approved',
      reject_reason: null,
      version: 1,
      student_id: '2021000001',
      owner_name: '测试校友001',
      college_id: 1,
      grad_year: 2025,
      attachment_ids: [],
      created_at: '2026-09-15T08:30:00Z',
    },
    {
      id: 13,
      title: '暑期支教社会实践',
      category: 'experience',
      content: '2025 年暑期赴山区小学支教三周，负责数学与计算机启蒙课程。',
      remark: '',
      status: 'rejected',
      reject_reason: '证明材料不清晰，请重新上传清晰的实践证明',
      version: 2,
      student_id: '2021000002',
      owner_name: '测试校友002',
      college_id: 2,
      grad_year: 2024,
      attachment_ids: [],
      created_at: '2026-09-16T02:10:00Z',
    },
    {
      id: 14,
      title: '国家奖学金获奖证书',
      category: 'certificate',
      content: '2025 学年获得国家奖学金，附证书扫描件。',
      remark: '证书原件扫描件',
      status: 'pending_first',
      reject_reason: null,
      version: 1,
      student_id: '2021000003',
      owner_name: '测试校友003',
      college_id: 1,
      grad_year: 2026,
      attachment_ids: [],
      created_at: '2026-09-17T01:20:00Z',
    },
    {
      id: 15,
      title: '优秀学生干部证书',
      category: 'certificate',
      content: '担任班级班长期间获校级优秀学生干部。',
      remark: '',
      status: 'pending_second',
      reject_reason: null,
      version: 1,
      student_id: '2021000004',
      owner_name: '测试校友004',
      college_id: 3,
      grad_year: 2023,
      attachment_ids: [],
      created_at: '2026-09-16T06:45:00Z',
    },
    {
      id: 16,
      title: '互联网+创新创业大赛银奖',
      category: 'award',
      content: '团队项目获互联网+大赛省级银奖。',
      remark: '获奖公示文件',
      status: 'pending_first',
      reject_reason: null,
      version: 1,
      student_id: '2021000005',
      owner_name: '测试校友005',
      college_id: 2,
      grad_year: 2025,
      attachment_ids: [],
      created_at: '2026-09-17T02:56:00Z',
    },
  ],
  reviews: [
    {
      id: 88,
      biz_type: 'archive',
      biz_id: 14,
      stage: 'first',
      status: 'pending',
      reviewer_id: null,
      reason: null,
      created_at: '2026-09-17T01:20:00Z',
      updated_at: '2026-09-17T01:20:00Z',
    },
    {
      id: 89,
      biz_type: 'archive',
      biz_id: 15,
      stage: 'second',
      status: 'pending',
      reviewer_id: null,
      reason: null,
      created_at: '2026-09-16T07:00:00Z',
      updated_at: '2026-09-16T07:00:00Z',
    },
    {
      id: 90,
      biz_type: 'archive',
      biz_id: 16,
      stage: 'first',
      status: 'pending',
      reviewer_id: null,
      reason: null,
      created_at: '2026-09-17T03:00:00Z',
      updated_at: '2026-09-17T03:00:00Z',
    },
    {
      id: 91,
      biz_type: 'activate_manual',
      biz_id: 56,
      stage: 'first',
      status: 'pending',
      reviewer_id: null,
      reason: null,
      created_at: '2026-09-17T00:40:00Z',
      updated_at: '2026-09-17T00:40:00Z',
    },
  ],
  reviewLogs: {
    88: [{ reviewer: '系统', action: '提交审核', reason: '', at: '2026-09-17T01:20:00Z' }],
  },
}

const dicts = {
  college: [
    { id: 1, label: '计算机学院', value: 'CS', sort: 1 },
    { id: 2, label: '经济管理学院', value: 'EM', sort: 2 },
    { id: 3, label: '机械工程学院', value: 'ME', sort: 3 },
    { id: 4, label: '外国语学院', value: 'FL', sort: 4 },
    { id: 5, label: '艺术与设计学院', value: 'AD', sort: 5 },
  ],
  major: [
    { id: 1, label: '计算机科学与技术', value: 'CSST', sort: 1 },
    { id: 2, label: '软件工程', value: 'SE', sort: 2 },
    { id: 3, label: '会计学', value: 'ACC', sort: 3 },
    { id: 4, label: '机械设计制造及其自动化', value: 'MD', sort: 4 },
  ],
  grad_year: [2021, 2022, 2023, 2024, 2025, 2026].map((y, i) => ({
    id: i + 1,
    label: `${y} 届`,
    value: y,
    sort: i + 1,
  })),
  honor_category: [
    { id: 1, label: '奖学金', value: 'scholarship', sort: 1 },
    { id: 2, label: '三好学生', value: 'three_good', sort: 2 },
    { id: 3, label: '优秀干部', value: 'excellent_cadre', sort: 3 },
    { id: 4, label: '证书', value: 'certificate', sort: 4 },
    { id: 5, label: '奖项', value: 'award', sort: 5 },
    { id: 6, label: '社会贡献', value: 'social_contribution', sort: 6 },
  ],
}

function envelope(data, code = 'OK', message = 'ok') {
  return { code, message, data, request_id: `mock-${rand()}`, timestamp: now() }
}

function success(data, status = 200) {
  return { status, data: envelope(data) }
}

function fail(code, message, status, data = null) {
  return { status, data: envelope(data, code, message) }
}

function issueTokens(userId) {
  const access_token = `mock_at_${rand()}`
  const refresh_token = `mock_rt_${rand()}`
  db.accessTokens[access_token] = userId
  db.refreshTokens[refresh_token] = userId
  return { access_token, refresh_token, expires_in: 7200, user: publicUser(db.user) }
}

function publicUser(u) {
  return { id: u.id, student_id: u.student_id, nickname: u.nickname, role: u.role }
}

function collegeLabel(id) {
  return dicts.college.find((c) => c.id === Number(id))?.label || '-'
}

function archiveOf(id) {
  return db.archives.find((a) => a.id === Number(id))
}

function parseBody(config) {
  if (!config.data) return {}
  if (typeof config.data === 'string') {
    try {
      return JSON.parse(config.data)
    } catch {
      return {}
    }
  }
  return config.data
}

function parseQuery(config) {
  return new URLSearchParams((config.url || '').split('?')[1] || '')
}

const HEX_64 = /^[0-9a-f]{64}$/

const routes = [
  // ---- 认证 ----
  {
    method: 'post',
    path: /^\/auth\/login$/,
    async handler(config) {
      const { phone, password, username } = parseBody(config)
      const account = phone || username
      if (!account || !HEX_64.test(password || '')) {
        return fail('INVALID_PARAMETER', '账号或密码格式不正确', 400)
      }
      if (!db.passwordHash) db.passwordHash = await sha256Hex(DEMO_PASSWORD)
      const okAccount = account === 'admin' || account === db.user.phone
      if (okAccount && password === db.passwordHash) {
        return success(issueTokens(db.user.id))
      }
      return fail('INVALID_PARAMETER', '账号或密码错误', 400)
    },
  },
  {
    method: 'post',
    path: /^\/auth\/refresh$/,
    handler(config) {
      const { refresh_token } = parseBody(config)
      const userId = db.refreshTokens[refresh_token]
      if (!userId) return fail('UNAUTHORIZED', '登录已过期，请重新登录', 401)
      delete db.refreshTokens[refresh_token]
      return success(issueTokens(userId))
    },
  },
  {
    method: 'post',
    path: /^\/auth\/logout$/,
    handler(config) {
      const auth = (config.headers && (config.headers.Authorization || config.headers.authorization)) || ''
      delete db.accessTokens[auth.replace(/^Bearer\s+/i, '')]
      return success({ logout: true })
    },
  },
  {
    method: 'get',
    path: /^\/auth\/me$/,
    handler() {
      return success(publicUser(db.user))
    },
  },

  // ---- 字典 ----
  {
    method: 'get',
    path: /^\/dicts\/([a-z_]+)$/,
    handler(config, match) {
      const type = match[1]
      if (!dicts[type]) return fail('NOT_FOUND', '字典类型不存在', 404)
      return success(dicts[type])
    },
  },

  // ---- 档案（管理端列表 / 详情） ----
  {
    method: 'get',
    path: /^\/archives$/,
    handler(config) {
      const qs = parseQuery(config)
      let list = [...db.archives]
      const keyword = (qs.get('keyword') || '').trim()
      const collegeId = qs.get('college_id')
      const gradYear = qs.get('grad_year')
      const category = qs.get('category')
      const status = qs.get('status')
      if (keyword) {
        list = list.filter(
          (a) => a.student_id.includes(keyword) || a.owner_name.includes(keyword) || a.title.includes(keyword),
        )
      }
      if (collegeId) list = list.filter((a) => String(a.college_id) === collegeId)
      if (gradYear) list = list.filter((a) => String(a.grad_year) === gradYear)
      if (category) list = list.filter((a) => a.category === category)
      if (status) list = list.filter((a) => a.status === status)

      const page = Number(qs.get('page') || 1)
      const pageSize = Number(qs.get('page_size') || 20)
      return success({
        list: list.slice((page - 1) * pageSize, page * pageSize),
        total: list.length,
        page,
        page_size: pageSize,
      })
    },
  },
  {
    method: 'get',
    path: /^\/archives\/(\d+)$/,
    handler(config, match) {
      const item = archiveOf(match[1])
      if (!item) return fail('NOT_FOUND', '档案不存在', 404)
      return success({ ...item, college: collegeLabel(item.college_id) })
    },
  },

  // ---- 审核 Reviews（API 规范第七章） ----
  {
    method: 'get',
    path: /^\/reviews$/,
    handler(config) {
      const qs = parseQuery(config)
      let list = [...db.reviews]
      const stage = qs.get('stage')
      const status = qs.get('status')
      const bizType = qs.get('biz_type')
      if (stage) list = list.filter((r) => r.stage === stage)
      if (status) list = list.filter((r) => r.status === status)
      if (bizType) list = list.filter((r) => r.biz_type === bizType)

      // 联查业务标题与提交人，便于列表展示
      const enriched = list.map((r) => {
        const archive = r.biz_type === 'archive' ? archiveOf(r.biz_id) : null
        return {
          ...r,
          biz_title: archive ? archive.title : r.biz_type === 'activate_manual' ? `人工核验申请 #${r.biz_id}` : '-',
          owner_name: archive ? archive.owner_name : '-',
          student_id: archive ? archive.student_id : '-',
          college: archive ? collegeLabel(archive.college_id) : '-',
        }
      })

      const page = Number(qs.get('page') || 1)
      const pageSize = Number(qs.get('page_size') || 20)
      return success({
        list: enriched.slice((page - 1) * pageSize, page * pageSize),
        total: enriched.length,
        page,
        page_size: pageSize,
      })
    },
  },
  {
    method: 'get',
    path: /^\/reviews\/(\d+)\/logs$/,
    handler(config, match) {
      const id = Number(match[1])
      const review = db.reviews.find((r) => r.id === id)
      if (!review) return fail('NOT_FOUND', '审核记录不存在', 404)
      const logs = db.reviewLogs[id] || []
      return success({ list: logs })
    },
  },
  {
    method: 'post',
    path: /^\/reviews\/(\d+)\/approve$/,
    handler(config, match) {
      const id = Number(match[1])
      const review = db.reviews.find((r) => r.id === id)
      if (!review) return fail('NOT_FOUND', '审核记录不存在', 404)
      if (review.status !== 'pending') return fail('INVALID_STATE', '当前状态不允许该操作', 409)

      review.status = 'approved'
      review.reviewer_id = db.user.id
      review.updated_at = new Date().toISOString()
      pushLog(id, '审核通过', '')

      const archive = archiveOf(review.biz_id)
      if (archive) {
        if (review.stage === 'first') {
          // 初审通过 → 生成复核待办，档案进入待复核
          archive.status = 'pending_second'
          db.reviews.push({
            id: Math.max(...db.reviews.map((r) => r.id)) + 1,
            biz_type: review.biz_type,
            biz_id: review.biz_id,
            stage: 'second',
            status: 'pending',
            reviewer_id: null,
            reason: null,
            created_at: new Date().toISOString(),
            updated_at: new Date().toISOString(),
          })
        } else {
          // 复核通过 → 业务状态置 approved，入库
          archive.status = 'approved'
        }
      }
      return success(review)
    },
  },
  {
    method: 'post',
    path: /^\/reviews\/(\d+)\/reject$/,
    handler(config, match) {
      const id = Number(match[1])
      const review = db.reviews.find((r) => r.id === id)
      if (!review) return fail('NOT_FOUND', '审核记录不存在', 404)
      if (review.status !== 'pending') return fail('INVALID_STATE', '当前状态不允许该操作', 409)
      const { reason } = parseBody(config)
      if (!reason || !reason.trim()) return fail('INVALID_PARAMETER', '驳回必须填写原因', 400)

      review.status = 'rejected'
      review.reviewer_id = db.user.id
      review.reason = reason.trim()
      review.updated_at = new Date().toISOString()
      pushLog(id, '审核驳回', review.reason)

      const archive = archiveOf(review.biz_id)
      if (archive) {
        archive.status = 'rejected'
        archive.reject_reason = review.reason
      }
      return success(review)
    },
  },
  // ---- 通知中心（M14）----
  {
    method: 'get',
    path: /^\/notifications$/,
    async handler(config) {
      const q = parseQuery(config)
      const type = q.get('type') || ''
      const unread = q.get('unread') === 'true'
      const page = Number(q.get('page') || 1)
      const pageSize = Number(q.get('page_size') || 20)
      let list = db.notifications.slice()
      if (type) list = list.filter((n) => n.type === type)
      if (unread) list = list.filter((n) => !n.read)
      const total = list.length
      const start = (page - 1) * pageSize
      const paged = list.slice(start, start + pageSize)
      return success({ list: paged, total, page, pageSize })
    },
  },
  {
    method: 'get',
    path: /^\/notifications\/unread-count$/,
    async handler() {
      const total = db.notifications.filter((n) => !n.read).length
      const byType = {}
      db.notifications.forEach((n) => {
        if (!n.read) byType[n.type] = (byType[n.type] || 0) + 1
      })
      return success({ total, by_type: byType })
    },
  },
  {
    method: 'post',
    path: /^\/notifications\/\d+\/read$/,
    async handler(config) {
      const id = Number((config.url.match(/\/notifications\/(\d+)\/read/) || [])[1])
      const n = db.notifications.find((x) => x.id === id)
      if (n) n.read = true
      return success({ id, read: true })
    },
  },
  {
    method: 'post',
    path: /^\/notifications\/read-all$/,
    async handler(config) {
      const q = parseQuery(config)
      const type = q.get('type') || ''
      let n = 0
      db.notifications.forEach((x) => {
        if (x.read) return
        if (type && x.type !== type) return
        x.read = true
        n += 1
      })
      return success({ updated: n })
    },
  },
  {
    method: 'get',
    path: /^\/notification-subscriptions$/,
    async handler() {
      return success({ categories: db.subscriptions.slice() })
    },
  },
  {
    method: 'put',
    path: /^\/notification-subscriptions$/,
    async handler(config) {
      const { categories } = parseBody(config)
      db.subscriptions = Array.isArray(categories) ? categories.slice() : []
      return success({ categories: db.subscriptions })
    },
  },
  // ---- 审计日志（M16）----
  {
    method: 'get',
    path: /^\/audit-logs$/,
    async handler(config) {
      const q = parseQuery(config)
      const userId = q.get('user_id')
      const action = q.get('action') || ''
      const bizType = q.get('biz_type') || ''
      const dateFrom = q.get('date_from') || ''
      const dateTo = q.get('date_to') || ''
      const page = Number(q.get('page') || 1)
      const pageSize = Number(q.get('page_size') || 20)
      let list = db.auditLogs.slice()
      if (userId) list = list.filter((x) => String(x.userId) === String(userId))
      if (action) list = list.filter((x) => x.action === action)
      if (bizType) list = list.filter((x) => x.bizType === bizType)
      if (dateFrom) list = list.filter((x) => x.createdAt >= dateFrom)
      if (dateTo) list = list.filter((x) => x.createdAt <= dateTo)
      const total = list.length
      const start = (page - 1) * pageSize
      const paged = list.slice(start, start + pageSize)
      return success({ list: paged, total, page, pageSize })
    },
  },
  {
    method: 'get',
    path: /^\/audit-logs\/actions$/,
    async handler() {
      return success({ actions: db.auditActions.slice() })
    },
  },
  {
    method: 'get',
    path: /^\/audit-logs\/export$/,
    async handler() {
      // Mock 直接返回空 xlsx 占位（前端会触发下载）
      return success({
        __blob__: true,
        filename: 'audit-logs.xlsx',
      })
    },
  },
  // ---- 账号停权 / 恢复 / 交接 / 变更日志（M15）----
  {
    method: 'post',
    path: /^\/admin\/users\/\d+\/suspend$/,
    async handler(config) {
      const id = Number((config.url.match(/\/admin\/users\/(\d+)\/suspend/) || [])[1])
      const { reason } = parseBody(config)
      if (!reason) return fail('INVALID_PARAMETER', '停权原因必填', 400)
      pushChangeLog(id, 'suspend', { reason })
      return success(null)
    },
  },
  {
    method: 'post',
    path: /^\/admin\/users\/\d+\/resume$/,
    async handler(config) {
      const id = Number((config.url.match(/\/admin\/users\/(\d+)\/resume/) || [])[1])
      pushChangeLog(id, 'resume', {})
      return success(null)
    },
  },
  {
    method: 'post',
    path: /^\/admin\/users\/\d+\/handover$/,
    async handler(config) {
      const id = Number((config.url.match(/\/admin\/users\/(\d+)\/handover/) || [])[1])
      const { target_user_id, reason } = parseBody(config)
      if (!target_user_id) return fail('INVALID_PARAMETER', 'target_user_id 必填', 400)
      if (!reason) return fail('INVALID_PARAMETER', '交接原因必填', 400)
      pushChangeLog(id, 'handover', { from: id, to: target_user_id, reason })
      return success({ transferred_reviews: 3, origin_status: 'suspended' })
    },
  },
  {
    method: 'get',
    path: /^\/admin\/users\/\d+\/change-logs$/,
    async handler(config) {
      const id = Number((config.url.match(/\/admin\/users\/(\d+)\/change-logs/) || [])[1])
      const q = parseQuery(config)
      const page = Number(q.get('page') || 1)
      const pageSize = Number(q.get('page_size') || 20)
      const list = (db.changeLogs[id] || []).slice()
      const total = list.length
      const start = (page - 1) * pageSize
      const paged = list.slice(start, start + pageSize)
      return success({ list: paged, total, page, pageSize })
    },
  },
  // ---- 辅导员管理（M34）----
  {
    method: 'get',
    path: /^\/counselors$/,
    async handler(config) {
      const q = parseQuery(config)
      const collegeId = q.get('college_id')
      const keyword = q.get('keyword') || ''
      const status = q.get('status') || ''
      const page = Number(q.get('page') || 1)
      const pageSize = Number(q.get('page_size') || 20)
      let list = db.counselors.slice()
      if (collegeId) list = list.filter((c) => String(c.collegeId) === String(collegeId))
      if (keyword) list = list.filter((c) => (c.realName || '').includes(keyword) || (c.phone || '').includes(keyword))
      if (status) list = list.filter((c) => c.status === status)
      const total = list.length
      const start = (page - 1) * pageSize
      const paged = list.slice(start, start + pageSize)
      return success({ list: paged, total, page, pageSize })
    },
  },
  {
    method: 'post',
    path: /^\/counselors$/,
    async handler(config) {
      const { real_name, phone, college_id, scope } = parseBody(config)
      if (!real_name || !phone || !college_id) {
        return fail('INVALID_PARAMETER', '姓名/手机号/学院必填', 400)
      }
      const id = (db.counselors[db.counselors.length - 1]?.id || 100) + 1
      const item = {
        id,
        realName: real_name,
        phone,
        collegeId: college_id,
        college: dicts.college.find((c) => c.id === Number(college_id))?.label || '-',
        role: 'counselor',
        status: 'active',
        createdAt: new Date().toISOString(),
      }
      db.counselors.push(item)
      return success({ ...item, scope: scope || { gradYears: [], majorIds: [], studentIds: [] } }, 201)
    },
  },
  {
    method: 'get',
    path: /^\/counselors\/\d+$/,
    async handler(config) {
      const id = Number((config.url.match(/\/counselors\/(\d+)/) || [])[1])
      const c = db.counselors.find((x) => x.id === id)
      if (!c) return fail('NOT_FOUND', '导员不存在', 404)
      const scope = db.counselorScopes[id] || { gradYears: [], majorIds: [], studentIds: [] }
      return success({ ...c, scope })
    },
  },
  {
    method: 'patch',
    path: /^\/counselors\/\d+$/,
    async handler(config) {
      const id = Number((config.url.match(/\/counselors\/(\d+)/) || [])[1])
      const c = db.counselors.find((x) => x.id === id)
      if (!c) return fail('NOT_FOUND', '导员不存在', 404)
      const body = parseBody(config)
      if (body.real_name !== undefined) c.realName = body.real_name
      if (body.phone !== undefined) c.phone = body.phone
      if (body.college_id !== undefined) {
        c.collegeId = body.college_id
        c.college = dicts.college.find((x) => x.id === Number(body.college_id))?.label || '-'
      }
      if (body.scope !== undefined) db.counselorScopes[id] = body.scope
      const scope = db.counselorScopes[id] || { gradYears: [], majorIds: [], studentIds: [] }
      return success({ ...c, scope })
    },
  },
  {
    method: 'post',
    path: /^\/counselors\/\d+\/disable$/,
    async handler(config) {
      const id = Number((config.url.match(/\/counselors\/(\d+)\/disable/) || [])[1])
      const c = db.counselors.find((x) => x.id === id)
      if (!c) return fail('NOT_FOUND', '导员不存在', 404)
      c.status = 'disabled'
      return success({ id, status: 'disabled' })
    },
  },
  {
    method: 'post',
    path: /^\/counselors\/\d+\/enable$/,
    async handler(config) {
      const id = Number((config.url.match(/\/counselors\/(\d+)\/enable/) || [])[1])
      const c = db.counselors.find((x) => x.id === id)
      if (!c) return fail('NOT_FOUND', '导员不存在', 404)
      c.status = 'active'
      return success({ id, status: 'active' })
    },
  },
  {
    method: 'post',
    path: /^\/counselors\/\d+\/password\/reset$/,
    async handler(config) {
      const id = Number((config.url.match(/\/counselors\/(\d+)\/password\/reset/) || [])[1])
      const { new_password } = parseBody(config)
      if (!new_password) return fail('INVALID_PARAMETER', 'new_password 必填', 400)
      const c = db.counselors.find((x) => x.id === id)
      if (!c) return fail('NOT_FOUND', '导员不存在', 404)
      return success({ id, revoked: true })
    },
  },
  // ---- 留言墙置顶 / 禁言（预留接口，未实现）----
  {
    method: 'put',
    path: /^\/admin\/wall\/messages\/\d+\/pin$/,
    async handler() {
      return fail('NOT_IMPLEMENTED', '置顶接口待后端升级', 501)
    },
  },
  {
    method: 'post',
    path: /^\/admin\/wall\/users\/\d+\/mute$/,
    async handler() {
      return fail('NOT_IMPLEMENTED', '禁言接口待后端升级', 501)
    },
  },
  // ---- 授权协议（M16）----
  {
    method: 'get',
    path: /^\/consents\/text$/,
    async handler() {
      return success({
        version: '2026-09-01',
        body: '校友档案展示授权协议（示例文本）\n\n1. 授权范围：校友通讯录 / 大屏风采展示\n2. 授权期限：自授权日起，至用户撤回为止\n3. 撤回方式：校友可在「我的资料 → 我的授权」页面随时撤回',
        updated_at: '2026-09-01T00:00:00Z',
      })
    },
  },
  {
    method: 'get',
    path: /^\/consents\/my$/,
    async handler() {
      return success({
        list: [
          { id: 1, scope: 'screen', archive_id: 12, archive_title: '全国大学生程序设计竞赛金奖', created_at: new Date(Date.now() - 30 * 86_400_000).toISOString(), revoked_at: null },
          { id: 2, scope: 'directory', archive_id: 12, archive_title: '全国大学生程序设计竞赛金奖', created_at: new Date(Date.now() - 60 * 86_400_000).toISOString(), revoked_at: null },
        ],
      })
    },
  },
  // ---- 标签管理（M13）----
  {
    method: 'get',
    path: /^\/tags$/,
    async handler(config) {
      const q = parseQuery(config)
      const group = q.get('group') || ''
      const keyword = q.get('keyword') || ''
      const page = Number(q.get('page') || 1)
      const pageSize = Number(q.get('page_size') || 20)
      let list = db.tags.slice()
      if (group) list = list.filter((t) => t.group === group)
      if (keyword) list = list.filter((t) => (t.name || '').includes(keyword))
      const total = list.length
      const start = (page - 1) * pageSize
      const paged = list.slice(start, start + pageSize)
      return success({ list: paged, total, page, pageSize })
    },
  },
  {
    method: 'post',
    path: /^\/tags$/,
    async handler(config) {
      const { name, group, sort } = parseBody(config)
      if (!name || !group) return fail('INVALID_PARAMETER', 'name/group 必填', 400)
      if (db.tags.some((t) => t.group === group && t.name === name)) {
        return fail('TAG_DUPLICATED', '同分组内标签名已存在', 409)
      }
      const id = (db.tags[db.tags.length - 1]?.id || 0) + 1
      const item = {
        id,
        name,
        group,
        sort: sort ?? 100,
        createdBy: db.user.id,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }
      db.tags.push(item)
      return success(item, 201)
    },
  },
  {
    method: 'patch',
    path: /^\/tags\/\d+$/,
    async handler(config) {
      const id = Number((config.url.match(/\/tags\/(\d+)/) || [])[1])
      const t = db.tags.find((x) => x.id === id)
      if (!t) return fail('NOT_FOUND', '标签不存在', 404)
      const body = parseBody(config)
      if (body.name !== undefined) t.name = body.name
      if (body.sort !== undefined) t.sort = body.sort
      t.updatedAt = new Date().toISOString()
      return success(t)
    },
  },
  {
    method: 'delete',
    path: /^\/tags\/\d+$/,
    async handler(config) {
      const id = Number((config.url.match(/\/tags\/(\d+)/) || [])[1])
      const i = db.tags.findIndex((x) => x.id === id)
      if (i < 0) return fail('NOT_FOUND', '标签不存在', 404)
      // 模拟"被引用"：mock 全部允许删除
      db.tags.splice(i, 1)
      return success({ id, deleted: true })
    },
  },
  {
    method: 'put',
    path: /^\/tags\/bindings\/.+\/\d+$/,
    async handler(config) {
      const m = config.url.match(/\/tags\/bindings\/([^/]+)\/(\d+)/)
      const bizType = m ? m[1] : ''
      const bizId = m ? Number(m[2]) : 0
      const { tag_ids } = parseBody(config)
      if (!db.tagBindings[bizType]) db.tagBindings[bizType] = {}
      db.tagBindings[bizType][bizId] = Array.isArray(tag_ids) ? tag_ids.slice() : []
      return success(db.tags.filter((t) => db.tagBindings[bizType][bizId].includes(t.id)))
    },
  },
  {
    method: 'get',
    path: /^\/tags\/bindings\/.+\/\d+$/,
    async handler(config) {
      const m = config.url.match(/\/tags\/bindings\/([^/]+)\/(\d+)/)
      const bizType = m ? m[1] : ''
      const bizId = m ? Number(m[2]) : 0
      const ids = db.tagBindings[bizType]?.[bizId] || []
      return success(db.tags.filter((t) => ids.includes(t.id)))
    },
  },
  // ---- 草稿（按登录用户过滤）----
  {
    method: 'get',
    path: /^\/drafts\/my$/,
    async handler(config) {
      const q = parseQuery(config)
      const bizType = q.get('biz_type') || ''
      let list = db.drafts.filter((d) => d.userId === db.user.id)
      if (bizType) list = list.filter((d) => d.bizType === bizType)
      // updated_at 倒序
      list = list.slice().sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt))
      return success({ list })
    },
  },
  {
    method: 'delete',
    path: /^\/drafts\/\d+$/,
    async handler(config) {
      const id = Number((config.url.match(/\/drafts\/(\d+)/) || [])[1])
      const i = db.drafts.findIndex((d) => d.id === id)
      if (i < 0) return fail('NOT_FOUND', '草稿不存在', 404)
      if (db.drafts[i].userId !== db.user.id) return fail('FORBIDDEN', '仅本人可删除', 403)
      db.drafts.splice(i, 1)
      return success({ id, deleted: true })
    },
  },
]

function pushLog(reviewId, action, reason) {
  if (!db.reviewLogs[reviewId]) db.reviewLogs[reviewId] = []
  db.reviewLogs[reviewId].push({
    reviewer: db.user.nickname,
    action,
    reason,
    at: new Date().toISOString(),
  })
}

let changeLogId = 9000
function pushChangeLog(userId, action, detail) {
  if (!db.changeLogs) db.changeLogs = {}
  if (!db.changeLogs[userId]) db.changeLogs[userId] = []
  db.changeLogs[userId].unshift({
    id: ++changeLogId,
    action,
    operatorId: db.user.id,
    detail,
    created_at: new Date().toISOString(),
  })
}

function delay(ms = 250) {
  return new Promise((r) => setTimeout(r, ms))
}

/** axios 自定义适配器：拦截所有请求并按路由表返回 mock 响应 */
export default async function mockAdapter(config) {
  await delay()
  const url = (config.url || '').split('?')[0]
  const method = (config.method || 'get').toLowerCase()

  for (const route of routes) {
    if (route.method !== method) continue
    const match = url.match(route.path)
    if (!match) continue
    try {
      const result = await route.handler(config, match)
      // 标记 __blob__：跳过 envelope，返回真实二进制
      if (result.data && result.data.__blob__) {
        const blob = new Blob(['mock-export'], { type: 'application/octet-stream' })
        return { data: blob, status: result.status, statusText: 'OK', headers: {}, config }
      }
      return { data: result.data, status: result.status, statusText: 'OK', headers: {}, config }
    } catch (e) {
      return Promise.reject({
        message: e.message,
        config,
        response: { data: envelope(null, 'INTERNAL_ERROR', e.message), status: 500, headers: {}, config },
      })
    }
  }

  const res = fail('NOT_FOUND', `mock 未实现的接口: ${method.toUpperCase()} ${url}`, 404)
  return Promise.reject({
    message: res.data.message,
    config,
    response: { data: res.data, status: 404, headers: {}, config },
  })
}
