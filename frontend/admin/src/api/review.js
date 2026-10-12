/** 审核模块（API 规范第七章） */
import http from './request'

/** 待办列表（筛选：stage / status / biz_type） */
export function getReviews(params) {
  return http.get('/reviews', { params })
}

/** 审核详情 */
export function getReview(id) {
  return http.get(`/reviews/${id}`)
}

/** 通过 */
export function approveReview(id) {
  return http.post(`/reviews/${id}/approve`)
}

/** 驳回（reason 必填） */
export function rejectReview(id, reason) {
  return http.post(`/reviews/${id}/reject`, { reason })
}

/** 审核日志 */
export function getReviewLogs(id) {
  return http.get(`/reviews/${id}/logs`)
}
