/**
 * Markdown 渲染工具（门户公告详情等只读场景）
 * - marked GFM + 换行即 <br>
 * - 图片标记 [[alumni-img:src|宽度]] 转 <img>：src 为后端静态资源 URL，兼容旧 base64 内容
 * - 基础 XSS 清理：去除 script 标签、on* 事件属性、javascript: 协议
 */
import { marked } from 'marked';

marked.setOptions({ gfm: true, breaks: true });

/** 编辑器图片标记：[[alumni-img:src|宽度]] */
const IMG_RE = /\[\[alumni-img:([^\]|]+)\|(\d+)\]\]/g;

/** 标记中的 src 还原为可展示的 URL（URL 直用；否则按旧 base64 处理，与后台 imgSrcOf 对齐） */
export function imgSrcOf(src: string): string {
  if (/^(data:|\/|https?:)/.test(src)) return src;
  const mime = src.startsWith('/9j/')
    ? 'image/jpeg'
    : src.startsWith('R0lGOD')
      ? 'image/gif'
      : src.startsWith('UklGR')
        ? 'image/webp'
        : 'image/png';
  return `data:${mime};base64,${src}`;
}

/** 渲染 markdown 源码为可安全插入的 HTML（含图片标记转 <img>） */
export function renderMarkdown(src: string): string {
  if (!src) return '';
  const html = marked.parse(src) as string;
  const withImgs = html.replace(IMG_RE, (_, s: string, w: string) => {
    const width = Math.min(Number(w) || 480, 720);
    return `<img src="${imgSrcOf(s)}" style="width:${width}px;max-width:100%" loading="lazy" />`;
  });
  return sanitize(withImgs);
}

/** 粗略判断一段文本是否为 Markdown 格式（含标题/加粗/列表/链接/图片标记/代码块） */
export function looksLikeMarkdown(src: string): boolean {
  if (!src) return false;
  return /(^|\n)#{1,6}\s|\*\*[^*]+\*\*|(^|\n)\s*[-*+]\s+\S|!\[[^\]]*\]\([^)]+\)|\[[^\]]+\]\([^)]+\)|```|\[\[alumni-img:/.test(
    src
  );
}

function sanitize(html: string): string {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/\son\w+\s*=\s*"[^"]*"/gi, '')
    .replace(/\son\w+\s*=\s*'[^']*'/gi, '')
    .replace(/(href|src)\s*=\s*"\s*javascript:[^"]*"/gi, '$1="#"');
}
