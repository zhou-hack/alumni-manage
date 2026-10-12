/**
 * 审核工作台的纯逻辑工具（与 ReviewListPage 保持同步），便于单元测试。
 */

/** 层级显示文案（与 utils/format.js 中的 STAGE_TEXT 一致） */
export const STAGE_TEXT = {
  counselor: '初审（导员）',
  first: '初审（院系）',
  second: '终审（校级）',
}

/** 合法 stage 集合（与后端 STAGES 对齐：counselor/first/second） */
export const STAGES = ['counselor', 'first', 'second']

/**
 * 计算当前角色的层级锁定：
 * - counselor → 'counselor'
 * - college_admin → 'first'
 * - school_admin / system_admin → ''（不锁定，可任意筛选）
 * - 其他 → ''
 */
export function lockedStageOf(role) {
  if (role === 'counselor') return 'counselor'
  if (role === 'college_admin') return 'first'
  return ''
}

/** 通过提示文案 */
export function approveTipOf(stage) {
  if (stage === 'counselor') return '导员初审通过，将转入院系初审'
  if (stage === 'first') return '院系初审通过，将转入校级终审'
  if (stage === 'second') return '校级终审通过，已入库'
  return '已通过'
}

/** 角色是否需要人工核验业务类型（仅校级 / 系统管理员可见） */
export function canSeeManualReview(role) {
  return role === 'school_admin' || role === 'system_admin'
}

/** 角色是否有审核权限（三级：counselor / college_admin / school_admin / system_admin） */
export function canReview(role) {
  return ['counselor', 'college_admin', 'school_admin', 'system_admin'].includes(role)
}

/** 视角文案（与 pageSub 文案保持一致） */
export function roleSubtitle(role) {
  if (role === 'counselor') {
    return '当前视角：辅导员（导员）· 仅处理管辖范围内学生资料的初审（stage=counselor）。点击任意记录查看业务详情与材料。'
  }
  if (role === 'college_admin') {
    return '当前视角：院级管理员 · 处理本院初审待办（stage=first）。点击任意记录查看业务详情与材料。'
  }
  return '当前视角：校级管理员 · 对初审通过项做终审与决策（stage=second）。点击任意记录查看业务详情与材料。'
}