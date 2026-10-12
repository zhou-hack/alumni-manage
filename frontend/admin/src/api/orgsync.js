/**
 * 组织架构同步 API（orgsync 模块 · 仅 school_admin / system_admin）
 * - sync：拉新数据（按 type 区分 user/old，对应学生/教师/校友基础数据）
 * - logs：同步日志（total/inserted/updated/skipped/failed + failures 详情）
 */
import http from './request'

/** 触发同步 */
export function syncOrg(type, mode = 'merge') {
  return http.post('/admin/org-sync/sync', { type, mode })
}

/** 同步日志列表 */
export function listOrgSyncLogs(params) {
  return http.get('/admin/org-sync/logs', { params })
}