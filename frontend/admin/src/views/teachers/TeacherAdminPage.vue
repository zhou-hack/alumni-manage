<script setup>
/**
 * 教师库管理（/teachers · 仅 school_admin / system_admin）
 * - 顶部"立即同步"按钮（仅 system_admin 可触发 POST /admin/teachers/sync）
 * - 中部筛选 + 教师列表（GET /admin/teachers）
 * - 底部同步记录（GET /admin/teachers/sync-logs）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, CircleCheck, Warning, Refresh, Search } from '@element-plus/icons-vue'
import { listTeachers, syncTeachers, listTeacherSyncLogs } from '@/api/teachers'
import { getDict } from '@/api/dict'
import { useUserStore } from '@/stores/user'
import { formatTime } from '@/utils/format'
import LucideIcon from '@/components/LucideIcon.vue'

const userStore = useUserStore()
const isSystemAdmin = computed(() => userStore.user?.role === 'system_admin')

const filter = reactive({
  college_id: '',
  major_id: '',
  status: '',
  keyword: '',
})
const colleges = ref([])
const majors = ref([])

const list = ref([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)

const logs = ref([])
const logsLoading = ref(false)

const STATUS_META = {
  active: { text: '在职', tone: 'success' },
  inactive: { text: '离职', tone: 'warning' },
}
function statusOf(s) { return STATUS_META[s] || { text: s || '—', tone: '' } }

async function loadColleges() {
  try { colleges.value = await getDict('college') } catch { /* 静默 */ }
}
async function loadMajors() {
  try { majors.value = await getDict('major') } catch { /* 静默 */ }
}

async function loadList() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      page_size: pageSize.value,
    }
    if (filter.college_id) params.college_id = filter.college_id
    if (filter.major_id) params.major_id = filter.major_id
    if (filter.status) params.status = filter.status
    if (filter.keyword) params.keyword = filter.keyword.trim()
    const res = await listTeachers(params)
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '教师列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadLogs() {
  logsLoading.value = true
  try {
    const res = await listTeacherSyncLogs({ page: 1, page_size: 20 })
    logs.value = res.data?.list || []
  } catch (e) {
    /* 静默 */
  } finally {
    logsLoading.value = false
  }
}

async function handleSync() {
  try {
    await ElMessageBox.confirm(
      '将按学号比对教师数据并触发同步，可能耗时数秒，确认继续？',
      '教师库同步',
      { type: 'info', confirmButtonText: '立即同步' },
    )
  } catch { return }
  try {
    const res = await syncTeachers()
    const d = res.data || {}
    ElMessage.success(
      `同步完成：总 ${d.total ?? 0} / 新增 ${d.inserted ?? 0} / 更新 ${d.updated ?? 0} / 跳过 ${d.skipped ?? 0} / 失败 ${d.failed ?? 0}`,
    )
    await loadList()
    await loadLogs()
  } catch (e) {
    ElMessage.error(e.message || '同步失败')
  }
}

function handleSearch() { page.value = 1; loadList() }
function handleReset() {
  Object.assign(filter, { college_id: '', major_id: '', status: '', keyword: '' })
  handleSearch()
}

function statusMeta(s) {
  if (s === 'success') return { text: '成功', type: 'success' }
  if (s === 'failed') return { text: '失败', type: 'danger' }
  if (s === 'partial') return { text: '部分成功', type: 'warning' }
  if (s === 'running') return { text: '运行中', type: 'info' }
  return { text: s || '—', type: '' }
}

onMounted(async () => {
  await Promise.all([loadColleges(), loadMajors()])
  await Promise.all([loadList(), loadLogs()])
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="eyebrow">教师档案</div>
        <h1 class="page-title">教师档案库</h1>
        <p class="page-sub">
          教师数据维护：列表检索 + 手动同步（仅系统管理员可触发）。同步操作按学号比对新增/更新/跳过。
        </p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        <el-button
          v-if="isSystemAdmin"
          type="primary"
          :icon="Connection"
          @click="handleSync"
        >
          立即同步
        </el-button>
      </div>
    </div>

    <div class="filters">
      <el-input
        v-model="filter.keyword"
        placeholder="搜索姓名 / 学号 / 工号"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="filter.college_id" placeholder="学院" clearable style="width: 180px" @change="handleSearch">
        <el-option v-for="c in colleges" :key="c.id" :label="c.label" :value="c.id" />
      </el-select>
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 140px" @change="handleSearch">
        <el-option label="在职" value="active" />
        <el-option label="离职" value="inactive" />
      </el-select>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <div class="list-card" v-loading="loading">
      <div class="th head">
        <div>姓名 / 工号</div>
        <div class="col-hide">学院 · 专业</div>
        <div class="col-hide">届别</div>
        <div>状态</div>
      </div>
      <template v-if="list.length">
        <div v-for="t in list" :key="t.id" class="th">
          <div class="col-main">
            <div class="cell-title">{{ t.real_name }}</div>
            <div class="cell-meta">{{ t.student_id || t.teacher_no || '—' }}</div>
          </div>
          <div class="cell col-hide">
            {{ t.college || '—' }}
            <div class="cell-meta">{{ t.major || '—' }}</div>
          </div>
          <div class="cell col-hide">{{ t.grad_year ? t.grad_year + ' 届' : '—' }}</div>
          <div>
            <el-tag :type="statusOf(t.status).tone" effect="light" size="small">
              {{ statusOf(t.status).text }}
            </el-tag>
          </div>
        </div>
      </template>
      <div v-else-if="!loading" class="empty">
        <div class="ico"><LucideIcon name="user-cog" :size="30" /></div>
        <h4>暂无教师档案</h4>
        <p>系统管理员点击右上角"立即同步"以拉取教师数据。</p>
      </div>
    </div>

    <el-pagination
      class="pager"
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50]"
      :total="total"
      layout="total, sizes, prev, pager, next, jumper"
      background
      @current-change="loadList"
      @size-change="loadList"
    />

    <h3 class="section-title">同步记录</h3>
    <div class="list-card mini" v-loading="logsLoading">
      <div class="th head">
        <div>触发时间</div>
        <div class="col-hide">结果</div>
        <div class="col-hide">数量统计</div>
        <div>操作人</div>
      </div>
      <template v-if="logs.length">
        <div v-for="l in logs" :key="l.id" class="th">
          <div class="col-main">
            <div class="cell-title">{{ formatTime(l.created_at) }}</div>
            <div class="cell-meta">{{ l.trigger || l.trigger_type || '—' }}</div>
          </div>
          <div class="cell col-hide">
            <el-tag :type="statusMeta(l.status).type" effect="light" size="small">
              {{ statusMeta(l.status).text }}
            </el-tag>
          </div>
          <div class="cell col-hide cell-ellipsis">
            总 {{ l.total ?? '—' }} / 新增 {{ l.inserted ?? 0 }} / 更新 {{ l.updated ?? 0 }} / 失败 {{ l.failed ?? 0 }}
          </div>
          <div class="cell">{{ l.operator_name || `UID ${l.operator_id || '—'}` }}</div>
        </div>
      </template>
      <div v-else-if="!logsLoading" class="empty">
        <div class="ico"><LucideIcon name="history" :size="28" /></div>
        <h4>暂无同步记录</h4>
        <p>系统管理员触发同步后将在此展示历史日志。</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
@import '../shared/list-shared.css';
.eyebrow {
  font-family: var(--font-mono, monospace);
  font-size: 11px;
  letter-spacing: 1.2px;
  color: var(--text-sub);
  text-transform: uppercase;
}
.page-title { font-size: 24px; font-weight: 700; margin: 4px 0 6px; color: var(--text-main); }
.page-sub { font-size: 13px; color: var(--text-sub); margin: 0; max-width: 720px; line-height: 1.6; }
.head-actions { display: flex; gap: 8px; }

.filters {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 16px 0 14px;
  flex-wrap: wrap;
}

.th {
  display: grid;
  grid-template-columns: 1.4fr 1.4fr 0.8fr 0.8fr;
  align-items: center;
  gap: 16px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--line);
}
.th.head {
  background: var(--surface-2);
  font-size: 12px;
  font-weight: 700;
  color: var(--text-sub);
  letter-spacing: 0.5px;
}
.th:last-child { border-bottom: none; }

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
  margin: 28px 0 12px;
}

.list-card.mini .th {
  grid-template-columns: 1.4fr 0.8fr 1.6fr 1fr;
  font-size: 12.5px;
  padding: 11px 18px;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
  display: flex;
}

@media (max-width: 860px) {
  .th {
    grid-template-columns: 1fr 1fr;
  }
  .th .col-hide,
  .th.head .col-hide { display: none; }
}
</style>
