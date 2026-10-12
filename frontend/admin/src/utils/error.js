/**
 * 业务错误对象（与 H5 端同契约：HTTP 状态码 + code 判断业务状态）
 */
export class BizError extends Error {
  constructor(code, message, status, data = null, requestId = '') {
    super(message)
    this.name = 'BizError'
    this.code = code
    this.status = status
    this.data = data
    this.requestId = requestId
  }
}

export const STATUS_TEXT = {
  400: '请求参数有误',
  401: '登录已过期，请重新登录',
  403: '无权限执行该操作',
  404: '请求的资源不存在',
  409: '操作冲突，请稍后重试',
  429: '操作过于频繁，请稍后再试',
  500: '服务器内部错误',
  502: '服务暂不可用，请稍后重试',
}
