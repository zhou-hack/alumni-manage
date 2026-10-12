import { describe, it, expect } from 'vitest'
import {
  STAGE_TEXT,
  STAGES,
  lockedStageOf,
  approveTipOf,
  canSeeManualReview,
  canReview,
  roleSubtitle,
} from '@/utils/reviewLogic'

describe('STAGE_TEXT', () => {
  it('三个层级文案齐全', () => {
    expect(STAGE_TEXT.counselor).toBe('初审（导员）')
    expect(STAGE_TEXT.first).toBe('初审（院系）')
    expect(STAGE_TEXT.second).toBe('终审（校级）')
  })
})

describe('STAGES', () => {
  it('与后端 STAGES 一致', () => {
    expect(STAGES).toEqual(['counselor', 'first', 'second'])
  })
})

describe('lockedStageOf', () => {
  it('counselor 锁定 counselor', () => {
    expect(lockedStageOf('counselor')).toBe('counselor')
  })
  it('college_admin 锁定 first', () => {
    expect(lockedStageOf('college_admin')).toBe('first')
  })
  it('school_admin 不锁定', () => {
    expect(lockedStageOf('school_admin')).toBe('')
  })
  it('system_admin 不锁定', () => {
    expect(lockedStageOf('system_admin')).toBe('')
  })
  it('校友 / 学生 不锁定', () => {
    expect(lockedStageOf('alumni')).toBe('')
    expect(lockedStageOf('student')).toBe('')
  })
  it('未知角色 / 空值 不锁定', () => {
    expect(lockedStageOf(null)).toBe('')
    expect(lockedStageOf(undefined)).toBe('')
    expect(lockedStageOf('xx')).toBe('')
  })
})

describe('approveTipOf', () => {
  it('counselor 通过 → 转院系', () => {
    expect(approveTipOf('counselor')).toContain('院系')
  })
  it('first 通过 → 转校级', () => {
    expect(approveTipOf('first')).toContain('校级')
  })
  it('second 通过 → 入库', () => {
    expect(approveTipOf('second')).toContain('入库')
  })
  it('非法 stage → 默认通过', () => {
    expect(approveTipOf('xx')).toBe('已通过')
  })
})

describe('canSeeManualReview', () => {
  it('仅校级/系统管理员可见', () => {
    expect(canSeeManualReview('school_admin')).toBe(true)
    expect(canSeeManualReview('system_admin')).toBe(true)
    expect(canSeeManualReview('counselor')).toBe(false)
    expect(canSeeManualReview('college_admin')).toBe(false)
    expect(canSeeManualReview('alumni')).toBe(false)
  })
})

describe('canReview', () => {
  it('三级审核角色都返回 true', () => {
    expect(canReview('counselor')).toBe(true)
    expect(canReview('college_admin')).toBe(true)
    expect(canReview('school_admin')).toBe(true)
    expect(canReview('system_admin')).toBe(true)
  })
  it('非审核角色返回 false', () => {
    expect(canReview('alumni')).toBe(false)
    expect(canReview('student')).toBe(false)
    expect(canReview('')).toBe(false)
    expect(canReview(null)).toBe(false)
  })
})

describe('roleSubtitle', () => {
  it('三类视角文案互不相同', () => {
    const c = roleSubtitle('counselor')
    const a = roleSubtitle('college_admin')
    const s = roleSubtitle('school_admin')
    expect(c).not.toBe(a)
    expect(a).not.toBe(s)
    expect(c).not.toBe(s)
  })
  it('包含关键字', () => {
    expect(roleSubtitle('counselor')).toContain('导员')
    expect(roleSubtitle('college_admin')).toContain('院级')
    expect(roleSubtitle('school_admin')).toContain('校级')
  })
})