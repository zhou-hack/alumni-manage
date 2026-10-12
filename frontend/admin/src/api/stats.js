/** 统计模块（API 规范第十章）：总量看板 / 荣誉分布 / 学院排行 */
import http from './request'

/** 总量看板：校友数/档案数/荣誉数/待审数 */
export function getOverview() {
  return http.get('/stats/overview')
}

/** 荣誉分布（按类别/级别，仅统计 Honor 表） */
export function getHonorStats() {
  return http.get('/stats/honors')
}

/** 学院排行（档案数 Top N） */
export function getCollegeRanking(limit = 8) {
  return http.get('/stats/colleges', { params: { limit } })
}
