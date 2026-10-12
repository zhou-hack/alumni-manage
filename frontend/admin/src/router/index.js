/** 路由（管理后台） */
import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '@/utils/token'

/** 路由角色声明 */
const ADMIN_ROLES = ['counselor', 'college_admin', 'school_admin', 'system_admin']
/** 校友侧角色（PRD V2.3：PC 端面向所有角色） */
const ALUMNI_ROLES = ['alumni', 'student']
/** 后台允许登录的角色（导员/校级/院系/系统管理员） */
const LOGIN_ROLES = ['counselor', 'college_admin', 'school_admin', 'system_admin']

const routes = [
  {
    // 后台独立登录页（与前台门户同款视觉，账号/令牌体系独立）
    // 命中 /login 后由组件内部读取 ?from= 并在登录成功后按角色回跳
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginPage.vue'),
    meta: { title: '登录', guestOnly: true },
  },
  {
    path: '/change-password',
    name: 'ChangePassword',
    component: () => import('@/views/auth/ChangePasswordPage.vue'),
    meta: { title: '修改密码', requiresAuth: true, forceOnly: true },
  },
  {
    path: '/',
    component: () => import('@/layout/AdminLayout.vue'),
    redirect: '/home',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'reviews',
        name: 'Reviews',
        component: () => import('@/views/review/ReviewListPage.vue'),
        meta: { title: '审核中心', roles: ['counselor', 'college_admin', 'school_admin', 'system_admin'] },
      },
      {
        path: 'archives',
        name: 'Archives',
        component: () => import('@/views/archive/ArchiveListPage.vue'),
        meta: { title: '档案管理', roles: ADMIN_ROLES },
      },
      {
        path: 'honors',
        name: 'Honors',
        component: () => import('@/views/honor/HonorListPage.vue'),
        meta: { title: '荣誉管理', roles: ADMIN_ROLES },
      },
      {
        path: 'honors/draft',
        name: 'HonorDraft',
        component: () => import('@/views/honor/HonorDraftPage.vue'),
        meta: { title: '荣誉证书解析', roles: ADMIN_ROLES },
      },
      // ---------------- 团队荣誉详情（/honors/team/{teamId} · counselor/college_admin/school_admin/system_admin） ----------------
      {
        path: 'honors/team/:teamId',
        name: 'HonorTeamDetail',
        component: () => import('@/views/honor/HonorTeamDetailPage.vue'),
        meta: { title: '团队荣誉详情', roles: ADMIN_ROLES },
      },
      {
        path: 'stats',
        name: 'Stats',
        component: () => import('@/views/stats/StatsPage.vue'),
        meta: { title: '统计看板', roles: ADMIN_ROLES },
      },
      {
        path: 'dicts',
        name: 'Dicts',
        component: () => import('@/views/dict/DictPage.vue'),
        meta: { title: '字典管理', roles: ['school_admin', 'system_admin'] },
      },
      {
        path: 'imports',
        name: 'Imports',
        component: () => import('@/views/import/ImportPage.vue'),
        meta: { title: '批量导入', roles: ['college_admin', 'school_admin', 'system_admin'] },
      },
      // ---------------- 门户运营（优秀校友 / 档案索引 / 留言墙） ----------------
      {
        path: 'outstanding',
        name: 'Outstanding',
        component: () => import('@/views/outstanding/OutstandingListPage.vue'),
        meta: { title: '优秀校友', roles: ['college_admin', 'school_admin', 'system_admin'] },
      },
      {
        path: 'hero',
        name: 'HeroCarousel',
        component: () => import('@/views/hero/HeroListPage.vue'),
        meta: { title: '首页轮播', roles: ['school_admin', 'system_admin'] },
      },
      {
        path: 'directory-admin',
        name: 'DirectoryAdmin',
        component: () => import('@/views/directory/DirectoryAdminPage.vue'),
        meta: { title: '档案索引', roles: ['school_admin', 'system_admin'] },
      },
      {
        path: 'wall',
        name: 'WallReview',
        component: () => import('@/views/wall/WallReviewPage.vue'),
        meta: { title: '留言墙审核', roles: ['school_admin', 'system_admin'] },
      },
      // ---------------- 荣誉大屏（M03 扩展 · 仅校级/系统管理员） ----------------
      // 只读展示页：荣誉类型分布 / 校友荣誉长廊 / 校友风采 / 学院分布
      // 「让谁上 / 谁下」的能力统一在 /honors 荣誉管理页（行内 + 批量 + 日志）
      {
        path: 'honor-screen',
        name: 'HonorScreen',
        component: () => import('@/views/screen/ScreenLayout.vue'),
        meta: { title: '荣誉大屏', roles: ['school_admin', 'system_admin'] },
        children: [
          {
            path: '',
            name: 'HonorScreenOverview',
            component: () => import('@/views/screen/HonorScreenPreviewPage.vue'),
            meta: { title: '荣誉大屏总览', roles: ['school_admin', 'system_admin'] },
          },
        ],
      },
      // ---------------- 荣誉墙管理（/honor-wall · school_admin / system_admin） ----------------
      // 「荣誉墙」= 校友门户 H5 的轮播位（对应后端 /api/v1/screen/feed）
      // 上架 / 下架 + 候选档案检索
      {
        path: 'honor-wall',
        name: 'HonorWallManage',
        component: () => import('@/views/screen/HonorWallManagePage.vue'),
        meta: { title: '荣誉墙', roles: ['school_admin', 'system_admin'] },
      },
      // ---------------- 人员管理（二级菜单：校友列表 / 学生列表；院级锁本院、导员锁管辖范围） ----------------
      {
        path: 'users/alumni',
        name: 'UserAlumni',
        component: () => import('@/views/users/UserListPage.vue'),
        meta: { title: '校友列表', roles: ADMIN_ROLES, userRole: 'alumni' },
      },
      {
        path: 'users/students',
        name: 'UserStudents',
        component: () => import('@/views/users/UserListPage.vue'),
        meta: { title: '学生列表', roles: ADMIN_ROLES, userRole: 'student' },
      },
      // ---------------- 站内消息中心（M14） ----------------
      {
        path: 'notifications',
        name: 'Notifications',
        component: () => import('@/views/notifications/NotificationCenter.vue'),
        meta: { title: '消息中心' }, // 所有登录角色可访问
      },
      // ---------------- 通知订阅设置（M14 · 所有登录角色） ----------------
      {
        path: 'notifications/prefs',
        name: 'NotificationPrefs',
        component: () => import('@/views/notifications/NotificationPrefs.vue'),
        meta: { title: '通知订阅' }, // 所有登录角色可访问
      },
      // ---------------- 全局搜索（M13 · 所有登录角色可访问） ----------------
      {
        path: 'search',
        name: 'GlobalSearch',
        component: () => import('@/views/search/SearchPage.vue'),
        meta: { title: '全局搜索' },
      },
      // ---------------- 注册审核通知（P4 体验类） ----------------
      {
        path: 'register-progress',
        name: 'RegisterProgress',
        component: () => import('@/views/register/RegisterProgressPage.vue'),
        meta: { title: '注册审核通知' },
      },
      // ---------------- 注册审核（管理端重做 · 三要素核验 + 人工申请） ----------------
      {
        path: 'register-review',
        name: 'RegisterReview',
        component: () => import('@/views/register/RegisterReviewPage.vue'),
        meta: { title: '注册审核', roles: ['college_admin', 'school_admin', 'system_admin'] },
      },
      // ---------------- 辅导员管理（M34 · 仅校级） ----------------
      {
        path: 'counselors',
        name: 'Counselors',
        component: () => import('@/views/counselors/CounselorListPage.vue'),
        meta: { title: '辅导员管理', roles: ['school_admin', 'system_admin'] },
      },
      // ---------------- 标签管理（M13 · 仅校级） ----------------
      {
        path: 'tags',
        name: 'Tags',
        component: () => import('@/views/tags/TagListPage.vue'),
        meta: { title: '标签管理', roles: ['school_admin', 'system_admin'] },
      },
      // ---------------- 字段权限矩阵（M15 · 读 school_admin / 写 system_admin） ----------------
      {
        path: 'field-permissions',
        name: 'FieldPermissions',
        component: () => import('@/views/fieldpermission/FieldPermissionPage.vue'),
        meta: { title: '字段权限', roles: ['school_admin', 'system_admin'] },
      },
      // ---------------- 教师档案库（[P1]· school_admin / system_admin） ----------------
      {
        path: 'teachers',
        name: 'Teachers',
        component: () => import('@/views/teachers/TeacherAdminPage.vue'),
        meta: { title: '教师档案', roles: ['school_admin', 'system_admin'] },
      },
      // ---------------- 校级荣誉管理（/admin/honors · school_admin / system_admin） ----------------
      {
        path: 'honors-admin',
        name: 'HonorsAdmin',
        component: () => import('@/views/honor/HonorAdminPage.vue'),
        meta: { title: '校级荣誉管理', roles: ['school_admin', 'system_admin'] },
      },
      // ---------------- 草稿箱（所有登录角色可访问） ----------------
      {
        path: 'drafts',
        name: 'Drafts',
        component: () => import('@/views/drafts/DraftBoxPage.vue'),
        meta: { title: '草稿箱' },
      },
      // ---------------- 授权协议（M16 · 所有登录角色） ----------------
      {
        path: 'consents',
        name: 'ConsentText',
        component: () => import('@/views/consents/ConsentTextPage.vue'),
        meta: { title: '授权协议' },
      },
      // ---------------- 审计日志（M16 · 仅系统管理员） ----------------
      {
        path: 'audit-logs',
        name: 'AuditLogs',
        component: () => import('@/views/audit/AuditLogPage.vue'),
        meta: { title: '审计日志', roles: ['system_admin'] },
      },
      // ---------------- 组织架构同步（按组织架构接学生/老师/校友） ----------------
      {
        path: 'org-sync',
        name: 'OrgSync',
        component: () => import('@/views/orgsync/OrgSyncPage.vue'),
        meta: { title: '组织架构同步', roles: ['school_admin', 'system_admin'] },
      },
      // ---------------- 校友照片收集（导员审核 + 后台推送） ----------------
      {
        path: 'photos',
        name: 'PhotoCollect',
        component: () => import('@/views/photo/PhotoCollectPage.vue'),
        meta: { title: '校友照片收集', roles: ['counselor', 'college_admin', 'school_admin', 'system_admin'] },
      },
      // ---------------- 通知公告（清单第七章 / PRD M08） ----------------
      {
        path: 'announcements',
        name: 'Announcements',
        component: () => import('@/views/announcement/AnnouncementListPage.vue'),
        meta: { title: '通知公告' }, // 所有登录角色可访问（页面内按角色切换视图）
      },
      {
        path: 'announcements/view/:id',
        name: 'AnnouncementDetail',
        component: () => import('@/views/announcement/AnnouncementDetailPage.vue'),
        meta: { title: '公告详情' },
      },
      {
        path: 'announcements/edit/:id?',
        name: 'AnnouncementEdit',
        component: () => import('@/views/announcement/AnnouncementEditPage.vue'),
        meta: { title: '编辑公告', roles: ['college_admin', 'school_admin', 'system_admin'] },
      },
      // ---------------- 校友侧页面（PRD M02/M03：PC 全量功能） ----------------
      {
        path: 'feed',
        name: 'AlumniFeed',
        component: () => import('@/views/alumni/AlumniFeedPage.vue'),
        meta: { title: '校友动态', roles: ALUMNI_ROLES },
      },
      {
        path: 'directory',
        name: 'AlumniDirectory',
        component: () => import('@/views/alumni/DirectoryPage.vue'),
        meta: { title: '校友通讯录', roles: ALUMNI_ROLES },
      },
      {
        path: 'archive-form',
        name: 'AlumniArchiveForm',
        component: () => import('@/views/alumni/ArchiveFormPage.vue'),
        meta: { title: '提交资料', roles: ALUMNI_ROLES },
      },
      {
        path: 'my-archives',
        name: 'AlumniMyArchives',
        component: () => import('@/views/alumni/MyArchivesPage.vue'),
        meta: { title: '我的资料', roles: ALUMNI_ROLES },
      },
      {
        path: 'my-honors',
        name: 'AlumniMyHonors',
        component: () => import('@/views/alumni/MyHonorsPage.vue'),
        meta: { title: '我的荣誉', roles: ALUMNI_ROLES },
      },
      // 角色首页：校友 → 我的首页（其他角色访问 /home 时由全局守卫重定向到其默认菜单）
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/alumni/AlumniHomePage.vue'),
        meta: { title: '我的首页', roles: ALUMNI_ROLES },
      },
    ],
  },
  {
    path: '/screen',
    name: 'Screen',
    component: () => import('@/views/screen/ScreenPage.vue'),
    meta: { title: '校友风采大屏', requiresAuth: true, roles: ['school_admin', 'system_admin'] },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/auth/ForbiddenPage.vue'),
    meta: { title: '无权限', requiresAuth: true },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/home',
  },
]

const router = createRouter({
  // Vite base = '/admin/'，但 hash 路由的 base 应为 '/'。
  // createWebHashHistory 默认 base = location.pathname='/admin/'，导致 #/honor-screen/config
  // 被解析为 '/admin/honor-screen/config'，命中不到 routes（routes 用相对父 '/'，绝对路径
  // '/honor-screen/config'）。显式传 '/' 修正。
  history: createWebHashHistory('/'),
  routes,
})

/** 延迟获取用户 store（避免顶层循环依赖） */
async function getUserStore() {
  const { useUserStore } = await import('@/stores/user')
  return useUserStore()
}

router.beforeEach(async (to) => {
  document.title = to.meta.title ? `${to.meta.title} - 校友管理平台后台` : '校友管理平台后台'
  const logged = Boolean(getToken())

  if (to.meta.requiresAuth && !logged) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && logged) {
    return { path: '/' }
  }
  if (!logged) return true

  const userStore = await getUserStore()

  // 后台登录门槛：仅校级/院系/系统管理员可进入（其余角色强制回到登录页并清令牌）
  if (to.meta.requiresAuth && !LOGIN_ROLES.includes(userStore.user?.role)) {
    const { clearTokens } = await import('@/utils/token')
    clearTokens()
    userStore.user = null
    return { name: 'Login' }
  }

  // 初始密码强制改密（PRD M01-03）：未改密前只允许访问改密页与登出
  if (userStore.mustChangePassword && !to.meta.forceOnly) {
    return { name: 'ChangePassword' }
  }

  // 角色首页：非校友角色访问 /home → 重定向到其默认菜单（而非 403）
  if (to.name === 'Home' && !ALUMNI_ROLES.includes(userStore.user?.role)) {
    return { path: userStore.defaultHome }
  }

  // 角色路由守卫：越权路由跳 403
  if (to.meta.roles && !to.meta.roles.includes(userStore.user?.role)) {
    return { name: 'Forbidden' }
  }
  return true
})

export default router
