/**
 * 教师库管理（/admin/teachers）
 * - GET /admin/teachers           教师列表（school_admin / system_admin）
 * - POST /admin/teachers/sync     手动触发同步（仅 system_admin；重入 409）
 * - GET /admin/teachers/sync-logs 同步记录（仅 system_admin）
 */
import http from './request'

/** 教师列表（college_id / major_id / status / keyword 筛选 + 分页） */
export function listTeachers(params = {}) {
  return http.get('/admin/teachers', { params })
}

/** 手动触发教师同步（仅 system_admin；返回 SyncResult） */
export function syncTeachers() {
  return http.post('/admin/teachers/sync')
}

/** 教师同步记录（分页） */
export function listTeacherSyncLogs(params = {}) {
  return http.get('/admin/teachers/sync-logs', { params })
}
