<script setup lang="ts">
// 校友荣誉大屏 · 独立页面（路由 /dashboard）
// 面向全体师生公开浏览（后端聚合接口 /api/v1/dashboard-honor 免认证）；
// 视觉与交互来自 [原荣誉大屏.html]，Vue 3 Composition API + ECharts 5 重构。
import { onMounted } from 'vue';
import HonorScreen from '../components/honor/HonorScreen.vue';

// SPA 路由跳转不会自动重置滚动位置，进入大屏时回到顶部，保证第一眼是完整的一屏
onMounted(() => {
  window.scrollTo(0, 0);
});
</script>

<template>
  <HonorScreen />
</template>
