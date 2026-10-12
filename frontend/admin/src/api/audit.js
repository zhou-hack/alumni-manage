/**
 * 审计日志接口（M16）
 * - 仅 system_admin 可见
 * - GET    /audit-logs        ?user_id=&action=&biz_type=&date_from=&date_to=&page=&page_size=
 * - GET    /audit-logs/actions —— 动作字典（筛选下拉）
 * - GET    /audit-logs/export  —— 直接下载 xlsx（返回二进制）
 */
import http from './request'

/** 审计日志分页查询 */
export function getAuditLogs(params = {}) {
  return http.get('/audit-logs', { params })
}

/** 动作字典（用于筛选下拉） */
export function getAuditActions() {
  return http.get('/audit-logs/actions')
}

/**
 * 导出 xlsx（带筛选条件；导出走文件流，前端用 a 标签触发）
 * 返回 Promise<{ blob, filename }>
 */
export async function exportAuditLogs(params = {}) {
  const res = await http.get('/audit-logs/export', { params, responseType: 'blob' })
  const blob = res.data instanceof Blob ? res.data : new Blob([res.data])
  return { blob, filename: 'audit-logs.xlsx' }
}