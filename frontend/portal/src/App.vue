<script setup lang="ts">
// ============================================================================
// 根组件：承载路由出口 + 路由切换体验
// ----------------------------------------------------------------------------
// 改造要点（解决"点击后空白需要手动刷新"问题）：
//   1) <router-view> 包裹 <Suspense>：异步 chunk 加载 + setup() 内首屏 API 请求期间
//      显示 PageLoading 骨架屏（不是空白）；
//   2) transition 改 mode="default"：旧页面淡出同时新页面加载中显示骨架，
//      避免 mode="out-in" 切换瞬间空白；
//   3) 路由切换时路由出口不销毁；
// ============================================================================
import { RouterView } from 'vue-router';
import PageLoading from './components/PageLoading.vue';
</script>

<template>
  <!-- 路由出口：Suspense 提供异步加载期间的占位，transition 提供切换过渡 -->
  <router-view v-slot="{ Component }">
    <Suspense>
      <transition name="page" mode="default">
        <!-- 已加载完成的页面 -->
        <component :is="Component" v-if="Component" :key="$route.fullPath" />
      </transition>

      <!-- 异步 chunk / 组件 setup 内 await 期间显示的占位 -->
      <template #fallback>
        <PageLoading />
      </template>
    </Suspense>
  </router-view>
</template>

<style>
/* ============================================================================
 * 路由切换过渡
 *  - mode="default"：进入与离开同时进行，新组件加载期间骨架屏先露出来
 *  - 进入：从下方 12px + 渐显（避免硬切换造成的"跳一下"感）
 *  - 离开：向上 8px + 渐隐
 *  - prefers-reduced-motion 时由 CSS 关掉所有过渡
 * ========================================================================== */
.page-enter-active,
.page-leave-active {
  transition:
    opacity 0.22s cubic-bezier(0.22, 0.61, 0.36, 1),
    transform 0.22s cubic-bezier(0.22, 0.61, 0.36, 1);
}
.page-enter-from {
  opacity: 0;
  transform: translateY(12px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
.page-leave-active {
  /* 离开的组件在离开期间绝对定位，避免与进入组件布局重叠导致抖动 */
  position: absolute;
  inset: 0;
  width: 100%;
}
@media (prefers-reduced-motion: reduce) {
  .page-enter-active,
  .page-leave-active {
    transition: none;
    position: static;
  }
}
</style>