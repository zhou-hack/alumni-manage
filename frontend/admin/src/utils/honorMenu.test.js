import { describe, it, expect } from 'vitest'
import { buildMenusWithColleges, readCollegeIdFromQuery } from '@/utils/honorMenu'

describe('buildMenusWithColleges', () => {
  const baseSchoolAdmin = [
    {
      path: '/grp-content',
      title: '内容管理',
      icon: 'FolderOpened',
      children: [
        { path: '/archives', title: '档案管理' },
        { path: '/honors', title: '荣誉管理' },
        { path: '/announcements', title: '公告管理' },
      ],
    },
    { path: '/stats', title: '统计看板', icon: 'DataLine' },
  ]

  it('校级管理员：把"荣誉管理"展开为分组（全部荣誉 + 各学院项，原位替换）', () => {
    const colleges = [
      { id: 1, value: 'cs', label: '计算机学院' },
      { id: 2, value: 'se', label: '智能建造学院' },
    ]
    const out = buildMenusWithColleges('school_admin', baseSchoolAdmin, colleges)
    const grp = out.find((m) => m.path === '/grp-content')
    expect(grp).toBeTruthy()
    const titles = grp.children.map((c) => c.title)
    expect(titles).toContain('全部荣誉')
    expect(titles).toContain('计算机学院')
    expect(titles).toContain('智能建造学院')
    // 顺序：原位替换 → 档案管理 → 全部荣誉 → 各学院 → 公告管理
    expect(grp.children[0].title).toBe('档案管理')
    expect(grp.children[0].path).toBe('/archives')
    expect(grp.children[1].title).toBe('全部荣誉')
    expect(grp.children[1].path).toBe('/honors')
    expect(grp.children[2].path).toBe('/honors?college_id=1')
    expect(grp.children[3].path).toBe('/honors?college_id=2')
    expect(grp.children[4].title).toBe('公告管理')
    // 总数：3（学院项）+ 1（全部荣誉）+ 2（其他） = 6
    expect(grp.children).toHaveLength(5)
  })

  it('系统管理员：行为与校级一致', () => {
    const out = buildMenusWithColleges('system_admin', baseSchoolAdmin, [{ id: 5, label: '管理学院' }])
    const grp = out.find((m) => m.path === '/grp-content')
    expect(grp.children.some((c) => c.path === '/honors?college_id=5')).toBe(true)
  })

  it('院级管理员：原样返回（不展开荣誉管理）', () => {
    const out = buildMenusWithColleges('college_admin', baseSchoolAdmin, [{ id: 1, label: 'X' }])
    const grp = out.find((m) => m.path === '/grp-content')
    expect(grp.children.map((c) => c.path)).toEqual(['/archives', '/honors', '/announcements'])
  })

  it('导员：原样返回', () => {
    const out = buildMenusWithColleges('counselor', [
      { path: '/archives', title: '档案查看' },
      { path: '/honors', title: '荣誉查看' },
    ], [{ id: 1, label: 'X' }])
    expect(out.map((m) => m.path)).toEqual(['/archives', '/honors'])
  })

  it('校友/学生：原样返回', () => {
    const out = buildMenusWithColleges('alumni', baseSchoolAdmin, [{ id: 1 }])
    expect(out).toEqual(baseSchoolAdmin)
  })

  it('字典加载失败：传入空数组也能保证"全部荣誉"项存在', () => {
    const out = buildMenusWithColleges('school_admin', baseSchoolAdmin, [])
    const grp = out.find((m) => m.path === '/grp-content')
    // 原位替换：第二项是「全部荣誉」，第三项是「公告管理」
    expect(grp.children[1].path).toBe('/honors')
    expect(grp.children[1].title).toBe('全部荣誉')
    // 没有学院项
    expect(grp.children.filter((c) => c.path.startsWith('/honors?college_id='))).toHaveLength(0)
  })

  it('学院缺少 id 时退化到 value，id 与 value 都没有则跳过', () => {
    const colleges = [
      { value: 'a', label: 'A' },
      { id: 7, label: 'B' },
      { label: 'C无id' }, // 既无 id 也无 value
      { id: '', value: '', label: 'D空' },
    ]
    const out = buildMenusWithColleges('school_admin', baseSchoolAdmin, colleges)
    const grp = out.find((m) => m.path === '/grp-content')
    const paths = grp.children.map((c) => c.path)
    expect(paths).toContain('/honors?college_id=7') // id 优先
    // 注意：当只有 value 时 buildMenusWithColleges 用 value 代替 id
    // 这里 A 只有 value 'a'，应当出现 ?college_id=a
    expect(paths.some((p) => p.endsWith('?college_id=a'))).toBe(true)
    // 不应包含被跳过的项
    expect(paths.filter((p) => p.startsWith('/honors?college_id='))).toHaveLength(2)
  })

  it('返回结果深拷贝，不污染原 menus', () => {
    const out = buildMenusWithColleges('school_admin', baseSchoolAdmin, [{ id: 1, label: 'X' }])
    out[0].children[0].title = '被改'
    expect(baseSchoolAdmin[0].children[0].title).not.toBe('被改')
  })

  it('空入参安全：menus 为 null/undefined 时返回 []', () => {
    expect(buildMenusWithColleges('school_admin', null, [])).toEqual([])
    expect(buildMenusWithColleges('school_admin', undefined, [])).toEqual([])
  })
})

describe('readCollegeIdFromQuery', () => {
  it('缺省返回 null', () => {
    expect(readCollegeIdFromQuery(null)).toBeNull()
    expect(readCollegeIdFromQuery(undefined)).toBeNull()
    expect(readCollegeIdFromQuery({})).toBeNull()
    expect(readCollegeIdFromQuery({ college_id: '' })).toBeNull()
  })

  it('字符串数字返回 Number', () => {
    expect(readCollegeIdFromQuery({ college_id: '12' })).toBe(12)
  })

  it('非法字符串返回 null', () => {
    expect(readCollegeIdFromQuery({ college_id: 'abc' })).toBeNull()
  })

  it('多个字段只取 college_id', () => {
    expect(readCollegeIdFromQuery({ college_id: '3', other: 'x' })).toBe(3)
  })
})