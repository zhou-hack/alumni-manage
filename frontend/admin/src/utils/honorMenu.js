/**
 * 荣誉管理菜单（school_admin / system_admin）按学院展开 — 已禁用
 *
 * 背景：
 *   早期为了校级管理员"按学院分屏"加过展开式二级子菜单，但实际使用率极低，
 *   用户反馈侧栏学院列表是噪音（每次刷新都看到一长串），已统一移除。
 *
 * 当前策略：所有角色统一保留原始静态菜单（ROLE_MENUS），不再展开学院子菜单。
 * 学院列表的查询仍在 /honors 页内通过"学院"下拉筛选项提供。
 *
 * 注：
 *   - 本文件保留 `buildMenusWithColleges` 入口（其它模块仍在 import），
 *     但函数体改为透传，不再做任何替换。
 *   - `readCollegeIdFromQuery` 保留：HonorListPage 通过路由 query 预筛仍可用。
 */

/**
 * @deprecated 不再展开学院子菜单。保留签名以兼容旧 import。
 * @returns {Array} 与 baseMenus 同结构的拷贝
 */
export function buildMenusWithColleges(role, baseMenus, _colleges = []) {
  return JSON.parse(JSON.stringify(baseMenus || []))
}

/**
 * 从路由 query 读取 college_id（字符串/数字都接受；非法值返回 null）
 */
export function readCollegeIdFromQuery(query) {
  const v = query?.college_id
  if (v == null || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}
