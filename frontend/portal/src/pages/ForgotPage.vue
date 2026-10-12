<script setup lang="ts">
// 忘记密码页（/forgot）：手机验证码 → 换取 reset_token → 设置新密码
// 对接 /auth/sms(scene=reset_password) → /auth/password/reset-token → /auth/password/reset
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import AuthShell from '../components/AuthShell.vue';
import { BTN_PRIMARY, INPUT, useCountdown } from '../components/auth-shared';
import DevCodeHint from '../components/auth/DevCodeHint.vue';
import ErrorBar from '../components/auth/ErrorBar.vue';
import Lucide from '../components/Lucide.vue';
import { api, checkPasswordStrength, sha256Hex, ApiError } from '../api';

const STEPS = ['验证身份', '设置新密码', '完成'];

const router = useRouter();

const step = ref(0);
const error = ref('');
const notice = ref('');
const busy = ref(false);

const phone = ref('');
const smsCode = ref('');
const devCode = ref<string | undefined>();
const resetToken = ref('');
const password = ref('');
const password2 = ref('');
const { left, start } = useCountdown();

const validPhone = () => /^1\d{10}$/.test(phone.value);

const sendSms = async () => {
  if (!validPhone()) {
    error.value = '请输入正确的 11 位手机号';
    return;
  }
  error.value = '';
  devCode.value = undefined;
  try {
    const d = await api.auth.sms({ phone: phone.value, scene: 'reset_password' });
    devCode.value = d.dev_code;
    start();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '验证码发送失败，请稍后重试';
  }
};

/** 校验验证码换取 reset_token */
const submitVerify = async () => {
  error.value = '';
  if (!validPhone()) {
    error.value = '请输入正确的 11 位手机号';
    return;
  }
  if (!/^\d{6}$/.test(smsCode.value)) {
    error.value = '请输入 6 位数字验证码';
    return;
  }
  busy.value = true;
  try {
    const d = await api.auth.passwordResetToken({ phone: phone.value, sms_code: smsCode.value });
    resetToken.value = d.reset_token;
    step.value = 1;
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '校验失败，请稍后重试';
  } finally {
    busy.value = false;
  }
};

/** 设置新密码（Bearer reset_token） */
const submitReset = async () => {
  error.value = '';
  if (!checkPasswordStrength(password.value)) {
    error.value = '密码至少 8 位，且需同时包含字母和数字';
    return;
  }
  if (password.value !== password2.value) {
    error.value = '两次输入的密码不一致';
    return;
  }
  busy.value = true;
  try {
    await api.auth.passwordReset({ new_password: await sha256Hex(password.value) }, resetToken.value);
    notice.value = '密码重置成功，请使用新密码登录';
    step.value = 2;
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '重置失败，请稍后重试';
  } finally {
    busy.value = false;
  }
};
</script>

<template>
  <AuthShell width="max-w-lg">
    <div class="card-enter rounded-2xl bg-white p-7 shadow-[0_24px_60px_rgba(10,6,30,0.5)] sm:p-9">
      <div class="flex items-center gap-3">
        <span class="h-px w-8 bg-gradient-to-r from-transparent to-lamp/70" />
        <span class="font-code text-[10px] tracking-[0.35em] text-tech">RESET PASSWORD</span>
      </div>
      <h1 class="font-title mt-2.5 text-3xl font-black tracking-wide text-ink">找回密码</h1>
      <p class="mt-2 text-sm text-slate-500">通过预留手机号验证身份后设置新密码</p>

      <!-- 步骤条 -->
      <ol class="mt-6 flex items-center gap-2">
        <li v-for="(label, i) in STEPS" :key="label" class="flex flex-1 items-center gap-2">
          <span
            class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-bold transition"
            :class="
              i < step
                ? 'bg-tech text-white'
                : i === step
                  ? 'bg-lamp text-night shadow-[0_0_10px_rgba(245,179,1,0.6)]'
                  : 'bg-slate-100 text-slate-400'
            "
          >
            {{ i < step ? '✓' : i + 1 }}
          </span>
          <span class="text-xs font-medium" :class="i === step ? 'text-ink' : 'text-slate-400'">
            {{ label }}
          </span>
          <span v-if="i < STEPS.length - 1" class="h-px flex-1 bg-slate-200" />
        </li>
      </ol>

      <div class="mt-6">
        <ErrorBar :message="error" />
      </div>

      <!-- 第一步：验证身份 -->
      <form v-if="step === 0" class="mt-4 space-y-4" novalidate @submit.prevent="submitVerify">
        <div>
          <label class="mb-1.5 block text-sm font-medium text-ink">手机号</label>
          <input
            :class="INPUT"
            type="tel"
            inputmode="numeric"
            maxlength="11"
            placeholder="请输入注册手机号"
            :value="phone"
            @input="phone = ($event.target as HTMLInputElement).value.replace(/\D/g, '')"
          />
        </div>
        <div>
          <label class="mb-1.5 block text-sm font-medium text-ink">短信验证码</label>
          <div class="flex gap-2.5">
            <input
              :class="INPUT"
              type="text"
              inputmode="numeric"
              maxlength="6"
              placeholder="6 位验证码"
              :value="smsCode"
              autocomplete="one-time-code"
              @input="smsCode = ($event.target as HTMLInputElement).value.replace(/\D/g, '')"
            />
            <button
              type="button"
              :disabled="left > 0"
              class="shrink-0 rounded-lg border border-tech/40 px-4 text-sm font-semibold text-tech transition hover:bg-tech/5 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
              @click="sendSms"
            >
              {{ left > 0 ? `${left}s` : '获取验证码' }}
            </button>
          </div>
          <DevCodeHint :code="devCode" />
        </div>
        <button type="submit" :disabled="busy" :class="BTN_PRIMARY">
          {{ busy ? '校验中…' : '下一步 · 验证身份' }}
        </button>
      </form>

      <!-- 第二步：设置新密码 -->
      <form v-else-if="step === 1" class="mt-4 space-y-4" novalidate @submit.prevent="submitReset">
        <div>
          <label class="mb-1.5 block text-sm font-medium text-ink">新密码</label>
          <input
            :class="INPUT"
            v-model="password"
            type="password"
            placeholder="至少 8 位，含字母和数字"
            autocomplete="new-password"
          />
        </div>
        <div>
          <label class="mb-1.5 block text-sm font-medium text-ink">确认新密码</label>
          <input
            :class="INPUT"
            v-model="password2"
            type="password"
            placeholder="再次输入新密码"
            autocomplete="new-password"
          />
        </div>
        <button type="submit" :disabled="busy" :class="BTN_PRIMARY">
          {{ busy ? '提交中…' : '确认重置' }}
        </button>
        <button
          type="button"
          class="w-full text-center text-xs text-slate-400 transition-colors hover:text-ink"
          @click="step = 0"
        >
          ← 返回上一步
        </button>
      </form>

      <!-- 第三步：完成 -->
      <div v-else class="mt-6 space-y-5 text-center">
        <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-tech/10 text-tech">
          <Lucide name="lock-keyhole" :size="30" />
        </div>
        <p class="text-sm leading-relaxed text-slate-600">{{ notice || '密码重置成功' }}</p>
        <button type="button" :class="BTN_PRIMARY" @click="router.push('/login')">
          前往登录
        </button>
      </div>

      <div class="mt-6 text-center text-sm text-slate-400">
        想起密码了？
        <RouterLink to="/login" class="font-semibold text-tech transition-colors hover:text-blue-700">
          返回登录
        </RouterLink>
      </div>
    </div>
  </AuthShell>
</template>

<style scoped>
.card-enter {
  animation: cardEnter 0.45s cubic-bezier(0.22, 0.61, 0.36, 1);
}
@keyframes cardEnter {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
