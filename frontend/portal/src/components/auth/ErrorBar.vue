<script setup lang="ts">
// 错误提示条（认证页共用）
defineProps<{ message: string }>();
</script>

<template>
  <div
    v-if="message"
    class="rounded-lg border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-600"
  >
    {{ message }}
  </div>
</template>
