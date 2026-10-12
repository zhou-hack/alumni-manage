// 全局登录态（参照旧版 F2 前台 auth.tsx 的轻量实现）：
// reactive 单例，首次加载凭本地 access_token 调 /auth/me 还原用户；
// 路由守卫等待 authReady，未登录访问 /profile* 时重定向 /login。
import { reactive } from 'vue';
import { api, tokenStore, type UserBrief } from './api';

interface AuthState {
  user: UserBrief | null;
  /** 首次 /auth/me 还原期间为 true（避免守卫闪跳登录页） */
  loading: boolean;
}

export const authState = reactive<AuthState>({ user: null, loading: true });

export function setUser(u: UserBrief | null) {
  authState.user = u;
}

/** 启动时还原登录态：有本地令牌则调 /auth/me；令牌失效时 client 已清理本地令牌 */
async function initAuth(): Promise<void> {
  if (!tokenStore.access) {
    authState.loading = false;
    return;
  }
  try {
    authState.user = await api.auth.me();
  } catch {
    tokenStore.clear();
  } finally {
    authState.loading = false;
  }
}

/** 首次还原完成的 Promise（路由守卫 / 页面据此等待，避免重复请求） */
export const authReady: Promise<void> = initAuth();

/** 登出：网络异常也强制本地登出 */
export async function logout(): Promise<void> {
  try {
    await api.auth.logout();
  } catch {
    /* 网络异常也强制本地登出 */
  }
  tokenStore.clear();
  authState.user = null;
}

/** 展示名：优先学籍真实姓名（real_name），无则退回昵称（nickname）
 *  统一导航栏/个人中心等处的用户称呼，避免测试昵称（如"冒烟校友"）直接露出 */
export function displayName(user: UserBrief | null | undefined): string {
  if (!user) return '';
  return (user.real_name || '').trim() || (user.nickname || '').trim();
}

/** 角色中文标签（与后台角色体系一致） */
export const ROLE_LABEL: Record<string, string> = {
  student: '在校生',
  alumni: '校友',
  counselor: '辅导员',
  college_admin: '学院管理员',
  school_admin: '校级管理员',
  system_admin: '系统管理员'
};
