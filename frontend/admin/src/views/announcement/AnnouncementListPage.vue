<script setup>
/**
 * 公告列表页（清单第七章 / PRD M08）
 * 双视图按角色自动切换：
 * - 管理员（院系/校级/系统）：维护视图 —— 全部状态检索、新建、编辑、发布、撤下、删除
 * - 校友 / 学生 / 导员：公开视图 —— 仅已发布公告的卡片流，点击进详情
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, View, Promotion, Download } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  getAnnouncements,
  updateAnnouncement,
  deleteAnnouncement,
  ANNOUNCEMENT_STATUS,
} from '@/api/announcement'
import { formatTime } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

const MANAGE_ROLES = ['college_admin', 'school_admin', 'system_admin']
const isManager = computed(() => MANAGE_ROLES.includes(userStore.user?.role))

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const statusFilter = ref('') // '' = 全部（仅管理员）

const statusOf = (s) => ANNOUNCEMENT_STATUS[s] || { text: s, tone: 'info' }

async function loadList() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize }
    if (isManager.value && statusFilter.value) params.status = statusFilter.value
    if (!isManager.value) params.status = 'published'
    const res = await getAnnouncements(params)
    // 默认按 ID 降序（最新的在前）
    list.value = (res.data.list || []).slice().sort((a, b) => a.id - b.id)
    total.value = res.data.total
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  page.value = 1
  loadList()
}

// ---------------- 管理员操作 ----------------
function goCreate() {
  router.push('/announcements/edit')
}

function goEdit(row) {
  router.push(`/announcements/edit/${row.id}`)
}

function goView(row) {
  router.push(`/announcements/view/${row.id}`)
}

async function handlePublish(row) {
  try {
    await ElMessageBox.confirm(`确认发布「${row.title}」？发布后对所有用户可见。`, '发布公告', { type: 'info' })
  } catch {
    return
  }
  try {
    await updateAnnouncement(row.id, { status: 'published' })
    ElMessage.success('已发布')
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function handleWithdraw(row) {
  try {
    await ElMessageBox.confirm(`确认撤下「${row.title}」？撤下后前台不再展示，可随时重新发布。`, '撤下公告', { type: 'warning' })
  } catch {
    return
  }
  try {
    await updateAnnouncement(row.id, { status: 'withdrawn' })
    ElMessage.success('已撤下')
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.title}」吗？删除后不可恢复。`, '删除公告', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteAnnouncement(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(loadList)
</script>

<template>
  <div class="ann-page">
    <!-- ==================== 管理员维护视图 ==================== -->
    <template v-if="isManager">
      <div class="page-head">
        <div>
          <h2 class="page-title">公告管理</h2>
          <p class="page-sub">面向全体用户发布通知公告，支持图文混排与 Word 导入（草稿 → 发布 ↔ 撤下）</p>
        </div>
        <el-button type="primary" :icon="Plus" @click="goCreate">新建公告</el-button>
      </div>

      <div class="filter-bar">
        <el-radio-group v-model="statusFilter" @change="onFilterChange">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="draft">草稿</el-radio-button>
          <el-radio-button value="published">已发布</el-radio-button>
          <el-radio-button value="withdrawn">已撤下</el-radio-button>
        </el-radio-group>
      </div>

      <el-table :data="list" v-loading="loading" class="ann-table">
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column label="标题" min-width="260">
          <template #default="{ row }">
            <span class="ann-title" @click="goView(row)">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <span class="status-tag" :class="'tone-' + statusOf(row.status).tone">{{ statusOf(row.status).text }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="publisher" label="发布人" width="120" />
        <el-table-column label="发布时间" width="150">
          <template #default="{ row }">{{ formatTime(row.published_at) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ formatTime(row.updated_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right" class-name="ops-col">
          <template #default="{ row }">
            <div class="ops-row">
              <el-button link type="primary" :icon="View" @click="goView(row)">预览</el-button>
              <el-button link type="primary" :icon="Edit" @click="goEdit(row)">编辑</el-button>
              <el-button v-if="row.status === 'draft'" link type="success" :icon="Promotion" @click="handlePublish(row)">发布</el-button>
              <el-button v-if="row.status === 'published'" link type="warning" :icon="Download" @click="handleWithdraw(row)">撤下</el-button>
              <el-button v-if="row.status === 'withdrawn'" link type="success" :icon="Promotion" @click="handlePublish(row)">重新发布</el-button>
              <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
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
    </template>

    <!-- ==================== 公开视图（校友 / 导员） ==================== -->
    <template v-else>
      <div class="page-head">
        <div>
          <h2 class="page-title">通知公告</h2>
          <p class="page-sub">学校与校友工作办公室发布的最新通知</p>
        </div>
      </div>

      <div v-loading="loading" class="public-list">
        <div v-for="row in list" :key="row.id" class="pub-card" @click="goView(row)">
          <div class="pub-title">{{ row.title }}</div>
          <div class="pub-meta">
            <span>{{ row.source }}</span>
            <span class="dot">·</span>
            <span>{{ formatTime(row.published_at) }}</span>
          </div>
        </div>
        <el-empty v-if="!loading && !list.length" description="暂无公告" />

        <div class="pager" v-if="total > pageSize">
          <el-pagination
            v-model:current-page="page"
            :total="total"
            :page-size="pageSize"
            layout="prev, pager, next"
            @current-change="loadList"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.ann-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.page-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-sub);
}

.filter-bar {
  display: flex;
}

.ann-table {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  overflow: hidden;
}

.ann-title {
  cursor: pointer;
  font-weight: 600;
}
.ann-title:hover {
  color: var(--brand);
}

/* 操作列：按钮强制同一行，不换行不压缩 */
.ann-table :deep(.ops-col .cell) {
  overflow: visible;
}
.ops-row {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  white-space: nowrap;
}
.ops-row .el-button + .el-button {
  margin-left: 8px;
  flex-shrink: 0;
}

.status-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: var(--radius-full);
  font-size: 12px;
}
.tone-info {
  color: #64748b;
  background: rgba(100, 116, 139, 0.12);
}
.tone-success {
  color: #059669;
  background: rgba(5, 150, 105, 0.12);
}
.tone-warning {
  color: #d97706;
  background: rgba(217, 119, 6, 0.12);
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 4px 0;
}

/* 公开视图 */
.public-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 200px;
}

.pub-card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: 16px 20px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}
.pub-card:hover {
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.pub-title {
  font-size: 15px;
  font-weight: 600;
}

.pub-meta {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-sub);
  display: flex;
  gap: 6px;
}
.dot {
  opacity: 0.6;
}
</style>
