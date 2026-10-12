// ============================================================================
// 路由表（vue-router）
//   /                  首页
//   /dashboard         数据看板（内含 ECharts，体积较大，按路由懒加载，不进首页包）
//   /news /news/:id    最新通知列表 / 详情
//   /login /activate /forgot   登录 / 校友注册（激活）/ 忘记密码（按路由懒加载）
//   /profile*          个人中心（requiresAuth，未登录重定向 /login?from=...）
// ============================================================================
import { createRouter, createWebHistory } from 'vue-router';
import Home from '../pages/Home.vue';
import NewsListPage from '../pages/NewsListPage.vue';
import NewsDetailPage from '../pages/NewsDetailPage.vue';
import { authReady, authState } from '../auth';

const DashboardPage = () => import('../pages/DashboardPage.vue');
const UnifiedLoginPage = () => import('../pages/UnifiedLoginPage.vue');
const ActivatePage = () => import('../pages/ActivatePage.vue');
const ForgotPage = () => import('../pages/ForgotPage.vue');
const ProfilePage = () => import('../pages/ProfilePage.vue');
const ProfileRecordsPage = () => import('../pages/ProfileRecordsPage.vue');
const ProfileHonorsPage = () => import('../pages/ProfileHonorsPage.vue');
const ProfilePhotosPage = () => import('../pages/ProfilePhotosPage.vue');
const ProfileSubmitPage = () => import('../pages/ProfileSubmitPage.vue');

/**
 * 404 兜底：桌面端回 /（H5 路由已下线，不再做 UA 分流）。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: Home },
    { path: '/dashboard', component: DashboardPage },
    { path: '/news', component: NewsListPage },
    { path: '/news/:id', component: NewsDetailPage },
    // 统一登录：portal /login 直接渲染 UnifiedLoginPage（不再跳到 admin）
    { path: '/login', component: UnifiedLoginPage },
    { path: '/activate', component: ActivatePage },
    { path: '/forgot', component: ForgotPage },
    { path: '/profile', component: ProfilePage, meta: { requiresAuth: true, title: '个人中心' } },
    { path: '/profile/records', component: ProfileRecordsPage, meta: { requiresAuth: true, title: '我的记录' } },
    { path: '/profile/honors', component: ProfileHonorsPage, meta: { requiresAuth: true, title: '我的荣誉' } },
    { path: '/profile/photos', component: ProfilePhotosPage, meta: { requiresAuth: true, title: '我的相册' } },
    { path: '/profile/submit', component: ProfileSubmitPage, meta: { requiresAuth: true, title: '提交资料' } },
    // 兜底：未知路径回 /
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ],
  // SPA 路由跳转不自动重置滚动位置；带 hash 时平滑滚动到锚点
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) return savedPosition;
    return false;
  }
});

// 登录守卫：先等首次 /auth/me 还原完成，再判断是否需要登录
router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true;
  await authReady;
  if (authState.user) return true;
  return { path: '/login', query: { from: to.fullPath } };
});

export default router;