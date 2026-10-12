<script setup>
/**
 * 辅导员管理（M34 · 仅 school_admin）
 * - 列表：按学院 / 关键词 / 状态筛选
 * - 详情抽屉：账号信息 + 管辖范围（届别 / 专业 / 学号）
 * - 新建 / 编辑：含 scope 多维度配置
 * - 停用 / 启用 / 重置密码
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search, Edit, Lock, Unlock, Key } from '@element-plus/icons-vue'
import {
  getCounselors,
  createCounselor,
  updateCounselor,
  getCounselor,
  disableCounselor,
  enableCounselor,
  resetCounselorPassword,
  statusOf,
} from '@/api/counselors'
import { getDict } from '@/api/dict'
import { sha256Hex } from '@/utils/crypto'
import { formatTime } from '@/utils/format'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const filter = reactive({ college_id: null, keyword: '', status: '' })
const colleges = ref([])

async function loadColleges() {
  try {
    colleges.value = await getDict('college')
  } catch {
    colleges.value = []
  }
}

async function loadList() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value }
    if (filter.college_id) params.college_id = filter.college_id
    if (filter.keyword.trim()) params.keyword = filter.keyword.trim()
    if (filter.status) params.status = filter.status
    const res = await getCounselors(params)
    list.value = (res.data.list || []).slice().sort((a, b) => a.id - b.id)
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  loadList()
}

function resetFilter() {
  filter.college_id = null
  filter.keyword = ''
  filter.status = ''
  onSearch()
}

const hasFilter = computed(() => filter.college_id || filter.keyword.trim() || filter.status)

const collegeNameOf = (id) => colleges.value.find((c) => c.id === id)?.label || id || '-'

/* ---------- 详情抽屉 ---------- */
const drawerOpen = ref(false)
const drawerLoading = ref(false)
const active = ref(null)

async function openDetail(row) {
  drawerOpen.value = true
  active.value = null
  drawerLoading.value = true
  try {
    const res = await getCounselor(row.id)
    active.value = res.data
  } catch (e) {
    ElMessage.error(e.message)
    drawerOpen.value = false
  } finally {
    drawerLoading.value = false
  }
}

function scopeSummary(s) {
  if (!s) return '-'
  const parts = []
  if (s.grad_years?.length) parts.push(`${s.grad_years.join('/')} 届`)
  if (s.major_ids?.length) parts.push(`专业 ${s.major_ids.length} 项`)
  if (s.student_ids?.length) parts.push(`指定学号 ${s.student_ids.length} 人`)
  return parts.join(' · ') || '未设置'
}

/* ---------- 新建 / 编辑 ---------- */
const editVisible = ref(false)
const editSaving = ref(false)
const isEditing = ref(false)
const editForm = reactive({
  id: null,
  real_name: '',
  phone: '',
  college_id: null,
  password: '',
  scope: { grad_years: [], major_ids: [], student_ids: [] },
})

function resetEditForm() {
  editForm.id = null
  editForm.real_name = ''
  editForm.phone = ''
  editForm.college_id = null
  editForm.password = ''
  editForm.scope = { grad_years: [], major_ids: [], student_ids: [] }
}

function openCreate() {
  isEditing.value = false
  resetEditForm()
  editVisible.value = true
}

async function openEdit(row) {
  isEditing.value = true
  resetEditForm()
  editVisible.value = true
  try {
    const res = await getCounselor(row.id)
    const d = res.data
    editForm.id = d.id
    editForm.real_name = d.realName || ''
    editForm.phone = ''
    editForm.college_id = d.collegeId || null
    editForm.scope = d.scope
      ? {
          grad_years: d.scope.grad_years || [],
          major_ids: d.scope.major_ids || [],
          student_ids: d.scope.student_ids || [],
        }
      : { grad_years: [], major_ids: [], student_ids: [] }
  } catch (e) {
    ElMessage.error(e.message)
    editVisible.value = false
  }
}

/* 重置密码子弹窗 */
const resetVisible = ref(false)
const resetSaving = ref(false)
const resetTarget = ref(null)
const resetForm = reactive({ pwd: '', pwd2: '' })
function openResetPwd(row) {
  resetTarget.value = row
  resetForm.pwd = ''
  resetForm.pwd2 = ''
  resetVisible.value = true
}
async function submitResetPwd() {
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
    await resetCounselorPassword(resetTarget.value.id, await sha256Hex(resetForm.pwd))
    resetVisible.value = false
    ElMessageBox.alert(
      `已为 ${resetTarget.value.realName} 重置密码，该用户所有已登录设备已强制下线。`,
      '重置成功',
      { confirmButtonText: '知道了', type: 'success' },
    )
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    resetSaving.value = false
  }
}

/* ---------- 提交新建 / 编辑 ---------- */
async function submitEdit() {
  if (!editForm.real_name.trim()) {
    ElMessage.warning('请填写真实姓名')
    return
  }
  if (!editForm.college_id) {
    ElMessage.warning('请选择所属学院')
    return
  }
  const scopeEmpty =
    !editForm.scope.grad_years.length &&
    !editForm.scope.major_ids.length &&
    !editForm.scope.student_ids.length
  if (scopeEmpty) {
    ElMessage.warning('管辖范围至少设置一项（届别 / 专业 / 学号）')
    return
  }
  editSaving.value = true
  try {
    if (isEditing.value) {
      const payload = {
        real_name: editForm.real_name.trim(),
        college_id: editForm.college_id,
        scope: editForm.scope,
      }
      if (editForm.phone.trim()) payload.phone = editForm.phone.trim()
      await updateCounselor(editForm.id, payload)
      ElMessage.success('已保存')
    } else {
      if (!/^1\d{10}$/.test(editForm.phone)) {
        ElMessage.warning('手机号格式错误（11 位，1 开头）')
        editSaving.value = false
        return
      }
      if (!editForm.password) {
        ElMessage.warning('请填写初始密码')
        editSaving.value = false
        return
      }
      if (editForm.password.length < 8) {
        ElMessage.warning('初始密码至少 8 位')
        editSaving.value = false
        return
      }
      await createCounselor({
        real_name: editForm.real_name.trim(),
        phone: editForm.phone.trim(),
        college_id: editForm.college_id,
        password: await sha256Hex(editForm.password),
        scope: editForm.scope,
      })
      ElMessage.success('已创建')
    }
    editVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    editSaving.value = false
  }
}

/* ---------- 停用 / 启用 ---------- */
async function handleToggle(row) {
  const isActive = row.status === 'active'
  const action = isActive ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确认${action}导员 ${row.realName}？${isActive ? '停用后，在途待办将回退至院系初审。' : ''}`,
      `${action}导员`,
      { type: isActive ? 'warning' : 'info' },
    )
  } catch {
    return
  }
  try {
    if (isActive) await disableCounselor(row.id)
    else await enableCounselor(row.id)
    ElMessage.success(`已${action}`)
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  await loadColleges()
  loadList()
})
</script>

<template>
  <div class="counselor-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">辅导员管理</h2>
        <p class="page-sub">维护参与档案初审的辅导员账号及其管辖范围（届别 / 专业 / 学号）；仅校级管理员可访问</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建辅导员</el-button>
    </div>

    <div class="filter-bar">
      <el-select
        v-model="filter.college_id"
        placeholder="学院"
        clearable
        filterable
        style="width: 180px"
        @change="onSearch"
      >
        <el-option v-for="c in colleges" :key="c.id" :label="c.label" :value="c.id" />
      </el-select>
      <el-select
        v-model="filter.status"
        placeholder="状态"
        clearable
        style="width: 120px"
        @change="onSearch"
      >
        <el-option label="在岗" value="active" />
        <el-option label="已停用" value="disabled" />
      </el-select>
      <el-input
        v-model="filter.keyword"
        placeholder="搜索姓名 / 手机号"
        clearable
        style="width: 200px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button v-if="hasFilter" text @click="resetFilter">重置</el-button>
      <el-button :icon="Refresh" @click="loadList">刷新</el-button>
    </div>

    <el-table :data="list" v-loading="loading" class="counselor-table" stripe>
      <el-table-column prop="id" label="ID" width="64" />
      <el-table-column prop="realName" label="姓名" min-width="100" />
      <el-table-column label="手机号" width="140">
        <template #default="{ row }">
          <span class="mono">{{ row.phone }}</span>
        </template>
      </el-table-column>
      <el-table-column label="学院" min-width="160">
        <template #default="{ row }">{{ collegeNameOf(row.collegeId) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusOf(row.status).type" effect="light">{{ statusOf(row.status).text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="150">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <div class="ops-row">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" :icon="Key" @click="openResetPwd(row)">重置密码</el-button>
            <el-button
              v-if="row.status === 'active'"
              link
              type="info"
              :icon="Lock"
              @click="handleToggle(row)"
            >停用</el-button>
            <el-button
              v-else
              link
              type="success"
              :icon="Unlock"
              @click="handleToggle(row)"
            >启用</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        :total="total"
        :page-size="pageSize"
        layout="total, prev, pager, next"
        @current-change="loadList"
      />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerOpen" direction="rtl" size="460px" title="辅导员详情">
      <div v-loading="drawerLoading" class="detail">
        <template v-if="active">
          <div class="detail-row">
            <div class="k">姓名</div>
            <div class="v">{{ active.realName }}</div>
          </div>
          <div class="detail-row">
            <div class="k">手机号</div>
            <div class="v mono">{{ active.phone }}</div>
          </div>
          <div class="detail-row">
            <div class="k">学院</div>
            <div class="v">{{ active.college }}</div>
          </div>
          <div class="detail-row">
            <div class="k">状态</div>
            <div class="v">
              <el-tag :type="statusOf(active.status).type" effect="light">
                {{ statusOf(active.status).text }}
              </el-tag>
            </div>
          </div>
          <div class="detail-row">
            <div class="k">创建时间</div>
            <div class="v">{{ formatTime(active.createdAt) }}</div>
          </div>
          <div class="detail-row col">
            <div class="k">管辖范围</div>
            <div class="scope">
              <div v-if="active.scope?.grad_years?.length" class="scope-line">
                <span class="scope-key">届别：</span>
                <el-tag
                  v-for="y in active.scope.grad_years"
                  :key="'y' + y"
                  effect="plain"
                  size="small"
                >{{ y }} 届</el-tag>
              </div>
              <div v-if="active.scope?.major_ids?.length" class="scope-line">
                <span class="scope-key">专业：</span>
                <el-tag
                  v-for="m in active.scope.major_ids"
                  :key="'m' + m"
                  effect="plain"
                  size="small"
                >#{{ m }}</el-tag>
              </div>
              <div v-if="active.scope?.student_ids?.length" class="scope-line">
                <span class="scope-key">指定学号：</span>
                <el-tag
                  v-for="s in active.scope.student_ids"
                  :key="'s' + s"
                  effect="plain"
                  size="small"
                >{{ s }}</el-tag>
              </div>
              <span v-if="scopeSummary(active.scope) === '-'" class="muted">未设置</span>
            </div>
          </div>
        </template>
      </div>
    </el-drawer>

    <!-- 新建 / 编辑 -->
    <el-dialog
      v-model="editVisible"
      :title="isEditing ? '编辑辅导员' : '新建辅导员'"
      width="640px"
      destroy-on-close
    >
      <el-form label-width="92px" label-position="left">
        <el-form-item label="姓名" required>
          <el-input v-model="editForm.real_name" maxlength="64" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="手机号" :required="!isEditing">
          <el-input
            v-model="editForm.phone"
            maxlength="11"
            :placeholder="isEditing ? '留空 = 不修改' : '11 位手机号'"
          />
        </el-form-item>
        <el-form-item v-if="!isEditing" label="初始密码" required>
          <el-input v-model="editForm.password" type="password" show-password maxlength="32" placeholder="至少 8 位" />
        </el-form-item>
        <el-form-item label="所属学院" required>
          <el-select v-model="editForm.college_id" filterable style="width: 100%">
            <el-option v-for="c in colleges" :key="c.id" :label="c.label" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="管辖范围" required>
          <div class="scope-edit">
            <div class="scope-block">
              <div class="scope-title">届别</div>
              <el-select
                v-model="editForm.scope.grad_years"
                multiple
                filterable
                allow-create
                placeholder="可选多个届别"
                style="width: 100%"
              >
                <el-option
                  v-for="y in Array.from({ length: 30 }, (_, i) => new Date().getFullYear() - i)"
                  :key="y"
                  :label="y + ' 届'"
                  :value="y"
                />
              </el-select>
            </div>
            <div class="scope-block">
              <div class="scope-title">专业 ID（多选）</div>
              <el-select
                v-model="editForm.scope.major_ids"
                multiple
                filterable
                allow-create
                placeholder="输入专业 ID 回车添加"
                style="width: 100%"
              />
            </div>
            <div class="scope-block">
              <div class="scope-title">指定学号</div>
              <el-select
                v-model="editForm.scope.student_ids"
                multiple
                filterable
                allow-create
                placeholder="输入学号回车添加"
                style="width: 100%"
              />
            </div>
          </div>
          <div class="scope-tip">管辖范围 = 届别 ∪ 专业 ∪ 学号，三者并集；可单独使用任一项</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="submitEdit">
          {{ isEditing ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="resetVisible" title="重置密码" width="440px" destroy-on-close>
      <div class="dlg-meta">
        即将为 {{ resetTarget?.realName }} 重置密码，重置后该用户所有已登录设备将被强制下线。
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
        <el-button type="warning" :loading="resetSaving" @click="submitResetPwd">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.counselor-page {
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
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.counselor-table {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  overflow: hidden;
}
.ops-row {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  white-space: nowrap;
}
.ops-row .el-button + .el-button {
  margin-left: 6px;
}
.pager {
  display: flex;
  justify-content: flex-end;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

/* 详情 */
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
  gap: 6px;
}
.k {
  width: 76px;
  flex: none;
  font-size: 12px;
  color: var(--text-sub);
}
.v {
  font-size: 14px;
  word-break: break-word;
}
.muted {
  color: var(--text-sub);
}
.scope {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.scope-line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.scope-key {
  width: 70px;
  flex: none;
  font-size: 12px;
  color: var(--text-sub);
}

/* 弹窗 */
.scope-edit {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}
.scope-block {
  background: rgba(15, 23, 42, 0.03);
  padding: 10px 12px;
  border-radius: 8px;
}
.scope-title {
  font-size: 12px;
  color: var(--text-sub);
  margin-bottom: 6px;
}
.scope-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-sub);
  line-height: 1.5;
}
.dlg-meta {
  margin-bottom: 14px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.04);
  font-size: 13px;
  color: var(--text-sub);
}
</style>