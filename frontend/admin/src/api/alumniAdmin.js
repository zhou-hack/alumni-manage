/**
 * 门户运营模块（优秀校友 / 校友档案索引 / 校友留言墙）
 * 对应开发文档：《开发文档-优秀校友与校友档案索引.md》《开发文档-校友留言墙.md》
 * - 优秀校友管理：/admin/outstanding-alumni（school_admin）
 * - 留言墙审核：/admin/wall/messages（school_admin）
 * - 档案索引监控：/alumni/directory（公开，收录由档案审核驱动）
 */
import http from './request'

/* ===== 优秀校友管理 ===== */

/** 全量列表（含 draft/withdrawn），支持 status 筛选 */
export function getOutstandingList(params) {
  return http.get('/admin/outstanding-alumni', { params })
}

/** 新增精选校友（user_id 必须是已激活校友；重复添加 409） */
export function createOutstanding(data) {
  return http.post('/admin/outstanding-alumni', data)
}

/** 修改展示信息 / 排序 / 上下架（status: draft/published/withdrawn） */
export function updateOutstanding(id, data) {
  return http.patch(`/admin/outstanding-alumni/${id}`, data)
}

/** 移除精选位 */
export function deleteOutstanding(id) {
  return http.delete(`/admin/outstanding-alumni/${id}`)
}

/* ===== 校友留言墙审核 ===== */

/** 审核队列（status: pending/approved/rejected/hidden + keyword） */
export function getWallMessages(params) {
  return http.get('/admin/wall/messages', { params })
}

/** 审核：{ action: 'approve' } / { action: 'reject', note } / { action: 'hide' }（取消展示） */
export function reviewWallMessage(id, data) {
  return http.put(`/admin/wall/messages/${id}/review`, data)
}

/** 删除留言（物理删除，不可恢复） */
export function deleteWallMessage(id) {
  return http.delete(`/admin/wall/messages/${id}`)
}

/**
 * 置顶 / 取消置顶（PUT /admin/wall/messages/{id}/pin，{ pinned: boolean }）
 * 仅已通过的留言可置顶；置顶后优先于按时间倒序展示。
 */
export function pinWallMessage(id, pinned = true) {
  return http.put(`/admin/wall/messages/${id}/pin`, { pinned })
}

/**
 * 禁言用户（POST /admin/wall/users/{userId}/mute，body: { duration_days?: int, reason: string }）
 * duration_days 缺省 = 永久；reason 必填。
 */
export function muteWallUser(userId, { durationDays, reason } = {}) {
  const body = { reason: String(reason || '').trim() }
  if (durationDays != null) body.duration_days = Number(durationDays)
  return http.post(`/admin/wall/users/${userId}/mute`, body)
}

/* ===== 用户侧留言（登录用户本人操作：admin 进入本页面也能看到自己发过的留言） ===== */

/** 我的留言（全部状态，含 pending/rejected/hidden + 软删记录） */
export function getMyWallMessages() {
  return http.get('/wall/messages/mine')
}

/** 删除我的留言（软删除；保留原行，可重新发送） */
export function deleteMyWallMessage(id) {
  return http.delete(`/wall/messages/${id}`)
}

/** 编辑我的 pending/rejected 留言（重置为 pending 重新走审核） */
export function updateMyWallMessage(id, data) {
  return http.put(`/wall/messages/${id}`, data)
}

/** 重新发送已撤回的留言（基于原 content 重建一条 pending） */
export function resubmitMyWallMessage(id) {
  return http.post(`/wall/messages/${id}/resubmit`)
}

/* ===== 校友档案索引（公开接口，收录监控） ===== */

/** 公开名录（keyword/name/grad_year/college_id/student_id + 分页） */
export function getDirectory(params) {
  return http.get('/alumni/directory', { params })
}
