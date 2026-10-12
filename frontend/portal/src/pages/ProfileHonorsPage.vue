<script setup lang="ts">
// 我的荣誉（/profile/honors）：荣誉墙管理（H5 HonorListPage + HonorFormPage 门户版）
// - 列表：状态标签 / 类别 / 级别 / 授予单位 / 驳回原因
// - 录入与编辑（待初审可改）：标题 / 类别（字典）/ 级别 / 获得日期 / 授予单位（必填）
// - 证书原件切换：有原件 → 附件上传（biz_type=honor，≤6 个）；无原件 → 仅文字记录
// - 关联档案（选填）：从本人档案中选择，写入 archive_id
import { computed, onMounted, ref } from 'vue';
import ProfileShell from '../components/profile/ProfileShell.vue';
import PageHeader from '../components/profile/PageHeader.vue';
import BackToProfile from '../components/profile/BackToProfile.vue';
import StatusTag from '../components/profile/StatusTag.vue';
import ToastBar from '../components/profile/ToastBar.vue';
import {
  HONOR_CATEGORY_FALLBACK,
  HONOR_LEVELS,
  fmtDate,
  fmtSize,
  fmtTime,
  levelLabel,
  prevalidateFiles,
  uploadAttachmentsChunked,
  useToast,
  type UploadedAtt
} from '../components/profile/shared';
import Lucide from '../components/Lucide.vue';
import { api, ApiError, type Archive, type DictItem, type Honor } from '../api';
import { authState } from '../auth';

const INPUT =
  'w-full rounded-lg border border-slate-200 bg-warm/70 px-4 py-3 text-base text-ink placeholder:text-slate-400 outline-none transition focus:border-tech focus:bg-white focus:ring-2 focus:ring-tech/20';
const LABEL = 'mb-1.5 block text-sm font-medium text-ink';

const HONOR_MAX_ATT = 6;

const user = computed(() => authState.user);
const { toast, show } = useToast();
const errText = (e: unknown, fallback: string) => (e instanceof ApiError ? e.message : fallback);
const fileRef = ref<HTMLInputElement | null>(null);

/* ================= 列表 ================= */
const list = ref<Honor[]>([]);
const total = ref(0);
const loading = ref(true);

const loadList = async () => {
  loading.value = true;
  try {
    // 后端 GET /honors 当前未按本人过滤（返回全量），前端凭 user_id 兜底过滤；
    // 拉取上限一页（100），过滤后全量展示（个人荣誉数据量小）
    const d = await api.honors.list({ page: '1', page_size: '100' });
    const mine = d.list.filter((h) => h.user_id == null || h.user_id === user.value?.id);
    list.value = mine;
    total.value = mine.length;
  } catch (e) {
    show(errText(e, '荣誉列表加载失败'), 'err');
  } finally {
    loading.value = false;
  }
};

/* ================= 类别字典 + 我的档案（关联用） ================= */
const categories = ref<DictItem[]>([]);
const myArchives = ref<Archive[]>([]);
const categoryText = (v?: string) =>
  categories.value.find((c) => c.value === v)?.label ??
  HONOR_CATEGORY_FALLBACK.find((c) => c.value === v)?.label ??
  v ??
  '';

onMounted(() => {
  loadList();
  api.dicts
    .list('honor_category')
    .then((d) => (categories.value = d))
    .catch(
      () =>
        (categories.value = HONOR_CATEGORY_FALLBACK.map((c, i) => ({
          id: i,
          label: c.label,
          value: c.value,
          sort: i
        })))
    );
  // 关联档案选项：本人档案（取前 50 条）
  api.archives
    .my({ page: '1', page_size: '50' })
    .then((d) => (myArchives.value = d.list))
    .catch(() => (myArchives.value = []));
});

/* ================= 录入 / 编辑表单 ================= */
const formOpen = ref(false);
const editingId = ref<number | null>(null);
const title = ref('');
const category = ref('');
const level = ref('');
const awardedAt = ref('');
const grantedBy = ref('');
const archiveId = ref<number | null>(null);
const submitting = ref(false);

/** 证书原件切换 + 附件 */
const hasOriginal = ref(true);
const keptIds = ref<number[]>([]); // 编辑模式保留的原附件
const newAtts = ref<UploadedAtt[]>([]);
const uploading = ref(false);
const failed = ref<{ name: string; reason: string }[]>([]);

const resetForm = () => {
  editingId.value = null;
  title.value = '';
  category.value = '';
  level.value = '';
  awardedAt.value = '';
  grantedBy.value = '';
  archiveId.value = null;
  hasOriginal.value = true;
  keptIds.value = [];
  newAtts.value = [];
  failed.value = [];
};

const openCreate = () => {
  resetForm();
  formOpen.value = true;
};

/** 待初审的荣誉可修改：回填表单 */
const openEdit = (h: Honor) => {
  editingId.value = h.id;
  title.value = h.title || '';
  category.value = h.category || '';
  level.value = h.level || '';
  awardedAt.value = h.awarded_at || '';
  grantedBy.value = h.granted_by || '';
  archiveId.value = h.archive_id ?? null;
  keptIds.value = h.attachment_ids || [];
  hasOriginal.value = (h.attachment_ids || []).length > 0;
  newAtts.value = [];
  failed.value = [];
  formOpen.value = true;
};

const toggleOriginal = (v: boolean) => {
  hasOriginal.value = v;
  if (!v) {
    keptIds.value = [];
    newAtts.value = [];
    failed.value = [];
  }
};

const onPickFiles = async (e: Event) => {
  const input = e.target as HTMLInputElement;
  const picked = Array.from(input.files ?? []);
  input.value = '';
  if (!picked.length) return;
  const quota = HONOR_MAX_ATT - keptIds.value.length - newAtts.value.length;
  const { ok, failed: bad } = prevalidateFiles(picked);
  const failList = [...bad];
  ok.slice(quota).forEach((f) => failList.push({ name: f.name, reason: `原件最多 ${HONOR_MAX_ATT} 个` }));
  const todo = ok.slice(0, quota);
  failed.value = failList;
  if (!todo.length) {
    if (failList.length) show(`${failList.length} 个文件未能上传`, 'err');
    return;
  }
  uploading.value = true;
  try {
    const d = await uploadAttachmentsChunked(todo, 'honor', grantedBy.value.trim() || undefined);
    if (d.success.length) newAtts.value = [...newAtts.value, ...d.success].slice(0, HONOR_MAX_ATT);
    const allFailed = [...failList, ...d.failed];
    failed.value = allFailed;
    if (d.success.length) show(`已上传 ${d.success.length} 份原件`);
    else if (allFailed.length) show(`${allFailed.length} 个文件未能上传`, 'err');
  } finally {
    uploading.value = false;
  }
};

const submitForm = async () => {
  if (submitting.value) return;
  if (!title.value.trim()) { show('请填写荣誉名称', 'err'); return; }
  if (!category.value) { show('请选择荣誉类别', 'err'); return; }
  if (!level.value) { show('请选择荣誉级别', 'err'); return; }
  if (!grantedBy.value.trim()) { show('请填写授予单位', 'err'); return; }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(awardedAt.value)) { show('请选择获得日期', 'err'); return; }
  if (hasOriginal.value && keptIds.value.length + newAtts.value.length === 0) {
    show('有证书原件时请上传原件附件', 'err');
    return;
  }
  submitting.value = true;
  try {
    const attachment_ids = hasOriginal.value ? [...keptIds.value, ...newAtts.value.map((a) => a.id)] : [];
    if (editingId.value != null) {
      await api.honors.update(editingId.value, {
        title: title.value.trim(),
        category: category.value,
        level: level.value,
        awarded_at: awardedAt.value
      });
      show('荣誉已更新');
    } else {
      const created = await api.honors.create({
        title: title.value.trim(),
        category: category.value,
        level: level.value,
        awarded_at: awardedAt.value,
        granted_by: grantedBy.value.trim(),
        archive_id: archiveId.value ?? undefined,
        attachment_ids
      });
      show('荣誉已录入，等待审核');
    }
    resetForm();
    formOpen.value = false;
    loadList();
  } catch (e) {
    show(errText(e, '提交失败'), 'err');
  } finally {
    submitting.value = false;
  }
};
</script>

<template>
  <ProfileShell>
    <PageHeader
      eyebrow="MY HONORS"
      title="我的荣誉"
      :desc="`共 ${total} 项荣誉与奖项 · 支持国家级 / 省级 / 市级 / 校级分级`"
    >
      <template #action>
        <div class="flex flex-wrap items-center gap-2.5">
          <BackToProfile />
          <button
            type="button"
            class="flex items-center gap-1.5 rounded-lg bg-tech px-5 py-2.5 text-sm font-bold text-white shadow-[0_6px_18px_rgba(37,99,235,0.4)] transition hover:bg-blue-700"
            @click="formOpen ? (formOpen = false) : openCreate()"
          >
            <span v-if="formOpen">收起表单</span>
            <span v-else class="inline-flex items-center">
              <Lucide name="award" :size="14" class="mr-1" />录入荣誉
            </span>
          </button>
        </div>
      </template>
    </PageHeader>

    <!-- 录入 / 编辑表单 -->
    <section
      v-if="formOpen"
      class="form-enter mb-6 rounded-2xl border border-lamp/30 bg-white p-6 shadow-[0_14px_36px_rgba(26,16,61,0.08)] sm:p-7"
    >
      <h2 class="font-title text-lg font-bold text-ink">
        {{ editingId != null ? '修改荣誉' : '录入荣誉' }}
      </h2>
      <p class="mt-1 text-xs text-slate-500">
        {{ editingId != null ? '仅待院系初审状态可修改' : '提交后进入审核流程，通过后进入荣誉墙' }}
      </p>
      <div class="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div class="sm:col-span-2">
          <label :class="LABEL" for="honor-title">荣誉名称</label>
          <input
            id="honor-title"
            :class="INPUT"
            v-model="title"
            maxlength="50"
            placeholder="如：福建省互联网+大学生创新创业大赛金奖"
          />
        </div>
        <div>
          <label :class="LABEL" for="honor-category">荣誉类别</label>
          <select id="honor-category" :class="`${INPUT} cursor-pointer appearance-none`" v-model="category">
            <option value="">请选择类别</option>
            <option v-for="c in categories.length ? categories : HONOR_CATEGORY_FALLBACK" :key="c.value" :value="c.value">
              {{ c.label }}
            </option>
          </select>
        </div>
        <div>
          <label :class="LABEL" for="honor-level">荣誉级别</label>
          <select id="honor-level" :class="`${INPUT} cursor-pointer appearance-none`" v-model="level">
            <option value="">请选择级别</option>
            <option v-for="l in HONOR_LEVELS" :key="l.value" :value="l.value">{{ l.label }}</option>
          </select>
        </div>
        <div>
          <label :class="LABEL" for="honor-granter">
            授予单位 <span class="text-red-400">*</span>
          </label>
          <input
            id="honor-granter"
            :class="INPUT"
            v-model="grantedBy"
            maxlength="50"
            placeholder="如：教育部 / 竞赛组委会"
          />
        </div>
        <div>
          <label :class="LABEL" for="honor-date">获得日期</label>
          <input id="honor-date" type="date" :class="`${INPUT} cursor-pointer`" v-model="awardedAt" />
        </div>
        <div class="sm:col-span-2">
          <label :class="LABEL" for="honor-archive">
            关联档案 <span class="font-normal text-slate-400">（选填）</span>
          </label>
          <select
            id="honor-archive"
            :class="`${INPUT} cursor-pointer appearance-none`"
            :value="archiveId ?? ''"
            @change="archiveId = ($event.target as HTMLSelectElement).value ? Number(($event.target as HTMLSelectElement).value) : null"
          >
            <option value="">不关联档案</option>
            <option v-for="a in myArchives" :key="a.id" :value="a.id">{{ a.title }}</option>
          </select>
        </div>
      </div>

      <!-- 证书原件 -->
      <div class="mt-5 rounded-xl border border-slate-100 bg-warm/40 p-4">
        <label class="flex cursor-pointer items-center gap-2.5">
          <input
            type="checkbox"
            :checked="hasOriginal"
            class="h-4 w-4 accent-[#2563eb]"
            @change="toggleOriginal(($event.target as HTMLInputElement).checked)"
          />
          <span class="text-sm font-medium text-ink">有证书原件</span>
        </label>

        <div v-if="hasOriginal" class="mt-3">
          <p v-if="keptIds.length > 0" class="mb-2 text-xs text-slate-500">
            已保留原有附件 {{ keptIds.length }} 个
          </p>
          <button
            type="button"
            :disabled="uploading || keptIds.length + newAtts.length >= HONOR_MAX_ATT"
            class="flex w-full flex-col items-center justify-center gap-1.5 rounded-lg border-2 border-dashed border-slate-200 bg-white py-5 text-slate-400 transition hover:border-tech/50 hover:text-tech disabled:cursor-not-allowed disabled:opacity-50"
            @click="fileRef?.click()"
          >
            <span class="text-xl">
              <Lucide v-if="uploading" name="loader-circle" :size="20" class="animate-spin" />
              <Lucide v-else name="paperclip" :size="20" />
            </span>
            <span class="text-xs font-medium">
              {{
                uploading
                  ? '上传中…'
                  : keptIds.length + newAtts.length >= HONOR_MAX_ATT
                    ? '已达上限'
                    : '点击上传原件（jpg/png/webp/pdf，≤10MB）'
              }}
            </span>
          </button>
          <input ref="fileRef" type="file" multiple hidden accept=".jpg,.jpeg,.png,.webp,.pdf" @change="onPickFiles" />

          <ul v-if="newAtts.length > 0" class="mt-3 space-y-2">
            <li
              v-for="a in newAtts"
              :key="a.id"
              class="flex items-center gap-3 rounded-lg border border-slate-100 bg-white px-3.5 py-2"
            >
              <span class="text-sm"><Lucide name="file-text" :size="15" /></span>
              <span class="min-w-0 flex-1 truncate text-sm text-ink" :title="a.name">{{ a.name }}</span>
              <span class="shrink-0 text-xs text-slate-400">{{ fmtSize(a.size) }}</span>
              <button
                type="button"
                class="shrink-0 rounded-md px-2 py-0.5 text-xs text-slate-400 transition hover:bg-red-50 hover:text-red-500"
                @click="newAtts = newAtts.filter((x) => x.id !== a.id)"
              >
                移除
              </button>
            </li>
          </ul>
          <ul v-if="failed.length > 0" class="mt-2 space-y-1.5">
            <li
              v-for="f in failed"
              :key="`${f.name}-${f.reason}`"
              class="flex items-center gap-3 rounded-lg bg-red-50 px-3.5 py-2 text-xs text-red-600"
            >
              <span class="min-w-0 flex-1 truncate">{{ f.name }}</span>
              <span class="shrink-0">{{ f.reason }}</span>
            </li>
          </ul>
        </div>
        <p v-else class="mt-2.5 text-xs leading-relaxed text-slate-500">
          无原件：仅做文字记录，无需上传附件；审核通过后该荣誉仍会计入荣誉统计。
        </p>
      </div>

      <div class="mt-6 flex gap-3">
        <button
          type="button"
          class="rounded-lg border border-slate-200 px-6 py-2.5 text-sm font-semibold text-slate-600 transition hover:bg-slate-50"
          @click="formOpen = false; resetForm()"
        >
          取消
        </button>
        <button
          type="button"
          :disabled="submitting || uploading"
          class="rounded-lg bg-tech px-6 py-2.5 text-sm font-bold text-white shadow-[0_6px_18px_rgba(37,99,235,0.4)] transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
          @click="submitForm"
        >
          {{ submitting ? '提交中…' : editingId != null ? '保存修改' : '提交录入' }}
        </button>
      </div>
    </section>

    <!-- 荣誉列表 -->
    <p v-if="loading" class="py-16 text-center text-sm text-slate-400">加载中…</p>
    <div v-else-if="list.length" class="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <article
        v-for="(h, i) in list"
        :key="h.id"
        class="item-enter flex flex-col rounded-2xl border border-lamp/25 bg-white p-5 shadow-[0_10px_26px_rgba(26,16,61,0.05)] sm:p-6"
        :style="{ animationDelay: `${Math.min(i, 6) * 0.05}s` }"
      >
        <div class="flex items-start justify-between gap-4">
          <div class="flex min-w-0 items-start gap-3">
            <span class="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-lamp/15 text-lamp">
              <Lucide name="trophy" :size="20" />
            </span>
            <div class="min-w-0">
              <h2 class="font-title truncate text-base font-bold text-ink">{{ h.title }}</h2>
              <p class="mt-1 text-xs text-slate-500">
                {{ categoryText(h.category) }} · {{ levelLabel(h.level) }} · {{ fmtDate(h.awarded_at) }}
              </p>
            </div>
          </div>
          <StatusTag :status="h.status" />
        </div>

        <div class="mt-3 space-y-1 text-xs text-slate-500">
          <p v-if="h.granted_by">授予单位:{{ h.granted_by }}</p>
          <p>
            {{ h.attachment_ids?.length ? `证书原件 ${h.attachment_ids.length} 份` : '无原件（文字记录）' }}
            {{ ' ' }}· {{ fmtTime(h.created_at) }}
          </p>
        </div>

        <!-- 驳回原因 -->
        <div
          v-if="h.status === 'rejected' && h.reject_reason"
          class="mt-3 rounded-lg bg-red-50 px-4 py-2.5 text-xs leading-relaxed text-red-600"
        >
          <b>驳回原因:</b>
          {{ h.reject_reason }}
        </div>

        <!-- 待初审可修改 -->
        <div v-if="h.status === 'pending_first'" class="mt-auto flex justify-end border-t border-slate-100 pt-3">
          <button
            type="button"
            class="rounded-lg border border-tech/40 px-3.5 py-1.5 text-xs font-semibold text-tech transition hover:bg-tech/5"
            @click="openEdit(h)"
          >
            修改
          </button>
        </div>
      </article>
    </div>
    <div
      v-else
      class="rounded-2xl border border-dashed border-slate-200 bg-white/60 py-16 text-center"
    >
      <p class="text-sm text-slate-500">暂无荣誉记录，点击右上角「录入荣誉」开始</p>
    </div>

    <ToastBar :toast="toast" />
  </ProfileShell>
</template>

<style scoped>
.form-enter {
  animation: formEnter 0.3s cubic-bezier(0.22, 0.61, 0.36, 1);
}
@keyframes formEnter {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.item-enter {
  animation: itemEnter 0.35s cubic-bezier(0.22, 0.61, 0.36, 1) both;
}
@keyframes itemEnter {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
