<script setup lang="ts">
// ============================================================================
// 校友个人中心（/profile/*）页面共用外壳（响应式版）
// ----------------------------------------------------------------------------
// - 桌面端（≥md）：HomeNav forceSolid + 1100px 宽容器
// - 小屏（<md，桌面浏览器窗口收窄）：蓝底顶栏 + 返回 + 标题 slot + 居中 640px 容器
// ============================================================================
import { computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import HomeNav from '../HomeNav.vue';
import { authState } from '../../auth';

const route = useRoute();
const router = useRouter();

/** 顶栏标题：未显式传入则用页面 meta.title，最后回退到「个人中心」 */
const props = withDefaults(defineProps<{ topTitle?: string }>(), { topTitle: '' });
const fallbackTitle = computed(() => {
  const meta = (route.meta?.title as string | undefined) ?? '';
  return props.topTitle || meta || '个人中心';
});

/** 路由切换回到页首 */
watch(
  () => route.path,
  () => window.scrollTo(0, 0),
  { immediate: true }
);

/** 兜底守卫：在页期间被登出时回登录页 */
watch(
  () => [authState.loading, authState.user] as const,
  ([loading, user]) => {
    if (!loading && !user) {
      router.replace({ path: '/login', query: { from: route.fullPath } });
    }
  },
  { immediate: true }
);

const goBack = () => {
  // 能退就退，否则回 /profile
  if (window.history.length > 1) router.back();
  else router.push('/profile');
};
</script>

<template>
  <!-- 首次还原登录态中：仅显示加载占位 -->
  <div v-if="authState.loading" class="min-h-screen bg-warm">
    <HomeNav force-solid />
    <div class="flex min-h-[60vh] items-center justify-center">
      <span class="font-code text-xs tracking-[0.3em] text-slate-400">LOADING…</span>
    </div>
  </div>

  <div v-else-if="authState.user" class="min-h-screen bg-warm pb-20 md:pb-24">
    <!-- 桌面顶栏：HomeNav，深色实底（≥md 才显示） -->
    <div class="hidden md:block">
      <HomeNav force-solid />
    </div>

    <!-- 小屏顶栏：深色实底 + 安全区 + 返回 + 标题 + 右侧操作插槽（<md 才显示） -->
    <header
      class="sticky top-0 z-30 flex h-14 items-center gap-2 bg-[#102a5c] px-3 text-white shadow-[0_4px_18px_rgba(5,10,30,0.25)] md:hidden"
    >
      <div class="h5-safe-top absolute inset-x-0 top-0 -z-10 bg-[#102a5c]" />
      <button
        type="button"
        aria-label="返回"
        class="flex h-10 w-10 items-center justify-center rounded-full transition hover:bg-white/10 active:bg-white/20"
        @click="goBack"
      >
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M15 18l-6-6 6-6" />
        </svg>
      </button>
      <h1 class="font-title min-w-0 flex-1 truncate text-base font-black tracking-wide">{{ fallbackTitle }}</h1>
      <div class="shrink-0">
        <slot name="topbar" />
      </div>
    </header>

    <!-- 内容区 -->
    <main
      class="shell-enter mx-auto w-full max-w-[640px] px-4 pb-2 pt-2 md:max-w-[1100px] md:px-6 md:pt-[104px] lg:w-[86%]"
    >
      <slot />
    </main>
  </div>
</template>

<style scoped>
/* 内容区入场：上浮淡入（与全站缓动一致） */
.shell-enter {
  animation: shellEnter 0.4s cubic-bezier(0.22, 0.61, 0.36, 1);
}
@keyframes shellEnter {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>