<script setup>
/**
 * 审核工作台（M04 + M34 三级审核，对齐 ui-design/review-workbench.html 设计范式）
 * 视觉：衬线标题页头 + KPI 卡 + chip 业务筛选 + 行列表卡片
 *
 * 三级审核：导员（counselor）→ 院系（college_admin）→ 校级（school_admin / system_admin）
 * - 导员：层级锁定 stage=counselor；按 counselor_scopes 越范围数据不可见（后端强制）
 * - 院级：层级锁定 stage=first；仅本学院数据（后端强制）
 * - 校级：默认 stage=second（终审），可手动查看 counselor / first 历史记录
 *
 * 业务类型 chips：
 * - 导员/院级不含 activate_manual（人工核验仅校级可见）
 */
import { computed, ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getReviews, approveReview, rejectReview, getReviewLogs } from '@/api/review'
import { BIZ_TYPE_TEXT, STAGE_TEXT, reviewStatusOf, formatTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import ReviewDetailDrawer from './ReviewDetailDrawer.vue'
import LucideIcon from '@/components/LucideIcon.vue'

const userStore = useUserStore()
const role = computed(() => userStore.user?.role)
/** 三级角色判定 */
const isCounselor = computed(() => role.value === 'counselor')
const isCollegeAdmin = computed(() => role.value === 'college_admin')
const isSchoolAdmin = computed(() => role.value === 'school_admin' || role.value === 'system_admin')
/** 层级锁定：导员 = counselor；院级 = first；校级 不锁定 */
const lockedStage = computed(() => {
  if (isCounselor.value) return 'counselor'
  if (isCollegeAdmin.value) return 'first'
  return ''
})
/** 锁定阶段下拉是否禁用 */
const stageLocked = computed(() => !!lockedStage.value)

const loading = ref(false)
const list = ref([])
const total = ref(0)

const filter = reactive({
  stage: lockedStage.value,
  status: 'pending',
  biz_type: '',
})
const page = ref(1)
const pageSize = ref(10)

/** 业务类型 chips（导员/院级不含人工核验） */
const bizChips = computed(() => {
  const base = [
    { value: '', label: '全部' },
    { value: 'archive', label: '档案材料' },
    { value: 'honor', label: '荣誉证书' },
    { value: 'archive_delete', label: '删除申请' },
  ]
  if (!isCounselor.value && !isCollegeAdmin.value) {
    base.push({ value: 'activate_manual', label: '人工核验' })
  }
  return base
})

/** KPI 概览（仅计数，page_size=1 取 total） */
const kpi = reactive({ pendingCounselor: 0, pendingFirst: 0, pendingSecond: 0, approved: 0, rejected: 0 })
async function loadKpi() {
  try {
    const tasks = [
      getReviews({ status: 'pending', stage: 'first', page_size: 1 }).then((r) => (kpi.pendingFirst = r.data.total)),
      getReviews({ status: 'approved', page_size: 1 }).then((r) => (kpi.approved = r.data.total)),
      getReviews({ status: 'rejected', page_size: 1 }).then((r) => (kpi.rejected = r.data.total)),
    ]
    // 校级：看待复核 + 导员待办（仅历史，不参与本角色处理）
    if (isSchoolAdmin.value) {
      tasks.push(
        getReviews({ status: 'pending', stage: 'second', page_size: 1 }).then((r) => (kpi.pendingSecond = r.data.total)),
        getReviews({ status: 'pending', stage: 'counselor', page_size: 1 }).then((r) => (kpi.pendingCounselor = r.data.total)),
      )
    }
    // 院级：看待初审（自己环节）+ 导员待办（管辖范围浏览）
    if (isCollegeAdmin.value) {
      tasks.push(
        getReviews({ status: 'pending', stage: 'counselor', page_size: 1 }).then((r) => (kpi.pendingCounselor = r.data.total)),
      )
    }
    // 导员：自己环节 = counselor 待办（与 pendingFirst 互斥；这里只显示 counselor）
    if (isCounselor.value) {
      tasks.push(
        getReviews({ status: 'pending', stage: 'counselor', page_size: 1 }).then((r) => (kpi.pendingCounselor = r.data.total)),
      )
    }
    await Promise.all(tasks)
  } catch {
    // KPI 失败不阻断列表
  }
}

async function loadList() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value }
    if (filter.stage) params.stage = filter.stage
    if (filter.status) params.status = filter.status
    if (filter.biz_type) params.biz_type = filter.biz_type
    const res = await getReviews(params)
    // 默认按 ID 降序（最新的在前）
    list.value = (res.data.list || []).slice().sort((a, b) => a.id - b.id)
    total.value = res.data.total
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function handleFilter() {
  page.value = 1
  loadList()
}

function pickBiz(v) {
  filter.biz_type = v
  handleFilter()
}

/** 通过按钮的提示文案：按当前审核层级自适应 */
function approveTip(row) {
  if (row.stage === 'counselor') return '导员初审通过，将转入院系初审'
  if (row.stage === 'first') return '院系初审通过，将转入校级终审'
  return '校级终审通过，已入库'
}
async function handleApprove(row) {
  try {
    await ElMessageBox.confirm(`确认通过「${row.biz_title}」的${STAGE_TEXT[row.stage] || '审核'}吗？`, '审核通过', {
      type: 'warning',
      confirmButtonText: '通过',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await approveReview(row.id)
    ElMessage.success(approveTip(row))
    loadList()
    loadKpi()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function handleReject(row) {
  let reason = ''
  try {
    const res = await ElMessageBox.prompt('请填写驳回原因（必填，将展示给提交人）', '审核驳回', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '如：证书照片不清晰，请重新上传',
      inputValidator: (v) => (v && v.trim() ? true : '驳回原因不能为空'),
    })
    reason = res.value
  } catch {
    return
  }
  try {
    await rejectReview(row.id, reason)
    ElMessage.success('已驳回')
    loadList()
    loadKpi()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// ---------- 审核详情（四类 biz_type 分流，见 ReviewDetailDrawer） ----------
const detailVisible = ref(false)
const detailReviewId = ref(null)

function showDetail(row) {
  detailReviewId.value = row.id
  detailVisible.value = true
}
function onActioned() {
  loadList()
  loadKpi()
}

// ---------- 审核日志 ----------
const logVisible = ref(false)
const logLoading = ref(false)
const logList = ref([])
const logTitle = ref('')

async function showLogs(row) {
  logTitle.value = `审核日志 - ${row.biz_title}`
  logVisible.value = true
  logLoading.value = true
  try {
    const res = await getReviewLogs(row.id)
    logList.value = res.data.list || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    logLoading.value = false
  }
}

function resetFilter() {
  filter.biz_type = ''
  filter.status = 'pending'
  filter.stage = lockedStage.value
  page.value = 1
  loadList()
}

const pageSub = computed(() => {
  if (isCounselor.value) {
    return '当前视角：辅导员（导员）· 仅处理管辖范围内学生资料的初审（stage=counselor）。点击任意记录查看业务详情与材料。'
  }
  if (isCollegeAdmin.value) {
    return '当前视角：院级管理员 · 处理本院初审待办（stage=first）。点击任意记录查看业务详情与材料。'
  }
  return '当前视角：校级管理员 · 对初审通过项做终审与决策（stage=second）。点击任意记录查看业务详情与材料。'
})

/** 当前锁定阶段的友好提示（工具栏右上角 tag） */
const stageLockLabel = computed(() => {
  if (isCounselor.value) return '初审（导员·管辖范围）'
  if (isCollegeAdmin.value) return '初审（院系·本院）'
  return ''
})

// ---------- 10 秒自动刷新（待办实时性；详情弹窗打开时跳过，避免打断操作） ----------
const lastRefreshedAt = ref('')
let autoTimer = null
function markRefreshTime() {
  lastRefreshedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

onMounted(() => {
  loadList()
  loadKpi()
  markRefreshTime()
  autoTimer = setInterval(() => {
    if (detailVisible.value || logVisible.value) return
    loadList()
    loadKpi()
    markRefreshTime()
  }, 10 * 1000)
})

onBeforeUnmount(() => {
  clearInterval(autoTimer)
})
</script>

<template>
  <div class="review-page">
    <!-- 页头 -->
    <div class="page-head">
      <div>
        <div class="eyebrow">审核中心</div>
        <h1 class="page-title">审核工作台</h1>
        <p class="page-sub">{{ pageSub }}</p>
      </div>
    </div>

    <!-- KPI -->
    <div class="kpis" :class="{ 'kpis-3': isCounselor }">
      <!-- 导员视角：导员待办 + 已通过 + 已驳回（无初审/复核） -->
      <template v-if="isCounselor">
        <div class="kpi">
          <div class="k-label">待初审（导员）</div>
          <div class="k-val">{{ kpi.pendingCounselor }}</div>
        </div>
        <div class="kpi gold">
          <div class="k-label">已通过</div>
          <div class="k-val">{{ kpi.approved }}</div>
        </div>
        <div class="kpi warn">
          <div class="k-label">已驳回</div>
          <div class="k-val">{{ kpi.rejected }}</div>
        </div>
      </template>
      <!-- 院级：导员待办（浏览）+ 待初审 + 已通过 + 已驳回 -->
      <template v-else-if="isCollegeAdmin">
        <div class="kpi">
          <div class="k-label">待初审（导员）</div>
          <div class="k-val">{{ kpi.pendingCounselor }}</div>
        </div>
        <div class="kpi">
          <div class="k-label">待初审（院系）</div>
          <div class="k-val">{{ kpi.pendingFirst }}</div>
        </div>
        <div class="kpi gold">
          <div class="k-label">已通过</div>
          <div class="k-val">{{ kpi.approved }}</div>
        </div>
        <div class="kpi warn">
          <div class="k-label">已驳回</div>
          <div class="k-val">{{ kpi.rejected }}</div>
        </div>
      </template>
      <!-- 校级：导员 + 院系 + 校级 + 已通过 + 已驳回 -->
      <template v-else>
        <div class="kpi">
          <div class="k-label">待初审（导员）</div>
          <div class="k-val">{{ kpi.pendingCounselor }}</div>
        </div>
        <div class="kpi">
          <div class="k-label">待初审（院系）</div>
          <div class="k-val">{{ kpi.pendingFirst }}</div>
        </div>
        <div class="kpi">
          <div class="k-label">待终审（校级）</div>
          <div class="k-val">{{ kpi.pendingSecond }}</div>
        </div>
        <div class="kpi gold">
          <div class="k-label">已通过</div>
          <div class="k-val">{{ kpi.approved }}</div>
        </div>
        <div class="kpi warn">
          <div class="k-label">已驳回</div>
          <div class="k-val">{{ kpi.rejected }}</div>
        </div>
      </template>
    </div>

    <!-- 筛选工具栏 -->
    <div class="toolbar">
      <div class="chips">
        <button
          v-for="c in bizChips"
          :key="c.value"
          class="chip"
          :class="{ active: filter.biz_type === c.value }"
          @click="pickBiz(c.value)"
        >
          {{ c.label }}
        </button>
      </div>
      <div class="toolbar-spacer"></div>
      <el-tag v-if="stageLockLabel" type="warning" effect="plain" class="stage-lock">{{ stageLockLabel }}</el-tag>
      <el-select
        v-if="!stageLocked"
        v-model="filter.stage"
        placeholder="审核层级"
        clearable
        style="width: 170px"
        @change="handleFilter"
      >
        <el-option label="初审（导员）" value="counselor" />
        <el-option label="初审（院系）" value="first" />
        <el-option label="终审（校级）" value="second" />
      </el-select>
      <!-- 锁定角色：filter.stage 不可见但保留值 -->
      <input v-else type="hidden" :value="filter.stage" />
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 130px" @change="handleFilter">
        <el-option label="待审核" value="pending" />
        <el-option label="已通过" value="approved" />
        <el-option label="已驳回" value="rejected" />
      </el-select>
      <el-button :icon="Refresh" @click="handleFilter">刷新</el-button>
      <span v-if="lastRefreshedAt" class="auto-refresh-tip" title="待办列表每 10 秒自动刷新">自动刷新于 {{ lastRefreshedAt }}</span>
    </div>

    <!-- 待办列表 -->
    <div class="list-card" v-loading="loading">
      <div class="list-row head">
        <div></div>
        <div>业务 / 标题</div>
        <div class="col-hide">提交人</div>
        <div class="col-hide">学院</div>
        <div class="col-hide">层级</div>
        <div>状态</div>
        <div class="col-hide col-actions">操作</div>
      </div>

      <template v-if="list.length">
        <div v-for="row in list" :key="row.id" class="list-row" @click="showDetail(row)">
          <div class="biz-ic" :class="row.biz_type"><LucideIcon :name="bizIcon(row.biz_type)" :size="18" /></div>
          <div class="col-main">
            <div class="cell-title">{{ row.biz_title }}</div>
            <div class="cell-meta">{{ BIZ_TYPE_TEXT[row.biz_type] || row.biz_type }} · #{{ row.id }}</div>
          </div>
          <div class="cell col-hide">
            {{ row.owner_name }}
            <div class="cell-meta">{{ row.student_id }}</div>
          </div>
          <div class="cell col-hide">{{ row.college }}</div>
          <div class="cell col-hide">{{ STAGE_TEXT[row.stage] || row.stage }}</div>
          <div>
            <span class="badge" :class="`badge--${reviewStatusOf(row.status).tone}`">
              <span class="dot"></span>{{ reviewStatusOf(row.status).text }}
            </span>
          </div>
          <div class="row-actions col-hide" @click.stop>
            <button class="act-link" @click="showDetail(row)">详情</button>
            <button class="act-link" @click="showLogs(row)">日志</button>
            <template v-if="row.status === 'pending'">
              <button class="act-link act-approve" @click="handleApprove(row)">通过</button>
              <button class="act-link act-reject" @click="handleReject(row)">驳回</button>
            </template>
          </div>
        </div>
      </template>

      <div v-else-if="!loading" class="empty">
        <div class="ico">✓</div>
        <h4>暂无审核任务</h4>
        <p>当前筛选条件下没有需要处理的记录，试试调整筛选条件。</p>
        <el-button size="small" @click="resetFilter">重置筛选</el-button>
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

    <!-- 审核详情抽屉（四类业务分流） -->
    <ReviewDetailDrawer v-model="detailVisible" :review-id="detailReviewId" @actioned="onActioned" />

    <!-- 审核日志抽屉 -->
    <el-drawer v-model="logVisible" :title="logTitle" size="360px">
      <div v-loading="logLoading">
        <el-empty v-if="!logLoading && !logList.length" description="暂无审核记录" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="(log, i) in logList"
            :key="i"
            :timestamp="formatTime(log.at)"
            placement="top"
          >
            <div><b>{{ log.reviewer }}</b> {{ log.action }}</div>
            <div v-if="log.reason" class="text-sub mt-12">原因：{{ log.reason }}</div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>
  </div>
</template>

<script>
/** 业务类型图标（内联，避免 script setup 中重复定义） */
const BIZ_ICON = { archive: 'folder', honor: 'medal', archive_delete: 'trash-2', activate_manual: 'search' }
export default {
  methods: {
    bizIcon(t) {
      return BIZ_ICON[t] || 'file-text'
    },
  },
}
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.eyebrow {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.4px;
  color: var(--brand-sub);
  margin-bottom: 8px;
}

.page-title {
  font-family: var(--font-display);
  font-size: 36px;
  font-weight: 700;
  line-height: 1.25;
  margin: 0;
  letter-spacing: -0.5px;
}

.page-sub {
  color: var(--text-sub);
  font-size: 13px;
  margin: 8px 0 0;
  max-width: 70ch;
}

/* ===== KPI ===== */
.kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.kpis.kpis-3 {
  grid-template-columns: repeat(3, 1fr);
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

.kpi:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
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

.kpi.gold .k-val {
  color: var(--green-700);
}

.kpi.warn .k-val {
  color: var(--amber-700);
}

/* ===== 工具栏 ===== */
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.chip {
  padding: 7px 14px;
  border-radius: var(--radius-full);
  border: 1px solid var(--line);
  background: var(--surface-1);
  color: var(--text-sub);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.14s;
}

.chip:hover {
  border-color: var(--brand-400);
  color: var(--brand-600);
}

.chip.active {
  background: var(--brand-600);
  color: #fff;
  border-color: var(--brand-600);
}

.toolbar-spacer {
  flex: 1;
}

.auto-refresh-tip {
  font-size: 12px;
  color: var(--text-sub);
  white-space: nowrap;
}

.stage-lock {
  height: 32px;
  line-height: 32px;
}

/* ===== 列表卡片 ===== */
.list-card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  overflow: hidden;
}

.list-row {
  display: grid;
  grid-template-columns: 44px 1.6fr 1fr 1fr 0.9fr 1fr 190px;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
  transition: background 0.12s;
}

.list-row:hover {
  background: var(--surface-2);
}

.list-row.head {
  background: var(--surface-2);
  cursor: default;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-sub);
  letter-spacing: 0.5px;
  border-bottom: 1px solid var(--line-strong);
}

.list-row.head:hover {
  background: var(--surface-2);
}

.list-row:last-child {
  border-bottom: none;
}

.biz-ic {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: grid;
  place-items: center;
  font-size: 18px;
  background: var(--brand-50);
}

.biz-ic.honor {
  background: var(--green-50);
}

.biz-ic.archive_delete {
  background: var(--red-50);
}

.biz-ic.activate_manual {
  background: var(--blue-50);
}

.col-main {
  min-width: 0;
}

.cell-title {
  font-weight: 600;
  color: var(--text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-meta {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 3px;
}

.cell {
  font-size: 13px;
  color: var(--text-sub);
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 24px;
  padding: 0 10px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 600;
}

.badge .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.badge--info {
  background: var(--blue-50);
  color: var(--blue-700);
}
.badge--info .dot {
  background: var(--blue-500);
}

.badge--success {
  background: var(--green-50);
  color: var(--green-700);
}
.badge--success .dot {
  background: var(--green-500);
}

.badge--warning {
  background: var(--amber-50);
  color: var(--amber-700);
}
.badge--warning .dot {
  background: var(--amber-500);
}

.badge--danger {
  background: var(--red-50);
  color: var(--red-700);
}
.badge--danger .dot {
  background: var(--red-500);
}

.row-actions {
  display: flex;
  gap: 4px;
  justify-content: flex-end;
}

.act-link {
  font-size: 13px;
  font-weight: 600;
  color: var(--brand-600);
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: var(--radius-sm);
  font-family: inherit;
}

.act-link:hover {
  background: var(--brand-50);
}

.act-approve {
  color: var(--green-700);
}
.act-approve:hover {
  background: var(--green-50);
}

.act-reject {
  color: var(--red-700);
}
.act-reject:hover {
  background: var(--red-50);
}

/* ===== 空态 ===== */
.empty {
  text-align: center;
  padding: 64px 24px;
}

.empty .ico {
  width: 60px;
  height: 60px;
  border-radius: var(--radius-full);
  background: var(--brand-50);
  color: var(--brand-400);
  display: grid;
  place-items: center;
  margin: 0 auto 16px;
  font-size: 28px;
}

.empty h4 {
  margin: 0 0 6px;
  font-family: var(--font-display);
  font-size: 18px;
}

.empty p {
  margin: 0 0 20px;
  color: var(--text-sub);
  font-size: 13px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 20px;
}

@media (max-width: 1080px) {
  .kpis {
    grid-template-columns: repeat(2, 1fr);
  }

  .kpis.kpis-3 {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 860px) {
  .page-title {
    font-size: 28px;
  }

  .list-row,
  .list-row.head {
    grid-template-columns: 40px 1fr 90px;
  }

  .list-row .col-hide,
  .list-row.head .col-hide {
    display: none;
  }
}
</style>
