<script setup lang="ts">
/**
 * PageLoading · 路由加载占位（Suspense fallback）
 * ----------------------------------------------------------------------
 * 用于 App.vue 的 `<Suspense>` 包裹路由异步 chunk 时显示：
 *   - 用户点击路由 → 新页面 chunk 异步加载 + setup() 内可能触发首屏 API 请求
 *   - 此期间显示骨架屏（含顶部蓝色导航栏模拟 + 主体内容骨架 + 底部 TabBar 模拟）
 *   - 用户感知"页面在加载"而非"白屏"
 *
 * 设计原则：
 *   - 与桌面端 Home 等页面 1:1 视觉对齐（顶部蓝色 56px + 主内容卡片 + 圆角）
 *   - 不用真 SVG icon 占位，用纯 CSS 灰块 + pulse 动画
 *   - 自适应深浅色背景（沿用 bg-warm）
 */
</script>

<template>
  <div class="h5-root h5-dvh min-h-screen bg-warm">
    <!-- 顶部 Header 骨架 -->
    <header class="sticky top-0 z-40 w-full bg-[#102a5c] shadow-[0_4px_18px_rgba(5,10,30,0.25)]">
      <div class="h5-safe-top absolute inset-x-0 top-0 -z-10 bg-[#102a5c]" />
      <div class="flex h-14 items-center gap-2 px-3">
        <div class="h-10 w-10 rounded-full bg-white/10" />
        <div class="h-3.5 flex-1 rounded-full bg-white/15" />
        <div class="h-5 w-12 rounded-full bg-white/10" />
      </div>
    </header>

    <!-- 主内容骨架 -->
    <main class="px-4 pt-4">
      <!-- 顶部圆图 + 文字（Hero 占位）-->
      <div class="overflow-hidden rounded-2xl bg-white shadow-[0_6px_18px_rgba(5,10,30,0.06)]">
        <div class="skel h-44 w-full bg-slate-200/80" />
      </div>

      <!-- 中部快捷入口 4x2 -->
      <div class="mt-3 rounded-2xl bg-white px-2 py-3 shadow-[0_6px_18px_rgba(5,10,30,0.06)]">
        <div class="grid grid-cols-4 gap-y-3 px-2">
          <div v-for="i in 8" :key="i" class="flex flex-col items-center gap-1.5">
            <div class="skel h-11 w-11 rounded-xl bg-slate-200/80" />
            <div class="skel h-2 w-10 rounded-full bg-slate-200/70" />
          </div>
        </div>
      </div>

      <!-- 列表项骨架 x3 -->
      <div class="mt-4 space-y-2.5">
        <div
          v-for="i in 3"
          :key="i"
          class="flex items-start gap-3 rounded-2xl bg-white p-3 shadow-[0_6px_18px_rgba(5,10,30,0.06)]"
        >
          <div class="skel h-14 w-14 shrink-0 rounded-xl bg-slate-200/80" />
          <div class="min-w-0 flex-1 space-y-1.5">
            <div class="skel h-3 w-3/4 rounded-full bg-slate-200/70" />
            <div class="skel h-2.5 w-1/2 rounded-full bg-slate-200/60" />
            <div class="skel h-2.5 w-2/3 rounded-full bg-slate-200/50" />
          </div>
        </div>
      </div>
    </main>

    <!-- 底部固定加载提示（小条 + 自旋）+ 底部安全区 -->
    <div class="pointer-events-none fixed inset-x-0 bottom-6 z-50 flex justify-center px-4">
      <div class="flex items-center gap-2 rounded-full bg-ink/85 px-4 py-2 text-[12px] font-medium text-warm shadow-lg backdrop-blur-sm">
        <span class="inline-block h-3 w-3 animate-spin rounded-full border-2 border-warm/30 border-t-warm" aria-hidden="true" />
        正在加载…
      </div>
    </div>
    <div class="h5-safe-bottom" />
  </div>
</template>

<style scoped>
/* 骨架块脉动动画：1.6s 一周期，透明度 1→0.4→1 */
.skel {
  animation: skel-pulse 1.6s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
@keyframes skel-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}
@media (prefers-reduced-motion: reduce) {
  .skel { animation: none; opacity: 0.7; }
}
</style>