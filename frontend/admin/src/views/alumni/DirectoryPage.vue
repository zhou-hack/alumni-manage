<script setup>
/**
 * 校友通讯录页（PC 端校友视角，清单 /student/directory）
 * 基于公开档案聚合的校友目录（公开口径：approved + published + 有效授权）
 * - 关键字搜索（姓名 / 档案标题）
 * - 学院 / 届别筛选
 * - 点击校友卡展开其公开档案列表
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getAlumniDirectory } from '@/api/showcase'
import { getDict } from '@/api/dict'
import { CATEGORY_TEXT, formatTime } from '@/utils/format'

const loading = ref(false)
const list = ref([])
const total = ref(0)

const filter = reactive({
  keyword: '',
  college_id: '',
  grad_year: '',
})

const colleges = ref([])
const gradYears = ref([])

async function loadDicts() {
  try {
    colleges.value = await getDict('college')
    gradYears.value = await getDict('grad_year')
  } catch {
    // 字典失败不阻塞
  }
}

async function load() {
  loading.value = true
  try {
    const params = {}
    if (filter.keyword) params.keyword = filter.keyword.trim()
    if (filter.college_id) params.college_id = filter.college_id
    if (filter.grad_year) params.grad_year = filter.grad_year
    const res = await getAlumniDirectory(params)
    list.value = res.data.list || []
    total.value = res.data.total || 0
    expanded.value = null
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  load()
}

function handleReset() {
  Object.assign(filter, { keyword: '', college_id: '', grad_year: '' })
  load()
}

// ---------- 展开校友公开档案 ----------
const expanded = ref(null)

function toggleExpand(user) {
  expanded.value = expanded.value === user.user_id ? null : user.user_id
}

/** 头像占位字（姓名首字） */
function avatarChar(name) {
  return (name || '友').slice(0, 1)
}

const hasFilter = computed(() => filter.keyword || filter.college_id || filter.grad_year)

onMounted(() => {
  loadDicts()
  load()
})
</script>

<template>
  <div class="directory-page">
    <div class="page-head">
      <div>
        <div class="eyebrow">校友通讯录</div>
        <h1 class="page-title">校友通讯录</h1>
        <p class="page-sub">基于已授权公开展示的档案聚合，共 {{ total }} 位校友在册。</p>
      </div>
    </div>

    <!-- 检索栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="handleSearch">
        <el-form-item label="关键字">
          <el-input
            v-model="filter.keyword"
            placeholder="姓名 / 档案标题"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="学院">
          <el-select v-model="filter.college_id" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="c in colleges" :key="c.id" :label="c.label" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="届别">
          <el-select v-model="filter.grad_year" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="y in gradYears" :key="y.id" :label="y.label" :value="y.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 目录卡片 -->
    <div v-loading="loading" class="dir-list">
      <template v-if="list.length">
        <div v-for="u in list" :key="u.user_id" class="dir-card" :class="{ open: expanded === u.user_id }">
          <div class="dir-row" @click="toggleExpand(u)">
            <div class="avatar">{{ avatarChar(u.name) }}</div>
            <div class="dir-main">
              <div class="dir-name">
                {{ u.name }}
                <span class="dir-year">{{ u.grad_year ? `${u.grad_year} 届` : '' }}</span>
              </div>
              <div class="dir-sub">{{ u.college }}</div>
            </div>
            <el-tag size="small" effect="plain">{{ u.archive_count }} 份公开档案</el-tag>
            <span class="dir-arrow" :class="{ up: expanded === u.user_id }">›</span>
          </div>

          <!-- 展开公开档案 -->
          <transition name="expand">
            <div v-if="expanded === u.user_id" class="dir-archives">
              <div v-for="a in u.archives" :key="a.id" class="arc-item">
                <el-tag size="small" type="info" effect="plain">
                  {{ CATEGORY_TEXT[a.category] || a.category }}
                </el-tag>
                <div class="arc-body">
                  <div class="arc-title">{{ a.title }}</div>
                  <div class="arc-content">{{ a.content }}</div>
                </div>
              </div>
            </div>
          </transition>
        </div>
      </template>

      <el-empty
        v-else-if="!loading"
        :description="hasFilter ? '没有匹配的校友，试试调整筛选条件' : '暂无公开档案校友'"
        :image-size="90"
      />
    </div>

    <p v-if="list.length" class="dir-foot text-sub">
      仅展示已通过审核并授权大屏公开展示的档案，最新更新 {{ formatTime(list[0]?.latest_at) }}。
    </p>
  </div>
</template>

<style scoped>
.page-head {
  margin-bottom: 22px;
}

.eyebrow {
  font-size: 11px;
  letter-spacing: 0.22em;
  color: var(--text-mute);
  margin-bottom: 6px;
}

.page-title {
  margin: 0 0 6px;
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  color: var(--text-main);
}

.page-sub {
  margin: 0;
  font-size: 13.5px;
  color: var(--text-sub);
}

.filter-card {
  border-radius: var(--radius-xl);
  margin-bottom: 16px;
}

.dir-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 200px;
}

.dir-card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.dir-card:hover {
  border-color: var(--brand-200);
}

.dir-card.open {
  border-color: var(--brand-600, #102a5c);
  box-shadow: var(--shadow-sm);
}

.dir-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  cursor: pointer;
}

.avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #2a4a78, #102a5c);
}

.dir-main {
  flex: 1;
  min-width: 0;
}

.dir-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}

.dir-year {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--text-mute);
}

.dir-sub {
  margin-top: 2px;
  font-size: 12.5px;
  color: var(--text-sub);
}

.dir-arrow {
  color: var(--text-mute);
  font-size: 18px;
  transition: transform 0.25s;
  transform: rotate(90deg);
}

.dir-arrow.up {
  transform: rotate(-90deg);
}

.dir-archives {
  padding: 4px 18px 14px 74px;
  border-top: 1px dashed var(--line);
  background: var(--surface-2, rgba(0, 0, 0, 0.02));
}

.arc-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 0;
}

.arc-item + .arc-item {
  border-top: 1px dashed var(--line);
}

.arc-body {
  flex: 1;
  min-width: 0;
}

.arc-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-main);
}

.arc-content {
  margin-top: 2px;
  font-size: 12.5px;
  color: var(--text-sub);
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.expand-enter-active,
.expand-leave-active {
  transition: max-height 0.3s ease, opacity 0.3s ease;
  max-height: 600px;
}

.expand-enter-from,
.expand-leave-to {
  max-height: 0;
  opacity: 0;
}

.dir-foot {
  margin-top: 14px;
  font-size: 12px;
  text-align: center;
}
</style>
