<script setup lang="ts">
// 新闻页面共用外壳：复用首页 HomeNav（forceSolid 实底模式）+ 内容区 + 页脚
// 避免维护两套导航样式差异；二级下拉 / 个人中心菜单 / 锚点跳转都交给 HomeNav 统一处理
import HomeNav from '../HomeNav.vue';
</script>

<template>
  <div class="flex min-h-screen flex-col bg-warm">
    <!-- 复用首页 HomeNav：forceSolid 让新闻页恒为纯蓝实底（与首屏大图无关） -->
    <HomeNav :force-solid="true" />

    <!-- 内容区：pt 留出固定顶栏高度（72px）+ 原间距（40px） -->
    <main class="mx-auto w-[80%] max-w-[1200px] flex-1 pb-10 pt-[112px]"><slot /></main>

    <footer class="border-t border-lamp/20 bg-night px-6 py-8 text-center text-xs text-warm/60">
      <p class="font-title text-base text-warm">福州软件职业技术学院 · 校友管理平台</p>
      <p class="mt-2">数字教育小镇 · 长乐区滨海大道999号 · 0591-83843292</p>
      <p class="font-code mt-2 text-[10px] tracking-widest text-warm/40">无论走多远，城堡永远为你亮灯</p>
    </footer>
  </div>
</template>
