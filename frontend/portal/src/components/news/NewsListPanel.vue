<script setup lang="ts">
// 学校最新资讯（右侧）：2 列 × 3 行共 6 条 + 分页左右切换
// 每条：分类（主色）+ 日期（弱化）+ 标题（最多 2 行省略）
// hover：标题变色 + 底部短线 + 整体轻微上移（克制，不使用发光/缩放）
import { computed, ref, watch } from 'vue';
import { ChevronLeft, ChevronRight } from '../icons';
import { PAGE_SIZE, type NewsItem } from '../../mock/news';

const props = defineProps<{ items: NewsItem[]; active: boolean }>();

const page = ref(0);
const totalPages = computed(() => Math.max(1, Math.ceil(props.items.length / PAGE_SIZE)));
const pageItems = computed(() => props.items.slice(page.value * PAGE_SIZE, page.value * PAGE_SIZE + PAGE_SIZE));

const go = (next: number) => {
  page.value = Math.min(Math.max(next, 0), totalPages.value - 1);
};

// 行级入场（对应原 framer-motion variants）：
// active 变 true 或翻页时整组重播——先渲染 hidden 态，双 rAF 后切 show 态以触发过渡
const entered = ref(false);
const play = () => {
  entered.value = false;
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      entered.value = true;
    });
  });
};
watch(
  () => props.active,
  (v) => {
    if (v) play();
  },
  { immediate: true }
);
watch(page, () => {
  if (props.active) play();
});

// 每条 0.5s ease-out，按行（每行 2 条）错开 90ms
const rowStyle = (idx: number) => ({
  opacity: entered.value ? 1 : 0,
  transform: entered.value ? 'translateY(0px)' : 'translateY(8px)',
  transition: `opacity 0.5s ease-out ${Math.floor(idx / 2) * 0.09}s, transform 0.5s ease-out ${Math.floor(idx / 2) * 0.09}s`,
  willChange: 'opacity, transform'
});
</script>

<template>
  <div class="flex h-full min-w-0 flex-col rounded-2xl border border-lamp/25 bg-white p-6 shadow-[0_14px_34px_rgba(26,16,61,0.07)]">
    <!-- 面板头：标题 + 页码 + 左右切换（放在头部，不遮挡新闻内容） -->
    <div class="mb-5 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2.5">
        <span class="h-3.5 w-[3px] rounded-full bg-lamp" />
        <h3 class="font-title text-2xl font-bold tracking-wide text-ink">最新资讯</h3>
      </div>
      <div class="flex items-center gap-2">
        <span class="font-code mr-0.5 text-sm text-slate-400"> {{ page + 1 }}/{{ totalPages }} </span>
        <button
          type="button"
          aria-label="上一页"
          :disabled="page === 0"
          class="flex h-8 w-8 items-center justify-center rounded-full border border-lamp/40 bg-white text-ink transition hover:scale-105 hover:bg-lamp/15 disabled:cursor-not-allowed disabled:opacity-35 disabled:hover:scale-100 disabled:hover:bg-white"
          @click="go(page - 1)"
        >
          <ChevronLeft :size="15" />
        </button>
        <button
          type="button"
          aria-label="下一页"
          :disabled="page >= totalPages - 1"
          class="flex h-8 w-8 items-center justify-center rounded-full border border-lamp/40 bg-white text-ink transition hover:scale-105 hover:bg-lamp/15 disabled:cursor-not-allowed disabled:opacity-35 disabled:hover:scale-100 disabled:hover:bg-white"
          @click="go(page + 1)"
        >
          <ChevronRight :size="15" />
        </button>
      </div>
    </div>

    <!-- 2 列 × 3 行：切换分页时整组重新依次出现 -->
    <ul :key="page" class="grid min-w-0 flex-1 content-center grid-cols-1 gap-x-7 gap-y-4 sm:grid-cols-2">
      <li v-for="(n, idx) in pageItems" :key="n.id" class="min-w-0" :style="rowStyle(idx)">
        <RouterLink :to="`/news/${n.id}`" class="group block min-w-0 transition-transform duration-300 hover:-translate-y-0.5">
          <div class="flex items-center gap-2 text-base">
            <span class="font-medium tracking-wide text-tech">{{ n.category }}</span>
            <span class="h-4 w-px bg-slate-200" />
            <span class="font-code text-slate-400">{{ n.date }}</span>
          </div>
          <p
            class="mt-2.5 line-clamp-2 min-h-[3.8rem] text-lg font-medium leading-relaxed text-ink transition-colors duration-300 group-hover:text-tech"
          >
            {{ n.title }}
          </p>
          <!-- hover 时出现的短线 -->
          <span class="mt-2 block h-px w-0 bg-lamp transition-all duration-300 group-hover:w-8" />
        </RouterLink>
      </li>
    </ul>
  </div>
</template>
