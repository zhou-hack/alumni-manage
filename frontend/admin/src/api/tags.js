/**
 * 标签管理（M13 · 仅 school_admin 可写）
 * - GET    /tags                          列表（?group=&keyword=&page=&page_size=）
 * - POST   /tags                          创建（name + group + sort）
 * - PATCH  /tags/{id}                     改名 / 调排序
 * - DELETE /tags/{id}                     删除（被引用 409）
 * - PUT    /tags/bindings/{bizType}/{bizId}  全量覆盖式绑定
 * - GET    /tags/bindings/{bizType}/{bizId}  查询已绑标签
 */
import http from './request'

/** 标签分组（约定：行业 / 兴趣 / 届别 / 自定义） */
export const TAG_GROUP_OPTIONS = [
  { value: 'industry', label: '行业' },
  { value: 'interest', label: '兴趣' },
  { value: 'year', label: '届别' },
  { value: 'skill', label: '技能' },
  { value: 'custom', label: '自定义' },
]

export function getTags(params = {}) {
  return http.get('/tags', { params })
}

export function createTag(payload) {
  return http.post('/tags', payload)
}

export function updateTag(id, payload) {
  return http.patch(`/tags/${id}`, payload)
}

export function deleteTag(id) {
  return http.delete(`/tags/${id}`)
}

/** 全量覆盖式设置某业务的标签 */
export function bindTags(bizType, bizId, tagIds) {
  return http.put(`/tags/bindings/${bizType}/${bizId}`, { tag_ids: tagIds })
}

/** 查询某业务的已绑标签 */
export function getBoundTags(bizType, bizId) {
  return http.get(`/tags/bindings/${bizType}/${bizId}`)
}