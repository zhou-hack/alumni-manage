<script setup lang="ts">
// 档案/荣誉审核状态标签（色调与文案见 shared.ts 的 ARCHIVE_STATUS 字典）
import { computed } from 'vue';
import { statusOf } from './shared';

const props = defineProps<{ status?: string }>();
const s = computed(() => statusOf(props.status));
</script>

<template>
  <span
    class="inline-flex shrink-0 items-center rounded-full px-3 py-0.5 text-xs font-semibold"
    :style="{ color: s.color, background: s.color + '1a' }"
  >
    {{ s.text }}
  </span>
</template>
