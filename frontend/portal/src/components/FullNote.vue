<script setup lang="ts">
// ------------------------------ 全屏幕布：单张便签 ------------------------------
// 无入场编排与浮动动画（数百张同屏时保证流畅）；拖动偏移由父级保存，卸载后原位恢复。
//
// 拖拽为手写 PointerEvent 实现而不用拖拽库：拖拽库会把指针的屏幕位移直接写进局部坐标，
// 不除以祖先缩放 —— 缩放 ≠100% 时便签位移 = 指针位移 × 缩放倍率（缩小滞后、放大飞出，即"不跟手"）。
// 这里手动把指针位移 ÷ zoom 换算到画布坐标系，任意缩放级别下都与光标 1:1 跟手，且天然无边界。
import { onMounted, ref } from 'vue';
import NotePaper from './NotePaper.vue';
import type { Note, Slot } from './messageWall';

const props = defineProps<{
  n: Note;
  /** 网格槽位（含确定性抖动与角度；原名 slot，避开 Vue 的 slot 保留语义） */
  slotPos: Slot;
  offset: { x: number; y: number };
  zIndex: number;
  zoom: number;
}>();
const emit = defineEmits<{
  press: [id: number];
  move: [id: number, x: number, y: number];
}>();

// 拖动偏移（画布坐标系）：按下时以父级保存的偏移为起点，松手后回写父级
const x = ref(props.offset.x);
const y = ref(props.offset.y);
const dragging = ref(false);
// 入场淡入（对应 React 版 motion 的 initial opacity 0 → animate 1，0.25s）
const shown = ref(false);
let start: { px: number; py: number; x: number; y: number } | null = null;

function onPointerDown(e: PointerEvent) {
  e.stopPropagation(); // 阻止触发画布的空白处平移
  start = { px: e.clientX, py: e.clientY, x: x.value, y: y.value };
  (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  dragging.value = true;
  emit('press', props.n.id);
}
function onPointerMove(e: PointerEvent) {
  if (!start) return;
  x.value = start.x + (e.clientX - start.px) / props.zoom;
  y.value = start.y + (e.clientY - start.py) / props.zoom;
}
function endDrag() {
  if (!start) return;
  start = null;
  dragging.value = false;
  emit('move', props.n.id, x.value, y.value);
}

onMounted(() => {
  // 双 rAF 确保初始 opacity:0 先落一帧，再触发过渡
  requestAnimationFrame(() =>
    requestAnimationFrame(() => {
      shown.value = true;
    })
  );
});
</script>

<template>
  <!-- 外层：槽位 + 拖动位移 + 角度，无过渡（保证拖拽 1:1 跟手） -->
  <div
    data-note
    class="absolute select-none"
    :class="dragging ? 'cursor-grabbing' : 'cursor-grab'"
    :style="{
      left: `${slotPos.x}px`,
      top: `${slotPos.y}px`,
      zIndex,
      transform: `translate(${x}px, ${y}px) rotate(${slotPos.rotate}deg)`
    }"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="endDrag"
    @pointercancel="endDrag"
  >
    <!-- 内层：入场淡入 + 拖拽态放大 1.07 / 加深阴影（0.25s 过渡，独立于位移层） -->
    <div
      :style="{
        opacity: shown ? 1 : 0,
        transform: `scale(${dragging ? 1.07 : 1})`,
        boxShadow: dragging ? '0 20px 44px rgba(26,16,61,0.24)' : '0 0px 0px rgba(26,16,61,0)',
        transition: 'opacity 0.25s, transform 0.25s, box-shadow 0.25s'
      }"
    >
      <NotePaper :n="n" class="h-[200px] w-[200px]" />
    </div>
  </div>
</template>
