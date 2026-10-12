/** 令牌本地存储（API 规范 1.2：access 2 小时，refresh 7 天） */
const ACCESS_KEY = 'alumni_admin_access_token'
const REFRESH_KEY = 'alumni_admin_refresh_token'
const EXPIRES_KEY = 'alumni_admin_token_expires_at'
const USER_KEY = 'alumni_admin_user'

export function getToken() {
  return localStorage.getItem(ACCESS_KEY) || ''
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY) || ''
}

export function setTokens({ access_token, refresh_token, expires_in }) {
  if (access_token) localStorage.setItem(ACCESS_KEY, access_token)
  if (refresh_token) localStorage.setItem(REFRESH_KEY, refresh_token)
  if (expires_in) {
    localStorage.setItem(EXPIRES_KEY, String(Date.now() + expires_in * 1000))
  }
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(EXPIRES_KEY)
}

export function getCachedUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export function setCachedUser(user) {
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
  else localStorage.removeItem(USER_KEY)
}
