<script setup>
/**
 * 校级荣誉管理（/honors-admin · 仅 school_admin / system_admin）
 * - GET /admin/honors —— 校级荣誉列表（含学院隔离、level、has_red_doc 筛选）
 * - GET /admin/honors/export —— 导出 xlsx
 * - GET /admin/honors/import/template —— 下载导入模板
 * - POST /admin/honors/import —— 上传导入
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Upload, Search, Refresh } from '@element-plus/icons-vue'
import {
  getAdminHonors,
  exportAdminHonors,
  downloadAdminHonorTemplate,
  importAdminHonors,
  HONOR_LEVELS,
} from '@/api/honor'
import { getDict, dictLabel } from '@/api/dict'
import { formatTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import { readCollegeIdFromQuery } from '@/utils/honorMenu'
import { useRoute } from 'vue-router'
import LucideIcon from '@/components/LucideIcon.vue'

const userStore = useUserStore()
const route = useRoute()
const role = computed(() => userStore.user?.role)
const collegeLocked = computed(() => role.value === 'college_admin' || role.value === 'counselor')

const loading = ref(false)
const importing = ref(false)

const filter = reactive({
  keyword: '',
  category: '',
  college_id: '',
  grad_year: '',
  level: '',
  has_red_doc: '',
})
const categories = ref([])
const colleges = ref([])
const gradYears = ref([])

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const collegePreset = ref(null)
const presetLabel = computed(() => {
  const id = collegePreset.value
  if (id == null) return ''
  return dictLabel('college', id) || `学院#${id}`
})

const LEVEL_OPTIONS = Object.entries(HONOR_LEVELS).map(([value, label]) => ({ label, value }))
const HAS_RED_DOC_OPTIONS = [
  { label: '全部', value: '' },
  { label: '有红头文件', value: 'true' },
  { label: '无红头文件', value: 'false' },
]

async function loadDicts() {
  try {
    ;[categories.value, colleges.value, gradYears.value] = await Promise.all([
      getDict('honor_category'), getDict('college'), getDict('grad_year'),
    ])
  } catch { /* 静默 */ }
}

async function loadList() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value }
    if (filter.keyword) params.keyword = filter.keyword.trim()
    if (filter.category) params.category = filter.category
    if (filter.college_id) params.college_id = Number(filter.college_id)
    if (filter.grad_year) params.grad_year = filter.grad_year
    if (filter.level) params.level = filter.level
    if (filter.has_red_doc) params.has_red_doc = filter.has_red_doc
    const res = await getAdminHonors(params)
    list.value = (res.data?.list || []).slice().sort((a, b) => b.id - a.id)
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '列表加载失败')
  } finally {
    loading.value = false
  }
}

function applyCollegePreset() {
  const fromQuery = readCollegeIdFromQuery(route.query)
  if (collegeLocked.value && userStore.user?.college_id) {
    collegePreset.value = null
    filter.college_id = String(userStore.user.college_id)
    return
  }
  collegePreset.value = fromQuery
  filter.college_id = fromQuery != null ? String(fromQuery) : ''
}

function handleSearch() { page.value = 1; loadList() }
function handleReset() {
  Object.assign(filter, { keyword: '', category: '', college_id: '', grad_year: '', level: '', has_red_doc: '' })
  if (collegeLocked.value && userStore.user?.college_id) filter.college_id = String(userStore.user.college_id)
  handleSearch()
}

function levelOf(v) { return LEVEL_OPTIONS.find((o) => o.value === v)?.label || v }
function categoryOf(v) { return categories.value.find((c) => c.value === v)?.label || v }

async function handleExport() {
  try {
    const params = {}
    if (filter.keyword) params.keyword = filter.keyword.trim()
    if (filter.college_id) params.college_id = filter.college_id
    if (filter.grad_year) params.grad_year = filter.grad_year
    if (filter.level) params.level = filter.level
    const blob = await exportAdminHonors(params)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `荣誉列表_${new Date().toISOString().slice(0, 10)}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已下载')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  }
}

async function handleDownloadTemplate() {
  try {
    const blob = await downloadAdminHonorTemplate()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '荣誉导入模板.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(e.message || '模板下载失败')
  }
}

async function handleImport(file) {
  importing.value = true
  try {
    const res = await importAdminHonors(file)
    const d = res.data || {}
    const failed = d.failed ?? 0
    ElMessage[failed > 0 ? 'warning' : 'success'](
      `导入完成：共 ${d.total ?? 0} 条 / 成功 ${d.success ?? 0} / 失败 ${failed}`,
    )
    await loadList()
  } catch (e) {
    ElMessage.error(e.message || '导入失败')
  } finally {
    importing.value = false
  }
  return false
}

onMounted(async () => {
  applyCollegePreset()
  await loadDicts()
  await loadList()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="eyebrow">校级荣誉管理</div>
        <h1 class="page-title">校级荣誉管理</h1>
        <p class="page-sub">
          按学院隔离查看荣誉全量数据，支持导出 xlsx、下载导入模板、批量导入（成功行即时入库，失败行不影响整体）。
        </p>
      </div>
      <div class="head-actions">
        <el-button :icon="Download" @click="handleExport">导出 Excel</el-button>
        <el-button :icon="Download" @click="handleDownloadTemplate">下载模板</el-button>
        <el-upload
          accept=".xlsx,.xls"
          :show-file-list="false"
          :before-upload="handleImport"
          :disabled="importing"
        >
          <el-button type="primary" :icon="Upload" :loading="importing">导入 Excel</el-button>
        </el-upload>
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
      </div>
    </div>

    <div v-if="collegePreset != null" class="preset-banner">
      当前为【{{ presetLabel }}】学院子菜单视图
      <el-button size="small" link @click="collegePreset = null; filter.college_id = ''; handleSearch()">清除筛选</el-button>
    </div>

    <div class="filters">
      <el-input v-model="filter.keyword" placeholder="标题 / 学号 / 真实姓名" clearable style="width: 240px" @keyup.enter="handleSearch">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="filter.category" placeholder="类别" clearable style="width: 160px" @change="handleSearch">
        <el-option v-for="c in categories" :key="c.id" :label="c.label" :value="c.value" />
      </el-select>
      <el-select v-model="filter.college_id" placeholder="学院" clearable style="width: 180px" @change="handleSearch" :disabled="collegeLocked">
        <el-option v-for="c in colleges" :key="c.id" :label="c.label" :value="c.id" />
      </el-select>
      <el-select v-model="filter.grad_year" placeholder="届别" clearable style="width: 130px" @change="handleSearch">
        <el-option v-for="y in gradYears" :key="y.id" :label="y.label" :value="y.value" />
      </el-select>
      <el-select v-model="filter.level" placeholder="级别" clearable style="width: 130px" @change="handleSearch">
        <el-option v-for="o in LEVEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-select v-model="filter.has_red_doc" placeholder="红头文件" clearable style="width: 130px" @change="handleSearch">
        <el-option v-for="o in HAS_RED_DOC_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <div class="list-card" v-loading="loading">
      <div class="th head">
        <div>标题 / 级别</div>
        <div class="col-hide">学院 · 届别</div>
        <div class="col-hide">类别</div>
        <div class="col-hide">获奖人</div>
        <div class="col-hide">授予日期</div>
        <div>红头文件</div>
      </div>
      <template v-if="list.length">
        <div v-for="h in list" :key="h.id" class="th">
          <div class="col-main">
            <div class="cell-title">{{ h.title }}</div>
            <div class="cell-meta">
              <el-tag size="small" effect="plain">{{ levelOf(h.level) }}</el-tag>
            </div>
          </div>
          <div class="cell col-hide">
            {{ h.college || '—' }}
            <div class="cell-meta">{{ h.grad_year ? h.grad_year + ' 届' : '—' }}</div>
          </div>
          <div class="cell col-hide">{{ categoryOf(h.category) || '—' }}</div>
          <div class="cell col-hide">{{ h.created_by_name || h.real_name || '—' }}</div>
          <div class="cell col-hide">{{ h.awarded_at || '—' }}</div>
          <div>
            <el-tag v-if="h.red_doc_url" type="success" size="small" effect="light">已关联</el-tag>
            <el-tag v-else type="info" size="small" effect="light">无</el-tag>
          </div>
        </div>
      </template>
      <div v-else-if="!loading" class="empty">
        <div class="ico"><LucideIcon name="medal" :size="30" /></div>
        <h4>暂无校级荣誉数据</h4>
        <p>可通过"导入 Excel"批量录入，或等待导员/校友在档案中提交。</p>
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
.page-title { font-size: 22px; font-weight: 700; margin: 4px 0 6px; color: var(--text-main); }
.page-sub { font-size: 13px; color: var(--text-sub); margin: 0; max-width: 760px; line-height: 1.6; }
.head-actions { display: flex; gap: 8px; flex-wrap: wrap; }

.preset-banner {
  margin: 12px 0 8px;
  padding: 10px 16px;
  border-radius: 8px;
  background: var(--brand-50, #eef4ff);
  color: var(--brand-700, #1f497d);
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.filters {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 16px 0 14px;
  flex-wrap: wrap;
}

.th {
  display: grid;
  grid-template-columns: 2fr 1.4fr 1.2fr 1.2fr 1fr 1fr;
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

.pager {
  margin-top: 16px;
  justify-content: flex-end;
  display: flex;
}

@media (max-width: 960px) {
  .th {
    grid-template-columns: 1fr 1fr;
  }
  .th .col-hide,
  .th.head .col-hide { display: none; }
}
</style>
