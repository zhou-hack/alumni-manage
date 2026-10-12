<script setup lang="ts">
// 动态详情页（/news/:id）：分类 / 日期 / 标题 / 封面 / 正文 + 更多动态
// 数据：按 id 调 /announcements/{id}，失败回退 mock 按 id 查找；"更多动态"复用列表数据源
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ArrowLeft, ChevronRight } from '../components/icons';
import NewsShell from '../components/news/NewsShell.vue';
import { getNewsById, type NewsItem } from '../mock/news';
import { api } from '../api';
import { mapAnnouncement, useNews } from '../components/news/useNews';
import { renderMarkdown, looksLikeMarkdown } from '../utils/markdown';
import ImageViewer from '../components/ImageViewer.vue';

/** 图片查看器（与后台荣誉审核 el-image-viewer 同一交互：点击正文图片全屏打开） */
const viewerSrc = ref('');
const onBodyClick = (e: MouseEvent) => {
  const t = e.target as HTMLElement;
  if (t.tagName === 'IMG') viewerSrc.value = (t as HTMLImageElement).src;
};

const route = useRoute();
const { all } = useNews();

const news = ref<NewsItem | null>(null);
/** 公告原文（接口数据；用于 Markdown 检测与渲染） */
const rawContent = ref('');
// 加载完成后仍未找到（接口 404 且 mock 无此 id）→ 展示"未找到"提示
const notFound = ref(false);

/** 正文为 Markdown 时按 marked 渲染，否则按既有分段纯文本展示 */
const isMarkdown = computed(() => looksLikeMarkdown(rawContent.value));
const bodyHtml = computed(() => renderMarkdown(rawContent.value));

watch(
  () => route.params.id,
  async (raw) => {
    const id = Number(raw);
    news.value = null;
    rawContent.value = '';
    notFound.value = false;
    if (!Number.isFinite(id)) {
      notFound.value = true;
      return;
    }
    try {
      const d = await api.announcements.detail(id);
      rawContent.value = d.content ?? '';
      news.value = mapAnnouncement(d);
    } catch (e) {
      // 接口失败（含 404）：回退 mock 演示数据按 id 查找
      console.warn(`[news-detail] 公告详情接口请求失败（id=${id}），回退演示数据：`, e);
      const fallback = getNewsById(id);
      if (fallback) news.value = fallback;
      else notFound.value = true;
    }
  },
  { immediate: true }
);

const more = computed(() => {
  const current = news.value;
  if (!current) return [];
  return all.value.filter((n) => n.id !== current.id).slice(0, 3);
});
</script>

<template>
  <NewsShell>
    <!-- 未找到对应动态时的友好提示（不跳回首页、不留空白） -->
    <div
      v-if="notFound"
      class="rounded-2xl border border-lamp/25 bg-white px-8 py-14 text-center shadow-[0_10px_26px_rgba(26,16,61,0.06)]"
    >
      <p class="font-title text-xl font-bold text-ink">未找到该动态</p>
      <p class="mt-2 text-sm text-slate-500">该内容可能已被移除，或链接有误。</p>
      <RouterLink
        to="/news"
        class="mt-6 inline-flex items-center gap-1.5 rounded-full border border-lamp/40 bg-lamp/10 px-4 py-2 text-sm font-medium text-ink transition hover:bg-lamp/20"
      >
        查看全部动态
      </RouterLink>
    </div>

    <article v-else-if="news" class="mx-auto max-w-[900px]">
      <!-- 面包屑 -->
      <nav class="flex items-center gap-1.5 text-sm text-slate-400">
        <RouterLink to="/" class="transition-colors hover:text-tech"> 首页 </RouterLink>
        <ChevronRight :size="12" />
        <RouterLink to="/news" class="transition-colors hover:text-tech"> 最新动态 </RouterLink>
        <ChevronRight :size="12" />
        <span class="truncate text-slate-500">{{ news.category }}</span>
      </nav>

      <!-- 标题 + 元信息 -->
      <header class="mt-5">
        <div class="flex items-center gap-2 text-sm">
          <span class="h-1.5 w-1.5 rounded-full bg-lamp" />
          <span class="font-medium tracking-wide text-tech">{{ news.category }}</span>
          <span class="h-3.5 w-px bg-slate-200" />
          <span class="font-code text-slate-400">{{ news.date }}</span>
        </div>
        <h1 class="font-title mt-3 text-2xl font-black leading-snug tracking-[0.02em] text-ink md:text-3xl">
          {{ news.title }}
        </h1>
        <p class="mt-4 border-l-2 border-lamp/50 pl-4 text-base leading-relaxed text-slate-600">{{ news.summary }}</p>
      </header>

      <!-- 封面（公告无 AI 封面时不渲染图区） -->
      <div v-if="news.image" class="relative mt-7 aspect-[16/9] w-full overflow-hidden rounded-2xl border border-lamp/20 bg-warm">
        <img :src="news.image" :alt="news.title" class="absolute inset-0 h-full w-full object-cover" />
      </div>

      <!-- 正文：Markdown 内容按渲染结果展示（图片点击全屏查看），纯文本按分段展示 -->
      <div v-if="isMarkdown" class="md-body mt-8" v-html="bodyHtml" @click="onBodyClick" />
      <div v-else class="mt-8 space-y-5">
        <p v-for="(p, i) in news.body" :key="i" class="text-base leading-[1.95] text-slate-700">
          {{ p }}
        </p>
      </div>

      <!-- 更多动态 -->
      <section v-if="more.length" class="mt-12">
        <div class="flex items-center gap-2.5">
          <span class="h-3.5 w-[3px] rounded-full bg-lamp" />
          <h2 class="font-title text-lg font-bold tracking-wide text-ink">更多动态</h2>
        </div>
        <ul class="divide-y divide-night/5 overflow-hidden rounded-2xl border border-lamp/25 bg-white">
          <li v-for="n in more" :key="n.id">
            <RouterLink :to="`/news/${n.id}`" class="group flex items-center justify-between gap-4 px-5 py-4 transition-colors hover:bg-warm">
              <div class="min-w-0">
                <div class="flex items-center gap-2 text-sm">
                  <span class="font-medium text-tech">{{ n.category }}</span>
                  <span class="font-code text-slate-400">{{ n.date }}</span>
                </div>
                <p class="mt-1.5 line-clamp-1 text-base text-ink transition-colors group-hover:text-tech">
                  {{ n.title }}
                </p>
              </div>
              <ChevronRight
                :size="16"
                class="shrink-0 text-slate-300 transition-transform duration-300 group-hover:translate-x-0.5 group-hover:text-lamp"
              />
            </RouterLink>
          </li>
        </ul>
      </section>

      <div class="mt-10">
        <RouterLink to="/news" class="inline-flex items-center gap-1.5 text-sm font-medium text-tech transition-colors hover:text-ink">
          <ArrowLeft :size="15" />
          返回最新动态
        </RouterLink>
      </div>
    </article>

    <!-- 图片查看器（同后台荣誉审核 el-image-viewer 交互） -->
    <ImageViewer v-if="viewerSrc" :url="viewerSrc" @close="viewerSrc = ''" />
  </NewsShell>
</template>

<style scoped>
/* Markdown 正文排版（与后台预览同一套视觉） */
.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3) {
  margin: 22px 0 12px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--color-ink, #1a103d);
}
.md-body :deep(h1) { font-size: 24px; }
.md-body :deep(h2) { font-size: 20px; }
.md-body :deep(h3) { font-size: 17px; }
.md-body :deep(p) {
  margin: 12px 0;
  font-size: 16px;
  line-height: 1.95;
  color: #334155;
}
.md-body :deep(ul),
.md-body :deep(ol) {
  margin: 12px 0;
  padding-left: 26px;
  font-size: 16px;
  line-height: 1.95;
  color: #334155;
}
.md-body :deep(blockquote) {
  margin: 14px 0;
  padding: 10px 16px;
  border-left: 3px solid #f5b301;
  background: #fff9f0;
  color: #64748b;
}
.md-body :deep(code) {
  padding: 2px 6px;
  border-radius: 4px;
  background: #f1f5f9;
  font-family: Consolas, monospace;
  font-size: 14px;
}
.md-body :deep(pre) {
  margin: 14px 0;
  padding: 14px 16px;
  border-radius: 10px;
  background: #1e293b;
  color: #e2e8f0;
  overflow-x: auto;
}
.md-body :deep(pre code) {
  padding: 0;
  background: none;
  color: inherit;
}
.md-body :deep(img) {
  display: block;
  margin: 14px auto; /* 与后台公告预览同一口径：图片居中 */
  max-width: 100%;
  border-radius: 12px;
  cursor: zoom-in;
}
.md-body :deep(table) {
  margin: 14px 0;
  border-collapse: collapse;
  font-size: 15px;
}
.md-body :deep(th),
.md-body :deep(td) {
  padding: 8px 14px;
  border: 1px solid #e2e8f0;
}
.md-body :deep(a) {
  color: #2563eb;
}
</style>
