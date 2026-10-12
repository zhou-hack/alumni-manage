/** 认证模块（API 规范第二章，管理端） */
import http from './request'

/** 管理员登录（password 为 SHA-256 散列值） */
export function login({ account, password }) {
  // 兼容用户名或手机号登录
  const isPhone = /^1\d{10}$/.test(account)
  const payload = isPhone ? { phone: account, password } : { username: account, password }
  return http.post('/auth/login', payload)
}

/** 刷新令牌 */
export function refreshToken(refresh_token) {
  return http.post('/auth/refresh', { refresh_token }, { skipAuth: true })
}

/** 登出 */
export function logout() {
  return http.post('/auth/logout')
}

/** 当前用户信息 */
export function getMe() {
  return http.get('/auth/me')
}

/** 修改密码（old_password / new_password 均为 SHA-256 散列值；初始密码强制改密也走此接口） */
export function changePassword(old_password, new_password) {
  return http.post('/auth/password/change', { old_password, new_password })
}
