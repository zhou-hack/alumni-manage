/**
 * 荣誉墙管理 API（showcase 模块 · 仅 school_admin / system_admin）
 *
 * 后端对应路径（ShowcaseController）：
 *   - POST /api/v1/showcase/{archive_id}/publish    上架
 *   - POST /api/v1/showcase/{archive_id}/unpublish  下架
 *   - GET  /api/v1/screen/feed                      已上架公开列表（免认证）
 *
 * 「可上架候选」不在 showcase 模块内，复用 archives 模块：
 *   - GET /api/v1/archives?status=approved&keyword=...
 *
 * 历史命名兼容：ArchiveListPage.vue 早期使用 publishArchive / unpublishArchive
 * （命名按"对档案 publish/unpublish"的视角），与 HonorWallManagePage.vue 的
 * publishShowcase / unpublishShowcase（命名按"对荣誉墙 showcase"的视角）并存。
 */
import http from './request'

/** 上架（按荣誉墙视角命名）：path = /api/v1/showcase/{archiveId}/publish */
export function publishShowcase(archiveId) {
  return http.post(`/showcase/${archiveId}/publish`)
}

/** 下架（按荣誉墙视角命名）：path = /api/v1/showcase/{archiveId}/unpublish */
export function unpublishShowcase(archiveId) {
  return http.post(`/showcase/${archiveId}/unpublish`)
}

/**
 * 上架（按档案视角命名）—— 历史别名，与 publishShowcase 等价。
 * ArchiveListPage.vue 早期代码沿用此命名。
 */
export const publishArchive = publishShowcase

/**
 * 下架（按档案视角命名）—— 历史别名，与 unpublishShowcase 等价。
 * ArchiveListPage.vue 早期代码沿用此命名。
 */
export const unpublishArchive = unpublishShowcase

/**
 * 公开大屏 feed（已上架档案）。
 * 注：仅 school_admin / system_admin 进入该管理页时调用；
 * 同样为公开接口，便于前端无需鉴权也能拉一份作快照比对。
 */
export function getScreenFeed(params = {}) {
  return http.get('/screen/feed', { params })
}

/**
 * 校友通讯录（公开 · /api/v1/alumni/directory）
 * AlumniDirectoryPage.vue（admin 校友侧）使用，公开免认证。
 */
export function getAlumniDirectory(params = {}) {
  return http.get('/alumni/directory', { params })
}

/**
 * 荣誉大屏聚合数据（公开 · /api/v1/dashboard-honor）
 * 用于 admin 端「荣誉大屏预览页」与 portal 端 /dashboard 同源数据快照比对。
 * HonorsScreenPreviewPage.vue 等页面复用此接口。
 */
export function dashboardHonor() {
  return http.get('/dashboard-honor')
}
