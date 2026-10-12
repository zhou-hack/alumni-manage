<script setup lang="ts">
// 我的记录（/profile/records）：本人提交历史与审核状态（H5 RecordsPage 门户版）
// - 状态标签 + 驳回原因；驳回可修改重提 / 不改内容直接重提（version+1）
// - 草稿可编辑 / 直接提交审核；已通过可授权大屏展示 / 撤回授权；删除走申请审核
// 附件预览：预览接口需登录令牌，不能用裸 <a href>（浏览器不带 Authorization 头会 401），
// 必须带令牌拉 blob 生成对象 URL 打开（见 profile/shared.ts previewAttachment）
import { onMounted, ref } from 'vue';
import ProfileShell from '../components/profile/ProfileShell.vue';
import PageHeader from '../components/profile/PageHeader.vue';
import BackToProfile from '../components/profile/BackToProfile.vue';
import StatusTag from '../components/profile/StatusTag.vue';
import ConfirmDialog from '../components/profile/ConfirmDialog.vue';
import ToastBar from '../components/profile/ToastBar.vue';
import {
  categoryLabel,
  fmtSize,
  fmtTime,
  previewAttachment,
  useToast
} from '../components/profile/shared';
import Lucide from '../components/Lucide.vue';
import { api, ApiError, type Archive, type Attachment, type Consent } from '../api';

const PAGE_SIZE = 10;

const { toast, show } = useToast();

const list = ref<Archive[]>([]);
const total = ref(0);
const page = ref(1);
const loading = ref(true);
const busyId = ref<number | null>(null);
/** 已授权大屏展示：archive_id → 授权记录 id */
const consentMap = ref<Map<number, number>>(new Map());
/** 附件展开面板：已加载的附件详情（懒加载，缓存） */
const expandedId = ref<number | null>(null);
const attMap = ref<Map<number, Attachment[]>>(new Map());
const loadingAttId = ref<number | null>(null);
/** 确认弹窗状态 */
const confirm = ref<{
  kind: 'resubmit' | 'delete' | 'consent' | 'revoke' | 'submitDraft';
  item: Archive;
} | null>(null);
let loadingRef = false;

const errText = (e: unknown, fallback: string) =>
  e instanceof ApiError ? e.message : fallback;

/** 展开/收起附件面板（懒加载档案详情取附件列表） */
const toggleAtts = async (item: Archive) => {
  if (expandedId.value === item.id) {
    expandedId.value = null;
    return;
  }
  expandedId.value = item.id;
  if (attMap.value.has(item.id)) return;
  loadingAttId.value = item.id;
  try {
    const d = await api.archives.detail(item.id);
    // 详情含 attachments 对象列表；仅返回 ids 时按 id 生成占位条目
    const atts: Attachment[] =
      d.attachments && d.attachments.length
        ? d.attachments
        : (d.attachment_ids || []).map((id) => ({
            id,
            biz_type: 'archive',
            status: 'referenced',
            name: `附件 #${id}`,
            url: '',
            size: 0,
            mime: '',
            created_at: ''
          }));
    attMap.value = new Map(attMap.value).set(item.id, atts);
  } catch (e) {
    // 详情加载失败时给用户可见提示，并保留已展开状态以便重试
    show(errText(e, '附件列表加载失败，请重试'), 'err');
  } finally {
    loadingAttId.value = null;
  }
};

/** 附件预览（带 Authorization 头拉 blob，见 shared.ts） */
const openPreview = async (a: Attachment) => {
  try {
    await previewAttachment(a);
  } catch (e) {
    show(errText(e, '附件预览失败'), 'err');
  }
};

const loadConsents = async () => {
  try {
    const cs = await api.consents.my();
    consentMap.value = new Map(
      (cs as Consent[]).filter((c) => !c.revoked).map((c) => [c.archive_id, c.id])
    );
  } catch {
    // 授权查询失败不阻塞列表
  }
};

const loadList = async (reset = false) => {
  if (loadingRef) return;
  loadingRef = true;
  loading.value = true;
  const p = reset ? 1 : page.value + 1;
  try {
    const d = await api.archives.my({ page: String(p), page_size: String(PAGE_SIZE) });
    list.value = reset ? d.list : [...list.value, ...d.list];
    total.value = d.total;
    page.value = p;
  } catch (e) {
    show(errText(e, '记录加载失败'), 'err');
  } finally {
    loadingRef = false;
    loading.value = false;
  }
};

onMounted(() => {
  loadConsents();
  loadList(true);
});

/* ================= 操作 ================= */

/** 不改内容直接重提（生成 version+1 新版本） */
const doResubmit = async (item: Archive) => {
  if (busyId.value) return;
  busyId.value = item.id;
  try {
    await api.archives.resubmit(item.id);
    show('已重新提交审核');
    loadList(true);
  } catch (e) {
    show(errText(e, '重提失败'), 'err');
  } finally {
    busyId.value = null;
  }
};

/** 草稿直接提交审核（PATCH submit=true） */
const doSubmitDraft = async (item: Archive) => {
  if (busyId.value) return;
  busyId.value = item.id;
  try {
    const d = await api.archives.update(item.id, { submit: true });
    if (d.status === 'pending_first') {
      show('已提交，等待院系初审');
      loadList(true);
    } else {
      // 后端不支持 PATCH submit（契约缺口）：降级提示
      show('保存成功，请进入编辑页提交审核');
      loadList(true);
    }
  } catch (e) {
    show(errText(e, '提交失败'), 'err');
  } finally {
    busyId.value = null;
  }
};

/** 删除申请（非立即删除，进入 deleting 待审核） */
const doDeleteRequest = async (item: Archive) => {
  if (busyId.value) return;
  busyId.value = item.id;
  try {
    await api.archives.deleteRequest(item.id);
    show('删除申请已提交，等待审核');
    loadList(true);
  } catch (e) {
    show(errText(e, '删除申请失败'), 'err');
  } finally {
    busyId.value = null;
  }
};

/** 授权大屏展示（approved + consent 才可上架） */
const doConsent = async (item: Archive) => {
  if (busyId.value) return;
  busyId.value = item.id;
  try {
    const c = await api.consents.create({ archive_id: item.id, scope: 'screen' });
    consentMap.value = new Map(consentMap.value).set(item.id, c.id);
    show('已授权大屏展示，待管理员上架');
  } catch (e) {
    show(errText(e, '授权失败'), 'err');
  } finally {
    busyId.value = null;
  }
};

/** 撤回授权（撤回后档案自动下架） */
const doRevoke = async (item: Archive) => {
  const consentId = consentMap.value.get(item.id);
  if (!consentId || busyId.value) return;
  busyId.value = item.id;
  try {
    await api.consents.revoke(consentId);
    const next = new Map(consentMap.value);
    next.delete(item.id);
    consentMap.value = next;
    show('已撤回授权，档案已下架');
    loadList(true);
  } catch (e) {
    show(errText(e, '撤回失败'), 'err');
  } finally {
    busyId.value = null;
  }
};

const confirmHandlers: Record<string, (item: Archive) => void> = {
  resubmit: doResubmit,
  submitDraft: doSubmitDraft,
  delete: doDeleteRequest,
  consent: doConsent,
  revoke: doRevoke
};

const CONFIRM_META: Record<string, { title: string; message: string; confirmText: string; danger?: boolean }> = {
  resubmit: {
    title: '重新提交',
    message: '将不改内容直接重新提交审核（生成新版本），确定继续吗？',
    confirmText: '重新提交'
  },
  submitDraft: {
    title: '提交审核',
    message: '提交后进入两级审核流程（院系初审 → 校级复核），确定提交该草稿吗？',
    confirmText: '提交审核'
  },
  delete: {
    title: '申请删除',
    message: '删除申请提交后需管理员审核，审核通过前档案暂不会被删除。确定申请删除该档案吗？',
    confirmText: '申请删除',
    danger: true
  },
  consent: {
    title: '授权大屏展示',
    message: '授权后该档案可在校园大屏公开展示（需管理员上架），确定授权吗？',
    confirmText: '授权展示'
  },
  revoke: {
    title: '撤回授权',
    message: '撤回后，该档案将立即从大屏下架；如需展示可重新授权。确定撤回吗？',
    confirmText: '撤回授权',
    danger: true
  }
};

/* ================= 小按钮 ================= */
const btn =
  'rounded-lg px-3.5 py-1.5 text-xs font-semibold transition disabled:cursor-not-allowed disabled:opacity-50';
const btnPlain = `${btn} border border-tech/40 text-tech hover:bg-tech/5`;
const btnGhost = `${btn} border border-slate-200 text-slate-600 hover:bg-slate-50`;
const btnDanger = `${btn} border border-red-200 text-red-600 hover:bg-red-50`;
</script>

<template>
  <ProfileShell>
    <PageHeader
      eyebrow="MY RECORDS"
      title="我的记录"
      :desc="`共 ${total} 条提交记录 · 状态与驳回原因实时同步`"
    >
      <template #action>
        <div class="flex flex-wrap items-center gap-2.5">
          <BackToProfile />
          <RouterLink
            to="/profile/submit"
            class="flex items-center gap-1.5 rounded-lg bg-tech px-5 py-2.5 text-sm font-bold text-white shadow-[0_6px_18px_rgba(37,99,235,0.4)] transition hover:bg-blue-700"
          >
            <Lucide name="pen-line" :size="14" class="mr-1 inline-block" /> 提交新资料
          </RouterLink>
        </div>
      </template>
    </PageHeader>

    <!-- 列表 -->
    <div class="space-y-4">
      <article
        v-for="(item, i) in list"
        :key="item.id"
        class="item-enter rounded-2xl border border-slate-100 bg-white p-5 shadow-[0_10px_26px_rgba(26,16,61,0.05)] sm:p-6"
        :style="{ animationDelay: `${Math.min(i, 6) * 0.04}s` }"
      >
        <div class="flex items-start justify-between gap-4">
          <div class="min-w-0">
            <h2 class="truncate font-title text-lg font-bold text-ink">{{ item.title }}</h2>
            <p class="mt-1.5 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-slate-500">
              <span>
                {{ categoryLabel(item.category) }} · {{ fmtTime(item.created_at) }}
                <template v-if="item.version > 1"> · 第 {{ item.version }} 版</template>
              </span>
              <button
                v-if="item.attachment_ids?.length > 0"
                type="button"
                class="inline-flex items-center gap-1 rounded-full bg-tech/10 px-2.5 py-0.5 font-medium text-tech transition hover:bg-tech/20"
                @click="toggleAtts(item)"
              >
                <Lucide name="paperclip" :size="13" class="mr-1 inline-block" />附件 {{ item.attachment_ids.length }} 份
                <span
                  class="text-[9px] transition-transform"
                  :class="expandedId === item.id ? 'rotate-180' : ''"
                >▾</span>
              </button>
            </p>

            <!-- 附件展开面板（懒加载详情） -->
            <Transition name="expand">
              <div v-if="expandedId === item.id" class="overflow-hidden">
                <div class="mt-3 rounded-lg border border-slate-100 bg-warm/40 px-4 py-3">
                  <p v-if="loadingAttId === item.id" class="text-xs text-slate-400">附件加载中…</p>
                  <ul v-else class="space-y-1.5">
                    <li
                      v-for="a in attMap.get(item.id) || []"
                      :key="a.id"
                      class="flex items-center gap-2.5 text-xs"
                    >
                      <span><Lucide name="file-text" :size="13" /></span>
                      <span class="min-w-0 flex-1 truncate text-slate-600" :title="a.name">{{ a.name }}</span>
                      <span class="shrink-0 text-slate-400">{{ fmtSize(a.size) }}</span>
                      <button
                        type="button"
                        class="shrink-0 font-medium text-tech hover:text-blue-700"
                        @click="openPreview(a)"
                      >
                        预览
                      </button>
                    </li>
                  </ul>
                </div>
              </div>
            </Transition>
          </div>
          <StatusTag :status="item.status" />
        </div>

        <!-- 草稿：可编辑、可直接提交审核 -->
        <div
          v-if="item.status === 'draft'"
          class="mt-4 flex flex-wrap items-center justify-end gap-2.5 border-t border-slate-100 pt-4"
        >
          <span class="mr-auto text-xs text-slate-400">草稿未提交，不进入审核流</span>
          <RouterLink :to="`/profile/submit?id=${item.id}`" :class="btnPlain">编辑</RouterLink>
          <button
            type="button"
            :disabled="busyId === item.id"
            :class="`${btnPlain} bg-tech/5`"
            @click="confirm = { kind: 'submitDraft', item }"
          >
            提交审核
          </button>
        </div>

        <!-- 驳回：原因 + 修改重提 / 直接重提 / 申请删除 -->
        <div v-if="item.status === 'rejected'" class="mt-4 border-t border-slate-100 pt-4">
          <div class="rounded-lg bg-red-50 px-4 py-3 text-xs leading-relaxed text-red-600">
            <b>驳回原因:</b>
            {{ item.reject_reason || '见审核详情' }}
          </div>
          <div class="mt-3 flex flex-wrap justify-end gap-2.5">
            <RouterLink
              :to="`/profile/submit?id=${item.id}`"
              :class="`${btnPlain} bg-tech text-white hover:bg-blue-700 hover:text-white`"
            >
              修改内容后重提
            </RouterLink>
            <button
              type="button"
              :disabled="busyId === item.id"
              :class="btnPlain"
              @click="confirm = { kind: 'resubmit', item }"
            >
              不改内容直接重提
            </button>
            <button
              type="button"
              :disabled="busyId === item.id"
              :class="btnDanger"
              @click="confirm = { kind: 'delete', item }"
            >
              申请删除
            </button>
          </div>
        </div>

        <!-- 已通过：授权大屏 / 撤回 / 申请删除 -->
        <div
          v-if="item.status === 'approved'"
          class="mt-4 flex flex-wrap items-center justify-end gap-2.5 border-t border-slate-100 pt-4"
        >
          <button
            type="button"
            :disabled="busyId === item.id"
            :class="btnGhost"
            @click="confirm = { kind: 'delete', item }"
          >
            申请删除
          </button>
          <template v-if="consentMap.has(item.id)">
            <span class="rounded-full bg-tech/10 px-3.5 py-1 text-xs font-medium text-tech">
              已授权大屏展示
            </span>
            <button
              type="button"
              :disabled="busyId === item.id"
              :class="btnDanger"
              @click="confirm = { kind: 'revoke', item }"
            >
              撤回授权
            </button>
          </template>
          <button
            v-else
            type="button"
            :disabled="busyId === item.id"
            :class="btnPlain"
            @click="confirm = { kind: 'consent', item }"
          >
            授权大屏展示
          </button>
        </div>

        <!-- 审核中：进度提示 -->
        <p
          v-if="item.status === 'pending_first' || item.status === 'pending_second'"
          class="mt-4 border-t border-slate-100 pt-4 text-xs text-slate-400"
        >
          {{ item.status === 'pending_first' ? '等待院系初审，审核通过后进入校级复核' : '院系初审已通过，等待校级复核' }}
        </p>

        <p v-if="item.status === 'deleting'" class="mt-4 border-t border-slate-100 pt-4 text-xs text-slate-400">
          删除申请审核中，通过后档案将被删除
        </p>
      </article>
    </div>

    <!-- 加载中 / 空态 / 加载更多 -->
    <p v-if="loading && !list.length" class="py-16 text-center text-sm text-slate-400">加载中…</p>
    <div
      v-if="!loading && !list.length"
      class="rounded-2xl border border-dashed border-slate-200 bg-white/60 py-16 text-center"
    >
      <p class="text-sm text-slate-500">暂无提交记录</p>
      <RouterLink
        to="/profile/submit"
        class="mt-4 inline-flex rounded-lg bg-tech px-5 py-2.5 text-sm font-bold text-white shadow-[0_6px_18px_rgba(37,99,235,0.4)] transition hover:bg-blue-700"
      >
        提交第一份资料
      </RouterLink>
    </div>
    <div v-if="!loading && list.length < total" class="mt-5 text-center">
      <button
        type="button"
        :disabled="loading"
        class="rounded-lg border border-slate-200 bg-white px-6 py-2.5 text-sm font-semibold text-slate-600 shadow-sm transition hover:border-tech/40 hover:text-tech disabled:opacity-50"
        @click="loadList()"
      >
        加载更多（{{ list.length }}/{{ total }}）
      </button>
    </div>

    <!-- 弹窗与提示 -->
    <ConfirmDialog
      v-if="confirm"
      open
      :title="CONFIRM_META[confirm.kind].title"
      :message="CONFIRM_META[confirm.kind].message"
      :confirm-text="CONFIRM_META[confirm.kind].confirmText"
      :danger="CONFIRM_META[confirm.kind].danger"
      @confirm="confirmHandlers[confirm.kind]?.(confirm.item)"
      @close="confirm = null"
    />
    <ToastBar :toast="toast" />
  </ProfileShell>
</template>

<style scoped>
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
/* 附件面板展开/收起（高度过渡） */
.expand-enter-active,
.expand-leave-active {
  transition:
    max-height 0.22s ease,
    opacity 0.22s ease;
  max-height: 400px;
}
.expand-enter-from,
.expand-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>
