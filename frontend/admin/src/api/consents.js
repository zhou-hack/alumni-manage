/** 授权模块（PRD M07：大屏展示授权，Day 2 闭环：approved + consent 才可上架） */
import http from './request'

/** 发起授权（scope：screen 大屏展示） */
export function createConsent(archiveId, scope = 'screen') {
  return http.post('/consents', { archive_id: archiveId, scope })
}

/** 我的授权记录 */
export function getMyConsents() {
  return http.get('/consents/my')
}

/** 撤回授权（撤回后档案自动下架） */
export function revokeConsent(id) {
  return http.delete(`/consents/${id}`)
}

/** 当前生效的授权文本（含 version + 全文，前端授权弹窗用） */
export function getConsentText() {
  return http.get('/consents/text')
}
