<script setup lang="ts">
// 提交资料（/profile/submit）：新建 / 编辑（?id=）双模式（H5 ArchiveFormPage 门户版）
// - 基本信息表单（标题/类别/内容/备注）+ 附件批量上传（multipart，API 规范 6.1：
//   白名单 jpg/png/webp/pdf、单文件 ≤10MB、单次 ≤5 个，超出自动分批）
// - 草稿（API 规范第十二章）：主存储 localStorage（键 alumni_draft_archive，与 H5 端互通），
//   每次变更即写本地，每 1 分钟定时 /drafts/sync（版本号乐观锁，冲突用服务端最新覆盖本地）
// - 提交：submit=true 进入两级审核流；存草稿不入审核流
// - 编辑模式（?id=xx）：仅 draft / rejected 可编辑；rejected 修改后重提生成 version+1
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ProfileShell from '../components/profile/ProfileShell.vue';
import PageHeader from '../components/profile/PageHeader.vue';
import BackToProfile from '../components/profile/BackToProfile.vue';
import ToastBar from '../components/profile/ToastBar.vue';
import {
  ARCHIVE_CATEGORIES,
  categoryLabel,
  fmtSize,
  prevalidateFiles,
  uploadAttachmentsChunked,
  useToast
} from '../components/profile/shared';
import Lucide from '../components/Lucide.vue';
import { api, ApiError, type Archive } from '../api';

const INPUT =
  'w-full rounded-lg border border-slate-200 bg-warm/70 px-4 py-3 text-base text-ink placeholder:text-slate-400 outline-none transition focus:border-tech focus:bg-white focus:ring-2 focus:ring-tech/20';
const LABEL = 'mb-1.5 block text-sm font-medium text-ink';

interface AttItem {
  id: number;
  name: string;
  size: number;
  mime: string;
}

/** API 规范 6.1 限制 */
const MAX_COUNT = 9; // 表单附件总数上限
const ACCEPT = '.jpg,.jpeg,.png,.webp,.pdf';

/* ================= 草稿（localStorage 为主，服务端兜底；与 H5 端共用同一键） ================= */
const STORAGE_KEY = 'alumni_draft_archive';

interface LocalDraft {
  version: number;
  payload: {
    title: string;
    category: string;
    content: string;
    remark: string;
    attachment_ids: number[];
    draft_attachments?: AttItem[];
  };
  updatedAt: number;
}

function getLocalDraft(): LocalDraft | null {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null');
  } catch {
    return null;
  }
}

function setLocalDraft(d: LocalDraft) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(d));
}

function clearLocalDraft() {
  localStorage.removeItem(STORAGE_KEY);
}

const router = useRouter();
const route = useRoute();
const editId = route.query.id ? Number(route.query.id) : null;
const isEdit = editId != null;

const { toast, show } = useToast();
const fileRef = ref<HTMLInputElement | null>(null);

const loadingDetail = ref(isEdit);
const editStatus = ref<Archive['status'] | ''>('');
const rejectReason = ref('');

const title = ref('');
const category = ref('');
const content = ref('');
const remark = ref('');
const atts = ref<AttItem[]>([]);
const uploading = ref(false);
const failed = ref<{ name: string; reason: string }[]>([]);
const submitting = ref(false);
const saving = ref(false);

/** 草稿状态（仅新建模式启用） */
let draftVersion = 0;
let draftDirty = false;
const draftSavedAt = ref('');
/** 首个回填完成前，避免 watch 把初始空值写进草稿 */
let draftReady = false;

const errText = (e: unknown, fallback: string) => (e instanceof ApiError ? e.message : fallback);

const buildPayload = () => ({
  title: title.value.trim(),
  category: category.value,
  content: content.value.trim(),
  remark: remark.value.trim(),
  attachment_ids: atts.value.map((a) => a.id),
  // 草稿专用：附件完整对象（恢复时回填展示；正式提交时后端忽略）
  draft_attachments: atts.value
});

const restorePayload = (p: LocalDraft['payload']) => {
  title.value = p.title || '';
  category.value = p.category || '';
  content.value = p.content || '';
  remark.value = p.remark || '';
  if (Array.isArray(p.draft_attachments) && p.draft_attachments.length) {
    atts.value = p.draft_attachments;
  } else if (Array.isArray(p.attachment_ids)) {
    atts.value = p.attachment_ids.map((id, i) => ({ id, name: `附件 ${i + 1}（#${id}）`, size: 0, mime: '' }));
  }
};

/* ================= 本地落盘：表单每次变更即写 ================= */
watch([title, category, content, remark, atts], () => {
  if (!draftReady || isEdit) return;
  setLocalDraft({ version: draftVersion, payload: buildPayload(), updatedAt: Date.now() });
  draftDirty = true;
  draftSavedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false });
});

/* ================= 服务端定时同步（版本号乐观锁，仅新建模式） ================= */
let syncTimer: number | undefined;
onMounted(() => {
  if (isEdit) return;
  syncTimer = window.setInterval(async () => {
    if (!draftDirty) return;
    try {
      const d = await api.drafts.sync({ biz_type: 'archive', version: draftVersion, payload: buildPayload() });
      draftVersion = d.version;
      draftDirty = false;
    } catch (e) {
      if (e instanceof ApiError && e.code === 'DRAFT_VERSION_CONFLICT') {
        // 草稿已被其他设备修改：服务端最新覆盖本地（API 规范 12.1）
        const data = e.data as { version: number; payload: LocalDraft['payload'] } | undefined;
        if (data) {
          draftVersion = data.version;
          restorePayload(data.payload);
        }
        draftDirty = false;
        show('检测到其他设备的更新，已同步最新草稿');
      }
      // 其他失败静默：本地已有副本，下次继续尝试
    }
  }, 60 * 1000);
});
onBeforeUnmount(() => {
  if (syncTimer !== undefined) window.clearInterval(syncTimer);
});

/* ================= 编辑模式：加载详情 ================= */
const loadDetail = async (id: number) => {
  loadingDetail.value = true;
  try {
    const d = await api.archives.detail(id);
    if (d.status !== 'draft' && d.status !== 'rejected') {
      show('仅草稿或已驳回的档案可编辑', 'err');
      router.replace('/profile/records');
      return;
    }
    editStatus.value = d.status;
    rejectReason.value = d.reject_reason || '';
    title.value = d.title || '';
    category.value = d.category || '';
    content.value = d.content || '';
    remark.value = d.remark || '';
    // 附件回填（id 兜底命名）
    atts.value = (d.attachment_ids || []).map((id2, i) => ({
      id: id2,
      name: `附件 ${i + 1}（#${id2}）`,
      size: 0,
      mime: ''
    }));
  } catch (e) {
    show(errText(e, '档案加载失败'), 'err');
    router.replace('/profile/records');
  } finally {
    loadingDetail.value = false;
  }
};

/* ================= 挂载：编辑模式拉详情；新建模式恢复草稿 ================= */
onMounted(() => {
  if (editId != null) {
    loadDetail(editId);
    return;
  }
  // 新建：先恢复本地草稿（断网/退出重进不丢）
  const local = getLocalDraft();
  if (local?.payload) {
    draftVersion = local.version || 0;
    restorePayload(local.payload);
    draftSavedAt.value = new Date(local.updatedAt).toLocaleTimeString('zh-CN', { hour12: false });
    show('已恢复本地草稿，可继续编辑');
  } else {
    // 本地无草稿：检查云端（换设备续编，API 规范 12.2）
    api.drafts
      .my('archive')
      .then((list) => {
        const cloud = list.find((d) => d.biz_type === 'archive' && d.version > 0 && d.payload);
        if (!cloud) return;
        const p = cloud.payload as LocalDraft['payload'];
        draftVersion = cloud.version;
        restorePayload(p);
        setLocalDraft({ version: cloud.version, payload: p, updatedAt: Date.now() });
        draftSavedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false });
        show(`已恢复云端草稿「${(p.title || '未命名资料').slice(0, 20)}」`);
      })
      .catch(() => undefined); // 云端草稿查询失败不阻塞新建
  }
  draftReady = true;
});

/* ================= 附件上传（multipart，预校验 + ≤5/次自动分批） ================= */
const onPickFiles = async (e: Event) => {
  const input = e.target as HTMLInputElement;
  const picked = Array.from(input.files ?? []);
  input.value = '';
  if (!picked.length) return;

  // 前端预校验：类型白名单 + 大小（API 规范 6.1）
  const { ok, failed: bad } = prevalidateFiles(picked);
  const failList = [...bad];

  const quota = MAX_COUNT - atts.value.length;
  ok.slice(quota).forEach((f) => failList.push({ name: f.name, reason: `附件总数已达 ${MAX_COUNT} 个上限` }));
  const todo = ok.slice(0, quota);

  uploading.value = true;
  failed.value = failList;
  try {
    const d = await uploadAttachmentsChunked(todo, 'archive', remark.value.trim() || undefined);
    if (d.success.length) atts.value = [...atts.value, ...d.success].slice(0, MAX_COUNT);
    const allFailed = [...failList, ...d.failed];
    failed.value = allFailed;
    if (d.success.length) show(`已上传 ${d.success.length} 个附件`);
    else if (allFailed.length) show(`${allFailed.length} 个文件未能上传`, 'err');
    else show('上传失败，请稍后重试', 'err');
  } finally {
    uploading.value = false;
  }
};

const removeAtt = (id: number) => {
  atts.value = atts.value.filter((a) => a.id !== id);
};

/* ================= 校验与提交 ================= */
const validate = (): boolean => {
  if (!title.value.trim()) { show('请填写资料标题', 'err'); return false; }
  if (!category.value) { show('请选择资料类别', 'err'); return false; }
  if (!content.value.trim()) { show('请填写详细内容', 'err'); return false; }
  return true;
};

/** 提交用 payload：剔除草稿专用字段 draft_attachments */
const payload = () => {
  const { draft_attachments: _omit, ...p } = buildPayload();
  return { ...p, remark: p.remark || undefined };
};

/**
 * @param submit false=存草稿；true=提交进入待初审
 */
const handleSubmit = async (submit: boolean) => {
  if (submitting.value || saving.value || uploading.value) return;
  if (!validate()) return;

  // ---- 编辑模式 ----
  if (isEdit && editId != null) {
    if (editStatus.value === 'draft') {
      if (submit) {
        submitting.value = true;
        try {
          const d = await api.archives.update(editId, { ...payload(), submit: true });
          if (d.status === 'pending_first') {
            show('已提交，等待院系初审');
            router.replace('/profile/records');
          } else {
            // 后端未支持 PATCH submit（契约缺口）：降级提示
            show('修改已保存，请在记录页重试提交');
            router.replace('/profile/records');
          }
        } catch (e) {
          show(errText(e, '提交失败'), 'err');
        } finally {
          submitting.value = false;
        }
      } else {
        saving.value = true;
        try {
          await api.archives.update(editId, { ...payload(), submit: false });
          show('草稿已更新');
          router.replace('/profile/records');
        } catch (e) {
          show(errText(e, '保存失败'), 'err');
        } finally {
          saving.value = false;
        }
      }
      return;
    }
    if (editStatus.value === 'rejected') {
      submitting.value = true;
      try {
        if (submit) {
          // 修改内容后重提：先保存修改，再 resubmit 生成 version+1
          await api.archives.update(editId, payload());
          const d = await api.archives.resubmit(editId);
          show(`修改已保存并重新提交（第 ${d.version} 版）`);
        } else {
          await api.archives.update(editId, payload());
          show('修改已保存，可稍后在记录页重提');
        }
        router.replace('/profile/records');
      } catch (e) {
        show(errText(e, '操作失败'), 'err');
      } finally {
        submitting.value = false;
      }
      return;
    }
    show('当前状态不可编辑', 'err');
    return;
  }

  // ---- 新建模式 ----
  if (submit) {
    submitting.value = true;
    try {
      const created = await api.archives.create({ ...payload(), submit: true });
      clearLocalDraft(); // 正式提交成功：清除本地草稿（服务端草稿由后端自动删除）
      show('提交成功，等待院系初审');
      router.replace('/profile/records');
    } catch (e) {
      show(errText(e, '提交失败'), 'err');
    } finally {
      submitting.value = false;
    }
  } else {
    saving.value = true;
    try {
      await api.archives.create({ ...payload(), submit: false });
      clearLocalDraft();
      show('草稿已保存');
      router.replace('/profile');
    } catch (e) {
      show(errText(e, '保存失败'), 'err');
    } finally {
      saving.value = false;
    }
  }
};

const cardCls = 'rounded-2xl border border-slate-100 bg-white p-6 shadow-[0_10px_26px_rgba(26,16,61,0.05)] sm:p-7';
</script>

<template>
  <ProfileShell>
    <PageHeader
      :eyebrow="isEdit ? 'EDIT ARCHIVE' : 'SUBMIT ARCHIVE'"
      :title="isEdit ? '编辑资料' : '提交资料'"
      :desc="
        isEdit
          ? editStatus === 'rejected'
            ? '该档案曾被驳回，修改内容后重新提交将生成新版本'
            : '编辑草稿内容，可继续上传或移除附件'
          : '填写获奖、证书、实践等经历，上传证明材料，经两级审核后进入校友档案库'
      "
    >
      <template #action>
        <BackToProfile />
      </template>
    </PageHeader>

    <p v-if="loadingDetail" class="py-16 text-center text-sm text-slate-400">加载中…</p>
    <div v-else class="space-y-5">
      <!-- 驳回原因横幅（编辑已驳回档案时展示） -->
      <div
        v-if="isEdit && editStatus === 'rejected'"
        class="banner-enter rounded-xl bg-red-50 px-5 py-4 text-sm leading-relaxed text-red-600"
      >
        <b>该档案曾被驳回:</b>
        {{ rejectReason || '见记录页详情' }}。修改内容后重新提交将生成新版本。
      </div>

      <!-- 基本信息 -->
      <section :class="cardCls">
        <h2 class="font-title text-lg font-bold text-ink">基本信息</h2>
        <div class="mt-5 space-y-4">
          <div>
            <label :class="LABEL" for="arc-title">资料标题</label>
            <input
              id="arc-title"
              :class="INPUT"
              v-model="title"
              maxlength="50"
              placeholder="如：全国大学生程序设计竞赛金奖"
            />
            <p class="mt-1 text-right text-xs text-slate-400">{{ title.length }}/50</p>
          </div>
          <div>
            <label :class="LABEL" for="arc-category">资料类别</label>
            <select id="arc-category" :class="`${INPUT} cursor-pointer appearance-none`" v-model="category">
              <option value="">请选择类别</option>
              <option v-for="c in ARCHIVE_CATEGORIES" :key="c.value" :value="c.value">{{ c.label }}</option>
            </select>
          </div>
          <div>
            <label :class="LABEL" for="arc-content">详细内容</label>
            <textarea
              id="arc-content"
              :class="`${INPUT} min-h-[130px] resize-y`"
              v-model="content"
              maxlength="500"
              placeholder="请详细描述经历、成果或荣誉情况"
            />
            <p class="mt-1 text-right text-xs text-slate-400">{{ content.length }}/500</p>
          </div>
          <div>
            <label :class="LABEL" for="arc-remark">备注（选填）</label>
            <input
              id="arc-remark"
              :class="INPUT"
              v-model="remark"
              maxlength="100"
              placeholder="补充说明，将随附件一并提交"
            />
          </div>
        </div>
      </section>

      <!-- 证明材料 -->
      <section :class="cardCls">
        <div class="flex items-center justify-between gap-4">
          <h2 class="font-title text-lg font-bold text-ink">证明材料</h2>
          <span class="text-xs text-slate-400">{{ atts.length }}/{{ MAX_COUNT }}</span>
        </div>
        <p class="mt-1.5 text-xs leading-relaxed text-slate-500">
          支持 jpg / png / webp / pdf，单文件 ≤10MB，单次最多选 5 个（超出自动分批上传）
        </p>

        <button
          type="button"
          :disabled="uploading || atts.length >= MAX_COUNT"
          class="mt-4 flex w-full flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-slate-200 bg-warm/50 py-8 text-slate-400 transition hover:border-tech/50 hover:text-tech disabled:cursor-not-allowed disabled:opacity-50"
          @click="fileRef?.click()"
        >
          <span class="text-2xl">
            <Lucide v-if="uploading" name="loader-circle" :size="24" class="animate-spin" />
            <Lucide v-else name="paperclip" :size="24" />
          </span>
          <span class="text-sm font-medium">
            {{ uploading ? '上传中…' : atts.length >= MAX_COUNT ? '已达上限' : '点击选择文件（可多选）' }}
          </span>
        </button>
        <input ref="fileRef" type="file" multiple hidden :accept="ACCEPT" @change="onPickFiles" />

        <!-- 已上传列表 -->
        <ul v-if="atts.length > 0" class="mt-4 space-y-2">
          <li
            v-for="a in atts"
            :key="a.id"
            class="flex items-center gap-3 rounded-lg border border-slate-100 bg-warm/40 px-4 py-2.5"
          >
            <span class="text-sm"><Lucide name="file-text" :size="15" /></span>
            <span class="min-w-0 flex-1 truncate text-sm font-medium text-ink" :title="a.name">
              {{ a.name }}
            </span>
            <span class="shrink-0 text-xs text-slate-400">{{ fmtSize(a.size) }}</span>
            <button
              type="button"
              class="shrink-0 rounded-md px-2 py-0.5 text-xs text-slate-400 transition hover:bg-red-50 hover:text-red-500"
              @click="removeAtt(a.id)"
            >
              移除
            </button>
          </li>
        </ul>

        <!-- 校验/上传失败列表（重新选择即可重试） -->
        <ul v-if="failed.length > 0" class="mt-3 space-y-2">
          <li
            v-for="f in failed"
            :key="`${f.name}-${f.reason}`"
            class="flex items-center gap-3 rounded-lg bg-red-50 px-4 py-2.5 text-xs text-red-600"
          >
            <span class="min-w-0 flex-1 truncate" :title="f.name">{{ f.name }}</span>
            <span class="shrink-0">{{ f.reason }}</span>
          </li>
        </ul>

        <!-- 草稿状态提示（仅新建模式） -->
        <p v-if="!isEdit && draftSavedAt" class="mt-4 text-center text-xs text-slate-400">
          草稿已保存（{{ draftSavedAt }}）· 断网/关闭页面不丢失，1 分钟后自动同步云端
        </p>
      </section>

      <!-- 底部操作条 -->
      <div class="sticky bottom-4 z-10 flex flex-wrap gap-3 rounded-2xl border border-slate-100 bg-white/95 p-4 shadow-[0_18px_40px_rgba(26,16,61,0.12)] backdrop-blur-sm">
        <button
          type="button"
          :disabled="saving || submitting || uploading"
          class="flex-1 rounded-lg border border-tech/40 px-6 py-3 text-sm font-bold text-tech transition hover:bg-tech/5 disabled:cursor-not-allowed disabled:opacity-50"
          @click="handleSubmit(false)"
        >
          {{ saving ? '保存中…' : isEdit ? (editStatus === 'draft' ? '保存草稿' : '保存修改') : '存草稿' }}
        </button>
        <button
          type="button"
          :disabled="saving || submitting || uploading"
          class="flex-1 rounded-lg bg-tech px-6 py-3 text-sm font-bold text-white shadow-[0_6px_18px_rgba(37,99,235,0.4)] transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
          @click="handleSubmit(true)"
        >
          {{
            submitting
              ? '提交中…'
              : isEdit
                ? editStatus === 'rejected' ? '保存并重新提交' : '提交审核'
                : '提交审核'
          }}
        </button>
      </div>

      <p class="text-center text-xs leading-relaxed text-slate-400">
        提交后进入两级审核（院系初审 → 校级复核）；存草稿不进入审核流，可稍后继续编辑。
        当前类别：{{ category ? categoryLabel(category) : '未选择' }}
      </p>
    </div>

    <ToastBar :toast="toast" />
  </ProfileShell>
</template>

<style scoped>
.banner-enter {
  animation: bannerEnter 0.35s cubic-bezier(0.22, 0.61, 0.36, 1);
}
@keyframes bannerEnter {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
