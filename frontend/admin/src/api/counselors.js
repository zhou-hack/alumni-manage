/**
 * 导员管理接口（M34 · 仅 school_admin）
 * - GET    /counselors                       ?college_id=&keyword=&status=&page=&page_size=
 * - POST   /counselors                       创建（phone/real_name/college_id/password/scope）
 * - GET    /counselors/{id}                  详情（含 scope）
 * - PATCH  /counselors/{id}                  修改（字段可选，scope 给了即全量替换）
 * - POST   /counselors/{id}/disable          停用（在途待办回退至院系初审）
 * - POST   /counselors/{id}/enable           启用
 * - POST   /counselors/{id}/password/reset   重置密码（newPassword 为前端 SHA-256 散列值）
 */
import http from './request'

export const COUNSELOR_STATUS = {
  active: { text: '在岗', type: 'success' },
  disabled: { text: '已停用', type: 'info' },
}

export function statusOf(s) {
  return COUNSELOR_STATUS[s] || { text: s || '-', type: 'info' }
}

/** 列表 */
export function getCounselors(params = {}) {
  return http.get('/counselors', { params })
}

/** 创建 */
export function createCounselor(payload) {
  return http.post('/counselors', payload)
}

/** 详情 */
export function getCounselor(id) {
  return http.get(`/counselors/${id}`)
}

/** 修改 */
export function updateCounselor(id, payload) {
  return http.patch(`/counselors/${id}`, payload)
}

/** 停用 */
export function disableCounselor(id) {
  return http.post(`/counselors/${id}/disable`)
}

/** 启用 */
export function enableCounselor(id) {
  return http.post(`/counselors/${id}/enable`)
}

/** 重置密码 */
export function resetCounselorPassword(id, newPassword) {
  return http.post(`/counselors/${id}/password/reset`, { new_password: newPassword })
}