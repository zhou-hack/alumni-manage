<script setup lang="ts">
// 便签纸（页面内与全屏共用）
// 注意：根节点的 relative 定位必须保留（此前移除会导致全屏拖拽时鼠标不跟手）
import type { Note } from './messageWall';

defineProps<{ n: Note }>();
</script>

<template>
  <!-- 手机端缩小内边距与字号（132px 便签），md 起恢复 176px 排版 -->
  <div
    class="relative flex select-none flex-col overflow-hidden rounded-[6px] p-2.5 shadow-[0_8px_20px_rgba(26,16,61,0.12)] md:p-4"
    :style="{ background: n.color }"
  >
    <!-- 顶部胶带 -->
    <span class="absolute -top-1.5 left-1/2 h-3 w-10 -translate-x-1/2 -rotate-2 rounded-[2px] bg-lamp/35 md:h-3.5 md:w-16" />

    <p class="line-clamp-5 flex-1 text-[11px] leading-snug text-ink/85 md:line-clamp-none md:text-[13px] md:leading-relaxed">{{ n.text }}</p>

    <div class="mt-1.5 border-t border-ink/10 pt-1.5 text-right md:mt-2 md:pt-2">
      <p class="font-title text-xs font-black tracking-wide text-ink md:text-sm">{{ n.name }}</p>
      <!-- 届别·学院：10px + 70% 深墨 + 半粗，小字号下取消加宽字距（9px/50%/加宽字距在便签纸上发虚看不清） -->
      <p class="mt-0.5 font-code text-[9px] font-semibold leading-tight text-ink/70 md:text-[10px]">
        {{ n.gradYear }} · {{ n.college }}
      </p>
    </div>
  </div>
</template>
