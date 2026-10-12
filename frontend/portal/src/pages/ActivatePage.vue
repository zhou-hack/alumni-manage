<script setup lang="ts">
// 校友注册（激活）页（/activate）：三要素核验 → 短信验证 → 设置密码
// 对接后端 /auth/activate/* 三步接口，激活成功直接签发令牌进入平台
// 核验不通过兜底：人工核验申请（API 规范 2.1.4，说明 + 联系电话 + 证明材料）
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import AuthShell from '../components/AuthShell.vue';
import { BTN_PRIMARY, INPUT, useCountdown } from '../components/auth-shared';
import DevCodeHint from '../components/auth/DevCodeHint.vue';
import ErrorBar from '../components/auth/ErrorBar.vue';
import Lucide from '../components/Lucide.vue';
import {
  prevalidateFiles,
  uploadAttachmentsChunked,
  useToast,
  type UploadedAtt
} from '../components/profile/shared';
import ToastBar from '../components/profile/ToastBar.vue';
import { api, checkPasswordStrength, sha256Hex, tokenStore, ApiError } from '../api';
import { setUser } from '../auth';

const STEPS = ['身份核验', '短信验证', '设置密码'];

const router = useRouter();

const step = ref(0);
const error = ref('');
const busy = ref(false);

// 第一步：三要素
const studentId = ref('');
const realName = ref('');
const idCard = ref('');

// 第二步：验证码（可换绑手机号）
const verifyToken = ref('');
const maskedPhone = ref('');
const newPhone = ref('');
const devCode = ref<string | undefined>();
const { left, start } = useCountdown();

// 第三步：密码
const smsCode = ref('');
const password = ref('');
const password2 = ref('');

const { toast, show } = useToast();

/* ================= 人工核验申请（API 规范 2.1.4，核验不通过兜底） ================= */
const manualFileRef = ref<HTMLInputElement | null>(null);
const manualOpen = ref(false);
const manualDone = ref<number | null>(null); // request_id_no
const manualPhone = ref('');
const manualNote = ref('');
const manualAtts = ref<UploadedAtt[]>([]);
const manualFailed = ref<{ name: string; reason: string }[]>([]);
const manualUploading = ref(false);
const manualBusy = ref(false);

const openManual = () => {
  manualDone.value = null;
  manualOpen.value = true;
};

const onManualPick = async (e: Event) => {
  const input = e.target as HTMLInputElement;
  const picked = Array.from(input.files ?? []);
  input.value = '';
  if (!picked.length) return;
  const { ok, failed: bad } = prevalidateFiles(picked);
  manualFailed.value = bad;
  if (!ok.length) return;
  manualUploading.value = true;
  try {
    const d = await uploadAttachmentsChunked(ok, 'activate');
    if (d.success.length) manualAtts.value = [...manualAtts.value, ...d.success].slice(0, 5);
    manualFailed.value = [...bad, ...d.failed];
  } finally {
    manualUploading.value = false;
  }
};

const submitManual = async () => {
  if (manualBusy.value) return;
  if (!/^1\d{10}$/.test(manualPhone.value)) {
    show('请填写可联系的手机号', 'err');
    return;
  }
  manualBusy.value = true;
  try {
    const d = await api.auth.activateManualRequest({
      student_id: studentId.value.trim(),
      real_name: realName.value.trim(),
      id_card: idCard.value.trim(),
      contact_phone: manualPhone.value.trim(),
      note: manualNote.value.trim() || undefined,
      attachment_ids: manualAtts.value.map((a) => a.id)
    });
    manualDone.value = d.request_id_no;
  } catch (err) {
    show(err instanceof ApiError ? err.message : '申请提交失败，请稍后重试', 'err');
  } finally {
    manualBusy.value = false;
  }
};

/** 第一步：三要素比对 */
const submitVerify = async () => {
  error.value = '';
  if (!studentId.value.trim() || !realName.value.trim()) {
    error.value = '请填写学号与姓名';
    return;
  }
  if (!/^\d{15}(\d{2}[0-9Xx])?$/.test(idCard.value.trim())) {
    error.value = '身份证号格式错误';
    return;
  }
  busy.value = true;
  try {
    const d = await api.auth.activateVerify({
      student_id: studentId.value.trim(),
      real_name: realName.value.trim(),
      id_card: idCard.value.trim()
    });
    verifyToken.value = d.verify_token;
    maskedPhone.value = d.masked_phone;
    step.value = 1;
  } catch (err) {
    if (
      err instanceof ApiError &&
      err.code === 'AUTH_VERIFY_FAILED' &&
      (err.data as { can_manual_request?: boolean } | undefined)?.can_manual_request
    ) {
      // 核验不通过且可人工核验：打开申请面板（API 规范 2.1.4）
      error.value = '';
      openManual();
    } else {
      error.value = err instanceof ApiError ? err.message : '核验失败，请稍后重试';
    }
  } finally {
    busy.value = false;
  }
};

/** 第二步：发送验证码 */
const sendSms = async () => {
  error.value = '';
  if (newPhone.value && !/^1\d{10}$/.test(newPhone.value)) {
    error.value = '换绑手机号格式错误';
    return;
  }
  busy.value = true;
  try {
    const d = await api.auth.activateSms({
      verify_token: verifyToken.value,
      ...(newPhone.value ? { new_phone: newPhone.value } : {})
    });
    devCode.value = d.dev_code;
    if (d.masked_phone) maskedPhone.value = d.masked_phone;
    start();
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '验证码发送失败，请稍后重试';
  } finally {
    busy.value = false;
  }
};

/** 第三步：设置密码完成激活 */
const submitConfirm = async () => {
  error.value = '';
  if (!/^\d{6}$/.test(smsCode.value)) {
    error.value = '请输入 6 位数字验证码';
    return;
  }
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
    const d = await api.auth.activateConfirm({
      verify_token: verifyToken.value,
      sms_code: smsCode.value,
      password: await sha256Hex(password.value)
    });
    tokenStore.set(d.access_token, d.refresh_token);
    setUser(d.user);
    router.replace('/');
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '激活失败，请稍后重试';
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
        <span class="font-code text-[10px] tracking-[0.35em] text-tech">ALUMNI ACTIVATION</span>
      </div>
      <h1 class="font-title mt-2.5 text-3xl font-black tracking-wide text-ink">校友注册 · 激活</h1>
      <p class="mt-2 text-sm text-slate-500">
        凭学号、姓名、身份证号核验校友身份，激活后即可登录平台
      </p>

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

      <!-- 第一步：三要素 -->
      <form v-if="step === 0" class="mt-4 space-y-4" novalidate @submit.prevent="submitVerify">
        <div>
          <label class="mb-1.5 block text-sm font-medium text-ink">学号</label>
          <input :class="INPUT" v-model="studentId" placeholder="请输入在校学号" />
        </div>
        <div>
          <label class="mb-1.5 block text-sm font-medium text-ink">姓名</label>
          <input :class="INPUT" v-model="realName" placeholder="请输入真实姓名" />
        </div>
        <div>
          <label class="mb-1.5 block text-sm font-medium text-ink">身份证号</label>
          <input
            :class="INPUT"
            v-model="idCard"
            placeholder="用于身份核验，仅比对不留存"
            maxlength="18"
          />
        </div>
        <button type="submit" :disabled="busy" :class="BTN_PRIMARY">
          {{ busy ? '核验中…' : '下一步 · 身份核验' }}
        </button>
        <button
          type="button"
          class="w-full text-center text-xs text-slate-400 transition-colors hover:text-tech"
          @click="openManual"
        >
          信息不匹配？提交人工核验申请 →
        </button>
      </form>

      <!-- 第二步：短信验证 -->
      <div v-else-if="step === 1" class="mt-4 space-y-4">
        <div class="rounded-lg border border-tech/20 bg-tech/5 px-4 py-3 text-sm leading-relaxed text-ink">
          系统将向预留手机号 <span class="font-code font-bold text-tech">{{ maskedPhone }}</span>
          发送验证码；如号码已变更，可在下方填写新号换绑。
        </div>
        <div>
          <label class="mb-1.5 block text-sm font-medium text-ink">
            换绑手机号 <span class="font-normal text-slate-400">（选填）</span>
          </label>
          <input
            :class="INPUT"
            type="tel"
            inputmode="numeric"
            maxlength="11"
            placeholder="留空则使用原预留号码"
            :value="newPhone"
            @input="newPhone = ($event.target as HTMLInputElement).value.replace(/\D/g, '')"
          />
        </div>
        <div class="flex gap-2.5">
          <button type="button" :disabled="busy || left > 0" :class="BTN_PRIMARY" @click="sendSms">
            {{ left > 0 ? `重新发送（${left}s）` : busy ? '发送中…' : '发送验证码' }}
          </button>
        </div>
        <DevCodeHint :code="devCode" />
        <button
          v-if="devCode"
          type="button"
          class="w-full rounded-lg border border-tech/40 px-6 py-3 text-sm font-semibold text-tech transition hover:bg-tech/5"
          @click="step = 2"
        >
          已收到验证码，下一步
        </button>
        <button
          type="button"
          class="w-full text-center text-xs text-slate-400 transition-colors hover:text-ink"
          @click="step = 0"
        >
          ← 返回修改身份信息
        </button>
      </div>

      <!-- 第三步：设置密码 -->
      <form v-else class="mt-4 space-y-4" novalidate @submit.prevent="submitConfirm">
        <div>
          <label class="mb-1.5 block text-sm font-medium text-ink">短信验证码</label>
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
          <DevCodeHint :code="devCode" />
        </div>
        <div>
          <label class="mb-1.5 block text-sm font-medium text-ink">设置密码</label>
          <input
            :class="INPUT"
            v-model="password"
            type="password"
            placeholder="至少 8 位，含字母和数字"
            autocomplete="new-password"
          />
        </div>
        <div>
          <label class="mb-1.5 block text-sm font-medium text-ink">确认密码</label>
          <input
            :class="INPUT"
            v-model="password2"
            type="password"
            placeholder="再次输入密码"
            autocomplete="new-password"
          />
        </div>
        <button type="submit" :disabled="busy" :class="BTN_PRIMARY">
          {{ busy ? '激活中…' : '完成激活并登录' }}
        </button>
        <button
          type="button"
          class="w-full text-center text-xs text-slate-400 transition-colors hover:text-ink"
          @click="step = 1"
        >
          ← 返回上一步
        </button>
      </form>
    </div>

    <!-- 人工核验申请弹层（API 规范 2.1.4） -->
    <div
      v-if="manualOpen"
      class="fixed inset-0 z-[95] flex items-center justify-center bg-night/60 px-4 backdrop-blur-sm"
      @click="manualOpen = false"
    >
      <div
        class="dialog-pop max-h-[90vh] w-full max-w-md overflow-y-auto rounded-2xl bg-white p-6 shadow-[0_30px_80px_rgba(10,6,30,0.5)] sm:p-7"
        @click.stop
      >
        <div v-if="manualDone != null" class="text-center">
          <span class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-green-50 text-green-600">
            <Lucide name="circle-check-big" :size="30" />
          </span>
          <h3 class="font-title mt-4 text-lg font-bold text-ink">人工核验申请已提交</h3>
          <p class="mt-2.5 text-sm leading-relaxed text-slate-600">
            申请编号 <span class="font-code font-bold text-tech">#{{ manualDone }}</span>
            ，管理员将人工比对您的身份信息；通过后即可重新走激活流程登录。
          </p>
          <button
            type="button"
            class="mt-6 w-full rounded-lg bg-tech py-2.5 text-sm font-bold text-white transition hover:bg-blue-700"
            @click="manualOpen = false"
          >
            我知道了
          </button>
        </div>

        <template v-else>
          <h3 class="font-title text-lg font-bold text-ink">人工核验申请</h3>
          <p class="mt-2 text-xs leading-relaxed text-slate-500">
            您提交的「{{ studentId.trim() || '学号' }} · {{ realName.trim() || '姓名' }}」未能自动匹配。
            请留下联系方式并说明情况（如身份证升位、姓名生僻字等），管理员将人工比对后为您开通激活资格。
          </p>

          <div class="mt-5 space-y-4">
            <div>
              <label class="mb-1.5 block text-sm font-medium text-ink">
                联系电话 <span class="text-red-400">*</span>
              </label>
              <input
                :class="INPUT"
                type="tel"
                inputmode="numeric"
                maxlength="11"
                placeholder="用于接收审核结果通知"
                :value="manualPhone"
                @input="manualPhone = ($event.target as HTMLInputElement).value.replace(/\D/g, '')"
              />
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-ink">情况说明</label>
              <textarea
                :class="`${INPUT} min-h-[80px] resize-y`"
                v-model="manualNote"
                maxlength="200"
                placeholder="如：身份证 15 位升 18 位，旧号已停用"
              />
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-ink">
                证明材料 <span class="font-normal text-slate-400">（选填，学生证/毕业证/身份证照片）</span>
              </label>
              <button
                type="button"
                :disabled="manualUploading || manualAtts.length >= 5"
                class="w-full rounded-lg border-2 border-dashed border-slate-200 py-4 text-xs font-medium text-slate-400 transition hover:border-tech/50 hover:text-tech disabled:opacity-50"
                @click="manualFileRef?.click()"
              >
                {{
                  manualUploading
                    ? '上传中…'
                    : manualAtts.length >= 5
                      ? '已达上限（5 个）'
                      : '点击上传（jpg/png/webp/pdf，≤10MB）'
                }}
              </button>
              <input
                ref="manualFileRef"
                type="file"
                multiple
                hidden
                accept=".jpg,.jpeg,.png,.webp,.pdf"
                @change="onManualPick"
              />
              <ul v-if="manualAtts.length > 0" class="mt-2 space-y-1.5">
                <li
                  v-for="a in manualAtts"
                  :key="a.id"
                  class="flex items-center gap-2 rounded-lg bg-warm/60 px-3 py-1.5 text-xs text-ink"
                >
                  <span><Lucide name="file-text" :size="13" /></span>
                  <span class="min-w-0 flex-1 truncate">{{ a.name }}</span>
                  <button
                    type="button"
                    class="shrink-0 text-slate-400 hover:text-red-500"
                    @click="manualAtts = manualAtts.filter((x) => x.id !== a.id)"
                  >
                    移除
                  </button>
                </li>
              </ul>
              <ul v-if="manualFailed.length > 0" class="mt-2 space-y-1">
                <li
                  v-for="f in manualFailed"
                  :key="`${f.name}-${f.reason}`"
                  class="rounded-lg bg-red-50 px-3 py-1.5 text-xs text-red-600"
                >
                  {{ f.name }} · {{ f.reason }}
                </li>
              </ul>
            </div>
          </div>

          <div class="mt-6 flex gap-3">
            <button
              type="button"
              class="flex-1 rounded-lg border border-slate-200 py-2.5 text-sm font-semibold text-slate-600 transition hover:bg-slate-50"
              @click="manualOpen = false"
            >
              取消
            </button>
            <button
              type="button"
              :disabled="manualBusy || manualUploading"
              class="flex-1 rounded-lg bg-tech py-2.5 text-sm font-bold text-white shadow-[0_6px_18px_rgba(37,99,235,0.4)] transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
              @click="submitManual"
            >
              {{ manualBusy ? '提交中…' : '提交申请' }}
            </button>
          </div>
        </template>
      </div>
    </div>

    <!-- 轻提示 -->
    <ToastBar :toast="toast" />
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
.dialog-pop {
  animation: dialogPop 0.22s cubic-bezier(0.22, 0.61, 0.36, 1);
}
@keyframes dialogPop {
  from {
    opacity: 0;
    transform: scale(0.94) translateY(12px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}
</style>
