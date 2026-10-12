<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type CSSProperties } from 'vue';
// 显式 .vue 后缀：原 .tsx 文件暂未删除，省后缀时 TS 会优先解析到 .tsx
import AlumniSection from '../components/AlumniSection.vue';
import AlumniIndex from '../components/AlumniIndex.vue';
import HonorWall from '../components/HonorWall.vue';
import LatestNews from '../components/news/LatestNews.vue';
import HomeNav from '../components/HomeNav.vue';
import HomeHero from '../components/HomeHero.vue';
import MessageWall from '../components/MessageWall.vue';
import ModuleTitle from '../components/ModuleTitle.vue';
import { Reveal } from '../components/Reveal';
import { textToImage } from '../utils/image';
import { api } from '../api';

// 校园风光轮播默认图（后台未配置 campus 素材或接口异常时回退，1440 × 500）
const FALLBACK_SLIDES = [
  {
    img: textToImage(
      'Grand European castle style university main building, beige stone walls, crenellated battlements, central pointed arch gate, blue sky, wide angle architectural photography, realistic',
      'landscape_16_9'
    ),
    title: '城堡主楼 · 日景',
    sub: '形似欧式城堡的主体教学楼，校园主体形象'
  },
  {
    img: textToImage(
      'Castle style campus with artificial moat lake, mirror reflection of towers on calm water, students rowing boats, coastal pine forest, golden hour sunlight, realistic photography',
      'landscape_16_9'
    ),
    title: '护城湖 · 倒影',
    sub: '校园外围人工护城湖仿若玉带，课后泛舟其上'
  },
  {
    img: textToImage(
      'Futuristic digital education town at dusk, starship shaped glass office building beside European castle university campus, purple and blue sky, cinematic wide shot',
      'landscape_16_9'
    ),
    title: '数字教育小镇 · 黄昏',
    sub: '一座城堡与一艘"飞船"交相辉映的数字教育小镇'
  },
  {
    img: textToImage(
      'Night view of castle university campus, warm golden windows glowing, starry sky, light strings and lanterns, graduation celebration atmosphere, cinematic night photography',
      'landscape_16_9'
    ),
    title: '城堡夜灯 · 毕业季',
    sub: '无论走多远，城堡永远为你亮灯'
  }
];

interface CampusSlide {
  img: string;
  title: string;
  sub: string;
}

const SLIDES = ref<CampusSlide[]>(FALLBACK_SLIDES);

// 加载后台「首页轮播」中 placement=campus 的素材（仅 published；空/异常回退默认图）
onMounted(async () => {
  try {
    const d = await api.hero.slides('campus');
    const list = (d.list || [])
      .filter((s) => s.image)
      .map((s) => ({ img: s.image as string, title: s.title, sub: s.subtitle || '' }));
    if (list.length) SLIDES.value = list;
  } catch (e) {
    // 接口异常时保持默认轮播，但记录以便排查（2026-09-25 健康审计 P1-7）
    console.warn('[home/hero/campus] 接口请求失败，保持默认轮播图：', e);
  }
});

const slide = ref(0);
const dir = ref(1); // 切换方向：1 下一张 -1 上一张
const currentSlide = computed(() => SLIDES.value[slide.value % SLIDES.value.length]);
// 方向写入 CSS 变量，进出动画按方向取反（对应原 framer custom={dir}）
const slideStyle = computed(() => ({ '--dir': String(dir.value) }) as CSSProperties);

const go = (next: number, direction = 1) => {
  dir.value = direction;
  slide.value = next;
};

// 轮播自动播放 3.8s（slide 每次变化后重新计时，与手动切换共用同一节奏）
let timer: number | undefined;
const startAutoPlay = () => {
  timer = window.setInterval(() => go((slide.value + 1) % SLIDES.value.length, 1), 3800);
};
onMounted(startAutoPlay);
watch(slide, () => {
  window.clearInterval(timer);
  startAutoPlay();
});
onBeforeUnmount(() => window.clearInterval(timer));
</script>

<template>
  <div class="min-h-screen bg-warm">
    <!-- ===== 顶部导航栏（浅色白底，悬浮在首屏大图之上） ===== -->
    <HomeNav />

    <!-- ===== 首屏：校园实景大图轮播（两张） ===== -->
    <HomeHero />

    <!-- ===== 校园风光轮播（宽度随屏收敛，高度分级，上方留白与下方一致） ===== -->
    <div
      id="beautiful-campus"
      class="mx-auto mt-16 w-[92%] max-w-[1500px] scroll-mt-24 md:w-[86%] xl:w-[80%]"
    >
      <!-- 模块标题：与优秀校友标题同款三段式设计（自带级联入场） -->
      <ModuleTitle eyebrow="BEAUTIFUL CAMPUS" title="美丽校园" subtitle="览时光之景，育青春之志" />

      <Reveal :delay="0.08">
        <div class="relative h-[380px] overflow-hidden rounded-[15px] sm:h-[460px] lg:h-[600px]">
          <!-- 交叉滑动淡切：进出同时进行，无空白留白 -->
          <Transition name="campus">
            <div :key="slide" class="absolute inset-0" :style="slideStyle">
              <img :src="currentSlide.img" :alt="currentSlide.title" class="h-full w-full object-cover" />
              <!-- 底部渐变遮罩 + 文案 -->
              <div
                class="absolute inset-x-0 bottom-0 bg-gradient-to-t from-night/85 via-night/30 to-transparent px-6 pb-5 pt-16 md:px-10"
              >
                <p class="font-title text-xl font-black text-warm md:text-3xl">{{ currentSlide.title }}</p>
                <p class="mt-1 text-xs text-warm/75 md:text-sm">{{ currentSlide.sub }}</p>
              </div>
            </div>
          </Transition>

          <!-- 左右切换 -->
          <button
            aria-label="上一张"
            class="absolute left-3 top-1/2 -translate-y-1/2 rounded-full bg-night/50 px-3 py-2 text-warm backdrop-blur transition hover:bg-lamp hover:text-night"
            @click="go((slide - 1 + SLIDES.length) % SLIDES.length, -1)"
          >
            ‹
          </button>
          <button
            aria-label="下一张"
            class="absolute right-3 top-1/2 -translate-y-1/2 rounded-full bg-night/50 px-3 py-2 text-warm backdrop-blur transition hover:bg-lamp hover:text-night"
            @click="go((slide + 1) % SLIDES.length, 1)"
          >
            ›
          </button>

          <!-- 指示器：窗灯样式 -->
          <div class="absolute right-4 top-4 flex gap-1.5">
            <button
              v-for="(s, i) in SLIDES"
              :key="s.title"
              :aria-label="s.title"
              :class="`h-2.5 w-2.5 rounded-full transition ${
                i === slide ? 'bg-lamp shadow-[0_0_8px_rgba(245,179,1,0.8)]' : 'bg-warm/40 hover:bg-warm/70'
              }`"
              @click="go(i, i > slide ? 1 : -1)"
            />
          </div>
        </div>
      </Reveal>
    </div>

    <!-- ===== 学校最新动态（放在美丽校园轮播下方） ===== -->
    <LatestNews />

    <!-- ===== 优秀校友 ===== -->
    <AlumniSection />

    <!-- ===== 荣誉墙（按类别陈列的证书/荣誉展示墙，位于优秀校友下方） ===== -->
    <HonorWall />

    <!-- ===== 校友档案索引（搜索 + 届次筛选） ===== -->
    <AlumniIndex />

    <!-- ===== 校友留言墙（便签上浮 + 可拖拽） ===== -->
    <MessageWall />

    <!-- ===== 页脚 ===== -->
    <footer class="mt-16 border-t border-lamp/20 bg-night px-6 py-8 text-center text-xs text-warm/60">
      <p class="font-title text-base text-warm">福州软件职业技术学院 · 校友管理平台</p>
      <p class="mt-2">数字教育小镇 · 长乐区滨海大道999号 · 0591-83843292</p>
      <p class="font-code mt-2 text-[10px] tracking-widest text-warm/40">无论走多远，城堡永远为你亮灯</p>
    </footer>
  </div>
</template>

<style scoped>
/* 交叉滑动淡切：进出同时进行（0.45s / [0.32,0.72,0.24,1]），方向由 --dir 决定 */
.campus-enter-active,
.campus-leave-active {
  transition:
    opacity 0.45s cubic-bezier(0.32, 0.72, 0.24, 1),
    transform 0.45s cubic-bezier(0.32, 0.72, 0.24, 1);
}
.campus-enter-from {
  opacity: 0;
  transform: translateX(calc(var(--dir) * 60px)) scale(1.03);
}
.campus-enter-to {
  opacity: 1;
  transform: translateX(0) scale(1);
}
.campus-leave-from {
  opacity: 1;
  transform: translateX(0) scale(1);
}
.campus-leave-to {
  opacity: 0;
  transform: translateX(calc(var(--dir) * -60px)) scale(1.02);
}
</style>
