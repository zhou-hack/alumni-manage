<script setup>
/**
 * 草稿箱（所有登录用户可看自己的草稿）
 * - 列表：按 bizType 过滤 / 关键词搜索（payload 里的 title）
 * - 加载：将该草稿写入 localStorage 对应 key（与编辑器一致）
 * - 删除：单条 / 全部已加载过的本地草稿
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, Delete, EditPen, Upload } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { getMyDrafts, deleteDraftById } from '@/api/draft'
import { formatTime } from '@/utils/format'

const router = useRouter()
const STORAGE_KEY = 'alumni_admin_draft_archive'

const loading = ref(false)
const list = ref([])
const filter = reactive({ biz_type: '', keyword: '' })

async function loadList() {
  loading.value = true
  try {
    let res
    if (filter.biz_type) {
      res = await getMyDrafts(filter.biz_type)
      list.value = res.data?.list || []
    } else {
      res = await getMyDrafts()
      list.value = res.data?.list || []
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  loadList()
}

const hasFilter = computed(() => filter.biz_type || filter.keyword.trim())
function resetFilter() {
  filter.biz_type = ''
  filter.keyword = ''
  loadList()
}

const BIZ_TYPE_TEXT = {
  archive: '档案',
  honor: '荣誉',
  draft: '通用',
}
function bizTextOf(t) {
  return BIZ_TYPE_TEXT[t] || t || '-'
}

function titleOf(d) {
  return d?.payload?.title || d?.payload?.name || `草稿 #${d.id}`
}

function previewOf(d) {
  const p = d?.payload
  if (!p) return '-'
  const parts = []
  if (p.title) parts.push(`标题：${p.title}`)
  if (p.category) parts.push(`分类：${p.category}`)
  if (p.content) parts.push(`正文：${String(p.content).slice(0, 60)}${String(p.content).length > 60 ? '…' : ''}`)
  return parts.join(' · ') || `version=${d.version}`
}

/* ---------- 加载到本地编辑器 ---------- */
function loadToEditor(d) {
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        version: d.version,
        payload: d.payload,
        dirty: false,
        updatedAt: d.updated_at,
      }),
    )
    ElMessage.success('已加载到编辑器，请前往"提交资料"页面查看')
  } catch (e) {
    ElMessage.error('加载失败：' + e.message)
  }
}

function gotoEdit(d) {
  loadToEditor(d)
  if (d.biz_type === 'archive') {
    router.push('/archive-form')
  } else {
    ElMessage.info('该类型草稿暂未配置编辑器跳转，请手动使用加载按钮')
  }
}

/* ---------- 删除 ---------- */
async function handleDelete(d) {
  try {
    await ElMessageBox.confirm(
      `确认删除草稿「${titleOf(d)}」？删除后无法恢复。`,
      '删除草稿',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteDraftById(d.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function handleClearLocal() {
  try {
    await ElMessageBox.confirm(
      '确认清空本地编辑器缓存（仅清 localStorage，不影响服务端草稿）？',
      '清空本地',
      { type: 'info' },
    )
  } catch {
    return
  }
  localStorage.removeItem(STORAGE_KEY)
  ElMessage.success('已清空本地草稿')
}

onMounted(loadList)
</script>

<template>
  <div class="draft-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">草稿箱</h2>
        <p class="page-sub">展示当前账号下所有服务端草稿，可加载到编辑器继续编辑，或删除不再需要的草稿</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        <el-button @click="handleClearLocal">清空本地</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="filter.biz_type" placeholder="业务类型" clearable style="width: 140px" @change="onSearch">
        <el-option label="档案" value="archive" />
        <el-option label="荣誉" value="honor" />
      </el-select>
      <el-input
        v-model="filter.keyword"
        placeholder="搜索草稿标题"
        clearable
        style="width: 220px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button v-if="hasFilter" text @click="resetFilter">重置</el-button>
    </div>

    <div v-loading="loading" class="draft-list">
      <div v-for="d in list" :key="d.id" class="draft-card">
        <div class="card-head">
          <div>
            <div class="card-title">{{ titleOf(d) }}</div>
            <div class="card-meta">
              <el-tag effect="plain" size="small">{{ bizTextOf(d.biz_type) }}</el-tag>
              <span class="version">version {{ d.version }}</span>
              <span class="time">{{ formatTime(d.updated_at) }}</span>
            </div>
          </div>
          <div class="card-actions">
            <el-button link type="primary" :icon="EditPen" @click="gotoEdit(d)">去编辑</el-button>
            <el-button link type="warning" :icon="Upload" @click="loadToEditor(d)">仅加载</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(d)">删除</el-button>
          </div>
        </div>
        <div class="card-preview">{{ previewOf(d) }}</div>
      </div>
      <el-empty v-if="!loading && !list.length" description="暂无草稿" />
    </div>
  </div>
</template>

<style scoped>
.draft-page {
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
.head-actions {
  display: flex;
  gap: 8px;
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.draft-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.draft-card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: 14px 18px;
  transition: box-shadow 0.18s, transform 0.18s;
}
.draft-card:hover {
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
  transform: translateY(-1px);
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
}
.card-meta {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--text-sub);
}
.version {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.card-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.card-actions .el-button + .el-button {
  margin-left: 4px;
}
.card-preview {
  margin-top: 10px;
  font-size: 13px;
  color: var(--text-sub);
  line-height: 1.7;
}
</style>