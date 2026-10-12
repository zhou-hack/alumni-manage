/** 荣誉模块（API 规范第五章）· 管理端 + 校友侧共用 */
import http from './request'

/** 荣誉级别（API 规范 5.1） */
export const HONOR_LEVELS = {
  national: '国家级',
  provincial: '省级',
  city: '市级',
  school: '校级',
}

/** 荣誉检索列表（按角色自动过滤数据范围；校友端自动过滤本人） */
export function getHonors(params = {}) {
  return http.get('/honors', { params })
}

/** 荣誉详情 */
export function getHonor(id) {
  return http.get(`/honors/${id}`)
}

// ==================== 校友侧（PRD M03） ====================

/**
 * 创建荣誉（提交后进入初审）
 * @param {Object} payload { title, category, level, awarded_at, granted_by?, archive_id?, attachment_ids }
 */
export function createHonor(payload) {
  return http.post('/honors', payload)
}

/** 修改荣誉（仅 pending_first 之前可改，本人） */
export function updateHonor(id, payload) {
  return http.patch(`/honors/${id}`, payload)
}

// ==================== 荣誉证书解析草稿（[M03 扩展 2026-09-25]） ====================

/** 创建草稿（人工录入；阶段二 OCR 自动填充 parsed_json 后复用同一确认流） */
export function createHonorDraft(payload) {
  return http.post('/honor-drafts', payload)
}

/** 草稿详情（含实时成员匹配预览） */
export function getHonorDraft(id) {
  return http.get(`/honor-drafts/${id}`)
}

/** 人工修正草稿字段 */
export function updateHonorDraft(id, payload) {
  return http.patch(`/honor-drafts/${id}`, payload)
}

/** 实时成员匹配预览（不落库，确认页每次编辑成员名单后调用） */
export function previewDraftMembers(id, memberNames) {
  return http.post(`/honor-drafts/${id}/preview-members`, { member_names: memberNames })
}

/** 草稿 → 正式荣誉（团队生成公示行+个人行） */
export function confirmHonorDraft(id, payload) {
  return http.post(`/honor-drafts/${id}/confirm`, payload)
}

/** 丢弃草稿 */
export function discardHonorDraft(id) {
  return http.delete(`/honor-drafts/${id}`)
}

// ==================== [P1/M17] 荣誉大屏上下架 ====================

/** 单条上下架 */
export function setHonorScreenStatus(id, payload) {
  return http.post(`/admin/honors/${id}/screen-status`, payload)
}

/** 批量上下架（payload = { ids:number[], status:'online'|'offline', reason?:string }） */
export function batchHonorScreenStatus(payload) {
  return http.post('/admin/honors/screen-status:batch', payload)
}

/** 上下架变更日志分页 */
export function listHonorScreenLogs(params = {}) {
  return http.get('/admin/honors/screen-logs', { params })
}

// ==================== [P1/M03] 团队荣誉 ====================

/** 团队荣誉详情（按 team_id） */
export function getHonorTeam(teamId) {
  return http.get(`/honors/team/${teamId}`)
}

// ==================== 校级荣誉管理（/admin/honors · 仅 school_admin / system_admin） ====================

/** 校级荣誉列表（含学院隔离、level、has_red_doc 等筛选） */
export function getAdminHonors(params = {}) {
  return http.get('/admin/honors', { params })
}

/** 导出荣誉为 xlsx（流式） */
export function exportAdminHonors(params = {}) {
  return http.get('/admin/honors/export', { params, responseType: 'blob' })
}

/** 下载导入模板（xlsx） */
export function downloadAdminHonorTemplate() {
  return http.get('/admin/honors/import/template', { responseType: 'blob' })
}

/** 上传导入（multipart file）→ { total, success, failed, errors: [{row, student_id, reason}] } */
export function importAdminHonors(file) {
  const form = new FormData()
  form.append('file', file)
  return http.post('/admin/honors/import', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
