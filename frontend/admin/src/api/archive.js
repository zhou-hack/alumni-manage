/** 档案模块（API 规范第四章）· 管理端 + 校友侧共用 */
import http from './request'

/**
 * 管理端档案列表（组合筛选）
 * 筛选参数：keyword（学号/姓名）、college_id、grad_year、category、status、page、page_size
 */
export function getArchives(params) {
  return http.get('/archives', { params })
}

/**
 * 按人聚合（院系 → 专业 → 个人 层层递进）
 * level=college：院级卡片；level=major + college：专业卡片；level=person + college + major：个人卡片
 * 数据范围与列表同口径（院级锁本院、导员锁管辖范围）
 */
export function getArchiveAgg(params) {
  return http.get('/archives/agg', { params })
}

/** 档案详情 */
export function getArchive(id) {
  return http.get(`/archives/${id}`)
}

// ==================== 校友侧（PRD M02：PC 端面向所有角色全量功能） ====================

/** 资料类别（前端展示层字典，档案 category 自由扩展） */
export const ARCHIVE_CATEGORIES = [
  { label: '获奖经历', value: 'award' },
  { label: '证书资质', value: 'certificate' },
  { label: '实践经历', value: 'experience' },
  { label: '就业履历', value: 'career' },
  { label: '其他资料', value: 'other' },
]

/**
 * 创建档案（校友）
 * @param {Object} payload { title, category, content, remark, attachment_ids, submit }
 *   submit=false 存草稿；submit=true 进入 pending_first 待初审
 */
export function createArchive(payload) {
  return http.post('/archives', payload)
}

/** 我的提交记录（含状态与驳回原因，校友本人） */
export function getMyArchives(params = {}) {
  return http.get('/archives/my', { params })
}

/** 修改档案（仅 draft / rejected 状态可改，本人） */
export function updateArchive(id, payload) {
  return http.patch(`/archives/${id}`, payload)
}

/** 驳回重提：生成 version+1 新版本（M04-03） */
export function resubmitArchive(id) {
  return http.post(`/archives/${id}/resubmit`)
}

/** 删除申请（M02，非立即删除）：档案进入 deleting 待审核，重复发起 409 */
export function deleteRequestArchive(id) {
  return http.post(`/archives/${id}/delete-request`)
}
