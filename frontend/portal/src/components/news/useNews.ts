// ============================================================================
// 最新动态 · 真实公告接口数据源（模块级缓存，首页模块与列表页共用一次请求）
//
// 数据源：GET /announcements（仅 published），映射为组件既有的 NewsItem 结构：
//   title → 标题；content 截取 → 摘要 / 按空行分段 → 正文；published_at/created_at → 日期；
//   category 固定「通知公告」；封面图沿用 mock 既有封面循环兜底（接口无图字段）。
// 接口失败：回退 src/mock/news.ts 演示数据（console.warn 注明）；成功但为空：state = 'empty'。
// ============================================================================
import { computed, onMounted, ref } from 'vue';
import { api, type AnnouncementItem } from '../../api';
import { allNews as mockAll, featuredNews as mockFeatured, latestNews as mockLatest, type NewsItem } from '../../mock/news';

export type NewsState = 'loading' | 'ok' | 'empty' | 'error';

/** 展示用日期：ISO → 2026.09.18（与 mock 既有格式一致，可直接参与字符串排序） */
export function fmtNewsDate(iso?: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())}`;
}

/** 摘要：剔除图片标记后空白归一截取前 60 字 */
const toSummary = (content: string): string => {
  const plain = content
    .replace(/\[\[alumni-img:[^\]|]+\|\d+\]\]/g, '[图片]')
    .replace(/\s+/g, ' ')
    .trim();
  return plain.length > 60 ? `${plain.slice(0, 60)}…` : plain;
};

/** 正文：按换行分段；无有效段落时以摘要兜底，保证详情页不空 */
const toBody = (content: string, summary: string): string[] => {
  const parts = content
    .split(/\n+/)
    .map((s) => s.trim())
    .filter(Boolean);
  return parts.length ? parts : summary ? [summary] : [];
};

/** AnnouncementItem → NewsItem（组件层结构不变；封面取公告 cover_image，无封面则 image 置空由各展示位隐藏图区） */
export function mapAnnouncement(a: AnnouncementItem): NewsItem {
  const summary = toSummary(a.content ?? '');
  return {
    id: a.id,
    category: '通知公告',
    date: fmtNewsDate(a.published_at ?? a.created_at),
    title: a.title ?? '',
    summary,
    image: a.cover_image || '',
    body: toBody(a.content ?? '', summary)
  };
}

// ------------------------------ 模块级缓存 ------------------------------
const list = ref<NewsItem[]>([]);
const state = ref<NewsState>('loading');
let inflight: Promise<void> | null = null;

async function fetchNews(): Promise<void> {
  if (state.value !== 'loading') return;
  if (inflight) return inflight;
  inflight = (async () => {
    try {
      const res = await api.announcements.list({ page: '1', page_size: '20' });
      const mapped = (res.list ?? []).map(mapAnnouncement).sort((a, b) => b.date.localeCompare(a.date));
      list.value = mapped;
      state.value = mapped.length ? 'ok' : 'empty';
    } catch (e) {
      console.warn('[latest-news] 公告接口请求失败，回退演示数据：', e);
      state.value = 'error';
    }
  })();
  try {
    await inflight;
  } finally {
    inflight = null;
  }
}

/** 首页模块 / 列表页共用：挂载时触发一次请求（模块级缓存，重复调用不重复请求） */
export function useNews() {
  onMounted(() => {
    void fetchNews();
  });

  // 接口失败时回退 mock 演示数据（保持现有展示现状）
  const all = computed(() => (state.value === 'error' ? mockAll : list.value));
  // 头条取前 5 条，其余进右侧"最新资讯"（mock 回退时沿用既有 FEATURED 拆分）
  const featured = computed(() => (state.value === 'error' ? mockFeatured : all.value.slice(0, 5)));
  const latest = computed(() => (state.value === 'error' ? mockLatest : all.value.slice(5)));

  return { all, featured, latest, state, reload: fetchNews };
}
