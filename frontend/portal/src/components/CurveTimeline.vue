<script setup lang="ts">
// 横向"微曲线"在校履历：SVG 平滑曲线贯穿全部节点，节点上下交错排布；
// 内容超出可左右拖拽横移，下方配一条可拖拽的滑条（拖拽滑块/点击轨道均可控制）
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import type { AlumniTimelineItem } from '../mock/outstandingAlumni';

const ITEM_W = 150; // 每个履历节点的水平间距（px）
const CURVE_H = 230; // 曲线画布高（px）
const BASE_Y = 105; // 曲线基线
const CURVE_AMP = 24; // 曲线振幅（"微"曲线，不宜过大）

const props = defineProps<{ items: AlumniTimelineItem[] }>();

/** 由节点坐标生成平滑贝塞尔曲线（水平切线穿过每个点，形成柔和波浪） */
function smoothPath(pts: { x: number; y: number }[]): string {
  if (pts.length < 2) return '';
  let d = `M ${pts[0].x} ${pts[0].y}`;
  for (let i = 1; i < pts.length; i++) {
    const mx = (pts[i - 1].x + pts[i].x) / 2;
    d += ` C ${mx} ${pts[i - 1].y}, ${mx} ${pts[i].y}, ${pts[i].x} ${pts[i].y}`;
  }
  return d;
}

const scrollRef = ref<HTMLDivElement | null>(null);
const trackRef = ref<HTMLDivElement | null>(null);
const thumb = ref({ left: 0, width: 40 });
// 内容拖拽 / 滑条拖拽的临时状态（不进响应式模板渲染路径）
const contentDrag = { current: null as { startX: number; startScroll: number } | null };
const thumbDrag = { current: null as { startX: number; startScroll: number } | null };

const width = computed(() => props.items.length * ITEM_W);
const pts = computed(() =>
  props.items.map((_, i) => ({ x: i * ITEM_W + ITEM_W / 2, y: BASE_Y + CURVE_AMP * Math.sin(i * 1.05) }))
);
const path = computed(() => smoothPath(pts.value));

// 同步滑块宽度与位置（滚动 / 尺寸变化时）
const sync = () => {
  const el = scrollRef.value;
  const track = trackRef.value;
  if (!el || !track) return;
  const w = Math.max((el.clientWidth / el.scrollWidth) * track.clientWidth, 40);
  const scrollable = el.scrollWidth - el.clientWidth;
  const left = scrollable > 0 ? (track.clientWidth - w) * (el.scrollLeft / scrollable) : 0;
  thumb.value = { left, width: w };
};

// 鼠标滚轮横移：纵向滚动量转为横向位移；已到边界（或无需横移）时放行，
// 交给弹窗/页面继续滚动，避免把滚轮"吞掉"
const onWheel = (e: WheelEvent) => {
  const el = scrollRef.value;
  if (!el) return;
  const delta = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY;
  if (delta === 0) return;
  const maxScroll = el.scrollWidth - el.clientWidth;
  if (maxScroll <= 0) return;
  const atStart = el.scrollLeft <= 0 && delta < 0;
  const atEnd = el.scrollLeft >= maxScroll - 1 && delta > 0;
  if (atStart || atEnd) return;
  e.preventDefault();
  el.scrollLeft += delta;
};

let ro: ResizeObserver | null = null;
onMounted(() => {
  sync();
  ro = new ResizeObserver(sync);
  if (scrollRef.value) {
    ro.observe(scrollRef.value);
    // 需要 preventDefault，必须显式非 passive 注册
    scrollRef.value.addEventListener('wheel', onWheel, { passive: false });
  }
  if (trackRef.value) ro.observe(trackRef.value);
});
onBeforeUnmount(() => {
  ro?.disconnect();
  scrollRef.value?.removeEventListener('wheel', onWheel);
});

// 履历区拖拽横移
const onContentDown = (e: PointerEvent) => {
  const el = scrollRef.value;
  if (!el) return;
  contentDrag.current = { startX: e.clientX, startScroll: el.scrollLeft };
  const move = (ev: PointerEvent) => {
    if (!contentDrag.current) return;
    el.scrollLeft = contentDrag.current.startScroll - (ev.clientX - contentDrag.current.startX);
  };
  const up = () => {
    contentDrag.current = null;
    window.removeEventListener('pointermove', move);
    window.removeEventListener('pointerup', up);
  };
  window.addEventListener('pointermove', move);
  window.addEventListener('pointerup', up);
};

// 滑块拖拽：按轨道可位移比例换算滚动距离
const onThumbDown = (e: PointerEvent) => {
  e.stopPropagation();
  e.preventDefault();
  const el = scrollRef.value;
  const track = trackRef.value;
  if (!el || !track) return;
  thumbDrag.current = { startX: e.clientX, startScroll: el.scrollLeft };
  const move = (ev: PointerEvent) => {
    if (!thumbDrag.current) return;
    const scrollable = el.scrollWidth - el.clientWidth;
    const thumbW = Math.max((el.clientWidth / el.scrollWidth) * track.clientWidth, 40);
    const trackScrollable = track.clientWidth - thumbW;
    if (trackScrollable <= 0) return;
    el.scrollLeft =
      thumbDrag.current.startScroll + ((ev.clientX - thumbDrag.current.startX) / trackScrollable) * scrollable;
  };
  const up = () => {
    thumbDrag.current = null;
    window.removeEventListener('pointermove', move);
    window.removeEventListener('pointerup', up);
  };
  window.addEventListener('pointermove', move);
  window.addEventListener('pointerup', up);
};

// 点击轨道：跳转到对应位置（点在滑块上则交给滑块拖拽）
const onTrackDown = (e: PointerEvent) => {
  if (e.target !== trackRef.value) return;
  const el = scrollRef.value;
  const track = trackRef.value;
  if (!el || !track) return;
  const rect = track.getBoundingClientRect();
  const ratio = (e.clientX - rect.left) / rect.width;
  el.scrollLeft = ratio * (el.scrollWidth - el.clientWidth);
};
</script>

<template>
  <div class="relative">
    <!-- 曲线履历滚动区（隐藏原生滚动条，用下方自定义滑条替代） -->
    <div
      ref="scrollRef"
      class="cursor-grab select-none overflow-x-auto active:cursor-grabbing [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      @scroll="sync"
      @pointerdown="onContentDown"
    >
      <div class="relative" :style="{ width: `${width}px`, height: `${CURVE_H}px` }">
        <!-- 微曲线：渐变色描绘，入场时从左向右画出来（stroke-dashoffset 动画，pathLength=1 归一化） -->
        <svg :width="width" :height="CURVE_H" class="absolute inset-0" aria-hidden="true">
          <defs>
            <linearGradient id="tlGrad" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0" stop-color="#2563EB" />
              <stop offset="1" stop-color="#F5B301" />
            </linearGradient>
          </defs>
          <path
            class="tl-path"
            :d="path"
            fill="none"
            stroke="url(#tlGrad)"
            :stroke-width="2.5"
            stroke-linecap="round"
            pathLength="1"
          />
        </svg>

        <!-- 节点：圆点落在曲线上，年份与事件上下交错 -->
        <div
          v-for="(t, i) in items"
          :key="`${t.year}-${t.event}`"
          class="tl-node absolute"
          :style="{ left: `${pts[i].x}px`, top: `${pts[i].y}px`, animationDelay: `${0.4 + i * 0.09}s` }"
        >
          <span class="absolute -left-[7px] -top-[7px] h-3.5 w-3.5 rounded-full border-2 border-lamp bg-warm shadow-[0_0_8px_rgba(245,179,1,0.6)]" />
          <div :class="`absolute w-[132px] -translate-x-1/2 text-center ${i % 2 === 0 ? 'bottom-4' : 'top-4'}`">
            <p class="font-code text-xs font-bold text-tech">{{ t.year }}</p>
            <p class="mt-1 text-[12px] leading-snug text-slate-700">{{ t.event }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 两端渐隐：提示两侧还有更多履历 -->
    <div class="pointer-events-none absolute inset-y-0 left-0 w-10 bg-gradient-to-r from-warm to-transparent" />
    <div class="pointer-events-none absolute inset-y-0 right-0 w-10 bg-gradient-to-l from-warm to-transparent" />

    <!-- 可拖拽滑条：轨道点击跳转，滑块拖拽横移 -->
    <div
      ref="trackRef"
      class="relative mx-auto mt-3 h-1.5 w-[72%] cursor-pointer rounded-full bg-ink/10"
      @pointerdown="onTrackDown"
    >
      <div
        role="slider"
        aria-label="拖拽查看完整在校履历"
        :aria-valuemin="0"
        :aria-valuemax="100"
        :aria-valuenow="Math.round((thumb.left / Math.max(1, (trackRef?.clientWidth ?? 1) - thumb.width)) * 100)"
        class="absolute top-1/2 h-4 -translate-y-1/2 cursor-grab rounded-full bg-gradient-to-r from-tech to-lamp shadow-[0_2px_8px_rgba(37,99,235,0.35)] transition-transform hover:scale-y-125 active:cursor-grabbing"
        :style="{ left: `${thumb.left}px`, width: `${thumb.width}px` }"
        @pointerdown="onThumbDown"
      />
    </div>
  </div>
</template>

<style scoped>
/* 曲线入场：从左向右画出（原 framer-motion pathLength 0→1，1.1s easeInOut） */
.tl-path {
  stroke-dasharray: 1;
  stroke-dashoffset: 1;
  animation: tl-draw 1.1s ease-in-out forwards;
}
@keyframes tl-draw {
  to {
    stroke-dashoffset: 0;
  }
}
/* 节点入场：弹簧感缩放弹出（原 spring stiffness 260 damping 18，用回弹缓动近似）。
   基础样式不预置 opacity:0 —— 延迟期间由 backwards 填充 from 帧（隐藏），
   动画结束后回到自然样式（可见），避免播完又消失 */
.tl-node {
  animation: tl-pop 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) backwards;
}
@keyframes tl-pop {
  from {
    opacity: 0;
    transform: scale(0);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
