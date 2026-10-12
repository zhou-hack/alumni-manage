/**
 * 站内消息中心接口（M14）
 * - 我的消息列表 / 未读数 / 单条已读 / 全部已读
 * - 通知订阅：订阅设置 / 我的订阅
 *
 * 后端约定（与 notifications/NotificationController.java 一致）：
 *   GET    /api/v1/notifications              ?type=&unread=&page=&page_size=
 *   GET    /api/v1/notifications/unread-count
 *   POST   /api/v1/notifications/{id}/read
 *   POST   /api/v1/notifications/read-all     ?type=
 *   GET    /api/v1/notification-subscriptions
 *   PUT    /api/v1/notification-subscriptions   body: { categories: [...] }
 */
import http from './request'

/** 消息类型 → 中文/颜色（与后端枚举一致；前端兜底用） */
export const NOTIF_TYPE = {
  review: { text: '审核', tone: 'warning' },
  honor: { text: '荣誉', tone: 'success' },
  archive: { text: '档案', tone: 'primary' },
  announcement: { text: '公告', tone: 'info' },
  system: { text: '系统', tone: 'danger' },
  wall: { text: '留言墙', tone: 'warning' },
  consent: { text: '授权', tone: 'info' },
}
export function typeOf(t) {
  return NOTIF_TYPE[t] || { text: t || '其他', tone: 'info' }
}

/**
 * 我的消息分页
 * @param {Object} params { type, unread, page, page_size }
 */
export function getNotifications(params = {}) {
  return http.get('/notifications', { params })
}

/** 未读数（红点） → { total, by_type: {type: count} } */
export function getUnreadCount() {
  return http.get('/notifications/unread-count')
}

/** 单条已读（幂等） */
export function markRead(id) {
  return http.post(`/notifications/${id}/read`)
}

/**
 * 全部已读（可按类型过滤）
 * @param {string} [type] 留空表示所有类型
 */
export function markAllRead(type) {
  const params = type ? { type } : undefined
  return http.post('/notifications/read-all', null, { params })
}

/** 订阅场景字典（与后端 SubscriptionService.SCENES 对齐：review_result / announcement） */
export const SUBSCRIPTION_SCENES = [
  { key: 'review_result', label: '审核结果' },
  { key: 'announcement', label: '公告通知' },
]

/**
 * 我的订阅配置（SMS / Email 两个渠道，分别 enabled + scenes + email.address）
 * @returns {Promise<{data:{sms:{enabled,scenes:[]},email:{enabled,address,scenes:[]}}>}
 */
export function getSubscriptions() {
  return http.get('/notification-subscriptions/my')
}

/**
 * 保存订阅（覆盖式；后端期望 sms + email 同时提交）
 * @param {{sms:{enabled,scenes:string[]},email:{enabled,address,scenes:string[]}}} body
 */
export function saveSubscriptions(body) {
  return http.put('/notification-subscriptions', body)
}