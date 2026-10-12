<script setup>
/**
 * 荣誉墙管理（/honor-wall · 仅 school_admin / system_admin）
 *
 * 「荣誉墙」= 校友门户 H5 的轮播展示位（对应后端 /api/v1/screen/feed）。
 * 上架 / 下架由本页面操作；授权（consent scope=screen）由档案管理员在档案详情里勾选。
 *
 * 数据来源：
 *   - 后端已上架  ：GET  /api/v1/screen/feed           （公开；直接读）
 *   - 后端可上架候选：GET /api/v1/archives?status=approved&keyword=...
 *      —— 仅列出 published=false 的档案（已上架的归档归到 Tab 1）
 *   - 上架        ：POST /api/v1/showcase/{archive_id}/publish
 *   - 下架        ：POST /api/v1/showcase/{archive_id}/unpublish
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Promotion, RemoveFilled, Refresh, Search, View } from '@element-plus/icons-vue'
import { getArchives } from '@/api/archive'
import { publishShowcase, unpublishShowcase, getScreenFeed } from '@/api/showcase'
import { formatTime } from '@/utils/format'
import { dictLabel } from '@/api/dict'
import LucideIcon from '@/components/LucideIcon.vue'

const activeTab = ref('online')

/* ===================== Tab 1：已上架（feed） ===================== */
const online = ref([])
const onlineLoading = ref(false)
const onlineTotal = ref(0)
const onlinePage = ref(1)
const onlinePageSize = ref(12)

async function loadOnline() {
  onlineLoading.value = true
  try {
    const res = await getScreenFeed()
    // 兼容 axios 拦截器两种可能：res.data.list 或 res.list
    const list = (res && res.data && res.data.list) || (res && res.list) || []
    online.value = list
    onlineTotal.value = list.length
    if (!list.length) {
      ElMessage.info('当前荣誉墙暂无已上架档案')
    }
  } catch (e) {
    ElMessage.error(e?.message || '加载已上架列表失败')
  } finally {
    onlineLoading.value = false
  }
}

const pagedOnline = computed(() => {
  const start = (onlinePage.value - 1) * onlinePageSize.value
  return online.value.slice(start, start + onlinePageSize.value)
})

async function handleUnpublish(item) {
  try {
    await ElMessageBox.confirm(
      `确认将【${item.title}】从荣誉墙下架吗？`,
      '下架确认',
      { type: 'warning', confirmButtonText: '下架', cancelButtonText: '取消' },
    )
  } catch { return }
  try {
    await unpublishShowcase(item.id)
    ElMessage.success('已下架')
    await Promise.all([loadOnline(), loadCandidates()])
  } catch (e) {
    ElMessage.error(e?.message || '下架失败')
  }
}

function openPortal() {
  const base = import.meta.env.VITE_PORTAL_BASE || 'http://localhost:5175'
  window.open(`${base}/`, '_blank', 'noopener')
}

/* ===================== Tab 2：可上架候选（approved 但未上架） ===================== */
const candidates = ref([])
const candidatesLoading = ref(false)
const candidatesTotal = ref(0)
const candidatesPage = ref(1)
const candidatesPageSize = ref(10)

const filter = reactive({
  keyword: '',
  category: '',
  college: '',
})

async function loadCandidates() {
  candidatesLoading.value = true
  try {
    const params = { page: candidatesPage.value, page_size: candidatesPageSize.value, status: 'approved' }
    if (filter.keyword) params.keyword = filter.keyword.trim()
    if (filter.category) params.category = filter.category
    if (filter.college) params.college_id = filter.college
    const res = await getArchives(params)
    const list = res.data?.list || []
    candidates.value = list.filter((a) => !a.published)
    candidatesTotal.value = candidates.value.length
  } catch (e) {
    ElMessage.error(e?.message || '加载候选档案失败')
  } finally {
    candidatesLoading.value = false
  }
}

function handleSearch() {
  candidatesPage.value = 1
  loadCandidates()
}
function handleReset() {
  Object.assign(filter, { keyword: '', category: '', college: '' })
  handleSearch()
}

async function handlePublish(item) {
  try {
    await ElMessageBox.confirm(
      `确认将【${item.title}】上架到荣誉墙吗？需档案已通过审核且有有效大屏授权。`,
      '上架确认',
      { type: 'warning', confirmButtonText: '上架', cancelButtonText: '取消' },
    )
  } catch { return }
  try {
    await publishShowcase(item.id)
    ElMessage.success('已上架，前台轮播下次刷新即可见')
    await Promise.all([loadOnline(), loadCandidates()])
  } catch (e) {
    ElMessage.error(e?.message || '上架失败（可能缺少大屏授权）')
  }
}

/* ===================== 初始化 ===================== */
async function refreshAll() {
  // 先加载已上架（Tab 1），保证一进页面就能看到内容
  await loadOnline()
  // 候选档案异步加载（不阻塞 Tab 1 渲染）
  loadCandidates()
}

onMounted(refreshAll)

/* ===================== UI helpers ===================== */
const CATEGORY_LABEL = {
  award: '获奖经历',
  certificate: '证书资质',
  experience: '实践经历',
  career: '就业履历',
  other: '其他资料',
}
function categoryText(v) { return CATEGORY_LABEL[v] || v || '—' }
</script>

<template>
  <div class="wall-page">
    <div class="head">
      <div>
        <div class="eyebrow">荣誉墙</div>
        <h1 class="page-title">荣誉墙管理</h1>
        <p class="page-sub">
          控制校友门户 H5 轮播位（荣誉墙）显示哪些已审核档案。
          需先在档案详情里勾选「大屏授权」才能上架。
        </p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
        <el-button :icon="View" type="primary" plain @click="openPortal">前台预览</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="wall-tabs">
      <!-- ===================== Tab 1 ===================== -->
      <el-tab-pane label="已上架（前台展示）" name="online">
        <div class="kpi-row">
          <div class="kpi-card">
            <div class="kpi-label">已上架档案</div>
            <div class="kpi-val">{{ onlineTotal }}</div>
            <div class="kpi-sub">前端轮播可见</div>
          </div>
          <div class="kpi-tip">
            <LucideIcon name="Info" :size="14" />
            下架后不再出现在前台轮播。已上架档案上限 50 条（后端 SQL 限制）。
          </div>
        </div>

        <div class="grid" v-loading="onlineLoading">
          <article
            v-for="item in pagedOnline"
            :key="item.id"
            class="card"
          >
            <div class="cover">
              <img v-if="item.cover_url" :src="item.cover_url" :alt="item.title" />
              <div v-else class="cover-fallback">
                <LucideIcon name="Trophy" :size="36" />
              </div>
              <span class="cat">{{ categoryText(item.category) }}</span>
            </div>
            <div class="meta">
              <h4 class="title">{{ item.title }}</h4>
              <div class="info">
                <span>{{ item.owner }}</span>
                <span class="dot">·</span>
                <span>{{ item.college || '—' }}</span>
                <span v-if="item.grad_year" class="dot">·</span>
                <span v-if="item.grad_year">{{ item.grad_year }} 届</span>
              </div>
            </div>
            <div class="card-actions">
              <el-button type="warning" plain :icon="RemoveFilled" size="small" @click="handleUnpublish(item)">
                下架
              </el-button>
            </div>
          </article>
          <div v-if="!onlineLoading && !online.length" class="empty">
            暂未上架任何档案，去「可上架候选」Tab 把通过审核的档案放上来。
          </div>
        </div>

        <el-pagination
          v-model:current-page="onlinePage"
          v-model:page-size="onlinePageSize"
          :total="onlineTotal"
          layout="total, prev, pager, next"
          class="mt-12"
        />
      </el-tab-pane>

      <!-- ===================== Tab 2 ===================== -->
      <el-tab-pane label="可上架候选（approved + 未上架）" name="candidate">
        <el-card shadow="never" class="filter-card">
          <el-form inline @submit.prevent="handleSearch">
            <el-form-item label="关键词">
              <el-input
                v-model="filter.keyword"
                placeholder="档案标题 / 学号 / 姓名"
                clearable
                style="width: 220px"
                @keyup.enter="handleSearch"
              />
            </el-form-item>
            <el-form-item label="类别">
              <el-select v-model="filter.category" placeholder="全部" clearable style="width: 140px">
                <el-option label="获奖经历" value="award" />
                <el-option label="证书资质" value="certificate" />
                <el-option label="实践经历" value="experience" />
                <el-option label="就业履历" value="career" />
                <el-option label="其他资料" value="other" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
              <el-button @click="handleReset">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <el-table :data="candidates" v-loading="candidatesLoading" stripe>
            <el-table-column prop="id" label="档案ID" width="80" />
            <el-table-column prop="title" label="档案标题" min-width="200" show-overflow-tooltip />
            <el-table-column label="类别" width="120">
              <template #default="{ row }">{{ categoryText(row.category) }}</template>
            </el-table-column>
            <el-table-column label="获得者" width="140">
              <template #default="{ row }">{{ row.real_name || '—' }}<span class="sid">（{{ row.student_id || '—' }}）</span></template>
            </el-table-column>
            <el-table-column label="学院" width="140">
              <template #default="{ row }">{{ row.college || '—' }}</template>
            </el-table-column>
            <el-table-column label="届别" width="80">
              <template #default="{ row }">{{ row.grad_year ? row.grad_year + ' 届' : '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag type="success" size="small">已通过</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" plain :icon="Promotion" size="small" @click="handlePublish(row)">
                  上架
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!candidatesLoading && !candidates.length" class="empty">暂无已通过但未上架的档案</div>
        </el-card>

        <el-pagination
          v-model:current-page="candidatesPage"
          v-model:page-size="candidatesPageSize"
          :total="candidatesTotal"
          layout="total, prev, pager, next"
          class="mt-12"
          @current-change="loadCandidates"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.wall-page { padding: 0; }

.eyebrow {
  font-family: var(--font-mono, monospace);
  font-size: 11px;
  letter-spacing: 1.2px;
  color: var(--text-sub);
  text-transform: uppercase;
}
.page-title { font-size: 26px; font-weight: 700; margin: 6px 0 6px; color: var(--text-main); }
.page-sub { font-size: 13px; color: var(--text-sub); margin: 0; max-width: 720px; line-height: 1.6; }

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}
.head-actions { display: flex; gap: 10px; align-items: center; }

.wall-tabs { margin-top: 4px; }

/* 已上架 KPI */
.kpi-row {
  display: flex;
  gap: 14px;
  margin-bottom: 16px;
  align-items: stretch;
}
.kpi-card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 14px 22px;
  min-width: 180px;
}
.kpi-label { font-size: 12px; color: var(--text-sub); }
.kpi-val {
  font-size: 28px;
  font-weight: 700;
  color: var(--brand-700);
  margin-top: 4px;
  font-variant-numeric: tabular-nums;
}
.kpi-sub { font-size: 11px; color: var(--text-sub); margin-top: 2px; }
.kpi-tip {
  flex: 1;
  background: var(--surface-2);
  border: 1px dashed var(--line);
  border-radius: 12px;
  padding: 12px 18px;
  font-size: 12px;
  color: var(--text-sub);
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 卡片网格 */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}
.card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  transition: transform 0.12s, box-shadow 0.12s;
}
.card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(22, 55, 96, 0.12);
}
.cover {
  position: relative;
  aspect-ratio: 16 / 9;
  background: linear-gradient(135deg, var(--brand-100, #e8f0fa), var(--brand-50));
  overflow: hidden;
}
.cover img { width: 100%; height: 100%; object-fit: cover; }
.cover-fallback {
  width: 100%; height: 100%;
  display: grid; place-items: center;
  color: var(--brand-500);
}
.cat {
  position: absolute; left: 10px; top: 10px;
  padding: 3px 10px; border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  font-size: 11px; color: var(--brand-700); font-weight: 600;
}
.meta { padding: 10px 14px 4px; }
.title {
  font-size: 14px; font-weight: 600; color: var(--text-main);
  margin: 0;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.info {
  font-size: 12px; color: var(--text-sub);
  margin-top: 4px;
  display: flex; gap: 4px; flex-wrap: wrap;
}
.dot { color: var(--line); }
.card-actions {
  padding: 8px 14px 12px;
  display: flex;
  justify-content: flex-end;
}
.empty {
  grid-column: 1 / -1;
  padding: 36px 0;
  text-align: center;
  color: var(--text-sub);
  font-size: 13px;
}

.filter-card, .table-card {
  border-radius: var(--radius-xl);
  margin-bottom: 14px;
}

.mt-12 { margin-top: 12px; }

.sid { color: var(--text-sub); font-size: 11px; }
</style>
