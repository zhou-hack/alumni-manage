/** 字典模块（API 规范第三章）：查询缓存 + 增删改（Day 3 字典管理） */
import http from './request'

const cache = {}

export async function getDict(type, force = false) {
  if (!force && cache[type]) return cache[type]
  const res = await http.get(`/dicts/${type}`)
  cache[type] = res.data || []
  return cache[type]
}

export function dictLabel(type, idOrValue) {
  const list = cache[type] || []
  const item = list.find((d) => d.id === Number(idOrValue) || String(d.value) === String(idOrValue))
  return item ? item.label : idOrValue
}

export function addDictItem(type, payload) {
  return http.post(`/dicts/${type}`, payload)
}

export function updateDictItem(type, id, payload) {
  return http.patch(`/dicts/${type}/${id}`, payload)
}

export function deleteDictItem(type, id) {
  return http.delete(`/dicts/${type}/${id}`)
}

/** 维护后使本地缓存失效 */
export function invalidateDict(type) {
  delete cache[type]
}
