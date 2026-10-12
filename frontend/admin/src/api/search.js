/**
 * 增强检索 + 关键词联想（M13 · P1）
 * - GET /search         ?keyword=&types=&college_id=&grad_year=&tag_ids=&include_all=&page=&page_size=
 *   types: 逗号分隔（honor/archive/alumni）
 * - GET /search/suggest ?keyword=&limit=
 */
import http from './request'

/** 检索类型字典（与后端 SearchService 的 types 集合对齐） */
export const SEARCH_TYPES = [
  { value: 'honor', label: '荣誉' },
  { value: 'archive', label: '档案' },
  { value: 'alumni', label: '校友' },
]

/**
 * 聚合检索
 * @param {Object} params { keyword, types, college_id, grad_year, tag_ids, include_all, page, page_size }
 *  - types   string  逗号分隔（honor,archive,alumni）
 *  - tag_ids string  逗号分隔
 */
export function search(params = {}) {
  return http.get('/search', { params })
}

/**
 * 关键词联想（用于顶栏实时提示）
 * @param {string} keyword
 * @param {number} [limit]  默认 10，上限 20
 */
export function suggest(keyword, limit = 10) {
  return http.get('/search/suggest', { params: { keyword, limit } })
}
