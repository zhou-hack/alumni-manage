<script setup lang="ts">
// 页面标题区（响应式）：桌面端沿用三段式，H5 字号缩、action 全宽堆叠
defineProps<{ eyebrow: string; title: string; desc?: string }>();
</script>

<template>
  <header class="mb-5 flex flex-col gap-3 md:mb-7 md:flex-row md:flex-wrap md:items-end md:justify-between md:gap-4">
    <div class="min-w-0">
      <div class="flex items-center gap-2.5 md:gap-4">
        <span class="h-px w-7 bg-gradient-to-r from-transparent to-lamp/70 md:w-14" />
        <span class="font-code text-[10px] tracking-[0.32em] text-tech md:text-xs md:tracking-[0.4em]">{{ eyebrow }}</span>
      </div>
      <h1 class="font-title mt-2 text-[20px] font-black tracking-[0.04em] text-ink md:mt-3 md:text-3xl md:tracking-[0.04em]">
        {{ title }}
      </h1>
      <p v-if="desc" class="mt-1.5 line-clamp-2 text-xs text-slate-500 md:mt-2 md:text-sm">{{ desc }}</p>
    </div>
    <div v-if="$slots.action" class="flex w-full flex-col gap-2 md:w-auto md:flex-row md:flex-wrap md:items-center md:gap-2.5">
      <slot name="action" />
    </div>
  </header>
</template>