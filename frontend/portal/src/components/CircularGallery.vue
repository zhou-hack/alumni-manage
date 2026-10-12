<script setup lang="ts">
// 数字校友弧形展厅：还原 React Bits「Circular Gallery」的布局/弧线/透视/旋转/缩放/惯性
// 横向弧形排列 + 无限循环 + 拖拽/滚轮 + 0.08 阻尼；中心照片最大最清晰，两侧倾斜缩小、边缘出屏
// 巡航：空闲 2.5s 后慢速自转；点击卡片打开档案弹窗期间暂停（paused），关闭弹窗约 1.2s 后恢复
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { OutstandingAlumni } from '../mock/outstandingAlumni';

const CARD_W = 250; // 卡片宽
const CARD_H = 330; // 卡片高（竖版矩形）
const SPACING = 300; // 相邻卡片中心间距（明显间距）
const EASE = 0.08; // scrollEase 阻尼
const ARC = 70; // 弧线最大下沉（两侧更低）
const MAX_TILT = 9; // 最大平面倾斜角（deg）
const MAX_YAW = 7; // 最大 3D 偏转角（deg）

const props = withDefaults(
  defineProps<{
    items: OutstandingAlumni[];
    /** 暂停巡航（如：数字校友档案弹窗打开期间） */
    paused?: boolean;
  }>(),
  { paused: false }
);

const emit = defineEmits<{
  select: [a: OutstandingAlumni];
}>();

const mod = (n: number, m: number) => ((n % m) + m) % m;

const wrapRef = ref<HTMLDivElement | null>(null);
// 高频物理状态不进响应式系统，避免每帧触发依赖追踪
const itemRefs: (HTMLDivElement | null)[] = [];
const phys = { current: 0, target: 0, velocity: 0, dragging: false, lastX: 0, moved: 0, idleAt: 0 };

// 弹窗关闭（paused 由 true → false）后延迟 1.2s 恢复巡航，避免突然起步
watch(
  () => props.paused,
  (p) => {
    if (!p) phys.idleAt = performance.now() + 1200;
  }
);

let cleanup: (() => void) | null = null;

onMounted(() => {
  const wrap = wrapRef.value;
  if (!wrap) return;
  const N = props.items.length;
  const total = N * SPACING;
  let raf = 0;

  const onWheel = (e: WheelEvent) => {
    e.preventDefault();
    const s = phys;
    const d = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY;
    s.target += d * 0.6;
    s.idleAt = performance.now() + 2500;
  };
  const onDown = (e: PointerEvent) => {
    const s = phys;
    s.dragging = true;
    s.lastX = e.clientX;
    s.moved = 0;
    s.velocity = 0;
    s.idleAt = performance.now() + 2500;
    wrap.style.cursor = 'grabbing';
  };
  const onMove = (e: PointerEvent) => {
    const s = phys;
    if (!s.dragging) return;
    const dx = e.clientX - s.lastX;
    s.lastX = e.clientX;
    s.moved += Math.abs(dx);
    s.target -= dx; // 拖动方向与画廊移动一致
    s.velocity = -dx;
    s.idleAt = performance.now() + 2500;
  };
  const onUp = () => {
    phys.dragging = false;
    wrap.style.cursor = 'grab';
  };

  wrap.addEventListener('wheel', onWheel, { passive: false });
  wrap.addEventListener('pointerdown', onDown);
  window.addEventListener('pointermove', onMove);
  window.addEventListener('pointerup', onUp);

  const tick = () => {
    const s = phys;
    // 惯性：松手后速度自然衰减
    if (!s.dragging && Math.abs(s.velocity) > 0.05) {
      s.target += s.velocity;
      s.velocity *= 0.94;
    }
    // 空闲慢速巡航，保持展厅"活"感（弹窗打开期间暂停，直接读 props 避免重建 rAF 循环）
    if (!s.dragging && !props.paused && performance.now() > s.idleAt) s.target += 0.35;
    // 0.08 阻尼平滑跟随
    s.current += (s.target - s.current) * EASE;

    const half = wrap.clientWidth / 2;
    for (let i = 0; i < N; i++) {
      const el = itemRefs[i];
      if (!el) continue;
      // 无限循环：相对中心的位置包裹到 [-total/2, total/2)
      const d = mod(i * SPACING - s.current + total / 2, total) - total / 2;
      const k = Math.min(Math.abs(d) / (half + CARD_W), 1); // 0=中心 1=边缘外
      const x = d;
      const y = k * k * ARC; // 弧线：两侧下沉
      const scale = 1 - k * 0.26; // 中心最大，两侧缩小
      const rotZ = (d / (half + CARD_W)) * MAX_TILT; // 平面倾斜
      const rotY = -(d / (half + CARD_W)) * MAX_YAW; // 3D 偏转
      const opacity = 1 - k * 0.45;
      el.style.transform = `translate(-50%, -50%) translate3d(${x.toFixed(1)}px, ${y.toFixed(1)}px, 0) rotateZ(${rotZ.toFixed(2)}deg) rotateY(${rotY.toFixed(2)}deg) scale(${scale.toFixed(3)})`;
      el.style.opacity = opacity.toFixed(3);
      el.style.zIndex = String(1000 - Math.round(Math.abs(d)));
      el.dataset.focus = Math.abs(d) < SPACING / 2 ? '1' : '0';
    }
    raf = requestAnimationFrame(tick);
  };
  raf = requestAnimationFrame(tick);

  cleanup = () => {
    cancelAnimationFrame(raf);
    wrap.removeEventListener('wheel', onWheel);
    wrap.removeEventListener('pointerdown', onDown);
    window.removeEventListener('pointermove', onMove);
    window.removeEventListener('pointerup', onUp);
  };
});

onBeforeUnmount(() => cleanup?.());

const setItemRef = (el: unknown, i: number) => {
  itemRefs[i] = el as HTMLDivElement | null;
};

// 拖拽位移超过阈值时不触发选中（区分拖拽与点击）
const onCardClick = (a: OutstandingAlumni) => {
  if (phys.moved < 6) emit('select', a);
};
</script>

<template>
  <div
    ref="wrapRef"
    class="relative h-[520px] w-full cursor-grab select-none overflow-hidden"
    :style="{ perspective: '1100px' }"
  >
    <!-- 中心聚焦光晕 -->
    <div class="pointer-events-none absolute left-1/2 top-1/2 h-[420px] w-[360px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-lamp/10 blur-3xl" />

    <div
      v-for="(a, i) in items"
      :key="a.name"
      :ref="(el) => setItemRef(el, i)"
      class="group absolute left-1/2 top-1/2"
      :style="{ willChange: 'transform, opacity' }"
    >
      <button
        type="button"
        :aria-label="`查看 ${a.name} 的数字校友档案`"
        class="block outline-none"
        @click="onCardClick(a)"
      >
        <div
          class="relative overflow-hidden rounded-2xl border border-lamp/40 bg-white shadow-[0_18px_40px_rgba(26,16,61,0.12)] transition-shadow duration-300 group-data-[focus=1]:border-lamp group-data-[focus=1]:shadow-[0_0_36px_rgba(245,179,1,0.4)]"
          :style="{ width: `${CARD_W}px`, height: `${CARD_H}px` }"
        >
          <!-- 真实照片（如有） -->
          <img
            v-if="a.photo"
            :src="a.photo"
            :alt="a.name"
            :draggable="false"
            class="h-full w-full object-cover object-top"
            loading="lazy"
            decoding="async"
          />
          <!-- 占位：渐变底 + 姓氏首字 + 柔和高光（与移动端 Avatar 风格统一） -->
          <div
            v-else
            aria-hidden="true"
            class="flex h-full w-full flex-col items-center justify-center bg-gradient-to-br from-night via-tech to-night/85"
          >
            <span
              class="absolute inset-0 bg-[radial-gradient(ellipse_at_top_left,rgba(255,255,255,0.18),transparent_55%)]"
            />
            <span
              class="font-title relative text-[88px] font-black text-white/95 drop-shadow-[0_4px_18px_rgba(0,0,0,0.4)]"
            >
              {{ (a.name || '·').slice(0, 1) }}
            </span>
          </div>
        </div>
        <!-- 姓名跟随照片移动 -->
        <p class="mt-4 text-center font-title text-base font-bold tracking-wide text-ink">{{ a.name }}</p>
      </button>
    </div>
  </div>
</template>
