/**
 * 统一请求封装（与 H5 端同契约）
 * 401 → 静默刷新一次 → 失败跳登录页；响应拆解 {code, message, data, request_id}
 */
import axios from 'axios'
import { BizError, STATUS_TEXT } from '@/utils/error'
import { getToken, getRefreshToken, setTokens, clearTokens } from '@/utils/token'
import mockAdapter from './mock'

const BASE_URL = import.meta.env.VITE_API_BASE || '/api/v1'
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

const http = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
})

// 后端未就绪期间走本地 mock（前端先行策略）
if (USE_MOCK) {
  http.defaults.adapter = mockAdapter
}

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token && !config.skipAuth) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshing = null
let forceLogout = null

export function onForceLogout(fn) {
  forceLogout = fn
}

function doLogout() {
  clearTokens()
  if (typeof forceLogout === 'function') forceLogout()
}

function silentRefresh() {
  if (!refreshing) {
    refreshing = http
      .post('/auth/refresh', { refresh_token: getRefreshToken() }, { skipAuth: true, _isRefresh: true })
      .then((res) => {
        setTokens(res.data.data)
        return true
      })
      .catch(() => false)
      .finally(() => {
        setTimeout(() => {
          refreshing = null
        }, 0)
      })
  }
  return refreshing
}

http.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const { response, config } = error

    if (!response) {
      throw new BizError('NETWORK_ERROR', '网络连接失败，请检查网络后重试', 0)
    }

    const status = response.status
    const body = response.data || {}
    const code = body.code || ''
    const message = body.message || STATUS_TEXT[status] || '请求失败'

    if (status === 401 && !config._isRefresh && !config._retried && getRefreshToken()) {
      config._retried = true
      const ok = await silentRefresh()
      if (ok) {
        config.headers = { ...config.headers }
        config.headers.Authorization = `Bearer ${getToken()}`
        return http.request(config)
      }
      doLogout()
      throw new BizError('UNAUTHORIZED', message, status, body.data, body.request_id)
    }

    throw new BizError(code || 'HTTP_' + status, message, status, body.data, body.request_id)
  },
)

export default http
