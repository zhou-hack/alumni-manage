<script setup lang="ts">
// ============================================================================
// 首页首屏：校园实景大图轮播（两张）
//
// 版式（按设计稿）：
//   图片盒子 = 满宽 + 1653×869 比例（在 1653×869 的视口下正好铺满一屏），
//   图片按 object-cover 填充盒子，下沿用一条暖白色波浪压出"裁切"缺口，
//   与页面底色融为一体；文案压在左下方，轮播指示器在波浪之上、文案下方。
//   自动播放 6s，页面切到后台时跳过推进，回到前台从当前张继续。
// ============================================================================
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { api } from '../api';

interface Slide {
  img: string;
  title: string;
  sub: string;
}

/** 默认轮播（后台无已发布素材或接口异常时回退） */
const FALLBACK: Slide[] = [
  {
    img: '/bgcsvg/front.jpg',
    title: '桃李天下 · 薪火相传',
    sub: '欢迎全球校友回家看看'
  },
  {
    img: '/bgcsvg/side.jpg',
    title: '城堡长灯 · 静候归人',
    sub: '无论走多远，这里永远为你亮灯'
  }
];

const slides = ref<Slide[]>(FALLBACK);

const INTERVAL = 6000; // 自动切换间隔
// 底部波浪：高度与曲线均由设计稿给定（viewBox 1440 × 70），勿随意改动
const WAVE_H = 70;
const WAVE_PATH = 'M0,40 C240,80 480,0 720,30 C960,60 1200,20 1440,45 L1440,70 L0,70 Z';

const index = ref(0);
const slide = computed(() => slides.value[index.value % slides.value.length]);

// 加载后台维护的首屏轮播素材（GET /hero/slides?placement=hero，仅 published；空/异常回退默认两张）
onMounted(async () => {
  try {
    const d = await api.hero.slides('hero');
    const list = (d.list || []).map((s) => ({
      img: s.image || '/bgcsvg/front.jpg',
      title: s.title,
      sub: s.subtitle || ''
    }));
    if (list.length) slides.value = list;
  } catch {
    /* 接口异常时保持默认轮播 */
  }
});

// 自动轮播：切换只推进序号，切到后台时跳过，回到前台从当前张继续
let timer: number | undefined;
onMounted(() => {
  timer = window.setInterval(() => {
    if (document.hidden) return;
    index.value = (index.value + 1) % slides.value.length;
  }, INTERVAL);
});
onBeforeUnmount(() => window.clearInterval(timer));
</script>

<template>
  <!-- 首屏整体占满一屏：图片盒子按比例贴在顶部，余下高度露出暖白底色，
       波浪正好把图片下沿接到这片底色上（竖屏单独适配：取消整屏最小高度，
       避免图片过短时下方留出大段空白、美丽校园模块被推出首屏）；
       id="home"：导航「首页」/ 品牌区跳转的 hash 锚点（/#home） -->
  <section id="home" class="hero-sec relative min-h-screen w-full overflow-hidden bg-warm">
    <!-- 图片盒子：满宽 + 1653×869 -->
    <div class="relative aspect-[1653/869] w-full overflow-hidden bg-night">
      <!-- 背景大图：交叉淡入 + 缓慢推近（Ken Burns），换张时不会出现空白 -->
      <Transition name="hero-fade">
        <div :key="index" class="absolute inset-0">
          <img
            :src="slide.img"
            :alt="`福州软件职业技术学院校园实景 · ${slide.title}`"
            class="h-full w-full object-cover"
          />
        </div>
      </Transition>

      <!-- 压暗：底部加深，保证白色文案在任何一张图上都清晰 -->
      <div class="pointer-events-none absolute inset-0 bg-gradient-to-t from-night/80 via-night/20 to-night/10" />

      <!-- 文案：与站内其它模块同宽（随屏收敛），底部对齐（留出波浪与指示器的位置；
           竖屏压缩字号与底部留白，避免与顶部导航重叠） -->
      <div
        class="absolute inset-x-0 bottom-0 z-10 mx-auto flex w-[92%] max-w-[1500px] flex-col justify-end pb-[76px] md:w-[86%] md:pb-[124px] xl:w-[80%]"
      >
        <Transition name="hero-text" mode="out-in">
          <div :key="index">
            <span class="mb-2 block h-1 w-12 rounded-full bg-lamp md:mb-4" />
            <h1
              class="font-title text-2xl font-black tracking-wide text-white drop-shadow-[0_4px_18px_rgba(0,0,0,0.55)] md:text-5xl lg:text-6xl"
            >
              {{ slide.title }}
            </h1>
            <p class="mt-2 text-xs text-white/85 md:mt-4 md:text-lg">{{ slide.sub }}</p>
          </div>
        </Transition>
      </div>

      <!-- 轮播指示器：居中，位于波浪波峰之上（多于 1 张时显示；竖屏下移适配缩小后的波浪） -->
      <div
        v-if="slides.length > 1"
        class="absolute inset-x-0 bottom-[52px] z-10 flex items-center justify-center gap-2.5 md:bottom-[92px]"
      >
        <button
          v-for="(s, i) in slides"
          :key="`${s.img}-${i}`"
          type="button"
          :aria-label="`切换到第 ${i + 1} 张：${s.title}`"
          :aria-current="i === index"
          :class="`h-1.5 rounded-full transition-all duration-300 ${
            i === index ? 'w-8 bg-lamp' : 'w-3 bg-white/55 hover:bg-white/85'
          }`"
          @click="index = i"
        />
      </div>

      <!-- 底部波浪裁切：用暖白底色压出波形缺口，图片下沿随之被裁成波浪（竖屏降低波浪高度） -->
      <svg
        class="hero-wave pointer-events-none absolute inset-x-0 bottom-0 z-[5] w-full"
        :style="{ height: `${WAVE_H}px` }"
        :viewBox="`0 0 1440 ${WAVE_H}`"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <path :d="WAVE_PATH" fill="#FFF9F0" />
      </svg>
    </div>
  </section>
</template>

<style scoped>
/* 竖屏/窄屏适配：取消整屏最小高度，首屏高度随图片比例收缩，
   美丽校园模块在首屏内自然露出，无需下拉；波浪同步降低，避免占据图片过半高度 */
@media (max-width: 768px), (orientation: portrait) and (max-width: 1024px) {
  .hero-sec {
    min-height: 0 !important;
  }
  .hero-wave {
    height: 40px !important;
  }
}

/* 背景大图交叉淡入淡出 + Ken Burns 推近：
   进入 1.1s 淡入、6.6s 线性推近；退出 1.1s 淡出，进出同时进行 */
.hero-fade-enter-active {
  transition:
    opacity 1.1s ease,
    transform 6.6s linear;
}
.hero-fade-leave-active {
  transition: opacity 1.1s ease;
}
.hero-fade-enter-from {
  opacity: 0;
  transform: scale(1.06);
}
.hero-fade-leave-to {
  opacity: 0;
}

/* 文案切换：旧文案先退场（mode=out-in），新文案再上浮入场 */
.hero-text-enter-active,
.hero-text-leave-active {
  transition:
    opacity 0.7s cubic-bezier(0.22, 0.61, 0.36, 1),
    transform 0.7s cubic-bezier(0.22, 0.61, 0.36, 1);
}
.hero-text-enter-from {
  opacity: 0;
  transform: translateY(24px);
}
.hero-text-leave-to {
  opacity: 0;
  transform: translateY(-12px);
}
</style>
