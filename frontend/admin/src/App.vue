<script setup>
/**
 * 根组件：仅承载路由出口。
 * 启动预热：在路由守卫之前同步调用 loadMenus，让菜单能立刻基于 localStorage
 * 缓存的学院字典渲染（解决"首次登录展开，再登录菜单消失"问题）。
 */
import { onBeforeMount } from 'vue'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/utils/token'

onBeforeMount(() => {
  if (getToken()) {
    const store = useUserStore()
    // fire-and-forget；loadMenus 内部已有缓存兜底
    store.loadMenus().catch(() => {})
  }
})
</script>

<template>
  <router-view />
</template>
