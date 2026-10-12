<script setup>
/**
 * 荣誉管理页（管理端检索，M03）
 * 按类别 / 学院 / 届别 / 级别 / 状态 / 关键词检索（任务清单 4.M03 完成标志）
 * 角色适配（PRD M06-01）：院级管理员/导员仅可见本院荣誉（后端同样强制过滤）
 */
import { computed, ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, View, EditPen, VideoPlay, VideoPause, Tickets, UserFilled } from '@element-plus/icons-vue'
import {
  getHonors,
  getHonor,
  HONOR_LEVELS,
  setHonorScreenStatus,
  batchHonorScreenStatus,
  listHonorScreenLogs,
} from '@/api/honor'
import { getScreenStats } from '@/api/screen'
import { getDict, dictLabel } from '@/api/dict'
import { statusOf, formatTime } from '@/utils/format'
import { readCollegeIdFromQuery } from '@/utils/honorMenu'
import { useUserStore } from '@/stores/user'
import AttachmentPreviewer from '@/components/AttachmentPreviewer.vue'
import AlumniProfileEditDialog from '@/components/AlumniProfileEditDialog.vue'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const role = computed(() => userStore.user?.role)
const collegeLocked = computed(() => role.value === 'college_admin' || role.value === 'counselor')

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

/** 表格多选（仅当用户勾选时才参与批量上下架） */
const selectedRows = ref([])

const filter = reactive({
  keyword: '',
  category: '',
  college_id: '',
  grad_year: '',
  level: '',
  status: '',
  screen_status: '',
})

/** 学院预筛上下文：来自路由 ?college_id=<id>（校级/系统管理员按学院子菜单进入） */
const collegePreset = ref(null)
const presetLabel = computed(() => {
  const id = collegePreset.value
  if (id == null) return ''
  return dictLabel('college', id) || `学院#${id}`
})
/** 是否处于学院预筛模式（用于顶部 Banner 提示 + 清除按钮） */
const presetActive = computed(() => collegePreset.value != null)

const categories = ref([])
const colleges = ref([])
const gradYears = ref([])

const LEVEL_OPTIONS = Object.entries(HONOR_LEVELS).map(([value, label]) => ({ label, value }))

/**
 * 荣誉状态枚举（与后端 honor.status 对齐）
 * - pending_counselor：M34 新增，导员初审（替代旧的 pending_first 的"待初审"语义）
 * - pending_first：保留旧数据兼容（历史记录被院系管理员初审后的状态）
 * - pending_second：待院系/校级复核
 * - approved / rejected：终态
 * 注：filter 仅下拉展示前端可见的语义，搜索时若带入非法值会被忽略。
 */
const STATUS_OPTIONS = [
  { label: '待初审（导员）', value: 'pending_counselor' },
  { label: '待院系初审', value: 'pending_first' },
  { label: '待校级复核', value: 'pending_second' },
  { label: '已通过', value: 'approved' },
  { label: '已驳回', value: 'rejected' },
]

/** [P1/M17] 大屏上下架状态筛选（在 backend admin 接口中独立于 status） */
const SCREEN_STATUS_OPTIONS = [
  { label: '已上架', value: 'online', tag: 'success' },
  { label: '已下架', value: 'offline', tag: 'info' },
]
const screenStatusOf = (v) =>
  SCREEN_STATUS_OPTIONS.find((s) => s.value === v) || { label: v, tag: '' }

/* ==================== 校友风采/座右铭录入（闭环 V18 alumni_profile） ==================== */
/* 1) 操作列里点「风采」→ 弹窗：管理员为该荣誉获得者补全照片/简介/座右铭等
   2) 数据写入 alumni_base 5 个新字段 → HonorScreenService.loadAlumni 读取 → 大屏展示 */
const profileDialogVisible = ref(false)
const profileTargetHonorId = ref(null)
function openProfileDialog(row) {
  profileTargetHonorId.value = row.id
  profileDialogVisible.value = true
}
function onProfileSaved() {
  // 头像/座右铭变化后大屏预览会刷新，无需刷新列表（owner_name 等不变）
  ElMessage.success('校友风采已更新，大屏稍后将展示新内容')
}

const categoryText = (v) => categories.value.find((c) => c.value === v)?.label || v

async function loadDicts() {
  try {
    ;[categories.value, colleges.value, gradYears.value] = await Promise.all([
      getDict('honor_category'),
      getDict('college'),
      getDict('grad_year'),
    ])
  } catch {
    // 字典加载失败不阻塞列表
  }
}

/**
 * 同步路由 ?college_id=<id> 到筛选条件 + 触发列表刷新。
 * 校级/系统管理员走「荣誉管理 / 学院荣誉」子菜单进入时，列表自动锁定该学院。
 * 院级管理员/导员仍由 collegeLocked 强锁本院，预筛以 collegeLocked 为优先。
 */
function applyCollegePreset() {
  const fromQuery = readCollegeIdFromQuery(route.query)
  // 院级管理员/导员：忽略预筛，按本院锁定
  if (collegeLocked.value && userStore.user?.college_id) {
    collegePreset.value = null
    filter.college_id = String(userStore.user.college_id)
    return
  }
  collegePreset.value = fromQuery
  filter.college_id = fromQuery != null ? String(fromQuery) : ''
}
function clearCollegePreset() {
  router.replace({ path: '/honors', query: {} })
}

// 路由 query 变化时重新应用预筛
watch(() => route.query.college_id, () => {
  applyCollegePreset()
  page.value = 1
  loadList()
})

/** 状态值合法集合：避免历史脏数据/下拉旧值导致后端返回 0 条 */
const VALID_STATUS = new Set(STATUS_OPTIONS.map((s) => s.value))

async function loadList() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value }
    if (filter.keyword) params.keyword = filter.keyword.trim()
    if (filter.category) params.category = filter.category
    if (filter.college_id) params.college_id = Number(filter.college_id)
    if (filter.grad_year) params.grad_year = filter.grad_year
    if (filter.level) params.level = filter.level
    // 状态：仅当下拉选中的值属于合法集合时才下发；非法值一律忽略，避免"再次进入为空"
    if (filter.status && VALID_STATUS.has(filter.status)) {
      params.status = filter.status
    } else if (filter.status) {
      // 自愈：UI 残留了非法状态值时静默清空（不弹错），下次搜索即生效
      filter.status = ''
    }
    // 大屏上下架状态（M17）：直传 online/offline；非法值后端校验 400
    if (filter.screen_status) params.screen_status = filter.screen_status
    // 来自 Tab 2 校友荣誉长廊跳转的 honor_id 定位
    const presetHonorId = readHonorIdFromQuery(route.query)
    if (presetHonorId) params.honor_id = presetHonorId
    const res = await getHonors(params)
    // 默认按 ID 升序（最新提交的在末尾，与档案列表视觉一致）
    list.value = (res.data.list || []).slice().sort((a, b) => a.id - b.id)
    total.value = res.data.total || 0
  } catch (e) {
    console.error('[HonorListPage] loadList failed:', e)
    ElMessage.error(e?.message || '荣誉列表加载失败')
  } finally {
    loading.value = false
  }
}

/** 解析路由 ?honor_id=<id>（来自 HonorScreenPreviewPage 跳转定位） */
function readHonorIdFromQuery(q) {
  const raw = q?.honor_id
  if (raw == null || raw === '') return null
  const n = Number(raw)
  return Number.isFinite(n) && n > 0 ? n : null
}

function handleSearch() {
  page.value = 1
  loadList()
}

function handleReset() {
  Object.assign(filter, { keyword: '', category: '', college_id: '', grad_year: '', level: '', status: '', screen_status: '' })
  if (collegeLocked.value && userStore.user?.college_id) {
    filter.college_id = String(userStore.user.college_id)
  }
  handleSearch()
}

// ---------- 详情抽屉 ----------
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

async function showDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const res = await getHonor(row.id)
    detail.value = res.data
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    detailLoading.value = false
  }
}

/** 附件删除后：本地同步移除并刷新列表 */
function onAttachmentDeleted(att) {
  if (detail.value?.attachments) {
    detail.value.attachments = detail.value.attachments.filter((x) => x.id !== att.id)
  }
  loadList()
}

/* ==================== [P1/M17] 上下架操作 ==================== */

/** 单条上下架（行内按钮触发）：异步、不阻塞列表 */
async function toggleScreenStatus(row) {
  const next = row.screen_status === 'online' ? 'offline' : 'online'
  const label = next === 'online' ? '上架' : '下架'
  try {
    await ElMessageBox.confirm(
      `确认将【${row.title}】${label}到大屏吗？`,
      `${label}确认`,
      { type: 'warning', confirmButtonText: label, cancelButtonText: '取消' },
    )
  } catch {
    return // 用户取消
  }
  try {
    const res = await setHonorScreenStatus(row.id, { status: next, reason: `${label}（行内操作）` })
    const { applied, skipped } = res.data || {}
    if (applied === 0 && skipped === 1) {
      ElMessage.warning('该条已经是目标状态，无需重复操作')
    } else {
      ElMessage.success(`${label}成功`)
    }
    loadList()
  } catch (e) {
    ElMessage.error(e?.message || `${label}失败`)
  }
}

/** 批量上下架：基于 selectedRows，仅在线/离线异化存在时才记 log */
async function batchToggleScreenStatus(target) {
  const rows = selectedRows.value || []
  if (rows.length === 0) {
    ElMessage.warning('请先勾选要操作的荣誉（可多选）')
    return
  }
  const ids = rows.map((r) => r.id)
  const label = target === 'online' ? '批量上架' : '批量下架'
  try {
    await ElMessageBox.confirm(
      `将对已勾选的 ${ids.length} 条荣誉执行${label},确认继续吗?`,
      `${label}确认`,
      { type: 'warning', confirmButtonText: label, cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const res = await batchHonorScreenStatus({
      ids,
      status: target,
      reason: `${label}（批量操作）`,
    })
    const d = res.data || {}
    const msg =
      `请求 ${d.requested} 条;已变更 ${d.applied};已是目标态 ${d.skipped};失败 ${d.failed}`
    ElMessage.success(msg)
    selectedRows.value = []
    loadList()
  } catch (e) {
    ElMessage.error(e?.message || `${label}失败`)
  }
}

/** 表格 selection 变更（保持 id 引用,避免后续 row 引用丢失） */
function onSelectionChange(rows) {
  selectedRows.value = rows
}

/* ==================== [P1/M17] 上下架变更日志 ==================== */

const logsDialogVisible = ref(false)
const logsLoading = ref(false)
const logs = ref([])
const logsTotal = ref(0)
const logsPage = ref(1)
const logsPageSize = ref(10)

const LOG_ACTION_OPTIONS = [
  { label: '全部', value: '' },
  { label: '单条上架', value: 'online' },
  { label: '单条下架', value: 'offline' },
  { label: '批量上架', value: 'batch_online' },
  { label: '批量下架', value: 'batch_offline' },
]
const logActionOf = (v) => LOG_ACTION_OPTIONS.find((x) => x.value === v) || { label: v }

async function openLogsDialog() {
  logsDialogVisible.value = true
  logsPage.value = 1
  await loadLogs()
}
async function loadLogs() {
  logsLoading.value = true
  try {
    const res = await listHonorScreenLogs({ page: logsPage.value, page_size: logsPageSize.value })
    logs.value = res.data.list || []
    logsTotal.value = res.data.total || 0
  } catch (e) {
    ElMessage.error(e?.message || '日志加载失败')
  } finally {
    logsLoading.value = false
  }
}

/**
 * 进入页面时把与"列表范围/筛选"无关的脏状态清掉，避免"再次进入为空"
 * （典型场景：上次会话残留了 status=pending_second 之类返回 0 条的旧枚举值）
 * 注：filter.college_id 由 applyCollegePreset() 根据路由 ?college_id 重置，无需在此处处理。
 */
function resetStaleFilter() {
  filter.keyword = ''
  filter.category = ''
  filter.grad_year = ''
  filter.level = ''
  filter.status = ''
  filter.screen_status = ''
}

onMounted(async () => {
  // 0) 先把非路由来源的脏筛选条件清空（保险丝）
  resetStaleFilter()
  // 1) 把路由 ?college_id 应用到 filter
  applyCollegePreset()
  // 2) 加载字典
  await loadDicts()
  // 3) 字典就绪后再次同步（dictLabel 才能取到 label）
  applyCollegePreset()
  // 4) 加载列表 + 分布迷你数据
  loadList()
  loadDistMini()
})

/* ==================== Tab 1 头部 · 荣誉类型分布迷你卡（驱动首页迷你概览） ==================== */
const distMini = ref(null)
async function loadDistMini() {
  try {
    const res = await getScreenStats({ top_n: 4 })
    distMini.value = res.data || res
  } catch {
    /* 分布数据加载失败不阻塞主列表 */
  }
}
function ratioOf(max, v) {
  if (!max) return 0
  return Math.max(0, Math.min(100, (v / max) * 100))
}
const distMaxLevel = computed(() => {
  const list = distMini.value?.level_dist?.list || []
  return list.reduce((m, x) => Math.max(m, x.count || 0), 0)
})
const distMaxCategory = computed(() => {
  const list = distMini.value?.category_dist?.list || []
  return list.reduce((m, x) => Math.max(m, x.count || 0), 0)
})
const PIE_PALETTE = ['#3D7EBB', '#5f95c6', '#7BB0E0', '#5A9A8F']
function pieColor(i) { return PIE_PALETTE[i % PIE_PALETTE.length] }
</script>

<template>
  <div>
    <!-- 学院预筛 Banner：仅校级/系统管理员从荣誉管理·学院子菜单进入时显示 -->
    <el-alert
      v-if="presetActive"
      type="info"
      :closable="false"
      show-icon
      class="preset-banner"
    >
      <template #title>
        <span>当前按学院预筛：<b>{{ presetLabel }}</b></span>
        <span class="preset-meta">共 {{ total }} 条</span>
      </template>
      <template #default>
        <el-button link type="primary" @click="clearCollegePreset">查看全部荣誉</el-button>
      </template>
    </el-alert>

    <!-- 顶部 · 荣誉类型分布迷你卡（与下方列表一起作为「荣誉管理」总览） -->
    <section class="dist-mini-row" v-if="distMini">
      <div class="mini-card">
        <h4>按级别分布</h4>
        <ul v-if="(distMini.level_dist?.list || []).length">
          <li v-for="(item, idx) in distMini.level_dist.list" :key="idx">
            <span class="ml-name">{{ item.name }}</span>
            <div class="ml-track"><div class="ml-fill" :style="{ width: ratioOf(distMaxLevel, item.count) + '%' }"></div></div>
            <span class="ml-count">{{ item.count }}</span>
          </li>
        </ul>
        <div v-else class="mini-empty">暂无级别数据</div>
      </div>
      <div class="mini-card">
        <h4>按类别分布</h4>
        <ul v-if="(distMini.category_dist?.list || []).length">
          <li v-for="(item, idx) in distMini.category_dist.list.slice(0, 4)" :key="idx">
            <span class="pie-dot" :style="{ background: pieColor(idx) }"></span>
            <span class="ml-name">{{ item.name }}</span>
            <span class="ml-count">{{ item.count }}</span>
          </li>
        </ul>
        <div v-else class="mini-empty">暂无类别数据</div>
      </div>
    </section>

    <!-- 检索栏：类别 / 学院 / 届别（任务清单 4.M03）＋级别 / 状态 / 关键词 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="handleSearch">
        <el-form-item label="关键词">
          <el-input
            v-model="filter.keyword"
            placeholder="学号 / 姓名 / 荣誉名称"
            clearable
            style="width: 190px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="filter.category" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="c in categories" :key="c.id" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="学院">
          <el-select
            v-model="filter.college_id"
            :placeholder="collegeLocked ? '本学院' : '全部'"
            :clearable="!collegeLocked"
            :disabled="collegeLocked || presetActive"
            style="width: 150px"
          >
            <el-option v-for="c in colleges" :key="c.id" :label="c.label" :value="String(c.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="届别">
          <el-select v-model="filter.grad_year" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="y in gradYears" :key="y.id" :label="y.label" :value="y.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="filter.level" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="l in LEVEL_OPTIONS" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="大屏状态">
          <el-select v-model="filter.screen_status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="s in SCREEN_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-head-row">
          <span>荣誉清单</span>
          <div class="head-actions">
            <el-button :icon="Tickets" @click="openLogsDialog">上下架日志</el-button>
            <el-button type="primary" :icon="EditPen" @click="router.push('/honors/draft')">
              录入荣誉证书
            </el-button>
          </div>
        </div>
      </template>

      <!-- [P1/M17] 批量工具栏：仅当选中行数 >0 时展示，避免占用视觉空间 -->
      <div v-if="selectedRows.length > 0" class="batch-bar">
        <span class="batch-text">已选 {{ selectedRows.length }} 条</span>
        <el-button type="success" :icon="VideoPlay" @click="batchToggleScreenStatus('online')">
          批量上架
        </el-button>
        <el-button type="warning" :icon="VideoPause" @click="batchToggleScreenStatus('offline')">
          批量下架
        </el-button>
        <el-button link @click="selectedRows = []">取消选择</el-button>
      </div>

      <el-table
        :data="list"
        v-loading="loading"
        stripe
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="44" />
        <el-table-column prop="id" label="ID" width="80" sortable />
        <el-table-column prop="title" label="荣誉名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="类别" width="100">
          <template #default="{ row }">{{ categoryText(row.category) }}</template>
        </el-table-column>
        <el-table-column label="级别" width="90">
          <template #default="{ row }">{{ HONOR_LEVELS[row.level] || row.level }}</template>
        </el-table-column>
        <el-table-column prop="awarded_at" label="授予日期" width="110" />
        <el-table-column prop="granted_by" label="授予单位" min-width="150" show-overflow-tooltip />
        <el-table-column prop="owner_name" label="获得者" width="100" />
        <el-table-column prop="student_id" label="学号" width="120" />
        <el-table-column label="学院" width="130">
          <template #default="{ row }">{{ dictLabel('college', row.college_id) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusOf(row.status).type" size="small">
              {{ statusOf(row.status).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大屏状态" width="100">
          <template #default="{ row }">
            <el-tag :type="screenStatusOf(row.screen_status).tag" size="small">
              {{ screenStatusOf(row.screen_status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="150">
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="showDetail(row)">详情</el-button>
            <el-button link type="primary" :icon="UserFilled" @click="openProfileDialog(row)">风采</el-button>
            <el-button
              v-if="row.screen_status === 'online'"
              link
              type="warning"
              :icon="VideoPause"
              @click="toggleScreenStatus(row)"
            >下架</el-button>
            <el-button
              v-else
              link
              type="success"
              :icon="VideoPlay"
              @click="toggleScreenStatus(row)"
            >上架</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        class="mt-12"
        @current-change="loadList"
      />
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="荣誉详情" size="420px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="荣誉名称">{{ detail.title }}</el-descriptions-item>
            <el-descriptions-item label="类别">{{ categoryText(detail.category) }}</el-descriptions-item>
            <el-descriptions-item label="级别">{{ HONOR_LEVELS[detail.level] || detail.level }}</el-descriptions-item>
            <el-descriptions-item label="授予日期">{{ detail.awarded_at }}</el-descriptions-item>
            <el-descriptions-item label="授予单位">{{ detail.granted_by || '-' }}</el-descriptions-item>
            <el-descriptions-item label="获得者">{{ detail.owner_name }}（{{ detail.student_id }}）</el-descriptions-item>
            <el-descriptions-item label="学院">{{ dictLabel('college', detail.college_id) }}</el-descriptions-item>
            <el-descriptions-item label="届别">{{ detail.grad_year }} 届</el-descriptions-item>
            <el-descriptions-item label="证书原件">
              {{ detail.attachment_ids?.length ? `${detail.attachment_ids.length} 份附件` : '无原件（文字记录）' }}
            </el-descriptions-item>
            <el-descriptions-item label="关联档案">
              {{ detail.archive_id ? `档案 #${detail.archive_id}` : '未关联' }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusOf(detail.status).type" size="small">{{ statusOf(detail.status).text }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ formatTime(detail.created_at) }}</el-descriptions-item>
          </el-descriptions>

          <h4 class="section-title">证书原件（{{ detail.attachments?.length || 0 }}）</h4>
          <AttachmentPreviewer
            :attachments="detail.attachments || []"
            deletable
            @deleted="onAttachmentDeleted"
          />

          <template v-if="detail.status === 'rejected' && detail.reject_reason">
            <h4 class="section-title">驳回原因</h4>
            <el-alert :title="detail.reject_reason" type="error" :closable="false" />
          </template>
        </template>
      </div>
    </el-drawer>

    <!-- [P1/M17] 上下架变更日志对话框 -->
    <el-dialog
      v-model="logsDialogVisible"
      title="荣誉大屏上下架变更日志"
      width="900px"
      :close-on-click-modal="false"
    >
      <el-table :data="logs" v-loading="logsLoading" stripe size="small">
        <el-table-column prop="id" label="日志ID" width="80" />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column prop="honor_id" label="荣誉ID" width="90" />
        <el-table-column prop="honor_title" label="荣誉名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">{{ logActionOf(row.action).label }}</template>
        </el-table-column>
        <el-table-column label="变更" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ screenStatusOf(row.before_status).label }}</el-tag>
            →
            <el-tag size="small" :type="screenStatusOf(row.after_status).tag">
              {{ screenStatusOf(row.after_status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator_name" label="操作人" width="120">
          <template #default="{ row }">{{ row.operator_name || '-' }}（{{ row.operator_role }}）</template>
        </el-table-column>
        <el-table-column prop="client_ip" label="IP" width="120" />
        <el-table-column prop="batch_size" label="批量" width="60" />
        <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
      </el-table>
      <el-pagination
        v-model:current-page="logsPage"
        v-model:page-size="logsPageSize"
        :total="logsTotal"
        layout="total, prev, pager, next"
        class="mt-12"
        @current-change="loadLogs"
      />
    </el-dialog>

    <!-- [V18 alumni_profile] 校友风采/座右铭/头像 录入弹窗（管理员为该荣誉获得者补全信息） -->
    <AlumniProfileEditDialog
      v-if="profileDialogVisible && profileTargetHonorId != null"
      v-model="profileDialogVisible"
      mode="byHonor"
      :honor-id="profileTargetHonorId"
      @saved="onProfileSaved"
    />
  </div>
</template>

<style scoped>
.filter-card {
  border-radius: var(--radius-xl);
  margin-bottom: 14px;
}

/* Tab 1 头部 · 荣誉类型分布迷你卡 */
.dist-mini-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}
.mini-card {
  padding: 14px 16px;
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
}
.mini-card h4 {
  font-size: 14px;
  font-weight: 700;
  margin: 0 0 10px;
  color: var(--text-main);
}
.dist-mini-row ul { list-style: none; margin: 0; padding: 0; }
.dist-mini-row li {
  display: grid;
  grid-template-columns: 90px 1fr 50px;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 12.5px;
}
.ml-name { color: var(--text-sub); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ml-track { height: 8px; background: var(--gray-100); border-radius: 4px; overflow: hidden; }
.ml-fill { height: 100%; background: linear-gradient(90deg, var(--brand-500), var(--brand-700)); border-radius: 4px; transition: width 0.3s; }
.ml-count { font-weight: 700; color: var(--text-main); text-align: right; font-variant-numeric: tabular-nums; }
.pie-dot { width: 10px; height: 10px; border-radius: 3px; flex-shrink: 0; }
.mini-empty { padding: 12px 0; text-align: center; font-size: 12px; color: var(--text-sub); }
@media (max-width: 1080px) {
  .dist-mini-row { grid-template-columns: 1fr; }
}

.table-card {
  border-radius: var(--radius-xl);
}

.card-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-title {
  margin: 18px 0 8px;
  font-size: 14px;
}

/* [P1/M17] 头部操作按钮组 */
.head-actions {
  display: flex;
  gap: 8px;
}

/* [P1/M17] 批量操作工具栏 */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 12px;
  background: #f0f9ff;
  border: 1px solid #d6eaf7;
  border-radius: 6px;
}
.batch-text {
  font-size: 13px;
  color: #606266;
  margin-right: 4px;
}
</style>
