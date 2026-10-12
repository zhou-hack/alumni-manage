<script setup>
/**
 * 审计日志页（M16 · 仅 system_admin）
 * - 多条件组合查询（操作人 / 动作 / 业务类型 / 时间范围）
 * - 时间倒序展示，详情抽屉展示 detail JSON
 * - 导出 xlsx：按当前查询条件
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Refresh, Search } from '@element-plus/icons-vue'
import { getAuditLogs, getAuditActions, exportAuditLogs } from '@/api/audit'
import { formatTime } from '@/utils/format'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

// 筛选
const filter = ref({
  user_id: null,
  action: '',
  biz_type: '',
  date_from: '',
  date_to: '',
})

// 动作字典
const actions = ref([])

async function loadActions() {
  try {
    const res = await getAuditActions()
    actions.value = res.data?.actions || res.data?.list || (Array.isArray(res.data) ? res.data : [])
  } catch {
    actions.value = []
  }
}

async function loadList() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value }
    if (filter.value.user_id) params.user_id = filter.value.user_id
    if (filter.value.action) params.action = filter.value.action
    if (filter.value.biz_type) params.biz_type = filter.value.biz_type
    if (filter.value.date_from) params.date_from = filter.value.date_from
    if (filter.value.date_to) params.date_to = filter.value.date_to
    const res = await getAuditLogs(params)
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error(e.message)
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  loadList()
}

function resetFilter() {
  filter.value = { user_id: null, action: '', biz_type: '', date_from: '', date_to: '' }
  onSearch()
}

/* ---------- 导出 ---------- */
async function handleExport() {
  try {
    await ElMessageBox.confirm('按当前筛选条件导出全部审计日志？', '导出审计日志', { type: 'info' })
  } catch {
    return
  }
  try {
    const params = {}
    if (filter.value.user_id) params.user_id = filter.value.user_id
    if (filter.value.action) params.action = filter.value.action
    if (filter.value.biz_type) params.biz_type = filter.value.biz_type
    if (filter.value.date_from) params.date_from = filter.value.date_from
    if (filter.value.date_to) params.date_to = filter.value.date_to
    const { blob, filename } = await exportAuditLogs(params)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  }
}

/* ---------- 详情抽屉 ---------- */
const drawerOpen = ref(false)
const active = ref(null)
function openDetail(row) {
  active.value = row
  drawerOpen.value = true
}

/* ---------- 派生 ---------- */

const hasFilter = computed(
  () =>
    filter.value.user_id ||
    filter.value.action ||
    filter.value.biz_type ||
    filter.value.date_from ||
    filter.value.date_to,
)

function actionLabel(a) {
  if (!a) return '-'
  const found = actions.value.find((x) => (typeof x === 'string' ? x === a : x.action === a))
  if (!found) return a
  return typeof found === 'string' ? found : found.label || a
}

const detailJson = computed(() => {
  if (!active.value?.detail) return ''
  try {
    return JSON.stringify(active.value.detail, null, 2)
  } catch {
    return String(active.value.detail)
  }
})

/** 详情字段表：扁平化对象 → [{ key, value }]，便于阅读 */
const detailEntries = computed(() => {
  if (!active.value?.detail) return []
  const d = active.value.detail
  if (typeof d !== 'object' || d == null) return []
  // 优先字段顺序
  const order = [
    'action', 'target_type', 'target_id', 'recipients', 'log_id',
    'attachment_id', 'title', 'reason', 'note',
    'student_id', 'advisor', 'mode', 'source', 'sync_type',
    'count', 'total', 'inserted', 'updated', 'skipped', 'failed',
    'failures', 'ip'
  ]
  const seen = new Set()
  const rows = []
  for (const k of order) {
    if (k in d && d[k] !== undefined) {
      rows.push({ key: k, value: d[k] })
      seen.add(k)
    }
  }
  for (const k of Object.keys(d)) {
    if (!seen.has(k)) rows.push({ key: k, value: d[k] })
  }
  return rows
})

function renderValue(v) {
  if (v == null) return '—'
  if (Array.isArray(v)) {
    if (v.length === 0) return '[]'
    if (v.every((x) => typeof x !== 'object')) return v.join(', ')
    return JSON.stringify(v)
  }
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}

onMounted(async () => {
  await loadActions()
  loadList()
})
</script>

<template>
  <div class="audit-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">审计日志</h2>
        <p class="page-sub">记录登录、资料修改、审核操作、账号停权、工作交接、密码修改等后台行为；仅系统管理员可见</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        <el-button type="primary" :icon="Download" @click="handleExport">导出</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input-number
        v-model="filter.user_id"
        placeholder="操作人 ID"
        :controls="false"
        :min="0"
        style="width: 140px"
        @keyup.enter="onSearch"
      />
      <el-select
        v-model="filter.action"
        placeholder="动作"
        clearable
        filterable
        style="width: 180px"
        @change="onSearch"
      >
        <el-option v-for="a in actions" :key="actionLabel(a)" :label="actionLabel(a)" :value="typeof a === 'string' ? a : a.action" />
      </el-select>
      <el-select
        v-model="filter.biz_type"
        placeholder="业务类型"
        clearable
        filterable
        style="width: 180px"
        @change="onSearch"
      >
        <el-option label="档案" value="archive" />
        <el-option label="荣誉" value="honor" />
        <el-option label="审核" value="review" />
        <el-option label="账号" value="account" />
        <el-option label="认证" value="auth" />
        <el-option label="照片" value="photo_collect" />
        <el-option label="组织架构" value="org_sync" />
        <el-option label="系统" value="system" />
      </el-select>
      <el-date-picker
        v-model="filter.date_from"
        type="datetime"
        placeholder="起始时间"
        style="width: 180px"
        @change="onSearch"
      />
      <el-date-picker
        v-model="filter.date_to"
        type="datetime"
        placeholder="结束时间"
        style="width: 180px"
        @change="onSearch"
      />
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button v-if="hasFilter" text @click="resetFilter">重置</el-button>
    </div>

    <el-table :data="list" v-loading="loading" class="audit-table" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="操作人" width="120">
        <template #default="{ row }">
          <span class="user-id">#{{ row.user_id }}</span>
          <el-tag v-if="row.user_role" size="small" effect="plain" class="role-tag">{{ row.user_role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="动作" min-width="140">
        <template #default="{ row }">
          <span class="action-text">{{ actionLabel(row.action) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="业务" width="180">
        <template #default="{ row }">
          <span v-if="row.biz_type" class="biz-text">{{ row.biz_type }}#{{ row.biz_id ?? '-' }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="IP" width="130">
        <template #default="{ row }">
          <span class="mono">{{ row.ip || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        :total="total"
        :page-size="pageSize"
        layout="total, prev, pager, next, sizes"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="loadList"
        @size-change="(v) => { pageSize = v; loadList() }"
      />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerOpen" direction="rtl" size="520px" title="日志详情">
      <template v-if="active">
        <div class="detail">
          <div class="detail-row">
            <div class="k">记录 ID</div>
            <div class="v">#{{ active.id }}</div>
          </div>
          <div class="detail-row">
            <div class="k">操作人</div>
            <div class="v">#{{ active.user_id }}（{{ active.user_role || '-' }}）</div>
          </div>
          <div class="detail-row">
            <div class="k">动作</div>
            <div class="v">{{ actionLabel(active.action) }}</div>
          </div>
          <div class="detail-row">
            <div class="k">业务</div>
            <div class="v">{{ active.biz_type || '-' }}<span v-if="active.biz_id">#{{ active.biz_id }}</span></div>
          </div>
          <div class="detail-row">
            <div class="k">IP</div>
            <div class="v mono">{{ active.ip || '—' }}</div>
          </div>
          <div class="detail-row">
            <div class="k">时间</div>
            <div class="v">{{ formatTime(active.created_at) }}</div>
          </div>
          <div class="detail-row col">
            <div class="k">详情</div>
            <el-table
              v-if="detailEntries.length"
              :data="detailEntries"
              size="small"
              class="detail-table"
              :show-header="true"
            >
              <el-table-column label="字段" width="140">
                <template #default="{ row }">
                  <span class="k-field">{{ row.key }}</span>
                </template>
              </el-table-column>
              <el-table-column label="值">
                <template #default="{ row }">
                  <span class="v-field mono">{{ renderValue(row.value) }}</span>
                </template>
              </el-table-column>
            </el-table>
            <pre v-else-if="detailJson" class="json">{{ detailJson }}</pre>
            <span v-else class="muted">无</span>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.audit-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}
.page-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-sub);
}
.head-actions {
  display: flex;
  gap: 8px;
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.audit-table {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  overflow: hidden;
}
.user-id {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
.role-tag {
  margin-left: 6px;
}
.action-text {
  font-weight: 600;
}
.biz-text {
  font-size: 12px;
  color: var(--text-sub);
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
.muted {
  color: var(--text-sub);
}
.pager {
  display: flex;
  justify-content: flex-end;
}

/* 抽屉 */
.detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 4px 12px;
}
.detail-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.detail-row.col {
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
}
.k {
  width: 64px;
  flex: none;
  font-size: 12px;
  color: var(--text-sub);
}
.v {
  font-size: 14px;
  word-break: break-word;
}
.json {
  margin: 0;
  padding: 12px;
  background: rgba(15, 23, 42, 0.04);
  border-radius: 8px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 360px;
  overflow: auto;
}
.detail-table {
  border-radius: 8px;
}
.k-field {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: var(--text-sub);
}
.v-field {
  font-size: 13px;
  word-break: break-all;
}
</style>