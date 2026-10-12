/**
 * 校友照片收集 API（photo 模块）
 * - adminList：审核列表（status 筛选）
 * - review：approve/reject
 * - push：定向推送（target_type: all_users/college/role/single_user）
 * - pushLogs：推送日志
 */
import http from './request'

export function listAdminPhotos(params) {
  return http.get('/admin/photos', { params })
}

export function reviewPhoto(id, body) {
  return http.post(`/admin/photos/${id}/review`, body)
}

export function pushPhoto(id, body) {
  return http.post(`/admin/photos/${id}/push`, body)
}

export function listPhotoPushLogs(params) {
  return http.get('/admin/photos/push-logs', { params })
}