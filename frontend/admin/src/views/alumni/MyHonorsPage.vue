<script setup>
/**
 * 我的荣誉页（PC 端校友视角，PRD M03：荣誉/证书/奖项统一录入）
 * 逻辑对齐 H5 端 HonorFormPage + HonorListPage：
 * - 录入：名称/类别（honor_category 字典）/级别/授予单位（文本，字典化待 B1 确认）/日期/证书原件（可切换）
 * - 可选关联本人档案；编辑模式 PATCH（仅待初审可改）
 * - 列表：状态/附件预览
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { CaretTop, CaretBottom } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createHonor, getHonors, getHonor, updateHonor, HONOR_LEVELS } from '@/api/honor'
import { getDict } from '@/api/dict'
import { getMyArchives } from '@/api/archive'
import { statusOf, formatTime } from '@/utils/format'
import BatchUploader from '@/components/BatchUploader.vue'
import AttachmentPreviewer from '@/components/AttachmentPreviewer.vue'

const route = useRoute()
const router = useRouter()

const editId = computed(() => (route.query.id ? Number(route.query.id) : null))

// ---------------- 表单 ----------------
const form = reactive({
  title: '',
  category: '',
  level: '',
  awarded_at: '',
  granted_by: '',
  archive_id: null,
})
const submitting = ref(false)

// 证书原件
const hasOriginal = ref(true)
const attachments = ref([])
const keptIds = ref([])
const batchRemark = ref('')

function onToggleOriginal(val) {
  if (!val) {
    attachments.value = []
    keptIds.value = []
    batchRemark.value = ''
  }
}

// 字典
const categoryOptions = ref([])

// 我的档案（关联用）
const myArchives = ref([])

// ---------------- 列表 ----------------
const loading = ref(false)
const list = ref([])
/** 时间轴顺序：'desc' 最新在上；'asc' 最早在上 */
const order = ref('desc')
const orderIcon = computed(() => (order.value === 'desc' ? CaretTop : CaretBottom))
const orderLabel = computed(() => (order.value === 'desc' ? '最新在上' : '最早在上'))
function toggleOrder() {
  order.value = order.value === 'desc' ? 'asc' : 'desc'
}

/** 按年分组（按 awarded_at 取年份；按 order 排序） */
const grouped = computed(() => {
  const sorted = list.value.slice().sort((a, b) => {
    const ta = new Date(a.awarded_at || a.created_at || 0).getTime()
    const tb = new Date(b.awarded_at || b.created_at || 0).getTime()
    return order.value === 'desc' ? tb - ta : ta - tb
  })
  const map = new Map()
  for (const h of sorted) {
    const d = h.awarded_at ? new Date(h.awarded_at) : null
    const y = d && !Number.isNaN(d.getFullYear()) ? d.getFullYear() : '其他'
    if (!map.has(y)) map.set(y, [])
    map.get(y).push(h)
  }
  return Array.from(map.entries()).map(([year, items]) => ({ year, items }))
})

async function loadList() {
  loading.value = true
  try {
    const res = await getHonors({ page: 1, page_size: 50 })
    list.value = res.data.list || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

// 详情抽屉
const detailVisible = ref(false)
const detail = ref(null)
function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

// ---------------- 校验/提交 ----------------
function validate() {
  if (!form.title.trim()) return ElMessage.warning('请填写荣誉名称'), false
  if (!form.category) return ElMessage.warning('请选择荣誉类别'), false
  if (!form.level) return ElMessage.warning('请选择荣誉级别'), false
  if (!form.granted_by.trim()) return ElMessage.warning('请填写授予单位'), false
  if (!/^\d{4}-\d{2}-\d{2}$/.test(form.awarded_at)) return ElMessage.warning('请选择授予日期'), false
  if (hasOriginal.value && !attachments.value.length && !keptIds.value.length)
    return ElMessage.warning('有证书原件时请上传原件附件'), false
  return true
}

async function handleSubmit() {
  if (submitting.value) return
  if (!validate()) return
  const payload = {
    title: form.title.trim(),
    category: form.category,
    level: form.level,
    awarded_at: form.awarded_at,
    granted_by: form.granted_by.trim(),
    archive_id: form.archive_id,
    attachment_ids: hasOriginal.value
      ? [...keptIds.value, ...attachments.value.map((a) => a.id)]
      : [],
  }
  submitting.value = true
  try {
    if (editId.value) {
      await updateHonor(editId.value, payload)
      ElMessage.success('修改成功')
    } else {
      await createHonor(payload)
      ElMessage.success('提交成功，等待院系初审')
    }
    resetForm()
    router.replace('/my-honors')
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  Object.assign(form, { title: '', category: '', level: '', awarded_at: '', granted_by: '', archive_id: null })
  hasOriginal.value = true
  attachments.value = []
  keptIds.value = []
  batchRemark.value = ''
}

// ---------------- 编辑模式 ----------------
async function loadHonor() {
  try {
    const res = await getHonor(editId.value)
    const h = res.data
    form.title = h.title
    form.category = h.category
    form.level = h.level
    form.awarded_at = h.awarded_at
    form.granted_by = h.granted_by || ''
    form.archive_id = h.archive_id
    keptIds.value = h.attachment_ids || []
    hasOriginal.value = keptIds.value.length > 0
  } catch (e) {
    ElMessage.error(e.message)
    router.replace('/my-honors')
  }
}

onMounted(async () => {
  loadList()
  try {
    const list = await getDict('honor_category')
    categoryOptions.value = (list || []).map((d) => ({ label: d.label, value: d.value }))
  } catch {
    categoryOptions.value = []
  }
  try {
    const res = await getMyArchives({ page: 1, page_size: 50 })
    myArchives.value = res.data.list || []
  } catch {
    myArchives.value = []
  }
  if (editId.value) loadHonor()
})
</script>

<template>
  <div class="my-honors-page">
    <div class="page-head">
      <div class="eyebrow">我的荣誉</div>
      <h1 class="page-title">{{ editId ? '修改荣誉' : '我的荣誉' }}</h1>
    </div>

    <!-- 录入表单 -->
    <div class="form-card">
      <div class="card-label">{{ editId ? '修改荣誉信息' : '录入新荣誉（荣誉 / 证书 / 奖项统一）' }}</div>
      <el-form label-position="top" class="honor-form">
        <div class="grid-2">
          <el-form-item label="荣誉名称" required>
            <el-input v-model="form.title" placeholder="如：全国大学生程序设计竞赛金奖" maxlength="50" show-word-limit clearable />
          </el-form-item>
          <el-form-item label="荣誉类别" required>
            <el-select v-model="form.category" placeholder="请选择类别" style="width: 100%">
              <el-option v-for="o in categoryOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="荣誉级别" required>
            <el-select v-model="form.level" placeholder="请选择级别" style="width: 100%">
              <el-option v-for="(label, value) in HONOR_LEVELS" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item label="授予日期" required>
            <el-date-picker
              v-model="form.awarded_at"
              type="date"
              placeholder="请选择日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="授予单位" required>
            <el-input v-model="form.granted_by" placeholder="如：教育部 / 竞赛组委会" maxlength="50" clearable />
          </el-form-item>
          <el-form-item label="关联档案（可选）">
            <el-select v-model="form.archive_id" placeholder="不关联档案" clearable style="width: 100%">
              <el-option v-for="a in myArchives" :key="a.id" :label="a.title" :value="a.id" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="证书原件">
          <el-switch v-model="hasOriginal" @change="onToggleOriginal" />
          <span class="switch-tip">{{ hasOriginal ? '上传证书照片/扫描件' : '无原件，仅文字记录' }}</span>
        </el-form-item>

        <BatchUploader
          v-if="hasOriginal"
          v-model:files="attachments"
          v-model:remark="batchRemark"
          biz-type="honor"
          :max-count="9"
        />

        <div class="action-bar">
          <el-button v-if="editId" @click="router.replace('/my-honors')">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ editId ? '保存修改' : '提交录入' }}
          </el-button>
        </div>
      </el-form>
    </div>

    <!-- 我的荣誉列表（按年时间线） -->
    <div class="list-card" v-loading="loading">
      <div class="list-head">
        <span>已提交荣誉（{{ list.length }}）</span>
        <el-button
          link
          type="primary"
          :icon="orderIcon"
          class="order-toggle"
          @click="toggleOrder"
        >
          {{ orderLabel }}
        </el-button>
      </div>
      <template v-if="grouped.length">
        <div v-for="group in grouped" :key="group.year" class="year-block">
          <div class="year-mark">
            <span class="year-text">{{ group.year }}</span>
            <span class="year-count">{{ group.items.length }} 项</span>
          </div>
          <div class="year-items">
            <div
              v-for="row in group.items"
              :key="row.id"
              class="list-row"
              @click="openDetail(row)"
            >
              <div class="dot" aria-hidden="true"></div>
              <div class="row-main">
                <div class="row-title">
                  <span class="title-text">{{ row.title }}</span>
                  <el-tag :type="statusOf(row.status).type" size="small" effect="light">
                    {{ statusOf(row.status).text }}
                  </el-tag>
                </div>
                <div class="row-meta">
                  <span>{{ HONOR_LEVELS[row.level] || row.level }}</span>
                  <span>{{ row.awarded_at }}</span>
                  <span>{{ row.granted_by }}</span>
                </div>
                <div v-if="row.status === 'rejected' && row.reject_reason" class="row-reject">
                  驳回原因：{{ row.reject_reason }}
                </div>
              </div>
              <div class="row-actions">
                <el-button
                  v-if="row.status === 'pending_first'"
                  link
                  type="primary"
                  size="small"
                  @click.stop="router.push({ path: '/my-honors', query: { id: row.id } })"
                >
                  修改
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="还没有录入过荣誉" :image-size="80" />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="荣誉详情" size="520px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="荣誉名称" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="级别">{{ HONOR_LEVELS[detail.level] || detail.level }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusOf(detail.status).type" size="small">{{ statusOf(detail.status).text }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="授予单位" :span="2">{{ detail.granted_by || '-' }}</el-descriptions-item>
          <el-descriptions-item label="授予日期">{{ detail.awarded_at }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatTime(detail.created_at) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.reject_reason" label="驳回原因" :span="2">
            {{ detail.reject_reason }}
          </el-descriptions-item>
        </el-descriptions>
        <h4 class="section-title">证书原件</h4>
        <AttachmentPreviewer :attachments="detail.attachments || []" />
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-head {
  margin-bottom: 20px;
}

.eyebrow {
  font-size: 11px;
  letter-spacing: 0.22em;
  color: var(--text-mute);
  margin-bottom: 6px;
}

.page-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 26px;
  font-weight: 700;
  color: var(--text-main);
}

.form-card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: 20px 24px;
  margin-bottom: 18px;
  box-shadow: var(--shadow-sm);
}

.card-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 14px;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 18px;
}

.switch-tip {
  margin-left: 10px;
  font-size: 12.5px;
  color: var(--text-mute);
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.list-card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.list-head {
  padding: 14px 20px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
  border-bottom: 1px solid var(--line);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.order-toggle {
  font-size: 12px;
}

/* 时间线：按年聚合 */
.year-block {
  border-bottom: 1px solid var(--line);
}
.year-block:last-child {
  border-bottom: none;
}
.year-mark {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  background: var(--surface-2, rgba(15, 23, 42, 0.04));
  border-bottom: 1px dashed var(--line);
}
.year-text {
  font-size: 16px;
  font-weight: 700;
  color: var(--brand, #2f6bff);
}
.year-count {
  font-size: 12px;
  color: var(--text-sub);
}
.year-items {
  padding: 4px 0 8px;
}

.list-row {
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 20px 14px 50px;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
  transition: background 0.15s;
}
.list-row:last-child {
  border-bottom: none;
}
.list-row:hover {
  background: var(--surface-2);
}
.list-row .dot {
  position: absolute;
  left: 26px;
  top: 22px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--brand, #2f6bff);
  box-shadow: 0 0 0 3px rgba(47, 107, 255, 0.18);
}
/* 时间轴竖线 */
.year-items {
  position: relative;
}
.year-items::before {
  content: '';
  position: absolute;
  left: 30px;
  top: 12px;
  bottom: 12px;
  width: 2px;
  background: linear-gradient(180deg, rgba(47, 107, 255, 0.25), rgba(47, 107, 255, 0.05));
}

.row-main {
  flex: 1;
  min-width: 0;
}

.row-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-text {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-meta {
  display: flex;
  gap: 14px;
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--text-sub);
}

.row-reject {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: var(--red-50, #fef2f2);
  color: var(--red-700, #b91c1c);
  font-size: 12.5px;
}

.row-actions {
  flex: none;
  display: flex;
  align-items: center;
}

.section-title {
  margin: 18px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
}

@media (max-width: 900px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
