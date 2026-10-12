/**
 * 首页轮播管理（门户 Hero）：/hero/slides 公开读 + /admin/hero/slides 维护
 * 后端：showcase 模块 HeroSlideController（school_admin / system_admin）；
 * 图片为 base64 data URL（前端压缩 ≤500KB）或 URL。
 */
import http from './request'

/** 公开列表（仅 published；一般不用，管理页走 adminList） */
export function getHeroSlides() {
  return http.get('/hero/slides')
}

/** 管理列表（全状态） */
export function getHeroAdminList() {
  return http.get('/admin/hero/slides')
}

/** 新增轮播位 */
export function createHeroSlide(body) {
  return http.post('/admin/hero/slides', body)
}

/** 修改轮播位（字段可选） */
export function updateHeroSlide(id, body) {
  return http.patch(`/admin/hero/slides/${id}`, body)
}

/** 删除轮播位 */
export function deleteHeroSlide(id) {
  return http.delete(`/admin/hero/slides/${id}`)
}
