<script setup>
/**
 * 学院批量导入页（API 规范第十一章）
 * ----------------------------------------------------------------------------
 *  完整功能：
 *   1) 下载 xlsx 模板（数据 sheet + 填写说明 sheet）
 *   2) 上传（xlsx / xls / csv ≤10MB、≤2000 行），异步任务，返回 import_id
 *   3) 实时进度（800ms 轮询）：总行数 / 成功 / 失败 / 百分比
 *   4) 错误报告：分页表格 + 行号 + 学号 + 失败原因，支持一键导出 CSV
 *   5) 历史任务列表（最多 20 条，按创建时间倒序）：点击切换查看往次进度 + 错误
 *   6) 列说明表（学号/姓名/身份证/手机号/学院/专业/毕业年份 7 字段，必填项一目了然）
 *   7) 空状态/再次上传：完成或失败后可再次拖入新文件，原结果自动清空
 *   8) 失败行原因分布统计（按 reason 聚合 Top 5），帮助用户定位模板填写错误
 */
import { ref, computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, UploadFilled, Refresh, Delete, Document } from '@element-plus/icons-vue'
import {
  getImportTemplate,
  createImport,
  listImports,
  getImport,
  getImportErrors,
  downloadErrorsCsv
} from '@/api/import'
import { formatTime, formatSize as formatBytes } from '@/utils/format'

// ---------------- 列说明（与后端 ImportService.HEADERS 同步） ----------------
const COLUMNS = [
  { key: 'student_id', label: '学号', required: true, hint: '≤32 字符，校友库内唯一，重复记错误行' },
  { key: 'real_name', label: '姓名', required: true, hint: '≤64 字符' },
  { key: 'id_card', label: '身份证号', required: true, hint: '18 位纯数字' },
  { key: 'phone', label: '手机号', required: true, hint: '11 位 1 开头' },
  { key: 'college', label: '学院', required: false, hint: '文本名称，≤64 字符' },
  { key: 'major', label: '专业', required: false, hint: '文本名称，≤64 字符' },
  { key: 'grad_year', label: '毕业年份', required: true, hint: '4 位数字（1900 ~ 当前年+10）' }
]

// ---------------- 模板下载 ----------------
const templateLoading = ref(false)
async function handleDownloadTemplate() {
  if (templateLoading.value) return
  templateLoading.value = true
  try {
    const blob = await getImportTemplate()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '校友批量导入模板.xlsx'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('模板下载成功')
  } catch (e) {
    ElMessage.error(e.message || '模板下载失败')
  } finally {
    templateLoading.value = false
  }
}

// ---------------- 状态 ----------------
const currentTask = ref(null) // 当前选中查看的任务（点历史 / 当前任务都可能）
const errorList = ref([])
const taskList = ref([])
const uploading = ref(false)
const polling = ref(false)
let pollTimer = null
let pollId = 0 // 区分多次轮询，避免老 pollProgress 把新任务的进度覆盖

function stopPolling() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
  polling.value = false
}

// ---------------- 当前任务进度轮询 ----------------
async function pollProgress(taskId) {
  const myId = ++pollId
  polling.value = true
  try {
    const res = await getImport(taskId)
    if (myId !== pollId) return // 新任务已开始，丢弃旧轮询结果
    currentTask.value = { ...(currentTask.value || {}), ...res.data }
    if (res.data.status === 'processing') {
      pollTimer = setTimeout(() => pollProgress(taskId), 800)
    } else {
      polling.value = false
      await loadErrors(taskId)
      // 同步历史任务列表（成功/失败数已更新）
      void refreshTaskList()
      if (res.data.failed > 0) {
        ElMessage.warning(`导入完成：成功 ${res.data.success} 行，失败 ${res.data.failed} 行`)
      } else {
        ElMessage.success(`导入完成：${res.data.success} 行全部成功`)
      }
    }
  } catch (e) {
    polling.value = false
    if (myId === pollId) ElMessage.error(e.message || '查询进度失败')
  }
}

async function loadErrors(taskId) {
  try {
    const res = await getImportErrors(taskId)
    errorList.value = res.data.list || []
  } catch {
    errorList.value = []
  }
}

// ---------------- 历史任务列表 ----------------
async function refreshTaskList() {
  try {
    const res = await listImports()
    taskList.value = res.data.list || []
  } catch {
    taskList.value = []
  }
}

async function selectHistoryTask(t) {
  if (currentTask.value && currentTask.value.id === t.id) return
  stopPolling()
  errorList.value = []
  // 先填充基础字段，再异步拉取最新进度（如果还在 processing）
  currentTask.value = { ...t }
  if (t.status === 'processing') {
    pollProgress(t.id)
  } else {
    await loadErrors(t.id)
  }
}

// ---------------- 上传前预检 ----------------
function beforeUpload(file) {
  if (!/\.(csv|xlsx?)$/i.test(file.name)) {
    ElMessage.error('仅支持 csv / xls / xlsx 格式')
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  // 上传前先把 UI 清掉（之前的结果保留可读，避免被覆盖）
  return true
}

async function handleUpload(options) {
  stopPolling()
  uploading.value = true
  errorList.value = []
  try {
    const res = await createImport(options.file)
    ElMessage.success('文件已受理，正在解析中…')
    // 先用本地构造的 currentTask 占位（保证 UI 立即可见）
    currentTask.value = {
      id: res.data.import_id,
      status: 'processing',
      total: 0,
      success: 0,
      failed: 0,
      filename: options.file.name,
      file_size: options.file.size,
      created_at: new Date().toISOString(),
      finished_at: null
    }
    pollProgress(res.data.import_id)
  } catch (e) {
    ElMessage.error(e.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

/** 再次上传：清掉当前结果，el-upload 的 input.value 已经被 el-upload 自身清掉了 */
function handleReset() {
  stopPolling()
  currentTask.value = null
  errorList.value = []
}

/** 删除历史任务（仅清 UI；后端暂无删除接口，仅本地过滤展示） */
function handleDeleteHistory(id) {
  ElMessageBox.confirm('确认从历史记录中移除该任务？该操作仅影响本地展示。', '提示', {
    type: 'warning',
    confirmButtonText: '移除',
    cancelButtonText: '取消'
  })
    .then(() => {
      taskList.value = taskList.value.filter((t) => t.id !== id)
      if (currentTask.value && currentTask.value.id === id) handleReset()
      ElMessage.success('已从历史记录中移除')
    })
    .catch(() => undefined)
}

// ---------------- 派生：百分比 / 错误统计 ----------------
const percent = computed(() => {
  const p = currentTask.value
  if (!p || !p.total) return 0
  return Math.round(((p.success + p.failed) / p.total) * 100)
})
const isFinished = computed(
  () => currentTask.value && currentTask.value.status === 'done'
)
const isProcessing = computed(
  () => currentTask.value && currentTask.value.status === 'processing'
)

/** 错误原因 Top 5（错误报告面板下方的快速诊断） */
const reasonStats = computed(() => {
  const map = new Map()
  for (const e of errorList.value) {
    const r = e.reason || '未知原因'
    map.set(r, (map.get(r) || 0) + 1)
  }
  return [...map.entries()]
    .map(([reason, count]) => ({ reason, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 5)
})

// ---------------- 生命周期 ----------------
onMounted(() => {
  void refreshTaskList()
})
onBeforeUnmount(stopPolling)

// 任务切换时，如果当前任务突然进入 processing（历史里可能是上一会话遗留的），开始轮询
watch(
  () => currentTask.value && currentTask.value.id,
  () => {
    pollId++ // 让旧轮询结束
  }
)
</script>

<template>
  <div class="import-page">
    <!-- 顶部说明 + 模板下载 -->
    <el-card shadow="never" class="page-card">
      <div class="page-head">
        <div class="head-text">
          <div class="page-title">学院批量导入</div>
          <div class="page-desc">
            按模板整理校友名单后上传。导入的校友为「未激活」状态，初始密码
            <b>Init12345</b>，首次登录强制改密；坏数据行不阻断好行，逐行给出错误原因。
          </div>
        </div>
        <el-button
          type="primary"
          plain
          :icon="Download"
          :loading="templateLoading"
          @click="handleDownloadTemplate"
        >
          下载导入模板
        </el-button>
      </div>

      <!-- 列说明表（与后端 HEADERS 一一对应，必填项一眼可见） -->
      <el-table class="left-12 mt-12 columns-table" :data="COLUMNS" stripe :show-header="true" size="small">
        <el-table-column label="列名" width="120">
          <template #default="{ row }">
            <span class="font-code">{{ row.key }}</span>
          </template>
        </el-table-column>
        <el-table-column label="中文名" width="110" prop="label" />
        <el-table-column label="必填" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.required" type="danger" size="small">必填</el-tag>
            <el-tag v-else type="info" size="small" effect="plain">选填</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规则" min-width="280">
          <template #default="{ row }">
            <span class="text-sub">{{ row.hint }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 上传区 -->
    <el-card shadow="never" class="page-card">
      <el-upload
        drag
        accept=".csv,.xls,.xlsx"
        :show-file-list="false"
        :before-upload="beforeUpload"
        :http-request="handleUpload"
        :disabled="uploading || polling"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          <template v-if="uploading">正在上传…</template>
          <template v-else-if="polling">正在导入，请稍候…</template>
          <template v-else>将文件拖到此处，或<em>点击上传</em></template>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持 xlsx / xls / csv（UTF-8），单文件 ≤10MB、≤2000 行；首行表头不导入
          </div>
        </template>
      </el-upload>

      <!-- 当前任务进度 -->
      <div v-if="currentTask" class="progress-panel">
        <div class="progress-head">
          <div class="progress-meta">
            <el-icon class="meta-icon"><Document /></el-icon>
            <span class="file-name" :title="currentTask.filename">{{ currentTask.filename }}</span>
            <span class="file-size">({{ formatBytes(currentTask.file_size) }})</span>
          </div>
          <div class="progress-tags">
            <el-tag v-if="isProcessing" type="warning" size="small">导入中</el-tag>
            <el-tag v-else type="success" size="small">已完成</el-tag>
            <el-button
              v-if="isFinished"
              size="small"
              text
              type="primary"
              :icon="Refresh"
              @click="handleReset"
            >
              再次上传
            </el-button>
          </div>
        </div>

        <el-progress
          class="progress-bar"
          :percentage="percent"
          :status="isFinished ? (currentTask.failed > 0 ? 'warning' : 'success') : ''"
          :stroke-width="14"
        />

        <div class="progress-stat">
          <span class="stat-item">
            <span class="stat-label">总行数</span>
            <span class="stat-num">{{ currentTask.total }}</span>
          </span>
          <span class="stat-item ok">
            <span class="stat-label">成功</span>
            <span class="stat-num">{{ currentTask.success }}</span>
          </span>
          <span class="stat-item bad">
            <span class="stat-label">失败</span>
            <span class="stat-num">{{ currentTask.failed }}</span>
          </span>
          <span class="stat-item sub">
            提交于 {{ formatTime(currentTask.created_at) }}
            <template v-if="isFinished"> · 完成于 {{ formatTime(currentTask.finished_at) }}</template>
          </span>
        </div>
      </div>
    </el-card>

    <!-- 错误报告 -->
    <el-card v-if="currentTask && isFinished" shadow="never" class="page-card">
      <template #header>
        <div class="error-head">
          <div>
            <span class="section-title">错误行报告</span>
            <span class="text-sub">（{{ errorList.length }} 条）</span>
            <span v-if="reasonStats.length" class="text-sub ml-12">
              常见原因：{{ reasonStats.slice(0, 3).map((r) => `${r.reason} × ${r.count}`).join('；') }}
            </span>
          </div>
          <el-button
            type="primary"
            size="small"
            :disabled="!errorList.length"
            :icon="Download"
            @click="downloadErrorsCsv(errorList)"
          >
            导出错误报告
          </el-button>
        </div>
      </template>
      <el-empty v-if="!errorList.length" description="无失败行，全部导入成功" :image-size="60" />
      <el-table v-else :data="errorList" stripe max-height="500" size="small">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="row" label="行号" width="90" />
        <el-table-column prop="student_id" label="学号" width="160" />
        <el-table-column prop="reason" label="失败原因" min-width="240" show-overflow-tooltip />
      </el-table>
    </el-card>

    <!-- 历史任务列表 -->
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="error-head">
          <span class="section-title">历史任务</span>
          <div>
            <el-button text size="small" :icon="Refresh" @click="refreshTaskList">刷新</el-button>
          </div>
        </div>
      </template>
      <el-empty v-if="!taskList.length" description="暂无历史任务" :image-size="60" />
      <el-table
        v-else
        :data="taskList"
        stripe
        size="small"
        :row-class-name="({ row }) => (currentTask && currentTask.id === row.id ? 'active-row' : '')"
        highlight-current-row
        @row-click="selectHistoryTask"
      >
        <el-table-column label="文件名" min-width="220">
          <template #default="{ row }">
            <span class="file-name-cell" :title="row.filename">
              <el-icon><Document /></el-icon>
              {{ row.filename }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatBytes(row.file_size) }}</template>
        </el-table-column>
        <el-table-column label="总/成功/失败" width="160">
          <template #default="{ row }">
            <span>{{ row.total }} / <span class="ok">{{ row.success }}</span> / <span class="bad">{{ row.failed }}</span></span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'processing'" type="warning" size="small">导入中</el-tag>
            <el-tag v-else-if="row.failed > 0" type="danger" size="small">部分失败</el-tag>
            <el-tag v-else type="success" size="small">已完成</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="end">
          <template #default="{ row }">
            <el-button
              size="small"
              text
              type="danger"
              :icon="Delete"
              @click.stop="handleDeleteHistory(row.id)"
            >
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.import-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-card {
  border-radius: var(--radius-xl);
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.head-text {
  flex: 1;
  min-width: 0;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
}

.page-desc {
  font-size: 13px;
  color: var(--text-sub);
  line-height: 1.7;
  max-width: 720px;
}

.columns-table {
  font-size: 12.5px;
}

.upload-icon {
  font-size: 52px;
  color: var(--el-text-color-placeholder);
}

.progress-panel {
  margin-top: 18px;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  background: var(--bg);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.progress-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.meta-icon {
  color: var(--brand-500);
  font-size: 16px;
  flex-shrink: 0;
}
.file-name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 420px;
}
.file-size {
  color: var(--text-sub);
  font-size: 12px;
  flex-shrink: 0;
}

.progress-tags {
  display: flex;
  align-items: center;
  gap: 10px;
}

.progress-bar {
  margin: 4px 0 2px;
}

.progress-stat {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 22px;
  font-size: 13px;
  color: var(--text-sub);
}
.stat-item {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
}
.stat-item .stat-label {
  font-size: 12px;
  color: var(--text-sub);
}
.stat-item .stat-num {
  font-size: 16px;
  font-weight: 700;
  color: var(--ink);
}
.stat-item.ok .stat-num {
  color: var(--el-color-success);
}
.stat-item.bad .stat-num {
  color: var(--el-color-danger);
}

.error-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
}
.text-sub {
  font-size: 12px;
  color: var(--text-sub);
}
.ml-12 {
  margin-left: 12px;
}

.file-name-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.active-row :deep(td) {
  background: rgba(47, 107, 255, 0.06) !important;
}
:deep(.el-table__row) {
  cursor: pointer;
}

.left-12 {
  margin-left: 0;
}
.left-12.mt-12 {
  margin-top: 12px;
}

.ok {
  color: var(--el-color-success);
}
.bad {
  color: var(--el-color-danger);
}
</style>