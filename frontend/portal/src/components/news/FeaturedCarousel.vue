<script setup lang="ts">
// 学校头条（左侧）：大图 + 分类/日期 + 标题 + 摘要 + 圆点轮播
// 交互：自动播放 5.5s · 圆点切换 · 鼠标悬停暂停 · 鼠标离开继续 · 图片淡入轻微缩放 · 文字上移淡入
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import type { NewsItem } from '../../mock/news';

const AUTOPLAY_MS = 5500; // 5~6s

const props = defineProps<{ items: NewsItem[]; active: boolean }>();

const index = ref(0);
const paused = ref(false);
const current = computed(() => props.items[index.value]);

// 自动播放：进入视口后开始，鼠标悬停或离开视口时暂停
let timer: number | undefined;
const stopTimer = () => {
  if (timer !== undefined) {
    window.clearInterval(timer);
    timer = undefined;
  }
};
watch(
  () => [props.active, paused.value, props.items.length] as const,
  ([active, isPaused, len]) => {
    stopTimer();
    if (!active || isPaused || len <= 1) return;
    timer = window.setInterval(() => {
      index.value = (index.value + 1) % props.items.length;
    }, AUTOPLAY_MS);
  },
  { immediate: true }
);
onBeforeUnmount(stopTimer);
</script>

<template>
  <div v-if="current" class="min-w-0">
    <article
      class="group overflow-hidden rounded-2xl border border-lamp/25 bg-white shadow-[0_14px_34px_rgba(26,16,61,0.07)]"
      @mouseenter="paused = true"
      @mouseleave="paused = false"
    >
      <RouterLink :to="`/news/${current.id}`" class="block outline-none">
        <!-- 封面：16:9，overflow-hidden + hover 轻微放大（公告无 AI 封面时不渲染图区） -->
        <div v-if="current.image" class="relative aspect-[16/9] w-full overflow-hidden bg-warm">
          <div class="absolute inset-0 transition-transform duration-500 ease-out group-hover:scale-[1.03]">
            <Transition name="featured-img">
              <img
                :key="current.id"
                :src="current.image"
                :alt="current.title"
                class="absolute inset-0 h-full w-full object-cover"
              />
            </Transition>
          </div>
        </div>

        <!-- 文字：切换时上移 + 淡入（无封面时收紧上下间距与占位高度） -->
        <Transition name="featured-text" mode="out-in">
          <div :key="`txt-${current.id}`" class="px-6" :class="current.image ? 'pb-5 pt-5' : 'pb-4 pt-4'">
            <!-- 分类 + 日期：金色小点作轻微点缀 -->
            <div class="flex items-center gap-2 text-sm">
              <span class="h-1.5 w-1.5 rounded-full bg-lamp" />
              <span class="font-medium tracking-wide text-tech">{{ current.category }}</span>
              <span class="h-3.5 w-px bg-slate-200" />
              <span class="font-code text-slate-400">{{ current.date }}</span>
            </div>

            <h3
              class="font-title line-clamp-2 text-2xl font-bold leading-snug text-ink transition-colors group-hover:text-tech"
              :class="current.image ? 'mt-3 min-h-[4.2rem]' : 'mt-2'"
            >
              {{ current.title }}
            </h3>
            <p
              class="line-clamp-2 text-base leading-relaxed text-slate-600"
              :class="current.image ? 'mt-2.5 min-h-[3.3rem]' : 'mt-2'"
            >
              {{ current.summary }}
            </p>
          </div>
        </Transition>
      </RouterLink>

      <!-- 圆点：当前项使用站点主色 -->
      <div class="flex items-center gap-2 px-6" :class="current.image ? 'pb-5' : 'pb-4'">
        <button
          v-for="(n, i) in items"
          :key="n.id"
          type="button"
          :aria-label="`切换到第 ${i + 1} 条头条`"
          :aria-current="i === index"
          :class="`h-2 rounded-full transition-all duration-300 ${i === index ? 'w-6 bg-lamp' : 'w-2 bg-night/15 hover:bg-night/30'}`"
          @click="index = i"
        />
      </div>
    </article>
  </div>
</template>

<style scoped>
/* 图片：新图淡入 + 轻微缩放复位，旧图同时淡出（并行过渡，对应 AnimatePresence 默认行为） */
.featured-img-enter-active {
  transition: opacity 0.45s ease-out, transform 0.45s ease-out;
}
.featured-img-enter-from {
  opacity: 0;
  transform: scale(1.04);
}
.featured-img-leave-active {
  transition: opacity 0.45s ease-out;
}
.featured-img-leave-to {
  opacity: 0;
}

/* 文字：上移 + 淡入；旧文本立即让位（out-in + 零时长离场，对应原 key 替换即重播） */
.featured-text-enter-active {
  transition: opacity 0.4s ease-out, transform 0.4s ease-out;
}
.featured-text-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.featured-text-leave-active {
  transition: none;
}
</style>
