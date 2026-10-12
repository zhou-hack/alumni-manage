<script setup lang="ts">
// 站点统一的模块标题（三段式）：英文眉标 + 中文大标题 + 副标题
// 亮底页面各模块共用，避免同一段结构在每个模块里各写一遍。
// 入场动画（进入视口播一次）：两侧线条向中生长 → 眉标淡入 → 大标题上浮 → 垛口展开 → 副标题淡入
// 实现：IntersectionObserver + CSS transition（与 Reveal.ts 同一套风格），只播一次
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

// 统一缓动（与全站动效一致）
const EASE = 'cubic-bezier(0.22, 0.61, 0.36, 1)';

interface ModuleTitleProps {
  eyebrow: string;
  title: string;
  subtitle: string;
  /** 大标题下方的金色垛口装饰线 */
  ornament?: boolean;
  /** 标题上方的柔和金色微光 */
  glow?: boolean;
  /** 外层间距（不同模块的下边距略有差异） */
  className?: string;
}

const props = withDefaults(defineProps<ModuleTitleProps>(), {
  ornament: false,
  glow: false,
  className: 'mb-8'
});

// 进入视口 60% 后播放入场（once），对应原 framer 的 viewport={{ once: true, amount: 0.6 }}
const rootEl = ref<HTMLElement | null>(null);
const shown = ref(false);
let io: IntersectionObserver | null = null;

onMounted(() => {
  if (!rootEl.value) return;
  io = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) {
        shown.value = true;
        io?.disconnect();
        io = null;
      }
    },
    { threshold: 0.6 }
  );
  io.observe(rootEl.value);
});
onBeforeUnmount(() => io?.disconnect());

// 各环节错峰 90ms，时长递减，整体约 0.9s 播完
const lineStyle = computed(() => ({
  transform: shown.value ? 'scaleX(1)' : 'scaleX(0)',
  transition: `transform 0.7s ${EASE}`
}));
const eyebrowStyle = computed(() => ({
  opacity: shown.value ? 1 : 0,
  transform: shown.value ? 'translateY(0px)' : 'translateY(8px)',
  transition: `opacity 0.5s ${EASE} 0.09s, transform 0.5s ${EASE} 0.09s`
}));
const titleStyle = computed(() => ({
  opacity: shown.value ? 1 : 0,
  transform: shown.value ? 'translateY(0px)' : 'translateY(18px)',
  transition: `opacity 0.6s ${EASE} 0.18s, transform 0.6s ${EASE} 0.18s`
}));
const ornamentStyle = computed(() => ({
  opacity: shown.value ? 1 : 0,
  transform: shown.value ? 'scaleX(1)' : 'scaleX(0.4)',
  transition: `opacity 0.5s ${EASE} 0.3s, transform 0.5s ${EASE} 0.3s`
}));
const subStyle = computed(() => ({
  opacity: shown.value ? 1 : 0,
  transform: shown.value ? 'translateY(0px)' : 'translateY(10px)',
  transition: `opacity 0.5s ${EASE} 0.36s, transform 0.5s ${EASE} 0.36s`
}));
</script>

<template>
  <header ref="rootEl" class="relative text-center" :class="props.className">
    <span
      v-if="props.glow"
      class="pointer-events-none absolute left-1/2 top-2 h-24 w-72 -translate-x-1/2 rounded-full bg-lamp/10 blur-3xl"
    />
    <div class="relative flex items-center justify-center gap-4">
      <span
        class="h-px w-20 origin-right bg-gradient-to-r from-transparent to-lamp/70"
        :style="lineStyle"
      />
      <span class="font-code text-xs tracking-[0.4em] text-tech" :style="eyebrowStyle">
        {{ props.eyebrow }}
      </span>
      <span
        class="h-px w-20 origin-left bg-gradient-to-l from-transparent to-lamp/70"
        :style="lineStyle"
      />
    </div>
    <h2
      class="font-title relative mt-4 text-3xl font-black tracking-[0.08em] text-ink md:text-4xl"
      :style="titleStyle"
    >
      {{ props.title }}
    </h2>
    <svg
      v-if="props.ornament"
      viewBox="0 0 127 10"
      class="relative mx-auto mt-4 h-2.5 w-32 origin-center text-lamp"
      :style="ornamentStyle"
      aria-hidden="true"
    >
      <rect x="0" y="7" width="127" height="2" rx="1" fill="currentColor" opacity="0.8" />
      <rect
        v-for="i in 11"
        :key="i"
        :x="(i - 1) * 12"
        y="2"
        width="7"
        height="7"
        rx="1"
        fill="currentColor"
      />
    </svg>
    <p class="relative mt-4 text-base tracking-[0.18em] text-slate-700 md:text-lg" :style="subStyle">
      {{ props.subtitle }}
    </p>
  </header>
</template>
