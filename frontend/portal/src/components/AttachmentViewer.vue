<script setup lang="ts">
// ============================================================================
// 附件查看器（荣誉墙/校友档案共用）
//
// 展示端"证书原件附件"预览能力（PRD M02-03 / M03-02）：
//   - 图片附件  → 灯箱式查看（缩放、下载）
//   - PDF 附件  → PDF.js 在线预览（页码导航、缩放、下载）——PDF 相关代码按需懒加载，
//                 不进入首页主包，仅在用户真正打开 PDF 附件时下载 pdfjs-dist 与 worker
//   - Word/Excel → 文件名 + 下载按钮（PRD M02-03 仅要求 PDF 在线预览）
//
// 用法：
//   const att = ref<HonorAttachment | null>(null);
//   <AttachmentViewer :attachment="att" @close="att = null" />
// ============================================================================
import { defineAsyncComponent, defineComponent, h, onBeforeUnmount, ref, watch } from 'vue';
import { X } from './icons';
import type { HonorAttachment } from '../mock/outstandingAlumni';

// PDF 预览按需加载（含 pdfjs-dist 与 worker），不影响首页主包
const PdfPreview = defineAsyncComponent({
  loader: () => import('./PdfPreview.vue'),
  // 懒加载期间的兜底样式（与包内组件加载态一致）
  loadingComponent: defineComponent({
    name: 'PdfPreviewLoading',
    setup: () => () =>
      h('div', { class: 'flex h-full items-center justify-center gap-2 bg-night/40 text-warm/80' }, [
        h('span', { class: 'h-4 w-4 animate-spin rounded-full border-2 border-warm/30 border-t-lamp' }),
        '正在加载 PDF 预览组件…'
      ])
  })
});

const props = defineProps<{ attachment: HonorAttachment | null }>();
const emit = defineEmits<{ close: [] }>();

// 图片灯箱缩放（0.5x ~ 3x；关闭后复位，下次打开回到 100%）
const zoom = ref(1);

// ------------------------------ 背景滚动锁定 ------------------------------
// 打开时锁定背景滚动：position:fixed + 记录 scrollTop，关闭后恢复并还原滚动位置。
// 不用 overflow:hidden——避免锁定瞬间滚动条消失造成页面横向抖动。
// 嵌套安全：body 已被外层（如数字校友档案弹窗）锁定时不重复锁定，
// 否则会读到 scrollY=0 并把背景顶到页面顶部，关闭时还会破坏外层的锁定。
let savedScrollY = 0;
let lockedByMe = false;
function lockScroll() {
  const body = document.body;
  if (body.style.position === 'fixed') return;
  lockedByMe = true;
  savedScrollY = window.scrollY;
  body.style.position = 'fixed';
  body.style.top = `-${savedScrollY}px`;
  body.style.left = '0';
  body.style.right = '0';
  body.style.width = '100%';
}
function unlockScroll() {
  if (!lockedByMe) return;
  lockedByMe = false;
  const body = document.body;
  body.style.position = '';
  body.style.top = '';
  body.style.left = '';
  body.style.right = '';
  body.style.width = '';
  window.scrollTo(0, savedScrollY);
}

// ESC 关闭
const onKey = (e: KeyboardEvent) => {
  if (e.key === 'Escape') emit('close');
};

watch(
  () => props.attachment,
  (att, prev) => {
    if (att && !prev) {
      // 打开：锁定背景滚动 + 监听 ESC
      lockScroll();
      window.addEventListener('keydown', onKey);
    } else if (!att && prev) {
      // 关闭：恢复滚动 + 移除监听 + 灯箱缩放复位
      unlockScroll();
      window.removeEventListener('keydown', onKey);
      zoom.value = 1;
    }
  }
);

// 组件在打开状态下被卸载时兜底解锁
onBeforeUnmount(() => {
  if (props.attachment) {
    unlockScroll();
    window.removeEventListener('keydown', onKey);
  }
});
</script>

<template>
  <Transition name="viewer" :duration="{ enter: 250, leave: 300 }">
    <div v-if="attachment" class="fixed inset-0 z-[90]">
      <!-- 毛玻璃背景：压暗 + 背景模糊 + 轻微提饱和，点击关闭（静态背景仅淡入淡出，无位移动画，blur 无性能负担） -->
      <div class="absolute inset-0 bg-night/55 backdrop-blur-xl backdrop-saturate-150" @click="emit('close')" />

      <!-- 面板 -->
      <div class="viewer-panel absolute inset-4 overflow-hidden rounded-2xl bg-night/70 shadow-2xl md:inset-8">
        <!-- 关闭按钮 -->
        <button
          type="button"
          @click="emit('close')"
          aria-label="关闭"
          class="absolute right-4 top-4 z-10 flex h-9 w-9 items-center justify-center rounded-full bg-night/60 text-warm backdrop-blur transition hover:bg-lamp hover:text-night"
        >
          <X :size="16" />
        </button>

        <!-- 图片附件 → 灯箱式查看（缩放、下载） -->
        <div v-if="attachment.kind === 'image'" class="flex h-full flex-col">
          <div class="flex flex-1 items-center justify-center overflow-auto bg-night/40 p-6">
            <img
              :src="attachment.url"
              :alt="attachment.name"
              :style="{ transform: `scale(${zoom})`, transition: 'transform 0.2s ease-out' }"
              class="max-h-full max-w-full object-contain shadow-2xl"
            />
          </div>
          <!-- 工具栏 -->
          <div class="flex items-center justify-between border-t border-warm/10 bg-night/60 px-5 py-3 backdrop-blur">
            <span class="text-xs text-warm/70">{{ attachment.name }}</span>
            <div class="flex items-center gap-2">
              <button
                type="button"
                @click="zoom = Math.max(0.5, +(zoom - 0.25).toFixed(2))"
                class="rounded-lg border border-warm/20 px-3 py-1.5 text-xs text-warm transition hover:bg-warm/10"
              >
                −
              </button>
              <span class="w-12 text-center font-code text-xs text-warm/80">{{ Math.round(zoom * 100) }}%</span>
              <button
                type="button"
                @click="zoom = Math.min(3, +(zoom + 0.25).toFixed(2))"
                class="rounded-lg border border-warm/20 px-3 py-1.5 text-xs text-warm transition hover:bg-warm/10"
              >
                ＋
              </button>
              <button
                type="button"
                @click="zoom = 1"
                class="ml-1 rounded-lg border border-warm/20 px-3 py-1.5 text-xs text-warm transition hover:bg-warm/10"
              >
                复位
              </button>
              <a
                :href="attachment.url"
                download
                class="ml-2 rounded-lg bg-lamp px-3.5 py-1.5 text-xs font-semibold text-night transition hover:bg-lamp/90"
              >
                下载
              </a>
            </div>
          </div>
        </div>

        <!-- PDF 附件 → PDF.js 在线预览（按需懒加载，含加载中兜底） -->
        <PdfPreview v-else-if="attachment.kind === 'pdf'" :att="attachment" />

        <!-- Word/Excel → 文件名 + 下载按钮 -->
        <div v-else class="flex h-full items-center justify-center bg-night/40 p-6">
          <div class="w-full max-w-sm rounded-2xl bg-white p-8 text-center shadow-2xl">
            <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-xl bg-lamp/15">
              <span class="font-title text-xl font-black text-amber-700">
                {{ attachment.kind === 'word' ? 'W' : 'E' }}
              </span>
            </div>
            <p class="mt-4 font-title text-base font-bold text-ink">{{ attachment.name }}</p>
            <p class="mt-1 text-xs text-slate-500">
              {{ attachment.kind === 'word' ? 'Word 文档' : 'Excel 表格' }} · 暂不支持在线预览，请下载后查看
            </p>
            <a
              :href="attachment.url"
              download
              class="mt-6 inline-block rounded-lg bg-lamp px-6 py-2.5 text-sm font-semibold text-night transition hover:bg-lamp/90"
            >
              下载附件
            </a>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style>
/* 模态框整体淡入淡出（0.25s），面板缩放+上浮（0.3s），
   缓动与原 framer 一致：cubic-bezier(0.22, 0.61, 0.36, 1) */
.viewer-enter-active,
.viewer-leave-active {
  transition: opacity 0.25s cubic-bezier(0.22, 0.61, 0.36, 1);
}
.viewer-enter-from,
.viewer-leave-to {
  opacity: 0;
}
.viewer-enter-active .viewer-panel,
.viewer-leave-active .viewer-panel {
  transition:
    opacity 0.3s cubic-bezier(0.22, 0.61, 0.36, 1),
    transform 0.3s cubic-bezier(0.22, 0.61, 0.36, 1);
}
.viewer-enter-from .viewer-panel {
  opacity: 0;
  transform: scale(0.95) translateY(16px);
}
.viewer-leave-to .viewer-panel {
  opacity: 0;
  transform: scale(0.96) translateY(12px);
}
</style>
