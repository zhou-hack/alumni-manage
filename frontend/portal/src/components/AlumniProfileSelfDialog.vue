<script setup lang="ts">
/**
 * 校友本人编辑"大屏展示字段"弹窗（V18 · portal 端，不依赖 Element Plus）
 *
 * - 调用 api.alumniProfile.getMy() / updateMy()
 * - 与管理端 AlumniProfileEditDialog 同样 5 个字段：photoUrl / industry / intro / quote / motto
 * - 头像走 api.attachments.upload({ biz_type: 'alumni_photo' })
 *
 * Emits:
 *   - update:open (boolean)
 *   - saved    (newProfile)
 */
import { computed, onMounted, ref, watch } from 'vue';
import { api } from '../api';

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ 'update:open': [boolean]; saved: [unknown] }>();

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
});

const loading = ref(false);
const saving = ref(false);
const uploading = ref(false);
const previewUrl = ref('');
const form = ref(emptyForm());
/** 文件选择 input ref */
const fileInputRef = ref<HTMLInputElement | null>(null);

function emptyForm() {
  return { photoUrl: '', industry: '', intro: '', quote: '', motto: '' };
}

async function load() {
  loading.value = true;
  try {
    const d = await api.alumniProfile.getMy();
    form.value = {
      photoUrl: d.photoUrl || '',
      industry: d.industry || '',
      intro: d.intro || '',
      quote: d.quote || '',
      motto: d.motto || '',
    };
    previewUrl.value = d.photoUrl || '';
  } catch (e: any) {
    alert(e?.message || '资料加载失败');
  } finally {
    loading.value = false;
  }
}

async function submitForm() {
  saving.value = true;
  try {
    const updated = await api.alumniProfile.updateMy({ ...form.value });
    emit('saved', updated);
    visible.value = false;
  } catch (e: any) {
    alert(e?.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

function pickPhoto() {
  fileInputRef.value?.click();
}

async function onFileChange(ev: Event) {
  const input = ev.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  if (file.size > 10 * 1024 * 1024) {
    alert('照片超过 10MB');
    input.value = '';
    return;
  }
  uploading.value = true;
  try {
    const res = await api.attachments.upload({ biz_type: 'alumni_photo', remark: '校友风采照片', files: [file] });
    const arr = (res as any).success || [];
    if (arr[0]?.url) {
      form.value.photoUrl = arr[0].url;
      previewUrl.value = arr[0].url;
    } else {
      alert('上传失败');
    }
  } catch (e: any) {
    alert(e?.message || '上传失败');
  } finally {
    uploading.value = false;
    input.value = '';
  }
}

function clearPhoto() {
  form.value.photoUrl = '';
  previewUrl.value = '';
}

watch(visible, (v) => {
  if (v) load();
});

onMounted(() => {
  if (visible.value) load();
});
</script>

<template>
  <div
    v-if="visible"
    class="fixed inset-0 z-[95] flex items-end justify-center bg-night/60 px-4 backdrop-blur-sm md:items-center"
    @click="visible = false"
  >
    <div
      class="dialog-pop relative w-full max-w-md rounded-t-2xl bg-white p-6 pb-[max(1.5rem,env(safe-area-inset-bottom))] shadow-[0_-12px_40px_rgba(10,6,30,0.45)] md:rounded-2xl md:pb-6 md:shadow-[0_30px_80px_rgba(10,6,30,0.5)]"
      @click.stop
    >
      <div v-if="loading" class="absolute inset-0 z-10 flex items-center justify-center bg-white/70">
        <span class="text-sm text-slate-500">加载中…</span>
      </div>

      <div class="flex items-center justify-between">
        <h3 class="font-title text-base font-bold text-ink md:text-lg">我的风采 / 座右铭</h3>
        <button type="button" class="text-slate-400 hover:text-slate-600" @click="visible = false" aria-label="关闭">✕</button>
      </div>
      <p class="mt-1 text-xs text-slate-500">保存后下次刷新大屏即可看到新内容（最长 60 秒缓存）。</p>

      <!-- 头像 -->
      <section class="mt-4 flex items-center gap-4 rounded-xl border border-dashed border-slate-200 bg-slate-50 p-4">
        <div class="flex h-20 w-20 shrink-0 items-center justify-center overflow-hidden rounded-full bg-gradient-to-br from-tech to-blue-700 text-warm shadow-md">
          <img v-if="previewUrl" :src="previewUrl" alt="头像" class="h-full w-full object-cover" />
          <span v-else class="text-2xl font-bold">校</span>
        </div>
        <div class="flex flex-col gap-2">
          <button
            type="button"
            class="h-9 rounded-lg bg-tech px-4 text-xs font-bold text-white shadow-[0_6px_18px_rgba(37,99,235,0.4)] transition hover:bg-blue-700 disabled:opacity-60"
            :disabled="uploading"
            @click="pickPhoto"
          >{{ uploading ? '上传中…' : '上传照片' }}</button>
          <button
            v-if="form.photoUrl"
            type="button"
            class="h-9 rounded-lg border border-slate-200 bg-white px-4 text-xs font-semibold text-slate-600 transition hover:bg-slate-50"
            @click="clearPhoto"
          >清除</button>
          <p class="text-xs text-slate-500">JPG/PNG/WebP，≤10MB</p>
          <input
            ref="fileInputRef"
            type="file"
            accept="image/*"
            class="hidden"
            @change="onFileChange"
          />
        </div>
      </section>

      <!-- 字段 -->
      <div class="mt-5 space-y-3">
        <label class="block">
          <span class="text-xs font-semibold text-slate-600">所在行业 / 单位</span>
          <input
            v-model="form.industry"
            maxlength="64"
            placeholder="例如：互联网 / 教育 / 金融"
            class="mt-1 block w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm focus:border-tech focus:outline-none"
          />
        </label>
        <label class="block">
          <span class="text-xs font-semibold text-slate-600">个人简介（≤500 字）</span>
          <textarea
            v-model="form.intro"
            rows="3"
            maxlength="500"
            placeholder="一段话介绍自己的成长经历 / 当前在做的事"
            class="mt-1 block w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm focus:border-tech focus:outline-none"
          ></textarea>
        </label>
        <label class="block">
          <span class="text-xs font-semibold text-slate-600">人生信条（≤200 字）</span>
          <input
            v-model="form.quote"
            maxlength="200"
            placeholder="一句最想分享的话"
            class="mt-1 block w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm focus:border-tech focus:outline-none"
          />
        </label>
        <label class="block">
          <span class="text-xs font-semibold text-slate-600">座右铭（≤200 字）</span>
          <input
            v-model="form.motto"
            maxlength="200"
            placeholder="印在心里、推自己前行的那句话"
            class="mt-1 block w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm focus:border-tech focus:outline-none"
          />
        </label>
      </div>

      <div class="mt-5 flex flex-col-reverse gap-2.5 md:mt-6 md:flex-row md:gap-3">
        <button
          type="button"
          class="h-11 rounded-lg border border-slate-200 bg-white text-sm font-semibold text-slate-600 transition hover:bg-slate-50 md:flex-1 md:h-auto md:py-2.5"
          @click="visible = false"
        >
          取消
        </button>
        <button
          type="button"
          class="h-11 rounded-lg bg-tech text-sm font-bold text-white shadow-[0_6px_18px_rgba(37,99,235,0.4)] transition hover:bg-blue-700 disabled:opacity-60 md:flex-1 md:h-auto md:py-2.5"
          :disabled="saving"
          @click="submitForm"
        >
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialog-pop {
  animation: dialogPop 0.22s cubic-bezier(0.22, 0.61, 0.36, 1);
}
@keyframes dialogPop {
  from { opacity: 0; transform: translateY(20px) scale(0.97); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
</style>