<script setup lang="ts">
// 轻提示条（响应式）：H5 下避开底部安全区 + TabBar，居中、紧凑
import type { ToastState } from './shared';

defineProps<{ toast: ToastState | null }>();
</script>

<template>
  <div class="pointer-events-none fixed inset-x-0 bottom-20 z-[90] flex justify-center px-4 md:bottom-8">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="translate-y-4 scale-95 opacity-0"
      enter-to-class="translate-y-0 scale-100 opacity-100"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="translate-y-0 scale-100 opacity-100"
      leave-to-class="translate-y-2 scale-95 opacity-0"
    >
      <div
        v-if="toast"
        class="max-w-[80vw] rounded-full px-4 py-2 text-[13px] font-medium shadow-[0_10px_30px_rgba(10,6,30,0.35)] md:max-w-none md:px-5 md:py-2.5 md:text-sm"
        :class="toast.type === 'ok' ? 'bg-ink text-warm' : 'bg-red-600 text-white'"
      >
        {{ toast.msg }}
      </div>
    </Transition>
  </div>
</template>