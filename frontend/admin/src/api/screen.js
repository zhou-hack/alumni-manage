/**
 * 荣誉大屏管理 API（screen 模块 · 仅 school_admin / system_admin）
 *
 * - 配置 CRUD：current active / list / detail / create / update / delete
 * - 统计分析：4 维度聚合（trend / category / level / topOwners）+ overview KPI
 *
 * 字段命名（snake_case）与后端 DTO 对齐：
 *   layout: grid-3 | grid-4 | waterfall
 *   theme_color: tech | gold | dark | warm
 *   sort_by: awarded_at_desc | level_asc | pinned_first
 *   status: active | draft | archived
 *   refresh_sec: 0（手动）/ 10 / 30 / 60
 */
import http from './request'

/* ==================== 配置 ==================== */

/** 当前生效配置 */
export function getActiveScreenConfig() {
  return http.get('/admin/screen/config/active')
}

/** 配置列表（status 筛选 + 分页） */
export function listScreenConfigs(params) {
  return http.get('/admin/screen/config', { params })
}

/** 配置详情 */
export function getScreenConfig(id) {
  return http.get(`/admin/screen/config/${id}`)
}

/** 新建配置 */
export function createScreenConfig(data) {
  return http.post('/admin/screen/config', data)
}

/** 更新配置（PUT 全量替换关键字段） */
export function updateScreenConfig(id, data) {
  return http.put(`/admin/screen/config/${id}`, data)
}

/** 删除配置（仅非 active） */
export function deleteScreenConfig(id) {
  return http.delete(`/admin/screen/config/${id}`)
}

/* ==================== 统计分析 ==================== */

/** 4 维度聚合（概览 + 年趋势 + 类别 + 级别 + TOP 排行） */
export function getScreenStats(params) {
  return http.get('/admin/screen/stats', { params })
}
