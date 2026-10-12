<script setup>
/**
 * 全局搜索结果页（/search · 所有登录角色可访问）
 * - 顶栏输入 Enter 或点击联想词跳转到这里
 * - URL ?keyword=&types=&college_id=&grad_year=&tag_ids=
 * - 完整结果页见 E 阶段第二部分开发
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { search, SEARCH_TYPES } from '@/api/search'
import { formatTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const keyword = ref(String(route.query.keyword || ''))
const typeFilter = ref(String(route.query.types || '')) // 逗号分隔
const collegeId = ref(route.query.college_id || '')
const gradYear = ref(route.query.grad_year || '')
const tagIds = ref(String(route.query.tag_ids || ''))

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(Number(route.query.page) || 1)
const pageSize = ref(20)

const types = computed(() =>
  typeFilter.value ? typeFilter.value.split(',').filter(Boolean) : [],
)

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (types.value.length) params.types = types.value.join(',')
    if (collegeId.value) params.college_id = collegeId.value
    if (gradYear.value) params.grad_year = Number(gradYear.value)
    if (tagIds.value) params.tag_ids = tagIds.value
    const res = await search(params)
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error(e.message || '搜索失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function syncUrl() {
  const q = {}
  if (keyword.value.trim()) q.keyword = keyword.value.trim()
  if (typeFilter.value) q.types = typeFilter.value
  if (collegeId.value) q.college_id = collegeId.value
  if (gradYear.value) q.grad_year = gradYear.value
  if (tagIds.value) q.tag_ids = tagIds.value
  q.page = page.value
  router.replace({ path: '/search', query: q })
}

function onSearch() {
  page.value = 1
  syncUrl()
  load()
}

function goItem(row) {
  // 类型 → 路由映射
  const map = {
    honor: { name: 'HonorDraft' },
    archive: { name: 'Archives' },
    alumni: { name: 'AlumniDirectory' },
  }
  const r = map[row.type]
  if (r) router.push(r)
}

const TYPE_LABEL = Object.fromEntries(SEARCH_TYPES.map((t) => [t.value, t.label]))
function typeOf(t) {
  return TYPE_LABEL[t] || t || '其他'
}

onMounted(() => {
  if (keyword.value || typeFilter.value || collegeId.value || gradYear.value || tagIds.value) {
    load()
  }
})
watch(
  () => route.query,
  (q) => {
    keyword.value = String(q.keyword || '')
    typeFilter.value = String(q.types || '')
    collegeId.value = q.college_id || ''
    gradYear.value = q.grad_year || ''
    tagIds.value = String(q.tag_ids || '')
    page.value = Number(q.page) || 1
    load()
  },
)
</script>

<template>
  <div class="search-page">
    <div class="page-head">
      <div>
        <div class="eyebrow">全局搜索</div>
        <h1 class="page-title">聚合检索</h1>
        <p class="page-sub">跨荣誉 / 档案 / 校友 三类聚合；支持学院 / 届别 / 标签筛选</p>
      </div>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索关键词（荣誉名 / 校友姓名 / 档案标题）"
        clearable
        style="width: 360px"
        @keyup.enter="onSearch"
      />
      <el-select
        v-model="typeFilter"
        multiple
        collapse-tags
        placeholder="类型（多选）"
        style="width: 240px"
        @change="onSearch"
      >
        <el-option v-for="t in SEARCH_TYPES" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
      <el-input-number v-model="gradYear" placeholder="届别" :min="1990" :max="2100" :controls="false" style="width: 110px" @change="onSearch" />
      <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
    </div>

    <div v-loading="loading" class="results">
      <p v-if="!keyword && !typeFilter && !gradYear" class="muted">
        请输入关键词或选择类型开始检索（支持拼音容错）
      </p>
      <p v-else-if="!list.length && !loading" class="muted">无匹配结果</p>
      <div v-else class="result-list">
        <div v-for="r in list" :key="r.type + '-' + r.id" class="result-row" @click="goItem(r)">
          <el-tag size="small" :type="r.type === 'honor' ? 'success' : r.type === 'archive' ? 'primary' : 'info'">
            {{ typeOf(r.type) }}
          </el-tag>
          <div class="result-main">
            <div class="result-title">{{ r.title }}</div>
            <div class="result-meta">
              <span v-if="r.owner">{{ r.owner }}</span>
              <span v-if="r.college">· {{ r.college }}</span>
              <span v-if="r.gradYear">· {{ r.grad_year }} 届</span>
              <span v-if="r.created_at">· {{ formatTime(r.created_at) }}</span>
            </div>
            <div v-if="r.tags?.length" class="result-tags">
              <el-tag v-for="t in r.tags" :key="t" size="small" effect="plain">{{ t }}</el-tag>
            </div>
          </div>
          <div class="result-status">
            <el-tag v-if="r.status" size="small" effect="plain">{{ r.status }}</el-tag>
          </div>
        </div>
      </div>
    </div>

    <div v-if="total > pageSize" class="pager">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="onSearch"
      />
    </div>
  </div>
</template>

<style scoped>
@import '../shared/list-shared.css';
.search-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.eyebrow {
  font-family: var(--font-mono, monospace);
  font-size: 11px;
  letter-spacing: 1.2px;
  color: var(--text-sub);
  text-transform: uppercase;
}
.page-title { font-size: 24px; font-weight: 700; margin: 4px 0 6px; color: var(--text-main); }
.page-sub { font-size: 13px; color: var(--text-sub); margin: 0; max-width: 720px; line-height: 1.6; }

.filter-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.results {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 12px 16px;
  min-height: 240px;
}
.muted { color: var(--text-sub); font-size: 13px; padding: 30px 8px; text-align: center; }

.result-list {
  display: flex;
  flex-direction: column;
}
.result-row {
  display: flex;
  gap: 12px;
  padding: 14px 8px;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
  transition: background 0.15s;
}
.result-row:hover {
  background: rgba(15, 23, 42, 0.03);
}
.result-row:last-child {
  border-bottom: none;
}
.result-main {
  flex: 1;
  min-width: 0;
}
.result-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 4px;
}
.result-meta {
  font-size: 12px;
  color: var(--text-sub);
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.result-tags {
  margin-top: 6px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.result-status {
  display: flex;
  align-items: center;
}

.pager {
  display: flex;
  justify-content: flex-end;
}
</style>
