<script setup lang="ts">
// 最新动态列表页（/news）：全部动态按日期倒序，点击进入详情
// 数据：挂载时拉取 /announcements（见 components/news/useNews.ts），接口失败回退 mock 演示数据
import { ArrowLeft } from '../components/icons';
import { Reveal } from '../components/Reveal';
import NewsShell from '../components/news/NewsShell.vue';
import { NEWS_META } from '../mock/news';
import { useNews } from '../components/news/useNews';

const { all, state } = useNews();
</script>

<template>
  <NewsShell>
    <!-- 标题 -->
    <header class="mb-8">
      <div class="flex items-center gap-4">
        <span class="h-px w-14 bg-gradient-to-r from-transparent to-lamp/70" />
        <span class="font-code text-xs tracking-[0.4em] text-tech">{{ NEWS_META.eyebrow }}</span>
      </div>
      <h1 class="font-title mt-4 text-4xl font-black tracking-[0.06em] text-ink">最新动态</h1>
      <p class="mt-3 text-base text-slate-500">
        共 {{ all.length }} 条 · {{ state === 'error' ? NEWS_META.notice : '数据来自学校公告' }}
      </p>
    </header>

    <!-- 空态：接口成功但暂无已发布公告 -->
    <div
      v-if="state === 'empty'"
      class="rounded-2xl border border-lamp/25 bg-white px-8 py-14 text-center shadow-[0_10px_26px_rgba(26,16,61,0.06)]"
    >
      <p class="text-base text-slate-400">暂无最新动态，敬请期待</p>
    </div>

    <!-- 列表 -->
    <ul v-else class="space-y-5">
      <li v-for="(n, i) in all" :key="n.id">
        <Reveal :delay="Math.min(i, 5) * 0.05" :y="14">
          <RouterLink
            :to="`/news/${n.id}`"
            class="group flex flex-col gap-5 rounded-2xl border border-lamp/25 bg-white p-4 shadow-[0_10px_26px_rgba(26,16,61,0.06)] transition-all duration-300 hover:-translate-y-0.5 hover:border-lamp/50 sm:flex-row"
          >
            <div v-if="n.image" class="relative aspect-[16/9] w-full shrink-0 overflow-hidden rounded-xl bg-warm sm:w-60">
              <img
                :src="n.image"
                :alt="n.title"
                loading="lazy"
                class="absolute inset-0 h-full w-full object-cover transition-transform duration-500 ease-out group-hover:scale-[1.03]"
              />
            </div>
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2 text-sm">
                <span class="font-medium tracking-wide text-tech">{{ n.category }}</span>
                <span class="h-3.5 w-px bg-slate-200" />
                <span class="font-code text-slate-400">{{ n.date }}</span>
              </div>
              <h2 class="font-title mt-2.5 line-clamp-2 text-xl font-bold leading-snug text-ink transition-colors group-hover:text-tech">
                {{ n.title }}
              </h2>
              <p class="mt-2.5 line-clamp-2 text-base leading-relaxed text-slate-600">{{ n.summary }}</p>
              <span class="mt-3 block h-px w-0 bg-lamp transition-all duration-300 group-hover:w-10" />
            </div>
          </RouterLink>
        </Reveal>
      </li>
    </ul>

    <div class="mt-10">
      <RouterLink to="/" class="inline-flex items-center gap-1.5 text-sm font-medium text-tech transition-colors hover:text-ink">
        <ArrowLeft :size="15" />
        返回首页
      </RouterLink>
    </div>
  </NewsShell>
</template>
