<script setup>
/**
 * 人员管理 · 用户列表（/users/alumni 校友列表 · /users/students 学生列表）
 * 同一组件按路由 meta.userRole 复用： alumni=校友列表 / student=学生列表。
 * 数据：GET /admin/users（auth 模块），出参含学籍信息；手机号后端已脱敏。
 * 数据范围（后端强制）：院级锁本院、导员锁 counselor_scopes 管辖范围；校级/系统全量。
 * 操作权限：新增账号四角色均可（院级/导员限范围内）；编辑/重置密码等写操作仅校级/系统。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, Lock, Unlock, Switch, List, Plus } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  getUsers,
  createUser,
  updateUser,
  resetUserPassword,
  suspendUser,
  resumeUser,
  handoverUser,
  getUserChangeLogs,
  batchResetUserPassword,
} from '@/api/users'
import { getDict } from '@/api/dict'
import { sha256Hex } from '@/utils/crypto'
import { formatTime } from '@/utils/format'
import LucideIcon from '@/components/LucideIcon.vue'

const route = useRoute()
const userStore = useUserStore()
const userRole = computed(() => route.meta.userRole || 'alumni')
const isSystemAdmin = computed(() => userStore.user?.role === 'system_admin')
/** 行级写操作（编辑/重置密码）仅校级/系统；院级/导员只读 + 新增 */
const canEditRows = computed(() => ['school_admin', 'system_admin'].includes(userStore.user?.role))
/** 数据范围受限角色（院级锁本院 / 导员锁管辖）：隐藏学院筛选与学院表单项（后端强制覆盖） */
const isScopeLimited = computed(() => ['college_admin', 'counselor'].includes(userStore.user?.role))

const ROLE_TEXT = {
  student: '在校学生',
  alumni: '校友',
  counselor: '辅导员',
  college_admin: '院系管理员',
  school_admin: '校级管理员',
  system_admin: '系统管理员',
}

const isAlumniList = computed(() => userRole.value === 'alumni')

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')

// 表格多选（用于批量重置密码）
const selectedRows = ref([])
function isRowSelected(row) { return selectedRows.value.some((r) => r.id === row.id) }
function toggleSelectRow(row, checked) {
  if (checked) {
    if (!isRowSelected(row)) selectedRows.value = [...selectedRows.value, row]
  } else {
    selectedRows.value = selectedRows.value.filter((r) => r.id !== row.id)
  }
}
const allSelected = computed(() =>
  list.value.length > 0 && list.value.every((r) => isRowSelected(r)),
)
const someSelected = computed(() => selectedRows.value.length > 0)
function toggleSelectAll(checked) {
  if (checked) {
    const ids = new Set(selectedRows.value.map((r) => r.id))
    selectedRows.value = [...selectedRows.value, ...list.value.filter((r) => !ids.has(r.id))]
  } else {
    const ids = new Set(list.value.map((r) => r.id))
    selectedRows.value = selectedRows.value.filter((r) => !ids.has(r.id))
  }
}

/** 批量重置密码：弹二次确认 → 调用 /admin/users/batch-reset-password */
async function openBatchReset() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先勾选要重置的成员')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量重置选中的 ${selectedRows.value.length} 个账号密码为系统默认密码？\n重置后这些账号的全部登录态立刻作废，需用默认密码重新登录。`,
      '批量重置密码',
      { type: 'warning', confirmButtonText: '确认重置', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const userIds = selectedRows.value.map((r) => r.id)
    const res = await batchResetUserPassword(userIds)
    const d = res.data || {}
    ElMessage.success(`已批量重置：请求 ${d.total ?? userIds.length} 个 / 成功 ${d.reset ?? '—'} 个`)
    selectedRows.value = []
    loadList()
  } catch (e) {
    ElMessage.error(e.message || '批量重置失败')
  }
}

// ---------------- 学籍筛选（学院/专业/届别） ----------------
const college = ref('')
const major = ref('')
const gradYear = ref(null)
const colleges = ref([])

// 届别选项：今年 → 1996 倒序
const currentYear = new Date().getFullYear()
const yearOptions = Array.from({ length: currentYear - 1996 + 1 }, (_, i) => currentYear - i)

async function loadColleges() {
  try {
    colleges.value = await getDict('college')
  } catch {
    /* 字典加载失败不阻塞列表，仅下拉为空 */
  }
}

/** 有任一筛选生效时显示「重置」按钮 */
const hasFilter = computed(() => Boolean(college.value || major.value.trim() || gradYear.value))

function resetFilters() {
  college.value = ''
  major.value = ''
  gradYear.value = null
  handleSearch()
}

async function loadList() {
  loading.value = true
  try {
    const params = { role: userRole.value, page: page.value, page_size: pageSize.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (college.value) params.college = college.value
    if (major.value.trim()) params.major = major.value.trim()
    if (gradYear.value) params.grad_year = gradYear.value
    const res = await getUsers(params)
    // 默认按 ID 降序（最新的在前）
    list.value = (res.data.list || []).slice().sort((a, b) => a.id - b.id)
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error(e.message)
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadList()
}

// 校友列表 / 学生列表 之间切换时重新加载
watch(userRole, () => {
  page.value = 1
  keyword.value = ''
  college.value = ''
  major.value = ''
  gradYear.value = null
  loadList()
})

const pageSub = computed(() => {
  const base = isAlumniList.value
    ? '已激活校友账号（role=alumni），含学籍姓名 / 学院 / 届别；手机号为脱敏展示。'
    : '在校学生账号（role=student），含学籍姓名 / 学院 / 届别；手机号为脱敏展示。'
  const scope = userStore.user?.role === 'college_admin'
    ? '仅显示本院数据。'
    : userStore.user?.role === 'counselor'
      ? '仅显示管辖范围内的数据。'
      : ''
  return base + scope
})

/* ===== 新增账号弹窗（四角色均可；院级锁本院、导员锁管辖范围，后端强制） ===== */
const createVisible = ref(false)
const createSaving = ref(false)
const createForm = reactive({
  student_id: '',
  real_name: '',
  phone: '',
  college: '',
  major: '',
  grad_year: null,
})

function openCreate() {
  Object.assign(createForm, {
    student_id: '',
    real_name: '',
    phone: '',
    college: '',
    major: '',
    grad_year: null,
  })
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.student_id.trim()) {
    ElMessage.warning('请填写学号')
    return
  }
  if (!createForm.real_name.trim()) {
    ElMessage.warning('请填写姓名')
    return
  }
  if (!/^1\d{10}$/.test(createForm.phone.trim())) {
    ElMessage.warning('手机号格式错误（11 位，1 开头）')
    return
  }
  if (!createForm.grad_year) {
    ElMessage.warning('请选择届别')
    return
  }
  const payload = {
    student_id: createForm.student_id.trim(),
    real_name: createForm.real_name.trim(),
    phone: createForm.phone.trim(),
    role: userRole.value,
    grad_year: createForm.grad_year,
  }
  // 院级/导员不传学院（后端强制其归属学院）
  if (!isScopeLimited.value && createForm.college.trim()) payload.college = createForm.college.trim()
  if (createForm.major.trim()) payload.major = createForm.major.trim()
  createSaving.value = true
  try {
    const res = await createUser(payload)
    const pwd = res?.data?.initial_password
    createVisible.value = false
    ElMessageBox.alert(
      `已为 ${createForm.real_name.trim()}（${createForm.student_id.trim()}）创建账号，初始密码：${pwd || '系统默认密码'}。请提醒本人尽快登录并修改密码。`,
      '创建成功',
      { confirmButtonText: '知道了', type: 'success' },
    )
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    createSaving.value = false
  }
}

onMounted(() => {
  loadColleges()
  loadList()
})

/* ===== 编辑弹窗 ===== */
const editVisible = ref(false)
const editSaving = ref(false)
const editForm = reactive({
  id: null,
  student_id: '',
  real_name: '',
  nickname: '',
  phone: '',
  role: 'alumni',
  college: '',
  major: '',
  grad_year: null,
})

function openEdit(u) {
  Object.assign(editForm, {
    id: u.id,
    student_id: u.student_id,
    real_name: u.real_name || '',
    nickname: u.nickname || '',
    phone: '',
    role: u.role || 'alumni',
    college: u.college || '',
    major: u.major || '',
    grad_year: u.grad_year ?? null,
  })
  editVisible.value = true
}

async function submitEdit() {
  if (!editForm.nickname.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  if (editForm.phone && !/^1\d{10}$/.test(editForm.phone)) {
    ElMessage.warning('手机号格式错误（11 位，1 开头）')
    return
  }
  editSaving.value = true
  try {
    // 字段缺省 = 不修改：手机号留空不改；学籍留空不改（避免误清空）
    const payload = { nickname: editForm.nickname.trim(), role: editForm.role }
    if (editForm.phone.trim()) payload.phone = editForm.phone.trim()
    if (editForm.college.trim()) payload.college = editForm.college.trim()
    if (editForm.major.trim()) payload.major = editForm.major.trim()
    if (editForm.grad_year) payload.grad_year = editForm.grad_year
    await updateUser(editForm.id, payload)
    ElMessage.success('已保存')
    editVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    editSaving.value = false
  }
}

/* ===== 重置密码弹窗 ===== */
const resetVisible = ref(false)
const resetSaving = ref(false)
const resetTarget = ref(null)
const resetForm = reactive({ pwd: '', pwd2: '' })

function openReset(u) {
  resetTarget.value = u
  resetForm.pwd = ''
  resetForm.pwd2 = ''
  resetVisible.value = true
}

async function submitReset() {
  if (resetForm.pwd.length < 8 || !(/[A-Za-z]/.test(resetForm.pwd) && /\d/.test(resetForm.pwd))) {
    ElMessage.warning('新密码至少 8 位，且须同时包含字母和数字')
    return
  }
  if (resetForm.pwd !== resetForm.pwd2) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  resetSaving.value = true
  try {
    // API 规范 1.7：密码前端 SHA-256 散列后上传
    await resetUserPassword(resetTarget.value.id, await sha256Hex(resetForm.pwd))
    resetVisible.value = false
    ElMessageBox.alert(
      `已为 ${resetTarget.value.real_name || resetTarget.value.nickname}（${resetTarget.value.student_id}）重置密码，该用户所有已登录设备已被强制下线。`,
      '重置成功',
      { confirmButtonText: '知道了', type: 'success' },
    )
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    resetSaving.value = false
  }
}

/* ===== 停权弹窗 ===== */
const suspendVisible = ref(false)
const suspendSaving = ref(false)
const suspendForm = reactive({ reason: '' })
const suspendTarget = ref(null)

function openSuspend(u) {
  suspendTarget.value = u
  suspendForm.reason = ''
  suspendVisible.value = true
}

async function submitSuspend() {
  if (!suspendForm.reason.trim()) {
    ElMessage.warning('请填写停权原因')
    return
  }
  suspendSaving.value = true
  try {
    await suspendUser(suspendTarget.value.id, suspendForm.reason.trim())
    suspendVisible.value = false
    ElMessageBox.alert(
      `已停权 ${suspendTarget.value.real_name || suspendTarget.value.nickname}，该用户所有登录态已立刻作废。`,
      '停权成功',
      { confirmButtonText: '知道了', type: 'success' },
    )
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    suspendSaving.value = false
  }
}

async function handleResume(u) {
  try {
    await ElMessageBox.confirm(
      `确认恢复 ${u.real_name || u.nickname} 的账号？`,
      '恢复账号',
      { type: 'info' },
    )
  } catch {
    return
  }
  try {
    await resumeUser(u.id)
    ElMessage.success('已恢复')
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/* ===== 交接弹窗 ===== */
const handoverVisible = ref(false)
const handoverSaving = ref(false)
const handoverTarget = ref(null)
const handoverForm = reactive({ target_user_id: null, reason: '' })
const handoverCandidates = ref([])

async function openHandover(u) {
  handoverTarget.value = u
  handoverForm.target_user_id = null
  handoverForm.reason = ''
  handoverCandidates.value = []
  handoverVisible.value = true
  // 拉取同角色候选列表
  try {
    const res = await getUsers({ role: u.role, page: 1, page_size: 100 })
    handoverCandidates.value = (res.data.list || []).filter(
      (x) => x.id !== u.id && x.status !== 'suspended',
    )
  } catch {
    handoverCandidates.value = []
  }
}

async function submitHandover() {
  if (!handoverForm.target_user_id) {
    ElMessage.warning('请选择接手人')
    return
  }
  if (!handoverForm.reason.trim()) {
    ElMessage.warning('请填写交接原因')
    return
  }
  handoverSaving.value = true
  try {
    const res = await handoverUser(
      handoverTarget.value.id,
      handoverForm.target_user_id,
      handoverForm.reason.trim(),
    )
    handoverVisible.value = false
    const n = res?.data?.transferred_reviews ?? 0
    ElMessageBox.alert(
      `已交接 ${handoverTarget.value.real_name || handoverTarget.value.nickname} 的待办（${n} 条），原账号已停权。`,
      '交接成功',
      { confirmButtonText: '知道了', type: 'success' },
    )
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    handoverSaving.value = false
  }
}

/* ===== 变更日志抽屉 ===== */
const logsVisible = ref(false)
const logsLoading = ref(false)
const logsTarget = ref(null)
const logsList = ref([])

async function openChangeLogs(u) {
  logsTarget.value = u
  logsList.value = []
  logsVisible.value = true
  await loadChangeLogs()
}

async function loadChangeLogs() {
  if (!logsTarget.value) return
  logsLoading.value = true
  try {
    const res = await getUserChangeLogs(logsTarget.value.id, { page: 1, page_size: 50 })
    logsList.value = res.data.list || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    logsLoading.value = false
  }
}

const CHANGE_ACTION_TEXT = {
  suspend: '停权',
  resume: '恢复',
  handover: '交接',
  password_reset: '重置密码',
  profile_update: '资料修改',
}
function changeActionText(a) {
  return CHANGE_ACTION_TEXT[a] || a || '-'
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="eyebrow">人员管理 · {{ isAlumniList ? '校友列表' : '学生列表' }}</div>
        <h1 class="page-title">{{ isAlumniList ? '校友列表' : '学生列表' }}</h1>
        <p class="page-sub">{{ pageSub }}</p>
      </div>
    </div>

    <!-- 统计 -->
    <div class="kpis">
      <div class="kpi">
        <div class="k-label">{{ isAlumniList ? '校友总数' : '学生总数' }}</div>
        <div class="k-val">{{ total }}</div>
      </div>
    </div>

    <!-- 工具栏：学籍筛选 + 关键词搜索（院级/导员隐藏学院筛选：后端已锁定范围） -->
    <div class="toolbar">
      <el-select
        v-if="!isScopeLimited"
        v-model="college"
        placeholder="学院"
        clearable
        filterable
        style="width: 170px"
        @change="handleSearch"
      >
        <el-option v-for="c in colleges" :key="c.id" :label="c.label" :value="c.label" />
      </el-select>
      <el-input
        v-model="major"
        placeholder="专业关键字"
        clearable
        style="width: 160px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select
        v-model="gradYear"
        placeholder="届别"
        clearable
        filterable
        style="width: 120px"
        @change="handleSearch"
      >
        <el-option v-for="y in yearOptions" :key="y" :label="y + ' 届'" :value="y" />
      </el-select>
      <el-button v-if="hasFilter" text type="primary" @click="resetFilters">重置筛选</el-button>

      <div class="toolbar-spacer"></div>

      <el-input
        v-model="keyword"
        placeholder="搜索学号 / 昵称"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button :icon="Refresh" @click="resetFilters">刷新</el-button>
      <el-button type="primary" plain :icon="Plus" @click="openCreate">
        新增{{ isAlumniList ? '校友' : '学生' }}
      </el-button>
      <el-button
        v-if="isSystemAdmin"
        :icon="Lock"
        type="warning"
        plain
        :disabled="!selectedRows.length"
        @click="openBatchReset"
      >
        批量重置密码（{{ selectedRows.length }}）
      </el-button>
    </div>

    <!-- 列表 -->
    <div class="list-card" v-loading="loading">
      <div class="user-row head">
        <div class="col-check" v-if="isSystemAdmin">
          <el-checkbox
            :model-value="allSelected"
            :indeterminate="someSelected && !allSelected"
            @change="toggleSelectAll"
          />
        </div>
        <div>成员</div>
        <div class="col-hide">学院 · 专业 · 届别</div>
        <div class="col-hide">手机号</div>
        <div>角色</div>
        <div class="col-hide">注册时间</div>
        <div>操作</div>
      </div>

      <template v-if="list.length">
        <div v-for="u in list" :key="u.id" class="user-row">
          <div class="col-check" v-if="isSystemAdmin">
            <el-checkbox
              :model-value="isRowSelected(u)"
              @change="(v) => toggleSelectRow(u, v)"
            />
          </div>
          <div class="col-main">
            <div class="cell-title">
              <span class="av">{{ (u.real_name || u.nickname || '?').charAt(0) }}</span>
              {{ u.real_name || u.nickname || '—' }}
              <span v-if="u.nickname && u.real_name" class="nick">昵称：{{ u.nickname }}</span>
            </div>
            <div class="cell-meta">{{ u.student_id }} · #{{ u.id }}</div>
          </div>
          <div class="cell col-hide cell-ellipsis">
            {{ [u.college, u.major].filter(Boolean).join(' · ') || '—' }}
            <div class="cell-meta">{{ u.grad_year ? u.grad_year + '届' : '—' }}</div>
          </div>
          <div class="cell col-hide mono">{{ u.phone_masked || '—' }}</div>
          <div>
            <el-tag :type="isAlumniList ? 'success' : 'info'" effect="light">
              {{ ROLE_TEXT[u.role] || u.role }}
            </el-tag>
          </div>
          <div class="cell col-hide">{{ formatTime(u.created_at) }}</div>
          <div class="ops">
            <template v-if="canEditRows">
              <el-button size="small" text type="primary" @click="openEdit(u)">编辑</el-button>
              <el-button size="small" text type="warning" @click="openReset(u)">重置密码</el-button>
            </template>
            <template v-if="isSystemAdmin">
              <el-button
                v-if="u.status !== 'suspended'"
                size="small"
                text
                type="danger"
                :icon="Lock"
                @click="openSuspend(u)"
              >停权</el-button>
              <el-button
                v-else
                size="small"
                text
                type="success"
                :icon="Unlock"
                @click="handleResume(u)"
              >恢复</el-button>
              <el-button
                size="small"
                text
                type="primary"
                :icon="Switch"
                :disabled="u.status === 'suspended'"
                @click="openHandover(u)"
              >交接</el-button>
              <el-button size="small" text :icon="List" @click="openChangeLogs(u)">变更日志</el-button>
            </template>
          </div>
        </div>
      </template>

      <div v-else-if="!loading" class="empty">
        <div class="ico"><LucideIcon name="users" :size="30" /></div>
        <h4>暂无{{ isAlumniList ? '校友' : '学生' }}账号</h4>
        <p>可点击上方「新增」直接创建，或通过批量导入 + 本人激活产生；调整筛选条件试试。</p>
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

    <!-- 新增账号弹窗 -->
    <el-dialog
      v-model="createVisible"
      :title="`新增${isAlumniList ? '校友' : '学生'}账号`"
      width="520px"
      destroy-on-close
    >
      <div class="dlg-meta">
        创建后账号立即可用，初始密码为系统默认密码（创建成功后会告知）；
        学号已在校友库但未激活的，将直接挂载代为激活（以校友库学籍为准）。
        <template v-if="isScopeLimited">{{ userStore.user?.role === 'college_admin' ? '仅可新增本院人员。' : '仅可新增管辖范围内的人员。' }}</template>
      </div>
      <el-form label-width="82px" label-position="left">
        <el-form-item label="学号" required>
          <el-input v-model="createForm.student_id" maxlength="32" placeholder="登录与核验的唯一标识" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="createForm.real_name" maxlength="64" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="createForm.phone" maxlength="11" placeholder="11 位手机号（登录用，全平台唯一）" />
        </el-form-item>
        <el-form-item v-if="!isScopeLimited" label="学院">
          <el-select v-model="createForm.college" filterable allow-create clearable style="width: 100%" placeholder="选择或输入学院">
            <el-option v-for="c in colleges" :key="c.id" :label="c.label" :value="c.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="专业">
          <el-input v-model="createForm.major" maxlength="64" placeholder="如 软件工程（可选）" />
        </el-form-item>
        <el-form-item label="届别" required>
          <el-select v-model="createForm.grad_year" filterable style="width: 160px" placeholder="毕业年份">
            <el-option v-for="y in yearOptions" :key="y" :label="y + ' 届'" :value="y" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" title="编辑用户信息" width="520px" destroy-on-close>
      <div class="dlg-meta">
        {{ editForm.real_name || '—' }} · {{ editForm.student_id }}（学号与身份证号不可修改）
      </div>
      <el-form label-width="82px" label-position="left">
        <el-form-item label="昵称" required>
          <el-input v-model="editForm.nickname" maxlength="64" placeholder="展示昵称" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="editForm.phone" maxlength="11" placeholder="留空 = 不修改（换绑须全平台唯一）" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editForm.role" style="width: 100%">
            <el-option v-for="(text, key) in ROLE_TEXT" :key="key" :label="text" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="学院">
          <el-select v-model="editForm.college" filterable allow-create clearable style="width: 100%" placeholder="留空 = 不修改">
            <el-option v-for="c in colleges" :key="c.id" :label="c.label" :value="c.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="专业">
          <el-input v-model="editForm.major" maxlength="64" placeholder="留空 = 不修改" />
        </el-form-item>
        <el-form-item label="届别">
          <el-input-number v-model="editForm.grad_year" :min="1900" :max="2100" controls-position="right" style="width: 160px" placeholder="不改可清空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="resetVisible" title="重置密码" width="440px" destroy-on-close>
      <div class="dlg-meta">
        即将为 {{ resetTarget?.real_name || resetTarget?.nickname || '—' }}（{{ resetTarget?.student_id }}）设置新密码；
        重置后该用户所有已登录设备将被强制下线。
      </div>
      <el-form label-width="82px" label-position="left">
        <el-form-item label="新密码" required>
          <el-input v-model="resetForm.pwd" type="password" show-password maxlength="32" placeholder="至少 8 位，含字母和数字" />
        </el-form-item>
        <el-form-item label="确认密码" required>
          <el-input v-model="resetForm.pwd2" type="password" show-password maxlength="32" placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="warning" :loading="resetSaving" @click="submitReset">确认重置</el-button>
      </template>
    </el-dialog>

    <!-- 停权弹窗 -->
    <el-dialog v-model="suspendVisible" title="停权账号" width="460px" destroy-on-close>
      <div class="dlg-meta">
        即将停权 {{ suspendTarget?.real_name || suspendTarget?.nickname || '—' }}（{{ suspendTarget?.student_id }}）；
        停权后该用户所有登录态立刻作废，无法登录后台直至被恢复。
      </div>
      <el-form label-width="82px" label-position="left">
        <el-form-item label="停权原因" required>
          <el-input v-model="suspendForm.reason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="如：长期未登录 / 离职 / 违规待处理" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="suspendVisible = false">取消</el-button>
        <el-button type="danger" :loading="suspendSaving" @click="submitSuspend">确认停权</el-button>
      </template>
    </el-dialog>

    <!-- 交接弹窗 -->
    <el-dialog v-model="handoverVisible" title="账号交接" width="520px" destroy-on-close>
      <div class="dlg-meta">
        即将把 {{ handoverTarget?.real_name || handoverTarget?.nickname || '—' }}（{{ handoverTarget?.student_id }}）的待办转移给接手人，
        原账号将停权。接手人需与原账号同角色。
      </div>
      <el-form label-width="82px" label-position="left">
        <el-form-item label="接手人" required>
          <el-select v-model="handoverForm.target_user_id" filterable style="width: 100%" placeholder="从同角色未停权成员中选择">
            <el-option
              v-for="c in handoverCandidates"
              :key="c.id"
              :label="`${c.real_name || c.nickname}（${c.student_id}）`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="交接原因" required>
          <el-input v-model="handoverForm.reason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="如：人事调动 / 离岗交接" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handoverVisible = false">取消</el-button>
        <el-button type="primary" :loading="handoverSaving" @click="submitHandover">确认交接</el-button>
      </template>
    </el-dialog>

    <!-- 变更日志抽屉 -->
    <el-drawer
      v-model="logsVisible"
      direction="rtl"
      size="460px"
      :title="`变更日志 · ${logsTarget?.real_name || logsTarget?.nickname || ''}`"
    >
      <template v-if="logsTarget">
        <div class="logs-summary">
          学号 {{ logsTarget.student_id }} · #{{ logsTarget.id }} · {{ ROLE_TEXT[logsTarget.role] || logsTarget.role }}
        </div>
        <div v-loading="logsLoading" class="logs-list">
          <div v-for="log in logsList" :key="log.id" class="log-item">
            <div class="log-line1">
              <span class="log-action">{{ changeActionText(log.action) }}</span>
              <span class="log-time">{{ formatTime(log.created_at) }}</span>
            </div>
            <div class="log-meta">操作人 #{{ log.operatorId || '—' }}</div>
            <div v-if="log.detail" class="log-detail">
              <pre>{{ JSON.stringify(log.detail, null, 2) }}</pre>
            </div>
          </div>
          <el-empty v-if="!logsLoading && !logsList.length" description="暂无变更记录" />
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
@import '../shared/list-shared.css';

.kpis {
  display: grid;
  grid-template-columns: 240px;
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

.user-row {
  display: grid;
  grid-template-columns: 40px 1.6fr 1.4fr 1fr 0.8fr 1fr 0.9fr;
  align-items: center;
  gap: 16px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--line);
  transition: background 0.12s;
}

.col-check {
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-row.head .col-check {
  /* 头部选择框不缩进，对齐 */
}

.ops {
  display: flex;
  gap: 2px;
  white-space: nowrap;
}

/* 变更日志抽屉 */
.logs-summary {
  padding: 8px 12px;
  margin-bottom: 12px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.04);
  font-size: 13px;
  color: var(--text-sub);
}
.logs-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.log-item {
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--card);
}
.log-line1 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.log-action {
  font-weight: 600;
  font-size: 13px;
}
.log-time {
  font-size: 12px;
  color: var(--text-sub);
}
.log-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-sub);
}
.log-detail {
  margin-top: 8px;
}
.log-detail pre {
  margin: 0;
  padding: 8px 10px;
  background: rgba(15, 23, 42, 0.04);
  border-radius: 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 180px;
  overflow: auto;
}

.dlg-meta {
  margin: -4px 0 16px;
  padding: 10px 14px;
  border-radius: 8px;
  background: var(--surface-2);
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--text-sub);
}

.user-row:hover {
  background: var(--surface-2);
}

.user-row.head {
  background: var(--surface-2);
  font-size: 12px;
  font-weight: 700;
  color: var(--text-sub);
  letter-spacing: 0.5px;
  border-bottom: 1px solid var(--line-strong);
}

.user-row:last-child {
  border-bottom: none;
}

.av {
  display: inline-flex;
  width: 26px;
  height: 26px;
  margin-right: 9px;
  border-radius: 50%;
  align-items: center;
  justify-content: center;
  background: var(--brand-50);
  color: var(--brand-600);
  font-size: 12.5px;
  font-weight: 700;
  vertical-align: middle;
}

.nick {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--text-sub);
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 20px;
}

@media (max-width: 1080px) {
  .user-row {
    grid-template-columns: 1.6fr 1fr;
  }
  .user-row .col-hide,
  .user-row.head .col-hide {
    display: none;
  }
}
</style>
