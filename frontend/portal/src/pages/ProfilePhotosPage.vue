<script setup lang="ts">
// 我的相册（/profile/photos）：对接照片收集模块
// - 列表 GET /photos/mine（含审核状态：待审核/已通过/已驳回/已推送 + 驳回原因）
// - 上传：attachments.upload(biz_type=photo_collect) → POST /photos/submit（标题/描述/标签，提交后待审核）
// - 图片经带令牌 preview 接口转 Object URL 内联展示（<img> 不带 Authorization 会 401）
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import ProfileShell from '../components/profile/ProfileShell.vue';
import PageHeader from '../components/profile/PageHeader.vue';
import BackToProfile from '../components/profile/BackToProfile.vue';
import ToastBar from '../components/profile/ToastBar.vue';
import Lucide from '../components/Lucide.vue';
import {
  fetchAttachmentObjectUrl,
  fmtDate,
  prevalidateFiles,
  uploadAttachmentsChunked,
  useToast
} from '../components/profile/shared';
import { api, ApiError, type DictItem, type PhotoItem } from '../api';

const INPUT =
  'w-full rounded-lg border border-slate-200 bg-warm/70 px-4 py-3 text-base text-ink placeholder:text-slate-400 outline-none transition focus:border-tech focus:bg-white focus:ring-2 focus:ring-tech/20';
const LABEL = 'mb-1.5 block text-sm font-medium text-ink';

const { toast, show } = useToast();
const errText = (e: unknown, fallback: string) => (e instanceof ApiError ? e.message : fallback);

/* ================= 状态字典 ================= */
const PHOTO_STATUS: Record<string, { text: string; cls: string }> = {
  pending: { text: '待审核', cls: 'bg-amber-50 text-amber-600 ring-amber-200' },
  approved: { text: '已通过', cls: 'bg-green-50 text-green-600 ring-green-200' },
  rejected: { text: '已驳回', cls: 'bg-red-50 text-red-600 ring-red-200' },
  pushed: { text: '已推送', cls: 'bg-blue-50 text-blue-600 ring-blue-200' }
};
const statusOf = (s?: string) => PHOTO_STATUS[s ?? ''] ?? { text: s || '—', cls: 'bg-slate-50 text-slate-500 ring-slate-200' };

const FILTERS = [
  { key: '', text: '全部' },
  { key: 'pending', text: '待审核' },
  { key: 'approved', text: '已通过' },
  { key: 'pushed', text: '已推送' },
  { key: 'rejected', text: '已驳回' }
];

/* ================= 列表 ================= */
const list = ref<PhotoItem[]>([]);
const total = ref(0);
const page = ref(1);
const PAGE_SIZE = 24;
const loading = ref(true);
const statusFilter = ref('');

/** 照片 id → 预览 Object URL（卸载/重载时统一回收） */
const imgUrls = ref<Record<number, string>>({});
function revokeAll() {
  Object.values(imgUrls.value).forEach((u) => u && URL.revokeObjectURL(u));
  imgUrls.value = {};
}

async function loadList() {
  loading.value = true;
  try {
    const params: Record<string, string> = { page: String(page.value), page_size: String(PAGE_SIZE) };
    if (statusFilter.value) params.status = statusFilter.value;
    const d = await api.photos.mine(params);
    list.value = d.list || [];
    total.value = d.total || 0;
    revokeAll();
    // 并发拉取预览流（个人照片量小，逐张带令牌取 blob）
    const pairs = await Promise.all(
      list.value.map(async (p) => [p.id, await fetchAttachmentObjectUrl(p.attachment_id)] as const)
    );
    const map: Record<number, string> = {};
    pairs.forEach(([id, url]) => {
      if (url) map[id] = url;
    });
    imgUrls.value = map;
  } catch (e) {
    show(errText(e, '相册加载失败'), 'err');
  } finally {
    loading.value = false;
  }
}

function setFilter(k: string) {
  if (statusFilter.value === k) return;
  statusFilter.value = k;
  page.value = 1;
  loadList();
}

const hasMore = computed(() => page.value * PAGE_SIZE < total.value);
function loadMore() {
  page.value += 1;
  loadList();
}

/* ================= 标签字典（dicts.photo_tag；空字典则整段隐藏） ================= */
const tagDict = ref<DictItem[]>([]);
const tagText = (v: string) => tagDict.value.find((t) => t.value === v)?.label ?? v;

/* ================= 上传弹窗 ================= */
const uploadOpen = ref(false);
const uploading = ref(false);
const fileRef = ref<HTMLInputElement | null>(null);
const pickedFile = ref<File | null>(null);
const pickedPreview = ref('');
const form = ref({ title: '', caption: '', tags: [] as string[] });

function openUpload() {
  form.value = { title: '', caption: '', tags: [] };
  clearPicked();
  uploadOpen.value = true;
}

function clearPicked() {
  if (pickedPreview.value) URL.revokeObjectURL(pickedPreview.value);
  pickedPreview.value = '';
  pickedFile.value = null;
  if (fileRef.value) fileRef.value.value = '';
}

function pickFile() {
  fileRef.value?.click();
}

function onPickFile(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0];
  if (!f) return;
  const { ok, failed } = prevalidateFiles([f]);
  if (!ok.length) {
    show(failed[0]?.reason || '文件不符合要求', 'err');
    (e.target as HTMLInputElement).value = '';
    return;
  }
  if (!/^image\/(jpeg|png|webp)$/.test(f.type)) {
    show('相册仅支持图片（jpg/png/webp）', 'err');
    (e.target as HTMLInputElement).value = '';
    return;
  }
  clearPicked();
  pickedFile.value = f;
  pickedPreview.value = URL.createObjectURL(f);
}

function toggleTag(v: string) {
  const i = form.value.tags.indexOf(v);
  if (i >= 0) form.value.tags.splice(i, 1);
  else form.value.tags.push(v);
}

async function submitUpload() {
  if (!pickedFile.value) {
    show('请先选择照片', 'err');
    return;
  }
  uploading.value = true;
  try {
    const up = await uploadAttachmentsChunked([pickedFile.value], 'photo_collect', '我的相册');
    const att = up.success[0];
    if (!att) {
      show(up.failed[0]?.reason || '照片上传失败', 'err');
      return;
    }
    await api.photos.submit({
      attachment_id: att.id,
      title: form.value.title.trim() || undefined,
      caption: form.value.caption.trim() || undefined,
      tags: form.value.tags
    });
    show('已提交，待审核通过后进入相册流转', 'ok');
    uploadOpen.value = false;
    clearPicked();
    page.value = 1;
    statusFilter.value = '';
    loadList();
  } catch (e) {
    show(errText(e, '提交失败'), 'err');
  } finally {
    uploading.value = false;
  }
}

/* ================= 大图预览 ================= */
const viewerItem = ref<PhotoItem | null>(null);
function openViewer(p: PhotoItem) {
  if (imgUrls.value[p.id]) viewerItem.value = p;
}
function closeViewer() {
  viewerItem.value = null;
}

/* ================= 生命周期 ================= */
onMounted(() => {
  loadList();
  api.dicts
    .list('photo_tag')
    .then((d) => (tagDict.value = d))
    .catch(() => (tagDict.value = []));
});

onBeforeUnmount(() => {
  revokeAll();
  clearPicked();
});
</script>

<template>
  <ProfileShell>
    <PageHeader
      eyebrow="MY PHOTOS"
      title="我的相册"
      :desc="`共 ${total} 张 · 提交后经辅导员/校级审核，校级可推送到目标群体`"
    >
      <template #action>
        <div class="flex flex-wrap items-center gap-2.5">
          <BackToProfile />
          <button
            type="button"
            class="flex items-center gap-1.5 rounded-lg bg-tech px-5 py-2.5 text-sm font-bold text-white shadow-[0_6px_18px_rgba(37,99,235,0.4)] transition hover:bg-blue-700"
            @click="openUpload"
          >
            <Lucide name="images" :size="14" class="mr-1 inline-block" /> 上传照片
          </button>
        </div>
      </template>
    </PageHeader>

    <!-- 状态筛选 -->
    <div class="mt-5 flex flex-wrap gap-2">
      <button
        v-for="f in FILTERS"
        :key="f.key"
        type="button"
        class="rounded-full px-4 py-1.5 text-xs font-semibold ring-1 transition"
        :class="
          statusFilter === f.key
            ? 'bg-night text-warm ring-night'
            : 'bg-white text-slate-500 ring-slate-200 hover:text-ink hover:ring-slate-300'
        "
        @click="setFilter(f.key)"
      >
        {{ f.text }}
      </button>
    </div>

    <!-- 照片墙 -->
    <p v-if="loading" class="mt-8 text-sm text-slate-400">加载中…</p>

    <div v-else-if="list.length" class="mt-5 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
      <div
        v-for="p in list"
        :key="p.id"
        class="photo-card group cursor-pointer overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-[0_10px_26px_rgba(26,16,61,0.05)]"
        @click="openViewer(p)"
      >
        <div class="relative aspect-square overflow-hidden bg-slate-100">
          <img
            v-if="imgUrls[p.id]"
            :src="imgUrls[p.id]"
            :alt="p.title || '照片'"
            class="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
            loading="lazy"
          />
          <div v-else class="flex h-full w-full items-center justify-center text-slate-300">
            <Lucide name="images" :size="28" />
          </div>
          <span
            class="absolute left-2.5 top-2.5 rounded-full px-2.5 py-0.5 text-[11px] font-semibold ring-1"
            :class="statusOf(p.status).cls"
          >
            {{ statusOf(p.status).text }}
          </span>
          <span
            v-if="p.pushed_count > 0"
            class="absolute right-2.5 top-2.5 rounded-full bg-night/70 px-2.5 py-0.5 text-[11px] font-semibold text-warm"
            title="已被校级推送的次数"
          >
            推送 ×{{ p.pushed_count }}
          </span>
        </div>
        <div class="px-4 py-3">
          <p class="truncate text-sm font-bold text-ink">{{ p.title || '未命名照片' }}</p>
          <p class="mt-0.5 line-clamp-1 text-xs text-slate-500">{{ p.caption || '—' }}</p>
          <div class="mt-2 flex flex-wrap items-center gap-1.5">
            <span
              v-for="t in p.tags"
              :key="t"
              class="rounded-full bg-tech/5 px-2 py-0.5 text-[11px] font-medium text-tech"
            >
              {{ tagText(t) }}
            </span>
            <span class="ml-auto text-[11px] text-slate-400">{{ fmtDate(p.created_at) }}</span>
          </div>
          <p
            v-if="p.status === 'rejected' && p.reject_reason"
            class="mt-2 rounded-lg bg-red-50 px-3 py-1.5 text-[11px] leading-relaxed text-red-600"
          >
            驳回原因：{{ p.reject_reason }}
          </p>
        </div>
      </div>
    </div>

    <!-- 空态 -->
    <div
      v-else
      class="mt-5 flex flex-col items-center rounded-2xl border border-dashed border-slate-200 bg-white/60 px-6 py-16 text-center"
    >
      <span class="flex h-14 w-14 items-center justify-center rounded-2xl bg-tech/10 text-tech">
        <Lucide name="images" :size="26" />
      </span>
      <p class="mt-4 text-sm font-semibold text-ink">相册还是空的</p>
      <p class="mt-1 max-w-sm text-xs leading-relaxed text-slate-500">
        上传校园生活、活动合影等照片，审核通过后校级可推送给更多校友。
      </p>
      <button
        type="button"
        class="mt-5 rounded-lg bg-tech px-5 py-2.5 text-sm font-bold text-white shadow-[0_6px_18px_rgba(37,99,235,0.4)] transition hover:bg-blue-700"
        @click="openUpload"
      >
        上传第一张照片 →
      </button>
    </div>

    <!-- 加载更多 -->
    <div v-if="!loading && hasMore" class="mt-6 text-center">
      <button
        type="button"
        class="rounded-lg border border-slate-200 bg-white px-5 py-2 text-xs font-semibold text-slate-600 transition hover:border-tech/40 hover:text-tech"
        @click="loadMore"
      >
        加载更多（{{ total - page * PAGE_SIZE }} 张剩余）
      </button>
    </div>

    <!-- 上传弹窗 -->
    <Teleport to="body">
      <div
        v-if="uploadOpen"
        class="fixed inset-0 z-[95] flex items-end justify-center bg-night/60 px-4 backdrop-blur-sm md:items-center"
        @click="uploadOpen = false"
      >
        <div
          class="dialog-pop relative w-full max-w-md rounded-t-2xl bg-white p-6 pb-[max(1.5rem,env(safe-area-inset-bottom))] md:rounded-2xl md:pb-6"
          @click.stop
        >
          <div class="flex items-center justify-between">
            <h3 class="font-title text-base font-bold text-ink md:text-lg">上传照片</h3>
            <button type="button" class="text-slate-400 hover:text-slate-600" aria-label="关闭" @click="uploadOpen = false">✕</button>
          </div>

          <!-- 选图 -->
          <div class="mt-4">
            <button
              v-if="!pickedPreview"
              type="button"
              class="flex h-44 w-full flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-slate-200 bg-slate-50 text-slate-500 transition hover:border-tech/40 hover:bg-tech/5 hover:text-tech"
              @click="pickFile"
            >
              <Lucide name="images" :size="26" />
              <span class="text-sm font-medium">点击选择照片</span>
              <span class="text-[11px] text-slate-400">jpg / png / webp，≤10MB</span>
            </button>
            <div v-else class="relative mx-auto w-fit">
              <img :src="pickedPreview" alt="预览" class="max-h-52 rounded-xl border border-slate-100 object-contain" />
              <button
                type="button"
                class="absolute -right-2 -top-2 flex h-6 w-6 items-center justify-center rounded-full bg-night/70 text-xs text-white"
                aria-label="移除"
                @click="clearPicked"
              >
                ✕
              </button>
            </div>
            <input ref="fileRef" type="file" hidden accept=".jpg,.jpeg,.png,.webp" @change="onPickFile" />
          </div>

          <div class="mt-4 space-y-4">
            <div>
              <label :class="LABEL">标题（选填）</label>
              <input v-model="form.title" :class="INPUT" maxlength="128" placeholder="如 2020 届毕业合影" />
            </div>
            <div>
              <label :class="LABEL">描述（选填）</label>
              <textarea v-model="form.caption" :class="INPUT" rows="2" maxlength="500" placeholder="照片背后的故事…"></textarea>
            </div>
            <div v-if="tagDict.length">
              <label :class="LABEL">标签（选填）</label>
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="t in tagDict"
                  :key="t.value"
                  type="button"
                  class="rounded-full px-3.5 py-1.5 text-xs font-semibold ring-1 transition"
                  :class="
                    form.tags.includes(t.value)
                      ? 'bg-tech text-white ring-tech'
                      : 'bg-white text-slate-500 ring-slate-200 hover:text-tech hover:ring-tech/40'
                  "
                  @click="toggleTag(t.value)"
                >
                  {{ t.label }}
                </button>
              </div>
            </div>
          </div>

          <div class="mt-6 flex gap-3">
            <button
              type="button"
              class="flex-1 rounded-lg border border-slate-200 py-2.5 text-sm font-semibold text-slate-600 transition hover:bg-slate-50"
              @click="uploadOpen = false"
            >
              取消
            </button>
            <button
              type="button"
              class="flex-1 rounded-lg bg-tech py-2.5 text-sm font-bold text-white transition hover:bg-blue-700 disabled:opacity-50"
              :disabled="uploading || !pickedFile"
              @click="submitUpload"
            >
              {{ uploading ? '提交中…' : '提交审核' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 大图预览 -->
    <Teleport to="body">
      <div
        v-if="viewerItem"
        class="fixed inset-0 z-[96] flex items-center justify-center bg-night/85 p-4 backdrop-blur-sm"
        @click="closeViewer"
      >
        <div class="max-w-3xl" @click.stop>
          <img
            :src="imgUrls[viewerItem.id]"
            :alt="viewerItem.title || '照片'"
            class="max-h-[76vh] w-auto rounded-xl object-contain shadow-2xl"
          />
          <div class="mt-3 flex items-start justify-between gap-4 text-warm">
            <div class="min-w-0">
              <p class="font-title text-base font-bold">{{ viewerItem.title || '未命名照片' }}</p>
              <p v-if="viewerItem.caption" class="mt-1 text-sm leading-relaxed text-warm/70">{{ viewerItem.caption }}</p>
            </div>
            <button type="button" class="shrink-0 rounded-full bg-white/10 px-3 py-1.5 text-sm" @click="closeViewer">关闭 ✕</button>
          </div>
        </div>
      </div>
    </Teleport>

    <ToastBar :toast="toast" />
  </ProfileShell>
</template>

<style scoped>
.photo-card {
  animation: cardEnter 0.35s cubic-bezier(0.22, 0.61, 0.36, 1) both;
  transition:
    transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1),
    box-shadow 0.25s,
    border-color 0.25s;
}
.photo-card:hover {
  transform: translateY(-4px);
  border-color: rgba(37, 99, 235, 0.3);
  box-shadow: 0 18px 40px rgba(37, 99, 235, 0.12);
}
@keyframes cardEnter {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.dialog-pop {
  animation: dialogPop 0.3s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}
@keyframes dialogPop {
  from {
    opacity: 0;
    transform: translateY(16px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
