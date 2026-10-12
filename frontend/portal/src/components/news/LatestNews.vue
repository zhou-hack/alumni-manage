<script setup lang="ts">
// 学校最新动态（首页模块）
//
// 结构：模块标题（沿用站点统一的三段式）→ 查看更多 + 演示说明 → 左侧头条轮播 + 右侧最新资讯
// 数据：挂载时拉取 /announcements（见 useNews.ts），接口失败回退 mock 演示数据
// 入口动画：标题 → 头条 → 右侧新闻列表（逐行 90ms 错开），复用 IntersectionObserver（Reveal 组件同款）
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { ArrowRight } from '../icons';
import { Reveal } from '../Reveal';
import FeaturedCarousel from './FeaturedCarousel.vue';
import NewsListPanel from './NewsListPanel.vue';
import ModuleTitle from '../ModuleTitle.vue';
import { NEWS_META } from '../../mock/news';
import { useNews } from './useNews';

const { featured, latest, state } = useNews();

const rootRef = ref<HTMLElement | null>(null);
// 进入视口后：启动头条自动播放、触发右侧列表依次出现
const inView = ref(false);
let io: IntersectionObserver | null = null;
onMounted(() => {
  if (!rootRef.value) return;
  io = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) {
        inView.value = true;
        io?.disconnect();
        io = null;
      }
    },
    { threshold: 0.12 }
  );
  io.observe(rootRef.value);
});
onBeforeUnmount(() => io?.disconnect());
</script>

<template>
  <section id="latest-news" ref="rootRef" class="mx-auto mt-16 w-[92%] max-w-[1500px] scroll-mt-24 md:w-[86%] xl:w-[80%]">
    <!-- ===== 模块标题：与首页其它模块完全一致的三段式 ===== -->
    <Reveal :y="16">
      <ModuleTitle :eyebrow="NEWS_META.eyebrow" :title="NEWS_META.title" :subtitle="NEWS_META.subtitle" ornament />
    </Reveal>

    <!-- ===== 演示说明 + 查看更多 ===== -->
    <Reveal :delay="0.05" :y="16">
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <p class="text-sm text-slate-400">{{ state === 'error' ? NEWS_META.notice : '学校公告与最新资讯' }}</p>
        <RouterLink
          to="/news"
          class="group relative flex items-center gap-1.5 pb-1 text-base font-medium text-ink/55 transition-colors duration-300 hover:text-ink"
        >
          查看更多
          <ArrowRight
            :size="16"
            class="text-[#a97b00]/60 transition-all duration-300 group-hover:translate-x-0.5 group-hover:text-[#a97b00]"
          />
          <!-- 悬停时自左侧生长的金色细线，取代生硬的蓝色链接 -->
          <span
            aria-hidden="true"
            class="absolute bottom-0 left-0 h-px w-0 bg-gradient-to-r from-[#a97b00]/80 to-transparent transition-all duration-300 group-hover:w-full"
          />
        </RouterLink>
      </div>
    </Reveal>

    <!-- ===== 左：学校头条 · 右：最新资讯 ===== -->
    <div v-if="state === 'empty'" class="rounded-2xl border border-lamp/25 bg-white px-8 py-14 text-center shadow-[0_14px_34px_rgba(26,16,61,0.07)]">
      <p class="text-base text-slate-400">暂无最新动态，敬请期待</p>
    </div>
    <div v-else class="grid min-w-0 grid-cols-1 gap-6 lg:grid-cols-[45fr_55fr]">
      <Reveal :delay="0.1" :y="16" class="min-w-0">
        <FeaturedCarousel :items="featured" :active="inView" />
      </Reveal>
      <Reveal :delay="0.18" :y="16" class="min-w-0">
        <NewsListPanel :items="latest" :active="inView" />
      </Reveal>
    </div>
  </section>
</template>
