// 校友个人中心（/profile/*）共享工具：状态字典、常量、Toast、附件上传与预览
// 对齐旧版 F2 前台 components/profile/shared.tsx 与 H5 端字典
import { onBeforeUnmount, ref } from 'vue';
import { api, ApiError, BASE_URL, tokenStore, USE_MOCK, type Attachment } from '../../api';

/* ================= 字典常量 ================= */

/** 资料类别（档案 category 自由扩展，前端展示层字典） */
export const ARCHIVE_CATEGORIES: { label: string; value: string }[] = [
  { label: '获奖经历', value: 'award' },
  { label: '证书资质', value: 'certificate' },
  { label: '实践经历', value: 'experience' },
  { label: '就业履历', value: 'career' },
  { label: '其他资料', value: 'other' }
];

/** 荣誉级别（API 规范 5.1） */
export const HONOR_LEVELS: { label: string; value: string }[] = [
  { label: '国家级', value: 'national' },
  { label: '省级', value: 'provincial' },
  { label: '市级', value: 'city' },
  { label: '校级', value: 'school' }
];

/** 荣誉类别兜底字典（字典接口失败时使用） */
export const HONOR_CATEGORY_FALLBACK: { label: string; value: string }[] = [
  { label: '奖学金', value: 'scholarship' },
  { label: '竞赛获奖', value: 'competition' },
  { label: '荣誉称号', value: 'title' },
  { label: '职业证书', value: 'certificate' },
  { label: '其他荣誉', value: 'other' }
];

/** 档案状态机 → 展示文案与色调（API 规范 4.1） */
export const ARCHIVE_STATUS: Record<string, { text: string; color: string }> = {
  draft: { text: '草稿', color: '#6b7280' },
  pending_first: { text: '待院系初审', color: '#d97706' },
  pending_second: { text: '待校级复核', color: '#2563eb' },
  approved: { text: '已通过', color: '#16a34a' },
  rejected: { text: '已驳回', color: '#dc2626' },
  deleting: { text: '删除审核中', color: '#6b7280' },
  deleted: { text: '已删除', color: '#9ca3af' }
};

export function statusOf(status?: string): { text: string; color: string } {
  return ARCHIVE_STATUS[status ?? ''] ?? { text: status || '—', color: '#6b7280' };
}

export const categoryLabel = (v?: string) =>
  ARCHIVE_CATEGORIES.find((c) => c.value === v)?.label ?? v ?? '';

export const levelLabel = (v?: string) => HONOR_LEVELS.find((l) => l.value === v)?.label ?? v ?? '';

/* ================= 附件上传公共逻辑（API 规范 6.1） ================= */

export interface UploadedAtt {
  id: number;
  name: string;
  size: number;
  mime: string;
}

/** 单次请求文件数上限（API 规范 6.1） */
export const MAX_PER_REQUEST = 5;
/** 单文件大小上限 10MB */
export const MAX_FILE_SIZE = 10 * 1024 * 1024;
/** 类型白名单：jpg / png / webp / pdf */
export const allowMime = (f: File) =>
  /^image\/(jpeg|png|webp)$/.test(f.type) || f.type === 'application/pdf';

/**
 * 显式拒收的格式（iPhone 相册默认 HEIC 暂不支持）：
 * - HEIC/HEIF：体积小但浏览器/服务端识别差，前端拒收以避免 422 报错
 * - 提示用户"请在相册选择 JPEG / PNG"即可
 */
const HEIC_NAME_RE = /\.(heic|heif)$/i;

/** 前端预校验：返回（合法文件, 失败清单） */
export function prevalidateFiles(files: File[]): {
  ok: File[];
  failed: { name: string; reason: string }[];
} {
  const failed: { name: string; reason: string }[] = [];
  const ok = files.filter((f) => {
    // 1) HEIC/HEIF 黑名单（iPhone 相册常见）
    if (HEIC_NAME_RE.test(f.name) || /image\/(heic|heif)/.test(f.type)) {
      failed.push({
        name: f.name,
        reason: '暂不支持 HEIC/HEIF 格式，请在相册选择 JPEG/PNG 后再上传'
      });
      return false;
    }
    // 2) 类型白名单
    if (!allowMime(f)) {
      failed.push({ name: f.name, reason: '文件类型不允许（仅 jpg/png/webp/pdf）' });
      return false;
    }
    // 3) 大小
    if (f.size > MAX_FILE_SIZE) {
      failed.push({ name: f.name, reason: '文件大小超限（≤10MB）' });
      return false;
    }
    return true;
  });
  return { ok, failed };
}

/** 分批上传（≤5 个/次），返回成功与失败清单；单批失败不影响其他批 */
export async function uploadAttachmentsChunked(
  files: File[],
  bizType: string,
  remark?: string
): Promise<{ success: UploadedAtt[]; failed: { name: string; reason: string }[] }> {
  const ok: UploadedAtt[] = [];
  const failed: { name: string; reason: string }[] = [];
  for (let i = 0; i < files.length; i += MAX_PER_REQUEST) {
    const batch = files.slice(i, i + MAX_PER_REQUEST);
    try {
      const d = await api.attachments.upload({ biz_type: bizType, remark, files: batch });
      ok.push(
        ...(d.success || []).map((s) => ({
          id: s.id as number,
          name: s.name || '附件',
          size: s.size || 0,
          mime: s.mime || ''
        }))
      );
      (d.failed || []).forEach((f) => failed.push({ name: f.name, reason: f.reason || '上传失败' }));
    } catch (e) {
      const reason = e instanceof ApiError ? e.message : '上传失败';
      batch.forEach((f) => failed.push({ name: f.name, reason }));
    }
  }
  return { success: ok, failed };
}

/* ================= 附件预览 ================= */

/**
 * 附件预览（API 规范 6.2）：预览接口需要登录令牌，不能用裸 <a href>（浏览器不带
 * Authorization 头会 401）；必须带令牌拉取 blob，生成对象 URL 后新标签页打开。
 * mock 模式无真实文件流，直接打开附件记录的 url 字段。
 */
export async function previewAttachment(att: Pick<Attachment, 'id' | 'url'>): Promise<void> {
  if (USE_MOCK) {
    if (att.url) window.open(att.url, '_blank');
    return;
  }
  const res = await fetch(`${BASE_URL}/attachments/${att.id}/preview`, {
    headers: tokenStore.access ? { Authorization: `Bearer ${tokenStore.access}` } : {}
  });
  if (!res.ok) throw new ApiError(res.status, 'PREVIEW_FAILED', '附件预览失败，请稍后重试');
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank');
  // 延迟回收：新标签页加载完成前回收会导致打开空白
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/**
 * 带令牌拉取附件预览流并转为 <img> 可用的 Object URL（我的相册等需要内联展示图片的场景）。
 * 调用方负责在组件卸载时 URL.revokeObjectURL 回收；失败返回空串（调用处渲染占位）。
 */
export async function fetchAttachmentObjectUrl(attachmentId: number): Promise<string> {
  if (USE_MOCK) return '';
  try {
    const res = await fetch(`${BASE_URL}/attachments/${attachmentId}/preview`, {
      headers: tokenStore.access ? { Authorization: `Bearer ${tokenStore.access}` } : {}
    });
    if (!res.ok) return '';
    return URL.createObjectURL(await res.blob());
  } catch {
    return '';
  }
}

/* ================= 格式化 ================= */

/** ISO 8601 UTC → 本地 yyyy-MM-dd HH:mm */
export function fmtTime(iso?: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** ISO 8601 UTC → 本地 yyyy-MM-dd */
export function fmtDate(iso?: string): string {
  return fmtTime(iso).slice(0, 10);
}

/** 文件大小友好显示 */
export function fmtSize(bytes?: number): string {
  if (bytes == null) return '—';
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)}KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
}

/* ================= Toast ================= */

export interface ToastState {
  msg: string;
  type: 'ok' | 'err';
}

/** 轻量 toast：show(msg, type)，3s 自动消失；页面渲染 <ToastBar :toast="toast" /> */
export function useToast() {
  const toast = ref<ToastState | null>(null);
  let timer: number | undefined;
  const show = (msg: string, type: 'ok' | 'err' = 'ok') => {
    toast.value = { msg, type };
    window.clearTimeout(timer);
    timer = window.setTimeout(() => (toast.value = null), 3000);
  };
  onBeforeUnmount(() => window.clearTimeout(timer));
  return { toast, show };
}
