/**
 * 通知公告模块（清单第七章 / PRD M08）
 * - 正文图片标记 [[alumni-img:src|宽度]]：src 为后端静态资源 URL（/api/v1/announcements/images/{uuid}），
 *   旧内容为 base64（兼容渲染）；图片经 POST /announcements/images 上传落盘，不再内嵌正文
 * - 状态机：draft → published ↔ withdrawn（撤下后可重新发布）
 * - 列表/详情公开（仅 published 对匿名可见）；创建/维护/删除仅管理员
 */
import http from './request'

/** 公告状态 */
export const ANNOUNCEMENT_STATUS = {
  draft: { text: '草稿', tone: 'info' },
  published: { text: '已发布', tone: 'success' },
  withdrawn: { text: '已撤下', tone: 'warning' },
}

/** 上传公告图片（静态资源存储）→ { id, uuid, url, filename, size } */
export function uploadAnnouncementImage(file) {
  const form = new FormData()
  form.append('file', file)
  return http.post('/announcements/images', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 图片标记工具：[[alumni-img:src|宽度]]（src 为静态资源 URL；兼容旧 base64 内容） */
export const IMG_TOKEN = {
  re: /\[\[alumni-img:([^\]|]+)\|(\d+)\]\]/g,
  encode(src, width) {
    return `[[alumni-img:${src}|${width}]]`
  },
  decode(content) {
    const out = []
    let m
    IMG_TOKEN.re.lastIndex = 0
    while ((m = IMG_TOKEN.re.exec(content)) !== null) {
      out.push({ src: m[1], width: Number(m[2]), raw: m[0] })
    }
    return out
  },
}

/** 标记中的 src 还原为可展示的 URL（URL 直用；否则按旧 base64 处理） */
export function imgSrcOf(src) {
  if (/^(data:|\/|https?:)/.test(src)) return src
  const mime = src.startsWith('/9j/') ? 'image/jpeg' : src.startsWith('R0lGOD') ? 'image/gif' : src.startsWith('UklGR') ? 'image/webp' : 'image/png'
  return `data:${mime};base64,${src}`
}

/** 将 content 渲染为 HTML（文本换行 + 图片标记转 <img>，输出已转义安全） */
export function renderContentHtml(content = '') {
  const esc = (s) =>
    s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
  // split 捕获组（src、宽度）成对插入：[文本, src, 宽度, 文本, ...]
  const parts = String(content).split(IMG_TOKEN.re)
  return parts
    .map((seg, i) => {
      const t = i % 3
      if (t === 1) {
        const width = Math.min(Number(parts[i + 1]) || 480, 720)
        return `<img class="rich-img" src="${imgSrcOf(seg)}" style="width:${width}px" loading="lazy" />`
      }
      if (t === 2) return '' // 宽度段已并入 img
      return esc(seg).replace(/\n/g, '<br>')
    })
    .join('')
}

/**
 * 公告分页（公开：匿名仅见 published；管理员可传 status 检索草稿/撤下）
 * @param {Object} params { page, page_size, status, year }
 */
export function getAnnouncements(params = {}) {
  return http.get('/announcements', { params })
}

/** 公告详情（含 content / prev_id / next_id / related） */
export function getAnnouncement(id) {
  return http.get(`/announcements/${id}`)
}

/** 创建公告（初始为草稿） */
export function createAnnouncement(payload) {
  return http.post('/announcements', payload)
}

/** 更新公告：内容（title/content）与流转（status: published/withdrawn）可单独或一起提交 */
export function updateAnnouncement(id, payload) {
  return http.patch(`/announcements/${id}`, payload)
}

/** 删除公告 */
export function deleteAnnouncement(id) {
  return http.delete(`/announcements/${id}`)
}
