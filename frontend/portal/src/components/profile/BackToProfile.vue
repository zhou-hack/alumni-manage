<script setup lang="ts">
// 返回链接（响应式）：H5 下变为图标按钮 + 文字同行；桌面端保持文字链样式
</script>

<template>
  <RouterLink
    to="/profile"
    class="inline-flex h-10 items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-600 shadow-sm transition hover:border-tech/40 hover:text-tech md:px-4 md:py-2"
  >
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M15 18l-6-6 6-6" />
    </svg>
    <span class="hidden md:inline">返回个人中心</span>
    <span class="md:hidden">返回</span>
  </RouterLink>
</template>