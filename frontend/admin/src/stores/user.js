/** 用户状态（Pinia） */
import { defineStore } from 'pinia'
import { getMe, logout as logoutApi } from '@/api/auth'
import { getDict } from '@/api/dict'
import { getToken, getCachedUser, setCachedUser, clearTokens } from '@/utils/token'
import { buildMenusWithColleges } from '@/utils/honorMenu'

/**
 * 学院字典缓存（持久化到 localStorage）：
 * - 解决"首次登录菜单展开学院 → 再次登录后学院菜单消失"（字典为空 → buildMenusWithColleges 只剩"全部荣誉"）
 * - 后端 /api/v1/dicts/college 偶发返回空时仍能保证菜单稳定
 */
const COLLEGE_CACHE_KEY = 'admin_college_dict_cache'
function readCollegeCache() {
  try {
    const raw = localStorage.getItem(COLLEGE_CACHE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return null
    return parsed
  } catch {
    return null
  }
}
function writeCollegeCache(list) {
  try {
    localStorage.setItem(COLLEGE_CACHE_KEY, JSON.stringify(list || []))
  } catch {
    /* 忽略：隐私模式可能拒绝写入 */
  }
}

/**
 * 角色菜单配置（PRD V2.3：PC 端面向所有角色，按角色呈现菜单）
 * - alumni / student：校友视角（首页概览、提交资料、我的资料、我的荣誉，PRD M02/M03）
 * - counselor（导员）：参与三级审核初审（M34 P1）
 * - college_admin（院级）：初审 + 本院档案 + 本院统计
 * - school_admin / system_admin：全量（含字典、上下架、大屏顶栏入口）
 */
const ROLE_MENUS = {
  alumni: [
    { path: '/home', title: '我的首页', icon: 'HomeFilled' },
    { path: '/feed', title: '校友动态', icon: 'View' },
    { path: '/archive-form', title: '提交资料', icon: 'EditPen' },
    { path: '/my-archives', title: '我的资料', icon: 'Folder' },
    { path: '/drafts', title: '草稿箱', icon: 'Document' },
    { path: '/my-honors', title: '我的荣誉', icon: 'Medal' },
    { path: '/directory', title: '校友通讯录', icon: 'Avatar' },
    { path: '/notifications', title: '消息中心', icon: 'Bell' },
    { path: '/register-progress', title: '注册审核通知', icon: 'Notification' },
    { path: '/announcements', title: '通知公告', icon: 'Bell' },
  ],
  student: [
    { path: '/home', title: '我的首页', icon: 'HomeFilled' },
    { path: '/feed', title: '校友动态', icon: 'View' },
    { path: '/archive-form', title: '提交资料', icon: 'EditPen' },
    { path: '/my-archives', title: '我的资料', icon: 'Folder' },
    { path: '/drafts', title: '草稿箱', icon: 'Document' },
    { path: '/my-honors', title: '我的荣誉', icon: 'Medal' },
    { path: '/directory', title: '校友通讯录', icon: 'Avatar' },
    { path: '/notifications', title: '消息中心', icon: 'Bell' },
    { path: '/register-progress', title: '注册审核通知', icon: 'Notification' },
    { path: '/announcements', title: '通知公告', icon: 'Bell' },
  ],
  counselor: [
    { path: '/archives', title: '档案查看', icon: 'Folder' },
    { path: '/honors', title: '荣誉查看', icon: 'Medal' },
    { path: '/photos', title: '照片收集', icon: 'Picture' },
    {
      path: '/grp-review',
      title: '审核中心',
      icon: 'Checked',
      children: [{ path: '/reviews', title: '初审待办' }],
    },
    {
      path: '/grp-users',
      title: '人员管理',
      icon: 'User',
      children: [
        { path: '/users/alumni', title: '校友列表' },
        { path: '/users/students', title: '学生列表' },
      ],
    },
    { path: '/stats', title: '统计看板', icon: 'DataLine' },
    { path: '/notifications', title: '消息中心', icon: 'Bell' },
    { path: '/announcements', title: '通知公告', icon: 'Bell' },
  ],
  // 院级管理员：内容管理（档案/荣誉/公告）+ 审核中心（初审）+ 人员管理（本院）+ 批量导入 + 统计
  college_admin: [
    {
      path: '/grp-content',
      title: '内容管理',
      icon: 'FolderOpened',
      children: [
        { path: '/archives', title: '档案管理' },
        { path: '/honors', title: '荣誉管理' },
        { path: '/outstanding', title: '优秀校友' },
        { path: '/announcements', title: '公告管理' },
        { path: '/photos', title: '照片收集' },
      ],
    },
    {
      path: '/grp-review',
      title: '审核中心',
      icon: 'Checked',
      children: [{ path: '/reviews', title: '档案审核' }],
    },
    {
      path: '/grp-users',
      title: '人员管理',
      icon: 'User',
      children: [
        { path: '/users/alumni', title: '校友列表' },
        { path: '/users/students', title: '学生列表' },
        { path: '/imports', title: '批量导入' },
      ],
    },
    { path: '/stats', title: '统计看板', icon: 'DataLine' },
    { path: '/notifications', title: '消息中心', icon: 'Bell' },
    { path: '/announcements', title: '通知公告', icon: 'Bell' },
  ],
  // 校级管理员：内容管理 + 审核中心 + 系统管理（三组二级菜单）
  school_admin: [
    {
      path: '/grp-content',
      title: '内容管理',
      icon: 'FolderOpened',
      children: [
        { path: '/archives', title: '档案管理' },
        { path: '/honors', title: '荣誉管理' },
        { path: '/announcements', title: '公告管理' },
        { path: '/outstanding', title: '优秀校友' },
        { path: '/hero', title: '首页轮播' },
        { path: '/directory-admin', title: '档案索引' },
        { path: '/photos', title: '照片收集' },
      ],
    },
    {
      path: '/honor-screen',
      title: '荣誉大屏',
      icon: 'Monitor',
    },
    {
      path: '/honor-wall',
      title: '荣誉墙',
      icon: 'PictureFilled',
    },
    {
      path: '/grp-review',
      title: '审核中心',
      icon: 'Checked',
      children: [
        { path: '/reviews', title: '档案审核' },
        { path: '/wall', title: '留言墙审核' },
      ],
    },
    {
      path: '/grp-system',
      title: '系统管理',
      icon: 'Setting',
      children: [
        { path: '/users/alumni', title: '校友列表' },
        { path: '/users/students', title: '学生列表' },
        { path: '/counselors', title: '辅导员管理' },
        { path: '/dicts', title: '字典管理' },
        { path: '/imports', title: '批量导入' },
        { path: '/stats', title: '统计看板' },
        { path: '/consents', title: '授权协议' },
        { path: '/tags', title: '标签管理' },
        { path: '/org-sync', title: '组织架构同步' },
        { path: '/teachers', title: '教师档案' },
        { path: '/field-permissions', title: '字段权限' },
        { path: '/honors-admin', title: '校级荣誉管理' },
        { path: '/register-review', title: '注册审核' },
      ],
    },
    // 风采大屏入口仅保留顶栏按钮（AdminLayout 右上角），不放侧栏菜单
  ],
  // 系统管理员：与校级一致
  system_admin: [
    {
      path: '/grp-content',
      title: '内容管理',
      icon: 'FolderOpened',
      children: [
        { path: '/archives', title: '档案管理' },
        { path: '/honors', title: '荣誉管理' },
        { path: '/announcements', title: '公告管理' },
        { path: '/outstanding', title: '优秀校友' },
        { path: '/hero', title: '首页轮播' },
        { path: '/directory-admin', title: '档案索引' },
        { path: '/photos', title: '照片收集' },
      ],
    },
    {
      path: '/honor-screen',
      title: '荣誉大屏',
      icon: 'Monitor',
    },
    {
      path: '/honor-wall',
      title: '荣誉墙',
      icon: 'PictureFilled',
    },
    {
      path: '/grp-review',
      title: '审核中心',
      icon: 'Checked',
      children: [
        { path: '/reviews', title: '档案审核' },
        { path: '/wall', title: '留言墙审核' },
        { path: '/register-review', title: '注册审核' },
      ],
    },
    {
      path: '/grp-system',
      title: '系统管理',
      icon: 'Setting',
      children: [
        { path: '/users/alumni', title: '校友列表' },
        { path: '/users/students', title: '学生列表' },
        { path: '/counselors', title: '辅导员管理' },
        { path: '/dicts', title: '字典管理' },
        { path: '/imports', title: '批量导入' },
        { path: '/stats', title: '统计看板' },
        { path: '/consents', title: '授权协议' },
        { path: '/audit-logs', title: '审计日志' },
        { path: '/tags', title: '标签管理' },
        { path: '/org-sync', title: '组织架构同步' },
        { path: '/teachers', title: '教师档案' },
        { path: '/field-permissions', title: '字段权限' },
        { path: '/honors-admin', title: '校级荣誉管理' },
      ],
    },
    // 风采大屏入口仅保留顶栏按钮（AdminLayout 右上角），不放侧栏菜单
  ],
}

export const useUserStore = defineStore('user', {
  state: () => ({
    user: getCachedUser(),
    menusState: [], // 动态菜单：loadMenus() 后填充（默认静态 ROLE_MENUS）
    // 学院字典预热状态：标记学院列表是否已就绪（避免首次进入时菜单缺学院）
    collegesLoaded: false,
  }),
  getters: {
    isLoggedIn: () => Boolean(getToken()),
    roleText: (state) => {
      const map = {
        student: '在校学生',
        alumni: '校友',
        counselor: '辅导员',
        college_admin: '院系管理员',
        school_admin: '校级管理员',
        system_admin: '系统管理员',
      }
      return map[state.user?.role] || '管理员'
    },
    /** 当前角色可见菜单（优先使用动态菜单，否则回退到静态 ROLE_MENUS） */
    menus: (state) => state.menusState.length ? state.menusState : (ROLE_MENUS[state.user?.role] || []),
    /** 当前角色默认首页（第一个可点击的叶子菜单，分组时取其第一个子项） */
    defaultHome: (state) => {
      const list = state.menusState.length ? state.menusState : (ROLE_MENUS[state.user?.role] || [])
      const first = list[0]
      if (!first) return '/'
      return first.children?.[0]?.path || first.path || '/'
    },
    /** 初始密码是否需强制修改（PRD M01-03） */
    mustChangePassword: (state) => Boolean(state.user?.must_change_password),
  },
  actions: {
    setLoginUser(user) {
      this.user = user
      setCachedUser(user)
    },
    async fetchProfile() {
      if (!this.isLoggedIn) return
      const res = await getMe()
      this.user = res.data
      setCachedUser(res.data)
      await this.loadMenus()
    },
    /**
     * 加载动态菜单（仅校级/系统管理员的「荣誉管理」按学院展开）。
     * 其他角色直接使用静态 ROLE_MENUS。
     * 失败时回退静态菜单 + localStorage 缓存，确保 UI 不阻塞且菜单稳定。
     */
    async loadMenus() {
      const role = this.user?.role
      const base = ROLE_MENUS[role] || []
      if (!['school_admin', 'system_admin'].includes(role)) {
        this.menusState = base
        this.collegesLoaded = true
        return
      }
      // 1) 优先用 localStorage 缓存（保证"再次登录"也能稳定显示）
      const cached = readCollegeCache()
      if (cached && cached.length) {
        this.menusState = buildMenusWithColleges(role, base, cached)
      }
      // 2) 再异步拉后端刷新（失败/为空都不影响已有菜单）
      try {
        const colleges = await getDict('college')
        if (Array.isArray(colleges) && colleges.length) {
          writeCollegeCache(colleges)
          this.menusState = buildMenusWithColleges(role, base, colleges)
        }
      } catch (e) {
        // 后端失败时保留缓存版本（首次登录场景已有静态菜单兜底）
        if (!cached) this.menusState = base
      } finally {
        this.collegesLoaded = true
      }
    },
    /** 清空动态菜单（如切换角色时调用） */
    resetMenus() {
      this.menusState = []
    },
    async logout() {
      try {
        await logoutApi()
      } catch {
        // 登出接口失败不影响本地清理
      }
      clearTokens()
      this.user = null
      setCachedUser(null)
    },
  },
})
