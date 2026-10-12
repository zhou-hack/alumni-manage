/**
 * 校友大屏展示字段（V18）· api/alumniProfile.js
 *
 * 端点：
 *   GET   /api/v1/alumni/{id}/profile        公开读
 *   GET   /api/v1/admin/alumni/{id}/profile 校级/系统读
 *   PATCH /api/v1/admin/alumni/{id}/profile 校级/系统写
 *   GET   /api/v1/me/profile                校友本人读
 *   PATCH /api/v1/me/profile                校友本人写
 *   GET   /api/v1/honors/{id}/alumni-base-id 从荣誉 ID 反查校友库 ID（管理端风采入口）
 */
import http from './request'

/** 从 honor.id 反查 alumni_base_id（管理端 / 校友端均可调） */
export function getAlumniBaseIdByHonor(honorId) {
  return http.get(`/honors/${honorId}/alumni-base-id`)
}

/** 公开读（无需登录） */
export function getPublicProfile(id) {
  return http.get(`/alumni/${id}/profile`)
}

/** 校级/系统管理员读（带 token） */
export function getAdminProfile(id) {
  return http.get(`/admin/alumni/${id}/profile`)
}

/** 校级/系统管理员写 */
export function updateAdminProfile(id, payload) {
  return http.patch(`/admin/alumni/${id}/profile`, payload)
}

/** 校友本人读（带 token，service 自动取当前账号 alumni_base_id） */
export function getMyProfile() {
  return http.get('/me/profile')
}

/** 校友本人写 */
export function updateMyProfile(payload) {
  return http.patch('/me/profile', payload)
}
