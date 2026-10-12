<script setup>
/**
 * 档案管理页（管理端列表，API 规范 4.2）
 * 双视图（清单：按档案明细 / 按人聚合）：
 * - 明细视图：组合筛选 + 分页 + 详情抽屉
 * - 聚合视图：院系 → 专业 → 个人 卡片层层递进，进入个人可对其在审档案直接审核
 * 角色适配（PRD M06-01 三级组织权限）：院级管理员/导员锁定本学院（后端强制过滤）；
 *   上下架仅校级/系统管理员可操作（M05-01）
 */
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, View, Upload, Download } from '@element-plus/icons-vue'
import { getArchives, getArchive, getArchiveAgg } from '@/api/archive'
import { publishArchive, unpublishArchive } from '@/api/showcase'
import { getReviews, approveReview, rejectReview } from '@/api/review'
import { getDict, dictLabel } from '@/api/dict'
import { statusOf, formatTime, CATEGORY_TEXT } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import AttachmentPreviewer from '@/components/AttachmentPreviewer.vue'

const userStore = useUserStore()
const role = computed(() => userStore.user?.role)
/** 数据范围锁定本院（院级管理员/导员，后端同样会强制过滤） */
const collegeLocked = computed(() => role.value === 'college_admin' || role.value === 'counselor')
/** 上下架仅校级/系统管理员 */
const canPublish = computed(() => role.value === 'school_admin' || role.value === 'system_admin')
/** 审核角色（M34 三级审核：导员可初审；其余两级按 PRD 4.2） */
const isReviewer = computed(() => ['counselor', 'college_admin', 'school_admin', 'system_admin'].includes(role.value))

// ---------- 视图模式：list=按档案明细 / group=按人聚合（默认） ----------
const viewMode = ref('group')

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const filter = reactive({
  keyword: '',
  college_id: '',
  grad_year: '',
  category: '',
  status: '',
  date_from: '',
  date_to: '',
})

// 筛选字典
const colleges = ref([])
const gradYears = ref([])

/**
 * 档案状态枚举（与后端 archive.status 对齐）
 * - pending_counselor：M34 新增，导员初审（M34 之前为院系直接初审 → pending_first）
 * - pending_first：保留旧数据兼容
 * - pending_second：待院系/校级复核
 * 注：filter 仅下拉展示前端可见的语义，搜索时若带入非法值会被忽略。
 */
const STATUS_OPTIONS = [
  { label: '草稿', value: 'draft' },
  { label: '待初审（导员）', value: 'pending_counselor' },
  { label: '待院系初审', value: 'pending_first' },
  { label: '待校级复核', value: 'pending_second' },
  { label: '已通过', value: 'approved' },
  { label: '已驳回', value: 'rejected' },
]

const CATEGORY_OPTIONS = [
  { label: '获奖经历', value: 'award' },
  { label: '证书资质', value: 'certificate' },
  { label: '实践经历', value: 'experience' },
  { label: '就业履历', value: 'career' },
  { label: '其他资料', value: 'other' },
]

async function loadDicts() {
  try {
    colleges.value = await getDict('college')
    gradYears.value = await getDict('grad_year')
  } catch {
    // 字典加载失败不阻塞列表
  }
}

/** 状态值合法集合：避免历史脏数据/下拉旧值导致后端返回 0 条 */
const VALID_STATUS = new Set(STATUS_OPTIONS.map((s) => s.value))

async function loadList() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value }
    if (filter.keyword) params.keyword = filter.keyword.trim()
    if (filter.college_id) params.college_id = filter.college_id
    if (filter.grad_year) params.grad_year = filter.grad_year
    if (filter.category) params.category = filter.category
    // 状态：仅当下拉选中的值属于合法集合时才下发；非法值一律忽略，避免"再次进入为空"
    if (filter.status && VALID_STATUS.has(filter.status)) {
      params.status = filter.status
    } else if (filter.status) {
      filter.status = ''
    }
    if (filter.date_from) params.date_from = filter.date_from
    if (filter.date_to) params.date_to = filter.date_to
    const res = await getArchives(params)
    // 默认按 ID 降序（最新的在前）
    list.value = (res.data.list || []).slice().sort((a, b) => a.id - b.id)
    total.value = res.data.total
  } catch (e) {
    ElMessage.error(e?.message || '档案列表加载失败')
  } finally {
    loading.value = false
  }
}

function switchView(mode) {
  page.value = 1
  if (mode === 'group') {
    backTo('college')
  } else {
    loadList()
  }
}

// ---------- 按人聚合：院系 → 专业 → 个人 层层递进 ----------
const drill = reactive({ level: 'college', college: '', major: '', person: null })
const aggRows = ref([])
const aggLoading = ref(false)
const personList = ref([])
const personLoading = ref(false)

async function loadAgg() {
  aggLoading.value = true
  try {
    const params = { level: drill.level }
    if (drill.level !== 'college') params.college = drill.college
    if (drill.level === 'person') params.major = drill.major
    const res = await getArchiveAgg(params)
    aggRows.value = res.data || []
    // 院级/导员数据范围只有一个学院：自动进入专业层，省一次点击
    if (drill.level === 'college' && aggRows.value.length === 1 && collegeLocked.value) {
      enterCollege(aggRows.value[0])
    }
  } catch (e) {
    ElMessage.error(e?.message || '聚合数据加载失败')
  } finally {
    aggLoading.value = false
  }
}

function enterCollege(row) {
  drill.college = row.key
  drill.level = 'major'
  loadAgg()
}

function enterMajor(row) {
  drill.major = row.key
  drill.level = 'person'
  loadAgg()
}

async function enterPerson(row) {
  drill.person = row
  drill.level = 'archives'
  personLoading.value = true
  try {
    const res = await getArchives({ user_id: row.user_id, page: 1, page_size: 200 })
    personList.value = (res.data.list || []).slice().sort((a, b) => b.id - a.id)
    await ensureReviews()
  } catch (e) {
    ElMessage.error(e?.message || '该成员的档案加载失败')
  } finally {
    personLoading.value = false
  }
}

function backTo(level) {
  if (level === 'college') {
    drill.college = ''
    drill.major = ''
    drill.person = null
  } else if (level === 'major') {
    drill.major = ''
    drill.person = null
  } else if (level === 'person') {
    drill.person = null
  }
  drill.level = level
  if (level !== 'archives') loadAgg()
}

// ---------- 个人档案级快捷审核（聚合视图·个人层） ----------
/** archive_id → 对应待审 review 单 */
const reviewMap = ref({})
const reviewLoading = ref(false)

async function ensureReviews() {
  if (!isReviewer.value) return
  reviewLoading.value = true
  try {
    const res = await getReviews({ biz_type: 'archive', status: 'pending', page_size: 200 })
    const map = {}
    for (const r of res.data.list || []) map[r.biz_id] = r
    reviewMap.value = map
  } catch {
    reviewMap.value = {}
  } finally {
    reviewLoading.value = false
  }
}

/** 当前用户是否可审该档案（院级→初审；校级/系统→初审+复核） */
function canReviewArchive(archive) {
  if (!isReviewer.value) return false
  if (role.value === 'college_admin') return archive.status === 'pending_first'
  return archive.status === 'pending_first' || archive.status === 'pending_second'
}

async function quickApprove(archive) {
  const review = reviewMap.value[archive.id]
  if (!review) {
    ElMessage.warning('未找到对应的待审核单，请到审核中心处理')
    return
  }
  try {
    await ElMessageBox.confirm(`确认通过「${archive.title}」的${review.stage === 'first' ? '初审' : '复核'}吗？`, '审核通过', {
      type: 'warning',
      confirmButtonText: '通过',
    })
  } catch {
    return
  }
  try {
    await approveReview(review.id)
    ElMessage.success(review.stage === 'first' ? '初审通过，已转入校级复核' : '复核通过，已入库')
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function quickReject(archive) {
  const review = reviewMap.value[archive.id]
  if (!review) {
    ElMessage.warning('未找到对应的待审核单，请到审核中心处理')
    return
  }
  let reason = ''
  try {
    const res = await ElMessageBox.prompt(
      `请填写「${archive.title}」的驳回原因（必填，将展示给提交人）`,
      '审核驳回',
      {
        confirmButtonText: '驳回',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '如：证书照片不清晰，请重新上传',
        inputValidator: (v) => (v && v.trim() ? true : '驳回原因不能为空'),
      },
    )
    reason = res.value
  } catch {
    return
  }
  try {
    await rejectReview(review.id, reason)
    ElMessage.success('已驳回')
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function handleSearch() {
  page.value = 1
  loadList()
}

function handleReset() {
  Object.assign(filter, { keyword: '', college_id: '', grad_year: '', category: '', status: '', date_from: '', date_to: '' })
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
    const res = await getArchive(row.id)
    detail.value = res.data
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    detailLoading.value = false
  }
}

// ---------- 上下架（Day 2 F1③） ----------
/** 附件删除后：本地同步移除并刷新列表计数 */
function onAttachmentDeleted(att) {
  if (detail.value?.attachments) {
    detail.value.attachments = detail.value.attachments.filter((x) => x.id !== att.id)
  }
  loadList()
}
/** 详情抽屉打开时同步刷新上架状态 */
async function reloadDetail() {
  if (!detail.value) return
  try {
    const res = await getArchive(detail.value.id)
    detail.value = res.data
  } catch {
    // 刷新失败不阻塞列表
  }
}

async function handlePublish(row) {
  try {
    await ElMessageBox.confirm(`确认将「${row.title}」上架到大屏展示？`, '上架确认', { type: 'info' })
  } catch {
    return
  }
  try {
    await publishArchive(row.id)
    ElMessage.success('已上架，大屏可见')
    loadList()
    reloadDetail()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function handleUnpublish(row) {
  try {
    await ElMessageBox.confirm(`确认将「${row.title}」从大屏下架？`, '下架确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await unpublishArchive(row.id)
    ElMessage.success('已下架')
    loadList()
    reloadDetail()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/**
 * 进入页面时清掉非锁定的脏筛选条件，避免"再次进入为空"
 * （典型场景：上次会话残留了 status=pending_second 之类返回 0 条的旧枚举值）
 * 注：filter.college_id 在下方根据角色（collegeLocked）保留锁定或保留路由值，不在此处重置。
 */
function resetStaleFilter() {
  filter.keyword = ''
  filter.grad_year = ''
  filter.category = ''
  filter.status = ''
  filter.date_from = ''
  filter.date_to = ''
}

onMounted(() => {
  // 0) 先把非锁定的脏筛选条件清空（保险丝）
  resetStaleFilter()
  // 院级管理员/导员：学院筛选锁定本院（PRD M06-01）
  if (collegeLocked.value && userStore.user?.college_id) {
    filter.college_id = userStore.user.college_id
  }
  loadDicts()
  loadAgg()
})
</script>

<template>
  <div>
    <!-- 多级筛选栏（学号检索 + 学院/届别/类别/状态，M05-02） -->
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="handleSearch">
        <el-form-item label="关键词">
          <el-input
            v-model="filter.keyword"
            placeholder="学号 / 姓名 / 标题"
            clearable
            style="width: 190px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="学院">
          <el-select
            v-model="filter.college_id"
            :placeholder="collegeLocked ? '本学院' : '全部'"
            :clearable="!collegeLocked"
            :disabled="collegeLocked"
            style="width: 150px"
          >
            <el-option v-for="c in colleges" :key="c.id" :label="c.label" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="届别">
          <el-select v-model="filter.grad_year" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="y in gradYears" :key="y.id" :label="y.label" :value="y.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="filter.category" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="c in CATEGORY_OPTIONS" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="提交时间">
          <el-date-picker
            v-model="filter.date_from"
            type="date"
            placeholder="开始日期"
            value-format="YYYY-MM-DD"
            style="width: 140px"
          />
          <span class="date-sep">~</span>
          <el-date-picker
            v-model="filter.date_to"
            type="date"
            placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表（双视图：按档案明细 / 按人聚合） -->
    <el-card shadow="never" class="table-card">
      <div class="view-switch">
        <el-radio-group v-model="viewMode" @change="switchView">
          <el-radio-button value="group">按人聚合</el-radio-button>
          <el-radio-button value="list">按档案明细</el-radio-button>
        </el-radio-group>
        <span v-if="viewMode === 'group'" class="view-tip">院系 → 专业 → 个人 层层递进；进入个人可直接审核其在审档案</span>
      </div>

      <!-- 聚合视图：卡片层层递进 -->
      <div v-if="viewMode === 'group'" v-loading="aggLoading">
        <!-- 面包屑 -->
        <div class="crumbs">
          <button type="button" class="crumb" :class="{ on: drill.level === 'college' }" @click="backTo('college')">
            全部院系
          </button>
          <template v-if="drill.college">
            <span class="crumb-sep">/</span>
            <button type="button" class="crumb" :class="{ on: drill.level === 'major' }" @click="backTo('major')">
              {{ drill.college }}
            </button>
          </template>
          <template v-if="drill.major">
            <span class="crumb-sep">/</span>
            <button type="button" class="crumb" :class="{ on: drill.level === 'person' }" @click="backTo('person')">
              {{ drill.major }}
            </button>
          </template>
          <template v-if="drill.person">
            <span class="crumb-sep">/</span>
            <span class="crumb on">{{ drill.person.real_name || drill.person.student_id }}</span>
          </template>
        </div>

        <!-- 院级 / 专业 卡片 -->
        <div v-if="drill.level === 'college' || drill.level === 'major'" class="card-grid">
          <button
            v-for="row in aggRows"
            :key="row.key"
            type="button"
            class="grp-card"
            @click="drill.level === 'college' ? enterCollege(row) : enterMajor(row)"
          >
            <div class="grp-name">{{ row.key }}</div>
            <div class="grp-nums"><b>{{ row.people }}</b> 人 · <b>{{ row.archives }}</b> 份档案</div>
            <span class="grp-go">进入 →</span>
          </button>
          <el-empty v-if="!aggLoading && !aggRows.length" description="暂无档案数据" :image-size="60" class="grid-empty" />
        </div>

        <!-- 个人卡片 -->
        <div v-else-if="drill.level === 'person'" class="card-grid">
          <button
            v-for="p in aggRows"
            :key="p.user_id"
            type="button"
            class="grp-card person"
            @click="enterPerson(p)"
          >
            <div class="grp-name">{{ p.real_name || '—' }}</div>
            <div class="person-meta">{{ p.student_id }}<template v-if="p.grad_year"> · {{ p.grad_year }} 届</template></div>
            <div class="person-chips">
              <span class="pchip total">{{ p.total }} 份</span>
              <span v-if="p.approved" class="pchip ok">{{ p.approved }} 通过</span>
              <span v-if="p.pending" class="pchip pending">{{ p.pending }} 在审</span>
              <span v-if="p.rejected" class="pchip reject">{{ p.rejected }} 驳回</span>
              <span v-if="p.draft" class="pchip draft">{{ p.draft }} 草稿</span>
            </div>
          </button>
          <el-empty v-if="!aggLoading && !aggRows.length" description="该专业暂无档案数据" :image-size="60" class="grid-empty" />
        </div>

        <!-- 个人档案明细（可直接审核在审档案） -->
        <div v-else v-loading="personLoading">
          <el-table :data="personList" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
            <el-table-column label="类别" width="100">
              <template #default="{ row }">{{ CATEGORY_TEXT[row.category] || row.category }}</template>
            </el-table-column>
            <el-table-column label="状态" width="130">
              <template #default="{ row }">
                <el-tag :type="statusOf(row.status).type" size="small">{{ statusOf(row.status).text }}</el-tag>
                <el-tag v-if="row.published" type="success" size="small" class="ml-4">已上架</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="提交时间" width="150">
              <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="View" @click="showDetail(row)">详情</el-button>
                <el-button v-if="canReviewArchive(row)" link type="success" @click="quickApprove(row)">通过</el-button>
                <el-button v-if="canReviewArchive(row)" link type="danger" @click="quickReject(row)">驳回</el-button>
                <el-button
                  v-if="canPublish && row.status === 'approved' && !row.published"
                  link
                  type="success"
                  :icon="Upload"
                  @click="handlePublish(row)"
                >
                  上架
                </el-button>
                <el-button
                  v-else-if="canPublish && row.published"
                  link
                  type="warning"
                  :icon="Download"
                  @click="handleUnpublish(row)"
                >
                  下架
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <!-- 明细视图 -->
      <el-table v-if="viewMode === 'list'" :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="类别" width="100">
          <template #default="{ row }">{{ CATEGORY_TEXT[row.category] || row.category }}</template>
        </el-table-column>
        <el-table-column prop="owner_name" label="提交人" width="110" />
        <el-table-column prop="student_id" label="学号" width="120" />
        <el-table-column label="学院" width="130">
          <template #default="{ row }">{{ dictLabel('college', row.college_id) }}</template>
        </el-table-column>
        <el-table-column label="届别" width="80">
          <template #default="{ row }">{{ row.grad_year }} 届</template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="statusOf(row.status).type" size="small">
              {{ statusOf(row.status).text }}
            </el-tag>
            <el-tag v-if="row.published" type="success" size="small" class="ml-4">已上架</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="150">
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="showDetail(row)">详情</el-button>
            <el-button
              v-if="canPublish && row.status === 'approved' && !row.published"
              link
              type="success"
              :icon="Upload"
              @click="handlePublish(row)"
            >
              上架
            </el-button>
            <el-button
              v-else-if="canPublish && row.published"
              link
              type="warning"
              :icon="Download"
              @click="handleUnpublish(row)"
            >
              下架
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="viewMode === 'list'"
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        class="mt-12"
        @current-change="loadList"
      />
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="档案详情" size="420px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
            <el-descriptions-item label="类别">{{ CATEGORY_TEXT[detail.category] || detail.category }}</el-descriptions-item>
            <el-descriptions-item label="提交人">{{ detail.owner_name }}</el-descriptions-item>
            <el-descriptions-item label="学号">{{ detail.student_id }}</el-descriptions-item>
            <el-descriptions-item label="学院">{{ detail.college || dictLabel('college', detail.college_id) }}</el-descriptions-item>
            <el-descriptions-item label="届别">{{ detail.grad_year }} 届</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusOf(detail.status).type" size="small">{{ statusOf(detail.status).text }}</el-tag>
              <el-tag v-if="detail.published" type="success" size="small" class="ml-4">已上架大屏</el-tag>
              <el-tag v-else-if="detail.status === 'approved' && !detail.consent_active" type="info" size="small" class="ml-4">
                未获大屏授权
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="版本">第 {{ detail.version }} 版</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ formatTime(detail.created_at) }}</el-descriptions-item>
          </el-descriptions>

          <!-- 上下架操作（校级/系统管理员，已通过档案） -->
          <div v-if="canPublish && detail.status === 'approved'" class="detail-actions">
            <el-button v-if="!detail.published" type="success" :icon="Upload" @click="handlePublish(detail)">
              上架到大屏
            </el-button>
            <el-button v-else type="warning" plain :icon="Download" @click="handleUnpublish(detail)">
              从大屏下架
            </el-button>
            <span v-if="!detail.published && !detail.consent_active" class="text-sub">
              未获校友大屏授权，无法上架
            </span>
          </div>

          <h4 class="section-title">详细内容</h4>
          <p class="detail-content">{{ detail.content }}</p>

          <template v-if="detail.remark">
            <h4 class="section-title">备注</h4>
            <p class="detail-content">{{ detail.remark }}</p>
          </template>

          <h4 class="section-title">附件材料（{{ detail.attachments?.length || 0 }}）</h4>
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
  </div>
</template>

<style scoped>
.filter-card {
  border-radius: var(--radius-xl);
  margin-bottom: 14px;
}

.date-sep {
  margin: 0 6px;
  color: var(--text-sub);
}

.table-card {
  border-radius: var(--radius-xl);
}

.view-switch {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.view-tip {
  font-size: 12px;
  color: var(--text-sub);
}

.group-owner {
  font-weight: 600;
}

/* ===== 按人聚合 · 面包屑 + 递进卡片 ===== */
.crumbs {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 14px;
}
.crumb {
  border: none;
  background: transparent;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--text-sub);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.crumb:hover {
  background: var(--surface-2, #f1f5f9);
  color: var(--brand-500, #2f6bff);
}
.crumb.on {
  color: var(--text-main);
  font-weight: 600;
  cursor: default;
  background: transparent;
}
.crumb-sep {
  color: var(--text-sub);
  font-size: 12px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 14px;
}
.grid-empty {
  grid-column: 1 / -1;
}
.grp-card {
  position: relative;
  text-align: left;
  background: var(--card, #fff);
  border: 1px solid var(--line, #e5eaf2);
  border-radius: var(--radius-xl, 12px);
  padding: 18px;
  cursor: pointer;
  transition:
    transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1),
    box-shadow 0.25s,
    border-color 0.25s;
}
.grp-card:hover {
  transform: translateY(-3px);
  border-color: rgba(47, 107, 255, 0.35);
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.12);
}
.grp-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.35;
  word-break: break-all;
}
.grp-nums {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-sub);
}
.grp-nums b {
  font-size: 18px;
  color: var(--brand-500, #2f6bff);
  font-variant-numeric: tabular-nums;
}
.grp-go {
  position: absolute;
  right: 14px;
  bottom: 12px;
  font-size: 11px;
  color: var(--brand-500, #2f6bff);
  opacity: 0;
  transition: opacity 0.2s;
}
.grp-card:hover .grp-go {
  opacity: 1;
}
.person-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-sub);
  font-variant-numeric: tabular-nums;
}
.person-chips {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.pchip {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 600;
}
.pchip.total { background: #eef2ff; color: #4053c4; }
.pchip.ok { background: #e9f9ef; color: #1a7f43; }
.pchip.pending { background: #fff6e5; color: #b96a00; }
.pchip.reject { background: #fdecec; color: #c62f2f; }
.pchip.draft { background: #f1f5f9; color: #64748b; }

.group-summary {
  font-size: 13px;
  color: var(--text-sub);
}

.group-detail {
  padding: 4px 12px 8px 56px;
}

.group-inner-table {
  width: 100%;
}

.section-title {
  margin: 18px 0 8px;
  font-size: 14px;
}

.detail-content {
  margin: 0;
  line-height: 1.8;
  color: var(--text-main);
}
</style>
