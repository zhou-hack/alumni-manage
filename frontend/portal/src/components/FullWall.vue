<script setup lang="ts">
// ------------------------------ 全屏幕布：大画布 + 平移缩放 + 视口裁剪 ------------------------------
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import FullNote from './FullNote.vue';
import { Minimize2 } from './icons';
import {
  CELL,
  NOTE_SIZE,
  ZOOM_MAX,
  ZOOM_MIN,
  ZOOM_SPEED,
  ZERO_OFFSET,
  computeLayout,
  type Note
} from './messageWall';

const props = defineProps<{ notes: Note[] }>();
defineEmits<{ close: []; new: [] }>();

const viewportRef = ref<HTMLDivElement | null>(null);
// 视口尺寸（画布铺满整屏，先用窗口尺寸初始化避免首帧空白）
const vp = ref({ w: window.innerWidth, h: window.innerHeight });

let ro: ResizeObserver | null = null;
onMounted(() => {
  const el = viewportRef.value;
  if (!el) return;
  ro = new ResizeObserver(() => {
    vp.value = { w: el.clientWidth, h: el.clientHeight };
  });
  ro.observe(el);
});
onBeforeUnmount(() => ro?.disconnect());

const layout = computed(() => computeLayout(props.notes, vp.value.w, vp.value.h));

// 平移钳制：画布比视口小的方向允许居中微移，大的方向边缘允许 120px 过冲
function clampPan(px: number, py: number, zoom: number) {
  const mx = Math.max(0, (layout.value.width * zoom - vp.value.w) / 2) + 120;
  const my = Math.max(0, (layout.value.height * zoom - vp.value.h) / 2) + 120;
  return { x: Math.min(mx, Math.max(-mx, px)), y: Math.min(my, Math.max(-my, py)) };
}

// 初始视图：画布顶部对齐视口顶部并避开悬浮操作条（早的留言在上方，新留言向下平移即可看到）
function initialView(zoom = 1) {
  const p = clampPan(0, 96 - vp.value.h / 2 + (layout.value.height * zoom) / 2, zoom);
  return { zoom, x: p.x, y: p.y };
}

// 视图状态：zoom 缩放 + x/y 平移（画布中心相对视口中心的位移）
const view = ref(initialView(1));

// 画布尺寸/视口变化（新增便签、窗口缩放）时，把当前视图收回合法范围
watch(
  () => [layout.value.width, layout.value.height, vp.value.w, vp.value.h],
  () => {
    const v = view.value;
    const p = clampPan(v.x, v.y, v.zoom);
    if (p.x !== v.x || p.y !== v.y) view.value = { ...v, x: p.x, y: p.y };
  }
);

// 滚轮缩放：以光标位置为锚点，光标下的画布点缩放前后保持在原地
function onWheel(e: WheelEvent) {
  if (Math.abs(e.deltaY) < Math.abs(e.deltaX)) return; // 触控板横向滑动不参与缩放
  const el = viewportRef.value;
  if (!el) return;
  const rect = el.getBoundingClientRect();
  const vx = e.clientX - rect.left - rect.width / 2;
  const vy = e.clientY - rect.top - rect.height / 2;
  const v0 = view.value;
  const zoom = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, Number((v0.zoom * Math.exp(-e.deltaY * ZOOM_SPEED)).toFixed(3))));
  const k = zoom / v0.zoom;
  const p = clampPan(vx - (vx - v0.x) * k, vy - (vy - v0.y) * k, zoom);
  view.value = { zoom, x: p.x, y: p.y };
}

// 空白处拖拽平移（按到便签或按钮上不触发）
let pan: { px: number; py: number; x: number; y: number } | null = null;
function onPointerDown(e: PointerEvent) {
  const t = e.target as HTMLElement;
  if (t.closest('[data-note]') || t.closest('button')) return;
  pan = { px: e.clientX, py: e.clientY, x: view.value.x, y: view.value.y };
  (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
}
function onPointerMove(e: PointerEvent) {
  if (!pan) return;
  const p = clampPan(pan.x + e.clientX - pan.px, pan.y + e.clientY - pan.py, view.value.zoom);
  const v = view.value;
  if (v.x !== p.x || v.y !== p.y) view.value = { ...v, x: p.x, y: p.y };
}
function endPan() {
  pan = null;
}

// 便签拖动偏移（画布坐标系）：视口裁剪会卸载视野外便签，偏移保存在这里，回到视野后原位恢复
const offsets = reactive<Record<number, { x: number; y: number }>>({});
const draggingId = ref<number | null>(null);
const zMap = reactive<Record<number, number>>({});
let zCounter = 20;

function pressNote(id: number) {
  zCounter += 1;
  zMap[id] = zCounter;
  draggingId.value = id;
}
function moveNote(id: number, x: number, y: number) {
  offsets[id] = { x, y };
  draggingId.value = null;
}

// 视口裁剪：视野中心换算到画布坐标，四周各扩 1.5 个槽位做缓冲，只渲染落在其中的便签
const visibleIdx = computed(() => {
  const halfW = vp.value.w / 2 / view.value.zoom + CELL * 1.5;
  const halfH = vp.value.h / 2 / view.value.zoom + CELL * 1.5;
  const cx = layout.value.width / 2 - view.value.x / view.value.zoom;
  const cy = layout.value.height / 2 - view.value.y / view.value.zoom;
  const list: number[] = [];
  layout.value.slots.forEach((s, i) => {
    const id = props.notes[i].id;
    const off = offsets[id] ?? ZERO_OFFSET;
    const nx = s.x + off.x;
    const ny = s.y + off.y;
    if (
      id === draggingId.value || // 正在拖动的便签永不卸载，避免拖到一半被裁掉
      (nx + NOTE_SIZE > cx - halfW && nx < cx + halfW && ny + NOTE_SIZE > cy - halfH && ny < cy + halfH)
    ) {
      list.push(i);
    }
  });
  return list;
});

function resetView() {
  view.value = initialView(1);
}
</script>

<template>
  <!-- 平移/缩放视口：铺满幕布，空白处可按住平移，滚轮缩放 -->
  <div
    ref="viewportRef"
    class="absolute inset-0 cursor-grab touch-none overflow-hidden active:cursor-grabbing"
    @wheel="onWheel"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="endPan"
    @pointercancel="endPan"
  >
    <!-- 大画布：中心对齐视口中心，随 view 平移/缩放（translate 在 scale 之前，位移不受缩放倍率影响） -->
    <div
      class="absolute left-1/2 top-1/2"
      :style="{
        width: `${layout.width}px`,
        height: `${layout.height}px`,
        transform: `translate(calc(-50% + ${view.x}px), calc(-50% + ${view.y}px)) scale(${view.zoom})`
      }"
    >
      <FullNote
        v-for="i in visibleIdx"
        :key="notes[i].id"
        :n="notes[i]"
        :slot-pos="layout.slots[i]"
        :offset="offsets[notes[i].id] ?? ZERO_OFFSET"
        :z-index="zMap[notes[i].id] ?? 10"
        :zoom="view.zoom"
        @press="pressNote"
        @move="moveNote"
      />
    </div>
  </div>

  <!-- 悬浮操作条：浮在画布之上，不挤压画布尺寸（按钮区域可点击，其余透传平移） -->
  <div class="pointer-events-none absolute inset-x-0 top-0 z-20 bg-gradient-to-b from-warm via-warm/85 to-transparent pb-10">
    <div class="flex items-center justify-between px-8 pt-5">
      <div>
        <p class="font-code text-xs tracking-[0.4em] text-tech">MESSAGE WALL</p>
        <h3 class="font-title mt-1 text-2xl font-black tracking-wide text-ink">校友留言墙</h3>
        <p class="mt-1.5 text-xs tracking-widest text-slate-400">
          共 {{ notes.length }} 条留言 · 拖动空白处平移 · 滚轮缩放 · 按住便签可拖动 · 按 ESC 或"收拢幕布"返回
        </p>
      </div>
      <div class="pointer-events-auto flex items-center gap-3">
        <button
          type="button"
          title="点击复位为 100%"
          class="rounded-xl border border-ink/15 bg-white/70 px-3 py-2.5 font-code text-xs font-bold tabular-nums text-ink/70 transition hover:border-ink/35 hover:text-ink"
          @click="resetView"
        >
          {{ Math.round(view.zoom * 100) }}%
        </button>
        <button
          type="button"
          class="flex items-center gap-2 rounded-xl border border-tech/35 bg-tech/5 px-4 py-2.5 text-sm font-semibold text-tech transition hover:border-tech hover:bg-tech hover:text-white"
          @click="$emit('new')"
        >
          <span class="text-base leading-none">＋</span>
          新建留言
        </button>
        <button
          type="button"
          class="flex items-center gap-2 rounded-xl border border-ink/20 bg-white/70 px-4 py-2.5 text-sm font-semibold text-ink transition hover:border-ink/40 hover:bg-white"
          @click="$emit('close')"
        >
          <Minimize2 :size="16" />
          收拢幕布
        </button>
      </div>
    </div>
  </div>
</template>
