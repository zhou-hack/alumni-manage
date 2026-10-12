<script setup lang="ts">
// 确认弹窗（响应式）：H5 下底部安全区避让，按钮在窄屏上下堆叠
withDefaults(
  defineProps<{
    open: boolean;
    title: string;
    message: string;
    confirmText?: string;
    danger?: boolean;
  }>(),
  { confirmText: '确定', danger: false }
);

const emit = defineEmits<{ confirm: []; close: [] }>();

const onConfirm = () => {
  emit('close');
  emit('confirm');
};
</script>

<template>
  <div
    v-if="open"
    class="fixed inset-0 z-[95] flex items-end justify-center bg-night/60 px-4 backdrop-blur-sm md:items-center"
    @click="emit('close')"
  >
    <div
      class="dialog-pop w-full max-w-sm rounded-t-2xl bg-white p-6 pb-[max(1.5rem,env(safe-area-inset-bottom))] shadow-[0_-12px_40px_rgba(10,6,30,0.45)] md:rounded-2xl md:pb-6 md:shadow-[0_30px_80px_rgba(10,6,30,0.5)]"
      @click.stop
    >
      <h3 class="font-title text-base font-bold text-ink md:text-lg">{{ title }}</h3>
      <p class="mt-2 text-sm leading-relaxed text-slate-600">{{ message }}</p>
      <div class="mt-5 flex flex-col-reverse gap-2.5 md:mt-6 md:flex-row md:gap-3">
        <button
          type="button"
          class="h-11 rounded-lg border border-slate-200 bg-white text-sm font-semibold text-slate-600 transition hover:bg-slate-50 md:flex-1 md:h-auto md:py-2.5"
          @click="emit('close')"
        >
          取消
        </button>
        <button
          type="button"
          class="h-11 rounded-lg text-sm font-bold text-white shadow transition md:flex-1 md:h-auto md:py-2.5"
          :class="
            danger
              ? 'bg-red-600 shadow-[0_6px_18px_rgba(220,38,38,0.35)] hover:bg-red-700'
              : 'bg-tech shadow-[0_6px_18px_rgba(37,99,235,0.4)] hover:bg-blue-700'
          "
          @click="onConfirm"
        >
          {{ confirmText }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 弹层入场：移动端从底部上滑 + 缩放，桌面端居中缩放 + 上浮 */
.dialog-pop {
  animation: dialogPop 0.22s cubic-bezier(0.22, 0.61, 0.36, 1);
}
@keyframes dialogPop {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
@media (min-width: 768px) {
  .dialog-pop {
    animation-name: dialogPopMd;
  }
  @keyframes dialogPopMd {
    from {
      opacity: 0;
      transform: scale(0.94) translateY(10px);
    }
    to {
      opacity: 1;
      transform: scale(1) translateY(0);
    }
  }
}
</style>