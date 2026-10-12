<script setup lang="ts">
// 开发环境验证码回显（仅当后端返回 dev_code 字段时出现）
defineProps<{ code?: string }>();
</script>

<template>
  <div
    v-if="code"
    class="mt-3 rounded-lg border border-lamp/40 bg-lamp/10 px-4 py-2.5 text-xs leading-relaxed text-[#8a6200]"
  >
    开发环境验证码：<span class="font-code font-bold tracking-[0.3em]">{{ code }}</span>
    <span class="ml-2 text-[#8a6200]/70">（测试环境短信为模拟发送）</span>
  </div>
</template>
