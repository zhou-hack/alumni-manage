<script setup lang="ts">
// ============================================================================
// PDF 在线预览子组件（被 AttachmentViewer 通过 defineAsyncComponent 按需加载）
//
// 仅在用户真正打开 PDF 附件时才下载 pdfjs-dist 代码与 worker，
// 避免拖累首页主包体积。
//
// Vue 版不再使用 react-pdf，改为直接调用 pdfjs-dist：getDocument 加载文档，
// page.render 绘制到 <canvas>（按 devicePixelRatio 放大画布保证高分屏清晰）。
// ============================================================================
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as pdfjs from 'pdfjs-dist';
import type { PDFDocumentProxy, RenderTask } from 'pdfjs-dist';
import { ChevronLeft, ChevronRight, Maximize2, Minimize2 } from './icons';
import type { HonorAttachment } from '../mock/outstandingAlumni';

// PDF.js worker：Vite 会把 pdfjs-dist 的 worker 一并打包，运行时与主包同版本
pdfjs.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.min.js', import.meta.url).toString();

const props = defineProps<{ att: HonorAttachment }>();

const canvasRef = ref<HTMLCanvasElement | null>(null);
const numPages = ref(0);
const page = ref(1);
const zoom = ref(1.1);
const error = ref('');
const loading = ref(true);

let pdfDoc: PDFDocumentProxy | null = null;
/** 当前正在进行的渲染任务（翻页/缩放时先取消，避免竞态） */
let renderTask: RenderTask | null = null;
/** 渲染序号：防止"翻页过快"时上一次 getPage 晚到覆盖新画面 */
let renderSeq = 0;
/** 组件已卸载标记：加载完成前关闭查看器时直接销毁文档，避免泄漏 */
let destroyed = false;

/** 渲染当前页到 canvas（取消上一次未完成的渲染） */
async function renderPage() {
  const doc = pdfDoc;
  const canvas = canvasRef.value;
  if (!doc || !canvas) return;
  renderTask?.cancel();
  renderTask = null;
  const seq = ++renderSeq;

  const pdfPage = await doc.getPage(page.value);
  if (seq !== renderSeq) return; // 已被更新的请求取代

  // devicePixelRatio 缩放：画布按物理像素放大，CSS 尺寸保持逻辑像素，保证清晰度
  const dpr = window.devicePixelRatio || 1;
  const viewport = pdfPage.getViewport({ scale: zoom.value * dpr });
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  canvas.width = Math.floor(viewport.width);
  canvas.height = Math.floor(viewport.height);
  canvas.style.width = `${Math.floor(viewport.width / dpr)}px`;
  canvas.style.height = `${Math.floor(viewport.height / dpr)}px`;

  const task = pdfPage.render({ canvasContext: ctx, viewport });
  renderTask = task;
  try {
    await task.promise;
  } catch (e) {
    // 翻页/缩放触发的主动取消不算错误
    if ((e as { name?: string })?.name !== 'RenderingCancelledException') {
      error.value = (e as Error)?.message || '无法解析该 PDF';
    }
  } finally {
    if (renderTask === task) renderTask = null;
  }
}

onMounted(async () => {
  try {
    const doc = await pdfjs.getDocument(props.att.url).promise;
    if (destroyed) {
      void doc.destroy();
      return;
    }
    pdfDoc = doc;
    numPages.value = doc.numPages;
    page.value = 1;
    loading.value = false;
    await renderPage();
  } catch (e) {
    loading.value = false;
    error.value = (e as Error)?.message || '无法解析该 PDF';
  }
});

// 翻页 / 缩放时重新渲染当前页
watch([page, zoom], () => {
  if (pdfDoc) void renderPage();
});

onBeforeUnmount(() => {
  destroyed = true;
  renderTask?.cancel();
  renderTask = null;
  void pdfDoc?.destroy();
  pdfDoc = null;
});
</script>

<template>
  <div class="flex h-full flex-col">
    <div class="flex flex-1 items-center justify-center overflow-auto bg-night/40 p-6">
      <div v-if="error" class="rounded-xl bg-white/95 px-6 py-8 text-center">
        <p class="text-sm text-red-600">PDF 加载失败</p>
        <p class="mt-2 text-xs text-slate-500">{{ error }}</p>
        <a :href="att.url" download class="mt-4 inline-block rounded-lg bg-lamp px-4 py-2 text-xs font-semibold text-night">
          下载查看
        </a>
      </div>
      <div v-else-if="loading" class="flex items-center gap-2 text-warm/80">
        <span class="h-4 w-4 animate-spin rounded-full border-2 border-warm/30 border-t-lamp" />
        正在加载 PDF…
      </div>
      <!-- canvas 常驻 DOM（v-show 而非 v-if），保证加载完成后立即可绘制 -->
      <canvas v-show="!loading && !error" ref="canvasRef" class="shadow-2xl" />
    </div>
    <!-- 工具栏 -->
    <div class="flex flex-wrap items-center justify-between gap-2 border-t border-warm/10 bg-night/60 px-5 py-3 backdrop-blur">
      <span class="text-xs text-warm/70">{{ att.name }}</span>
      <div class="flex flex-wrap items-center gap-2">
        <!-- 页码导航 -->
        <button
          type="button"
          :disabled="page <= 1"
          @click="page = Math.max(1, page - 1)"
          class="flex items-center gap-1 rounded-lg border border-warm/20 px-2.5 py-1.5 text-xs text-warm transition hover:bg-warm/10 disabled:opacity-40"
        >
          <ChevronLeft :size="12" /> 上一页
        </button>
        <span class="font-code text-xs text-warm/80">
          {{ page }} / {{ numPages || '—' }}
        </span>
        <button
          type="button"
          :disabled="page >= numPages"
          @click="page = Math.min(numPages, page + 1)"
          class="flex items-center gap-1 rounded-lg border border-warm/20 px-2.5 py-1.5 text-xs text-warm transition hover:bg-warm/10 disabled:opacity-40"
        >
          下一页 <ChevronRight :size="12" />
        </button>
        <!-- 缩放 -->
        <button
          type="button"
          @click="zoom = Math.max(0.6, +(zoom - 0.15).toFixed(2))"
          class="rounded-lg border border-warm/20 px-2.5 py-1.5 text-xs text-warm transition hover:bg-warm/10"
          title="缩小"
        >
          <Minimize2 :size="12" />
        </button>
        <span class="w-12 text-center font-code text-xs text-warm/80">{{ Math.round(zoom * 100) }}%</span>
        <button
          type="button"
          @click="zoom = Math.min(2.5, +(zoom + 0.15).toFixed(2))"
          class="rounded-lg border border-warm/20 px-2.5 py-1.5 text-xs text-warm transition hover:bg-warm/10"
          title="放大"
        >
          <Maximize2 :size="12" />
        </button>
        <a
          :href="att.url"
          download
          class="ml-1 rounded-lg bg-lamp px-3.5 py-1.5 text-xs font-semibold text-night transition hover:bg-lamp/90"
        >
          下载
        </a>
      </div>
    </div>
  </div>
</template>
