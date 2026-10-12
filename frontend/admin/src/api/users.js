/**
 * 人员管理（管理端用户列表）：GET /admin/users?role=&keyword=&page=&page_size=
 * 后端：auth 模块 AdminUserController（school_admin / system_admin）；
 * 出参含学籍信息（real_name/college/major/grad_year），手机号为脱敏形式。
 */
import http from './request'

export function getUsers(params) {
  return http.get('/admin/users', { params })
}

/** 单个新增账号（student/alumni；初始密码为默认密码，出参 initial_password 告知；院级锁本院、导员锁管辖范围） */
export function createUser(payload) {
  return http.post('/admin/users', payload)
}

/** 编辑用户信息（字段缺省 = 不修改；学号/身份证不可改；手机号换绑须全平台唯一） */
export function updateUser(id, payload) {
  return http.patch(`/admin/users/${id}`, payload)
}

/** 重置密码（newPassword 为前端 SHA-256 散列值；重置后该用户全部登录态作废） */
export function resetUserPassword(id, newPassword) {
  return http.post(`/admin/users/${id}/reset-password`, { new_password: newPassword })
}

/** 停权（reason 必填；不可停权自己；token 立刻作废） */
export function suspendUser(id, reason) {
  return http.post(`/admin/users/${id}/suspend`, { reason })
}

/** 恢复 */
export function resumeUser(id) {
  return http.post(`/admin/users/${id}/resume`)
}

/** 交接（原账号停权，范围与在途待办转移；targetUserId + reason） */
export function handoverUser(id, targetUserId, reason) {
  return http.post(`/admin/users/${id}/handover`, { target_user_id: targetUserId, reason })
}

/** 变更留痕（created_at 倒序） */
export function getUserChangeLogs(id, params = {}) {
  return http.get(`/admin/users/${id}/change-logs`, { params })
}

/** 批量重置密码（仅 system_admin；body: { userIds }，后端自动用默认密码） */
export function batchResetUserPassword(userIds) {
  return http.post('/admin/users/batch-reset-password', {
    userIds,
  })
}
