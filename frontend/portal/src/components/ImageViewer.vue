<script setup lang="ts">
// 图片查看器（与后台荣誉审核 AttachmentPreviewer 的 el-image-viewer 同一交互口径）：
// 点击图片全屏打开；工具栏 缩小 / 放大 / 还原；滚轮缩放；点击遮罩、右上角 X 或 ESC 关闭。
// 门户无 element-plus 依赖，此处按其交互实现同一模块方法。
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { X, Maximize2, Minimize2, RefreshCw } from './icons';

defineProps<{ url: string }>();
const emit = defineEmits<{ (e: 'close'): void }>();

const scale = ref(1);

const zoomIn = () => {
  scale.value = Math.min(Math.round((scale.value + 0.25) * 100) / 100, 5);
};
const zoomOut = () => {
  scale.value = Math.max(Math.round((scale.value - 0.25) * 100) / 100, 0.2);
};
const reset = () => {
  scale.value = 1;
};

const onKey = (e: KeyboardEvent) => {
  if (e.key === 'Escape') emit('close');
};
const onWheel = (e: WheelEvent) => {
  if (e.deltaY < 0) zoomIn();
  else zoomOut();
};

onMounted(() => {
  window.addEventListener('keydown', onKey);
  document.body.style.overflow = 'hidden';
});
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey);
  document.body.style.overflow = '';
});
</script>

<template>
  <div
    class="fixed inset-0 z-[100] flex items-center justify-center bg-black/85"
    role="dialog"
    aria-label="图片查看器"
    @click="emit('close')"
  >
    <!-- 图片本体：点击不关闭，滚轮缩放（与 el-image-viewer 一致） -->
    <img
      :src="url"
      alt="查看图片"
      class="max-h-[85vh] max-w-[90vw] select-none object-contain transition-transform duration-150"
      :style="{ transform: `scale(${scale})` }"
      draggable="false"
      @click.stop
      @wheel.prevent="onWheel"
    />

    <!-- 工具栏（对齐 el-image-viewer：缩小 / 放大 / 还原） -->
    <div
      class="absolute bottom-8 left-1/2 flex -translate-x-1/2 items-center gap-1 rounded-full bg-white/10 px-3 py-1.5 backdrop-blur"
      @click.stop
    >
      <button type="button" aria-label="缩小" class="viewer-btn" @click="zoomOut">
        <Minimize2 :size="18" />
      </button>
      <span class="w-12 text-center font-code text-xs text-white/80">{{ Math.round(scale * 100) }}%</span>
      <button type="button" aria-label="放大" class="viewer-btn" @click="zoomIn">
        <Maximize2 :size="18" />
      </button>
      <span class="mx-1 h-4 w-px bg-white/20" />
      <button type="button" aria-label="还原" class="viewer-btn" @click="reset">
        <RefreshCw :size="18" />
      </button>
    </div>

    <!-- 关闭 -->
    <button
      type="button"
      aria-label="关闭"
      class="absolute right-5 top-5 rounded-full bg-white/10 p-2 text-white transition hover:bg-white/25"
      @click="emit('close')"
    >
      <X :size="20" />
    </button>
  </div>
</template>

<style scoped>
.viewer-btn {
  border-radius: 9999px;
  padding: 6px;
  color: rgba(255, 255, 255, 0.85);
  transition: background-color 0.15s;
}
.viewer-btn:hover {
  background-color: rgba(255, 255, 255, 0.2);
  color: #fff;
}
</style>
