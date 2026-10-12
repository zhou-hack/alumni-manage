<script setup lang="ts">
// ------------------------------ 新建留言弹窗 ------------------------------
// 仅需填写留言内容：姓名/届次/学院由服务端从登录身份关联带出；
// 「显示我的名字」勾选框默认不勾（匿名展示），由提交人自行选择；
// 新留言 pending 待审核，通过后才展示在墙上
import { computed, ref } from 'vue';
import { X } from './icons';
import type { Draft } from './messageWall';

const emit = defineEmits<{
  close: [];
  save: [d: Draft];
}>();

/** 提交中禁用按钮（父组件传入），防止连点重复提交 */
defineProps<{ submitting?: boolean }>();

const text = ref('');
const showName = ref(false); // 默认匿名，勾选后才在公开墙显示真实姓名
const canSave = computed(() => text.value.trim().length > 0);

function submit() {
  if (!canSave.value) return;
  emit('save', { text: text.value.trim().slice(0, 120), showName: showName.value });
}

const field =
  'w-full rounded-xl border border-ink/15 bg-white px-4 py-2.5 text-sm text-ink outline-none transition focus:border-tech';
</script>

<template>
  <div class="fixed inset-0 z-[90] flex items-center justify-center bg-night/70 px-4 backdrop-blur-sm" @click="emit('close')">
    <form class="relative w-full max-w-lg rounded-3xl border border-lamp/40 bg-warm p-7 shadow-2xl" @submit.prevent="submit" @click.stop>
      <button
        type="button"
        aria-label="关闭"
        class="absolute right-4 top-4 rounded-full bg-ink/5 p-2 text-ink/50 transition hover:bg-lamp hover:text-night"
        @click="emit('close')"
      >
        <X :size="18" />
      </button>

      <h3 class="font-title text-xl font-black text-ink">写一张留言便签</h3>
      <p class="mt-1 text-xs text-slate-500">以当前登录身份署名，提交后需审核，通过后展示在墙上</p>

      <div class="mt-6">
        <label class="mb-1.5 block text-xs font-semibold tracking-wider text-slate-600">留言</label>
        <textarea
          v-model="text"
          :maxlength="120"
          :rows="4"
          placeholder="想对母校、老师或同学说的话（120 字以内）"
          :class="`${field} resize-none`"
        />
        <p class="mt-1 text-right text-[11px] text-slate-400">{{ text.length }} / 120</p>
      </div>

      <!-- 署名方式：默认匿名，勾选后才在墙上显示真实姓名 -->
      <label class="mt-4 flex cursor-pointer items-center gap-2.5 rounded-xl border border-ink/10 bg-white/60 px-4 py-3 transition hover:border-tech/40">
        <input
          v-model="showName"
          type="checkbox"
          class="h-4 w-4 shrink-0 accent-[#2563eb]"
        />
        <span class="text-sm text-slate-700">
          在留言墙上显示我的名字
          <span class="mt-0.5 block text-[11px] text-slate-400">
            {{ showName ? '将显示真实姓名' : '当前为匿名展示（默认）' }}
          </span>
        </span>
      </label>

      <div class="mt-6 flex justify-end gap-3">
        <button
          type="button"
          :disabled="submitting"
          class="rounded-xl border border-ink/15 px-5 py-2.5 text-sm font-semibold text-slate-600 transition hover:bg-ink/5 disabled:cursor-not-allowed disabled:opacity-45"
          @click="emit('close')"
        >
          取消
        </button>
        <button
          type="submit"
          :disabled="!canSave || submitting"
          class="rounded-xl bg-tech px-6 py-2.5 text-sm font-bold text-white shadow-[0_6px_16px_rgba(37,99,235,0.32)] transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-45 disabled:shadow-none"
        >
          {{ submitting ? '提交中…' : '提交留言' }}
        </button>
      </div>
    </form>
  </div>
</template>

<style>
/* 进场/离场动画（对应 React 版 AnimatePresence + motion.form，0.3s easeOut）：
   压暗背景层即时出现/消失，仅表单做位移+缩放+淡入淡出 */
.draft-enter-active form,
.draft-leave-active form {
  transition:
    opacity 0.3s ease-out,
    transform 0.3s ease-out;
}
.draft-enter-from form {
  opacity: 0;
  transform: translateY(24px) scale(0.92);
}
.draft-leave-to form {
  opacity: 0;
  transform: translateY(16px) scale(0.94);
}
</style>
