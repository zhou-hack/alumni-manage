<script setup>
/**
 * 校友留言墙审核（/wall，school_admin / system_admin）
 * - 待审核队列：通过 / 驳回（必填原因，用户端「我的留言」可见）
 * - 已通过：取消展示（下墙，可重新展示）/ 删除（物理删除）
 * - 置顶 / 禁言：UI 已预留，后端端点未提供时返回友好提示
 * - 我的留言：登录管理员本人发过的全部留言（pending/rejected/approved/hidden/已撤回），
 *   支持编辑（pending/rejected）、删除（pending/rejected，软删）、重新发送（已撤回）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getWallMessages,
  reviewWallMessage,
  deleteWallMessage,
  pinWallMessage,
  muteWallUser,
  getMyWallMessages,
  updateMyWallMessage,
  deleteMyWallMessage,
  resubmitMyWallMessage,
} from '@/api/alumniAdmin'
import { formatTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import LucideIcon from '@/components/LucideIcon.vue'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const filter = reactive({ status: 'pending', keyword: '' })

const STATUS_META = {
  pending: { text: '待审核', type: 'warning' },
  approved: { text: '已通过', type: 'success' },
  rejected: { text: '已驳回', type: 'danger' },
  hidden: { text: '已取消', type: 'info' },
}
const statusOf = (s) => STATUS_META[s] || { text: s || '—', type: 'info' }

const kpi = reactive({ pending: 0, approved: 0, rejected: 0, hidden: 0 })
async function loadKpi() {
  try {
    const tasks = ['pending', 'approved', 'rejected', 'hidden'].map((s) =>
      getWallMessages({ status: s, page_size: 1 }).then((r) => (kpi[s] = r.data.total)),
    )
    await Promise.all(tasks)
  } catch {
    /* KPI 失败不阻断列表 */
  }
}

async function loadList() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value, status: filter.status }
    if (filter.keyword.trim()) params.keyword = filter.keyword.trim()
    const res = await getWallMessages(params)
    // 默认按 ID 降序（最新的在前）
    list.value = (res.data.list || []).slice().sort((a, b) => a.id - b.id)
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function pickStatus(s) {
  filter.status = s
  page.value = 1
  loadList()
}

function handleSearch() {
  page.value = 1
  loadList()
}

async function handleApprove(row) {
  try {
    await ElMessageBox.confirm(`确认通过「${row.name}」的留言吗？通过后将公开展示在留言墙上。`, '审核通过', {
      type: 'warning',
      confirmButtonText: '通过',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await reviewWallMessage(row.id, { action: 'approve' })
    ElMessage.success('已通过')
    loadList()
    loadKpi()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function handleReject(row) {
  let note = ''
  try {
    const res = await ElMessageBox.prompt('请填写驳回原因（将展示给留言人）', '审核驳回', {
      type: 'warning',
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '如：内容不符合社区规范',
      inputValidator: (v) => (v && v.trim() ? true : '驳回原因不能为空'),
    })
    note = res.value.trim()
  } catch {
    return
  }
  try {
    await reviewWallMessage(row.id, { action: 'reject', note })
    ElMessage.success('已驳回')
    loadList()
    loadKpi()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/** 取消展示（已通过 → 下墙，门户不再展示；原因选填） */
async function handleHide(row) {
  let note = ''
  try {
    const res = await ElMessageBox.prompt('确认取消展示该留言吗？取消后将从门户留言墙撤下（原因选填，仅管理端可见）。', '取消展示', {
      type: 'warning',
      confirmButtonText: '取消展示',
      cancelButtonText: '返回',
      inputType: 'textarea',
      inputPlaceholder: '如：内容已过时（可留空）',
    })
    note = (res.value || '').trim()
  } catch {
    return
  }
  try {
    await reviewWallMessage(row.id, { action: 'hide', note: note || undefined })
    ElMessage.success('已取消展示')
    loadList()
    loadKpi()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/** 重新展示（已取消 → 重新上墙）
 *
 * P0-11 修复要点：与"通过 pending" 是不同的业务动作，单独加二次确认，
 * 避免误触后被静默重新上墙。语义沿用后端 action='approve'（后端允许 hidden → approved）。
 */
async function handleRestore(row) {
  try {
    await ElMessageBox.confirm(
      `确认将「${row.name || '匿名'}」的这条留言重新展示到公开墙吗？`,
      '重新展示',
      { type: 'info', confirmButtonText: '重新展示', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await reviewWallMessage(row.id, { action: 'approve' })
    ElMessage.success('已重新展示')
    loadList()
    loadKpi()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/** 删除留言（物理删除，不可恢复） */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」的这条留言吗？删除后不可恢复。`, '删除留言', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteWallMessage(row.id)
    ElMessage.success('已删除')
    loadList()
    loadKpi()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/** 置顶 / 取消置顶（后端预留；调用失败给友好提示） */
async function handlePin(row) {
  const next = !row.is_pinned
  try {
    await ElMessageBox.confirm(
      `确认${next ? '置顶' : '取消置顶'}该留言吗？${next ? '置顶后将固定在留言墙顶部。' : ''}`,
      next ? '置顶留言' : '取消置顶',
      { type: 'info' },
    )
  } catch {
    return
  }
  try {
    await pinWallMessage(row.id, next)
    ElMessage.success(next ? '已置顶' : '已取消置顶')
    loadList()
  } catch (e) {
    ElMessage.warning(e.message || '后端尚未提供置顶接口，请等待升级')
  }
}

/** 禁言留言人（durationDays 缺省 = 永久；durationDays > 0 = 限时） */
async function handleMute(row) {
  if (!row.user_id) {
    ElMessage.warning('该留言无用户信息，无法禁言')
    return
  }
  let mode = '24h'
  try {
    const res = await ElMessageBox.confirm(
      '确认禁言该用户吗？禁言期内该用户无法在门户留言墙发新留言（内存级禁言，重启失效）。',
      '禁言用户',
      {
        type: 'warning',
        confirmButtonText: '确认禁言',
        cancelButtonText: '取消',
        distinguishCancelAndClose: true,
      },
    )
    // 弹二次输入限时/永久
    mode = await ElMessageBox.prompt(
      '禁言时长（天），留空或 0 = 永久。建议 1-90 天。',
      '禁言时长',
      {
        inputType: 'number',
        inputValue: 7,
        inputPlaceholder: '1~90',
        inputValidator: (v) => {
          const n = Number(v)
          if (!Number.isFinite(n) || n < 0 || n > 365) return '请输入 0~365 之间的整数（0=永久）'
          return true
        },
      },
    ).then((r) => (r.value === '' ? 0 : Number(r.value)))
      .catch(() => null)
    if (mode == null) return // 用户取消
  } catch {
    return
  }
  const days = Number(mode) || 0
  try {
    await muteWallUser(row.user_id, { durationDays: days > 0 ? days : undefined, reason: '管理员在留言墙审核中操作' })
    ElMessage.success(days > 0 ? `已禁言 ${days} 天` : '已永久禁言')
  } catch (e) {
    ElMessage.error(e.message || '禁言失败')
  }
}

/* ==================== 我的留言 ==================== */

const myLoading = ref(false)
const myList = ref([])
const myTotal = ref(0)
const myShowAll = ref(true)
const editDialogOpen = ref(false)
const editTarget = ref(null)
const editForm = reactive({ content: '', show_name: false })
const submittingEdit = ref(false)

async function loadMine() {
  myLoading.value = true
  try {
    const res = await getMyWallMessages()
    const all = res.data.list || []
    myList.value = myShowAll.value
      ? all
      : all.filter((m) => m.status === 'pending' || m.status === 'rejected')
    myTotal.value = res.data.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    myLoading.value = false
  }
}

function openEditDialog(row) {
  editTarget.value = row
  editForm.content = row.content || ''
  editForm.show_name = !!row.show_name
  editDialogOpen.value = true
}

async function submitEdit() {
  if (!editTarget.value) return
  const text = (editForm.content || '').trim()
  if (!text) {
    ElMessage.warning('留言内容不能为空')
    return
  }
  if (text.length > 120) {
    ElMessage.warning('留言最长 120 字')
    return
  }
  submittingEdit.value = true
  try {
    await updateMyWallMessage(editTarget.value.id, {
      content: text,
      show_name: editForm.show_name,
    })
    ElMessage.success('已保存并提交重新审核')
    editDialogOpen.value = false
    loadMine()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submittingEdit.value = false
  }
}

async function handleMyRemove(row) {
  try {
    await ElMessageBox.confirm(
      `确认撤回这条留言吗？撤回后可在「我的留言」中重新发送或编辑重提。`,
      '撤回留言',
      { type: 'warning', confirmButtonText: '撤回', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteMyWallMessage(row.id)
    ElMessage.success('已撤回')
    loadMine()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function handleMyResubmit(row) {
  try {
    await ElMessageBox.confirm(
      `将基于这条留言的原文重新发送一条新留言并提交审核，确认吗？`,
      '重新发送',
      { type: 'info', confirmButtonText: '重新发送', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const res = await resubmitMyWallMessage(row.id)
    ElMessage.success(`已重新发送，新留言 #${res.data?.id ?? ''} 已进入待审核`)
    loadMine()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/** 我的留言：approved → 管理员本人下架（调 admin review hide） */
async function handleMyHide(row) {
  let note = ''
  try {
    const res = await ElMessageBox.prompt(
      '确认将这条留言从公开墙下架吗？下架后将不在门户展示（原因选填，仅管理端可见）。',
      '下架我的留言',
      {
        type: 'warning',
        confirmButtonText: '下架',
        cancelButtonText: '返回',
        inputType: 'textarea',
        inputPlaceholder: '如：内容已过时（可留空）',
      },
    )
    note = (res.value || '').trim()
  } catch {
    return
  }
  try {
    await reviewWallMessage(row.id, { action: 'hide', note: note || undefined })
    ElMessage.success('已下架')
    loadMine()
    loadKpi()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/** 我的留言：hidden → 管理员本人重新上架（调 admin review approve） */
async function handleMyRestore(row) {
  try {
    await ElMessageBox.confirm('确认将这条留言重新展示到公开墙吗？', '重新展示', {
      type: 'info',
      confirmButtonText: '重新展示',
      cancelButtonText: '返回',
    })
  } catch {
    return
  }
  try {
    await reviewWallMessage(row.id, { action: 'approve' })
    ElMessage.success('已重新展示')
    loadMine()
    loadKpi()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

const pageSub = computed(
  () => `待审核 ${kpi.pending} 条 · 已通过 ${kpi.approved} 条 · 已驳回 ${kpi.rejected} 条 · 已取消 ${kpi.hidden} 条`,
)

/** 是否为校友侧角色（与 router/index.js 的 ALUMNI_ROLES 对齐） */
const ALUMNI_ROLES = ['alumni', 'student']
const isAlumniRole = computed(() => ALUMNI_ROLES.includes(userStore.user?.role))

/** 跳转门户留言墙 */
function goPortal() {
  // 与 vite.config.ts 的 portal 代理保持一致；本机默认 5175
  const portalBase = (import.meta.env.VITE_PORTAL_BASE || 'http://localhost:5175/wall')
  window.open(portalBase, '_blank', 'noopener')
}

onMounted(() => {
  loadList()
  loadKpi()
  loadMine()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="eyebrow">留言墙审核</div>
        <h1 class="page-title">留言墙审核</h1>
        <p class="page-sub">{{ pageSub }}。通过 / 取消展示 / 删除即时生效，门户刷新后可见。</p>
      </div>
    </div>

    <!-- KPI -->
    <div class="kpis">
      <div class="kpi warn">
        <div class="k-label">待审核</div>
        <div class="k-val">{{ kpi.pending }}</div>
      </div>
      <div class="kpi gold">
        <div class="k-label">已通过</div>
        <div class="k-val">{{ kpi.approved }}</div>
      </div>
      <div class="kpi">
        <div class="k-label">已驳回</div>
        <div class="k-val">{{ kpi.rejected }}</div>
      </div>
      <div class="kpi muted">
        <div class="k-label">已取消</div>
        <div class="k-val">{{ kpi.hidden }}</div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="chips">
        <button
          v-for="(meta, key) in STATUS_META"
          :key="key"
          class="chip"
          :class="{ active: filter.status === key }"
          @click="pickStatus(key)"
        >
          {{ meta.text }}
        </button>
      </div>
      <div class="toolbar-spacer"></div>
      <el-input
        v-model="filter.keyword"
        placeholder="搜索正文 / 留言人"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button :icon="Refresh" @click="handleSearch">刷新</el-button>
    </div>

    <!-- 列表 -->
    <div class="list-card" v-loading="loading">
      <div class="wall-row head">
        <div>留言内容</div>
        <div class="col-hide">留言人</div>
        <div>状态</div>
        <div class="col-hide">时间</div>
        <div class="col-actions">操作</div>
      </div>

      <template v-if="list.length">
        <div v-for="row in list" :key="row.id" class="wall-row">
          <div class="col-main">
            <div class="content">{{ row.content }}</div>
            <div class="cell-meta">#{{ row.id }}
              <el-tag v-if="row.is_pinned" size="small" type="warning" effect="plain" class="ml-8">置顶</el-tag>
            </div>
          </div>
          <div class="cell col-hide">
            {{ row.name || '—' }}
            <div class="cell-meta">{{ row.student_id || '' }}</div>
          </div>
          <div>
            <el-tag :type="statusOf(row.status).type" effect="light">{{ statusOf(row.status).text }}</el-tag>
            <div v-if="row.review_note" class="cell-meta">原因：{{ row.review_note }}</div>
          </div>
          <div class="cell col-hide">
            {{ formatTime(row.created_at) }}
            <div v-if="row.reviewed_at" class="cell-meta">审核于 {{ formatTime(row.reviewed_at) }}</div>
          </div>
          <div class="row-actions" @click.stop>
            <template v-if="row.status === 'pending'">
              <button class="act-link act-approve" @click="handleApprove(row)">通过</button>
              <button class="act-link act-reject" @click="handleReject(row)">驳回</button>
            </template>
            <template v-else-if="row.status === 'approved'">
              <button class="act-link" @click="handlePin(row)">{{ row.is_pinned ? '取消置顶' : '置顶' }}</button>
              <button class="act-link" @click="handleHide(row)">取消展示</button>
              <button class="act-link act-reject" @click="handleDelete(row)">删除</button>
            </template>
            <template v-else-if="row.status === 'hidden'">
              <button class="act-link act-approve" @click="handleRestore(row)">重新展示</button>
              <button class="act-link" @click="handlePin(row)">{{ row.is_pinned ? '取消置顶' : '置顶' }}</button>
              <button class="act-link act-reject" @click="handleDelete(row)">删除</button>
            </template>
            <template v-else>
              <button class="act-link act-reject" @click="handleDelete(row)">删除</button>
            </template>
            <button
              v-if="row.status !== 'approved'"
              class="act-link"
              :title="row.user_id ? '禁言该留言人' : '无用户信息'"
              :disabled="!row.user_id"
              @click="handleMute(row)"
            >禁言</button>
          </div>
        </div>
      </template>

      <div v-else-if="!loading" class="empty">
        <div class="ico"><LucideIcon name="message-circle" :size="30" /></div>
        <h4>{{ filter.status === 'pending' ? '没有待审核的留言' : '暂无记录' }}</h4>
        <p>校友在门户首页留言墙提交的留言会进入这里等待审核。</p>
      </div>

      <div v-if="total > pageSize" class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadList"
        />
      </div>
    </div>

    <!-- ==================== 我的留言 ==================== -->
    <div class="my-section">
      <div class="my-head">
        <div class="my-title">
          <LucideIcon name="user" :size="18" />
          <span>我的留言</span>
          <span class="my-count">{{ myTotal }}</span>
        </div>
        <div class="my-tools">
          <el-checkbox v-model="myShowAll" @change="loadMine">显示全部状态</el-checkbox>
          <el-button text :icon="Refresh" @click="loadMine">刷新</el-button>
        </div>
      </div>

      <div class="list-card my-card" v-loading="myLoading">
        <template v-if="myList.length">
          <div v-for="row in myList" :key="row.id" class="wall-row my-row">
            <div class="col-main">
              <div class="content" :class="{ deleted: row.deleted_at }">{{ row.content }}</div>
              <div class="cell-meta">
                #{{ row.id }}
                <span v-if="row.deleted_at" class="my-tag my-tag-del">已撤回 · {{ formatTime(row.deleted_at) }}</span>
                <span v-else-if="row.show_name" class="my-tag my-tag-show">显示姓名</span>
                <span v-else class="my-tag my-tag-anon">匿名</span>
              </div>
            </div>
            <div class="cell">
              <el-tag
                v-if="!row.deleted_at"
                :type="statusOf(row.status).type"
                effect="light"
                size="small"
              >{{ statusOf(row.status).text }}</el-tag>
              <div v-if="row.review_note && !row.deleted_at" class="cell-meta">原因：{{ row.review_note }}</div>
            </div>
            <div class="cell col-hide">
              {{ formatTime(row.created_at) }}
              <div v-if="row.reviewed_at && !row.deleted_at" class="cell-meta">审核于 {{ formatTime(row.reviewed_at) }}</div>
            </div>
            <div class="row-actions" @click.stop>
              <!-- 已撤回：仅可「重新发送」（基于原 content 重建 pending） -->
              <template v-if="row.deleted_at">
                <button class="act-link act-approve" @click="handleMyResubmit(row)">重新发送</button>
              </template>
              <!-- pending / rejected：可编辑（重新审核）或撤回（软删） -->
              <template v-else-if="row.status === 'pending' || row.status === 'rejected'">
                <button class="act-link" @click="openEditDialog(row)">编辑</button>
                <button class="act-link act-reject" @click="handleMyRemove(row)">撤回</button>
              </template>
              <!-- approved：管理员本人可「下架」 -->
              <template v-else-if="row.status === 'approved'">
                <button class="act-link" @click="handleMyHide(row)">取消展示</button>
              </template>
              <!-- hidden：管理员本人可「重新展示」或软删 -->
              <template v-else-if="row.status === 'hidden'">
                <button class="act-link act-approve" @click="handleMyRestore(row)">重新展示</button>
                <button class="act-link act-reject" @click="handleMyRemove(row)">撤回</button>
              </template>
              <!-- 兜底 -->
              <template v-else>
                <span class="cell-meta">该留言已进入展示流程，需联系管理员调整</span>
              </template>
            </div>
          </div>
        </template>

        <div v-else-if="!myLoading" class="empty">
          <div class="ico"><LucideIcon name="edit-3" :size="28" /></div>
          <h4>还没有留言</h4>
          <p v-if="isAlumniRole">前往门户首页的留言墙，提交你的第一条留言。</p>
          <template v-else>
            <p>作为管理员，你的留言会出现在这里。</p>
            <p class="muted">提示：去门户 H5「校友留言墙」页面，登录后即可发言。</p>
            <el-button type="primary" plain size="small" class="mt-12" @click="goPortal">
              <LucideIcon name="external-link" :size="14" />
              <span class="ml-4">打开门户留言墙</span>
            </el-button>
          </template>
        </div>
      </div>
    </div>

    <!-- 编辑留言弹窗 -->
    <el-dialog
      v-model="editDialogOpen"
      title="编辑留言"
      width="520px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="edit-form">
        <el-input
          v-model="editForm.content"
          type="textarea"
          :rows="5"
          :maxlength="120"
          show-word-limit
          placeholder="请输入留言内容（1~120 字）"
        />
        <el-checkbox v-model="editForm.show_name" class="mt-12">在公开墙显示真实姓名</el-checkbox>
        <p class="hint">保存后该留言将重置为「待审核」，需要管理员重新审核。</p>
      </div>
      <template #footer>
        <el-button @click="editDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="submittingEdit" @click="submitEdit">保存并提交审核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
@import '../shared/list-shared.css';

.kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.kpi {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: 20px;
  position: relative;
  overflow: hidden;
  transition: all 0.18s ease;
}

.kpi::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--brand-500);
}

.kpi.gold::before {
  background: var(--green-500);
}

.kpi.warn::before {
  background: var(--amber-500);
}

.kpi.muted::before {
  background: var(--text-sub, #909399);
}

.kpi .k-label {
  font-size: 13px;
  color: var(--text-sub);
}

.kpi .k-val {
  font-family: var(--font-display);
  font-size: 36px;
  font-weight: 700;
  margin-top: 8px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.wall-row {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 170px;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--line);
  transition: background 0.12s;
}

.wall-row:hover {
  background: var(--surface-2);
}

.wall-row.head {
  background: var(--surface-2);
  font-size: 12px;
  font-weight: 700;
  color: var(--text-sub);
  letter-spacing: 0.5px;
  border-bottom: 1px solid var(--line-strong);
}

.wall-row:last-child {
  border-bottom: none;
}

.content {
  font-size: 13.5px;
  color: var(--text-main);
  line-height: 1.6;
  word-break: break-word;
}

.content.deleted {
  text-decoration: line-through;
  color: var(--text-sub);
}

.act-link:disabled {
  color: var(--text-sub);
  opacity: 0.5;
  cursor: not-allowed;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 20px;
}

/* ----- 我的留言 ----- */
.my-section {
  margin-top: 32px;
}

.my-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding: 0 4px;
}

.my-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  font-size: 16px;
  color: var(--text-main);
}

.my-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--brand-50, #f0f6ff);
  color: var(--brand-500);
  font-size: 12px;
  font-weight: 600;
}

.my-tools {
  display: flex;
  align-items: center;
  gap: 12px;
}

.my-card {
  margin-top: 0;
}

.my-row {
  grid-template-columns: 2.4fr 1fr 1fr 200px;
}

.my-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  margin-left: 8px;
  border: 1px solid;
}

.my-tag-show {
  color: var(--brand-500);
  border-color: var(--brand-200, #c8d8ff);
  background: var(--brand-50, #f0f6ff);
}

.my-tag-anon {
  color: var(--text-sub);
  border-color: var(--line);
  background: var(--surface-2);
}

.my-tag-del {
  color: #d33;
  border-color: #f4c0c0;
  background: #fff2f2;
}

.edit-form .hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--text-sub);
}

@media (max-width: 1080px) {
  .wall-row {
    grid-template-columns: 2fr 1fr 160px;
  }
  .wall-row .col-hide,
  .wall-row.head .col-hide {
    display: none;
  }
  .my-row {
    grid-template-columns: 2fr 1fr 160px;
  }
  .my-row .col-hide {
    display: none;
  }
}
</style>
