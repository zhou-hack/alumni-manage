/**
 * Markdown 渲染工具（公告正文等）
 * - marked GFM + 换行即 <br>
 * - 基础 XSS 清理：去除 script 标签、on* 事件属性、javascript: 协议
 */
import { marked } from 'marked'

marked.setOptions({ gfm: true, breaks: true })

/** 渲染 markdown 源码为可安全插入的 HTML */
export function renderMarkdown(src) {
  if (!src) return ''
  return sanitize(marked.parse(src))
}

/** 粗略判断一段文本是否为 Markdown 格式（含标题/加粗/列表/链接/图片/代码块标记） */
export function looksLikeMarkdown(src) {
  if (!src) return false
  return /(^|\n)#{1,6}\s|\*\*[^*]+\*\*|(^|\n)\s*[-*+]\s+\S|!\[[^\]]*\]\([^)]+\)|\[[^\]]+\]\([^)]+\)|```/.test(src)
}

function sanitize(html) {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/\son\w+\s*=\s*"[^"]*"/gi, '')
    .replace(/\son\w+\s*=\s*'[^']*'/gi, '')
    .replace(/(href|src)\s*=\s*"\s*javascript:[^"]*"/gi, '$1="#"')
}
