<script setup lang="ts">
// 认证相关页面（登录/注册激活/忘记密码）共用外壳：
// 夜空城堡主题 —— 深紫夜空底 + 灯光金点缀 + 居中白卡片，与门户风格统一。
withDefaults(defineProps<{ width?: string }>(), { width: 'max-w-md' });

/** 背景星点（固定位置，纯装饰） */
const STARS = [
  { l: '12%', t: '22%' }, { l: '28%', t: '64%' }, { l: '46%', t: '14%' },
  { l: '63%', t: '38%' }, { l: '78%', t: '70%' }, { l: '88%', t: '26%' },
  { l: '8%', t: '82%' }, { l: '55%', t: '88%' }
];
</script>

<template>
  <div class="relative flex min-h-screen flex-col overflow-hidden bg-night">
    <!-- 背景装饰：城堡光晕 + 星点 -->
    <div
      aria-hidden
      class="pointer-events-none absolute inset-0"
      :style="{
        background:
          'radial-gradient(720px 420px at 18% -8%, rgba(37,99,235,0.28), transparent 65%),' +
          'radial-gradient(560px 380px at 88% 12%, rgba(245,179,1,0.14), transparent 60%),' +
          'radial-gradient(900px 520px at 50% 115%, rgba(26,16,61,0.9), transparent 70%)'
      }"
    />
    <div aria-hidden class="pointer-events-none absolute inset-0 opacity-70">
      <span
        v-for="(s, i) in STARS"
        :key="i"
        class="absolute h-1 w-1 rounded-full bg-warm/70"
        :style="{ left: s.l, top: s.t, boxShadow: '0 0 6px rgba(255,249,240,0.9)' }"
      />
    </div>

    <!-- 顶栏：品牌 + 返回首页 -->
    <header class="relative z-10 px-6 py-4">
      <div class="mx-auto flex w-[92%] max-w-[1100px] items-center justify-between gap-4">
        <RouterLink to="/" class="flex items-center gap-3 transition-opacity hover:opacity-85">
          <span class="flex h-9 w-9 items-center justify-center overflow-hidden rounded-xl bg-white ring-1 ring-white/50">
            <img src="/bgcsvg/school-badge.png" alt="校徽" class="h-full w-full object-cover" />
          </span>
          <span class="font-title text-lg font-bold text-warm">福软数字城堡</span>
        </RouterLink>
        <RouterLink to="/" class="shrink-0 text-xs text-warm/70 transition-colors hover:text-lamp">
          返回首页
        </RouterLink>
      </div>
    </header>

    <!-- 内容区 -->
    <main class="relative z-10 flex flex-1 items-center justify-center px-4 py-8 sm:py-12">
      <div class="w-full" :class="width"><slot /></div>
    </main>

    <!-- 页脚 -->
    <footer class="relative z-10 px-6 pb-6 pt-2 text-center">
      <p class="font-code text-[10px] tracking-[0.32em] text-warm/45">
        无论走多远，城堡永远为你亮灯
      </p>
    </footer>
  </div>
</template>
