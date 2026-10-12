/** 展示格式化工具 */

/** ISO 8601 UTC 时间 → 本地时区显示（API 规范 1.5） */
export function formatTime(iso) {
  if (!iso) return '-'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '-'
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 文件大小友好显示 */
export function formatSize(bytes) {
  if (bytes == null || Number.isNaN(bytes)) return '-'
  if (bytes < 1024) return `${bytes}B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(bytes < 10240 ? 1 : 0)}KB`
  return `${(bytes / 1024 / 1024).toFixed(2)}MB`
}

export const ARCHIVE_STATUS = {
  draft: { text: '草稿', type: 'info' },
  pending_first: { text: '待初审', type: 'warning' },
  pending_second: { text: '待复核', type: 'primary' },
  approved: { text: '已通过', type: 'success' },
  rejected: { text: '已驳回', type: 'danger' },
  deleting: { text: '删除审核中', type: 'info' },
  deleted: { text: '已删除', type: 'info' },
}

export function statusOf(status) {
  return ARCHIVE_STATUS[status] || { text: status || '-', type: 'info' }
}

/** 审核业务类型（API 规范第七章） */
export const BIZ_TYPE_TEXT = {
  archive: '档案审核',
  honor: '荣誉审核',
  archive_delete: '删除审核',
  activate_manual: '激活人工核验',
}

/** 审核层级 */
export const STAGE_TEXT = {
  counselor: '初审（导员）',
  first: '初审（院系）',
  second: '终审（校级）',
}

/** 审核状态（type 供 el-tag，tone 供自定义 badge 配色） */
export const REVIEW_STATUS = {
  pending: { text: '待审核', type: 'warning', tone: 'warning' },
  approved: { text: '已通过', type: 'success', tone: 'success' },
  rejected: { text: '已驳回', type: 'danger', tone: 'danger' },
}

export function reviewStatusOf(status) {
  return REVIEW_STATUS[status] || { text: status || '-', type: 'info', tone: 'info' }
}

/** 档案类别 */
export const CATEGORY_TEXT = {
  award: '获奖经历',
  certificate: '证书资质',
  experience: '实践经历',
  career: '就业履历',
  other: '其他资料',
}
