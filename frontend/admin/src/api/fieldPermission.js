/**
 * 字段级权限矩阵（admin/field-permissions）
 * - GET：拉全量角色×字段矩阵（school_admin / system_admin 可读）
 * - PUT：按角色覆盖某行字段（仅 system_admin 可写）
 *
 * 返回结构：
 *   {
 *     roles: {
 *       alumni:        { phone: 'masked', id_card: 'hidden', ... },
 *       counselor:     { ... },
 *       college_admin: { ... },
 *       school_admin:  { ... },
 *       system_admin:  { ... }
 *     }
 *   }
 */
import http from './request'

/** 拉取全量字段权限矩阵 */
export function getFieldPermissions() {
  return http.get('/admin/field-permissions')
}

/** 更新某角色的字段权限（fields: { fieldKey: 'visible'|'masked'|'hidden' }） */
export function updateFieldPermissions(role, fields) {
  return http.put(`/admin/field-permissions/${role}`, { fields })
}

/** 角色清单（与登录态对齐，前端只展示） */
export const ROLES = [
  { value: 'alumni', label: '校友' },
  { value: 'student', label: '在校学生' },
  { value: 'counselor', label: '辅导员' },
  { value: 'college_admin', label: '院系管理员' },
  { value: 'school_admin', label: '校级管理员' },
  { value: 'system_admin', label: '系统管理员' },
]

/** 可见性三档 */
export const VISIBILITY = [
  { value: 'visible', label: '可见', tone: 'success' },
  { value: 'masked', label: '脱敏', tone: 'warning' },
  { value: 'hidden', label: '隐藏', tone: 'danger' },
]

/** 默认字段集（与校友基础表 / 用户表对齐） */
export const FIELD_KEYS = [
  { key: 'real_name', label: '真实姓名' },
  { key: 'student_id', label: '学号' },
  { key: 'phone', label: '手机号' },
  { key: 'id_card', label: '身份证号' },
  { key: 'college', label: '学院' },
  { key: 'major', label: '专业' },
  { key: 'grad_year', label: '届别' },
  { key: 'email', label: '邮箱' },
  { key: 'current_city', label: '当前城市' },
  { key: 'industry', label: '所在行业' },
  { key: 'current_title', label: '现任职务' },
]
