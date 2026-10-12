<script setup>
/**
 * 统计看板页（Day 3 F1，API 规范第十章）· 图表版
 * - 总量看板：校友数 / 档案数 / 荣誉数 / 待审数 / 已上架（点击待审卡直达审核中心）
 * - ECharts 可视化：荣誉类别横向条形图、荣誉级别环形占比图、学院档案排行柱状图
 * - 数据源：/stats/overview · /stats/honors · /stats/colleges（limit=8）
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Folder, Medal, Bell, Picture } from '@element-plus/icons-vue'
import { getOverview, getHonorStats, getCollegeRanking } from '@/api/stats'
import { getDict, dictLabel } from '@/api/dict'
import { useUserStore } from '@/stores/user'
import { useEChart } from '@/composables/useEChart'

const router = useRouter()
const userStore = useUserStore()
/** 仅审核角色（院级/校级/系统）可直达审核中心；导员无审核权限 */
const canGoReview = computed(() => ['college_admin', 'school_admin', 'system_admin'].includes(userStore.user?.role))

const loading = ref(false)

// ---------------- 总量 ----------------
const overview = ref({
  alumni_count: 0,
  archive_count: 0,
  honor_count: 0,
  pending_review: 0,
  published_count: 0,
})

const cards = computed(() => [
  { key: 'alumni_count', label: '校友总数', value: overview.value.alumni_count, icon: User, accent: 'blue' },
  { key: 'archive_count', label: '档案资料', value: overview.value.archive_count, icon: Folder, accent: 'gold' },
  { key: 'honor_count', label: '荣誉记录', value: overview.value.honor_count, icon: Medal, accent: 'green' },
  // 待审核卡：点击直达审核中心（清单：待审核卡点击直达审核；导员无审核权限不显示入口）
  {
    key: 'pending_review',
    label: '待审核',
    value: overview.value.pending_review,
    icon: Bell,
    accent: 'orange',
    to: canGoReview.value ? '/reviews' : '',
  },
  { key: 'published_count', label: '已上架大屏', value: overview.value.published_count, icon: Picture, accent: 'purple' },
])

function handleCardClick(card) {
  if (card.to) router.push(card.to)
}

// ---------------- 荣誉分布 ----------------
const honorStats = ref({ by_category: [], by_level: [], total: 0 })

/** 荣誉类别 value → 中文（honor_category 字典；先取字典保证 dictLabel 缓存就绪） */
function categoryLabel(value) {
  return dictLabel('honor_category', value)
}

/** 荣誉级别 value → 中文（level 不在字典表，前端固定映射） */
const LEVEL_TEXT = {
  national: '国家级',
  provincial: '省级',
  city: '市级',
  school: '校级',
}

const LEVEL_COLORS = {
  national: '#c9a44a',
  provincial: '#102a5c',
  city: '#4a90d9',
  school: '#6b7f99',
}

// 荣誉类别 · 横向条形图（数据倒序入轴，使最大值置顶）
const catChartEl = ref(null)
const catOption = computed(() => {
  const rows = honorStats.value.by_category
  if (!rows.length) return null
  const sorted = [...rows].sort((a, b) => a.count - b.count)
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 40, top: 8, bottom: 0, containLabel: true },
    xAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#e9eef6' } },
    },
    yAxis: {
      type: 'category',
      data: sorted.map((c) => categoryLabel(c.key)),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#4a5870', width: 88, overflow: 'truncate' },
    },
    series: [
      {
        type: 'bar',
        data: sorted.map((c) => c.count),
        barWidth: 14,
        itemStyle: {
          borderRadius: [0, 4, 4, 0],
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 1, y2: 0,
            colorStops: [
              { offset: 0, color: '#4f80f7' },
              { offset: 1, color: '#1f4fd6' },
            ],
          },
        },
        label: { show: true, position: 'right', color: '#324058', fontWeight: 600 },
      },
    ],
  }
})
useEChart(catChartEl, catOption)

// 荣誉级别 · 环形占比图
const levelChartEl = ref(null)
const levelOption = computed(() => {
  const rows = honorStats.value.by_level
  if (!rows.length) return null
  return {
    tooltip: { trigger: 'item', formatter: '{b}：{c} 条（{d}%）' },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 14,
      textStyle: { color: '#4a5870', fontSize: 12 },
    },
    series: [
      {
        type: 'pie',
        radius: ['50%', '72%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: {
          label: { show: true, formatter: '{b}\n{d}%', fontSize: 13, fontWeight: 600 },
        },
        data: rows.map((l) => ({
          name: LEVEL_TEXT[l.key] || l.key,
          value: l.count,
          itemStyle: { color: LEVEL_COLORS[l.key] || '#9aabc2' },
        })),
      },
    ],
  }
})
useEChart(levelChartEl, levelOption)

// ---------------- 学院排行 ----------------
const colleges = ref([])

const collegeChartEl = ref(null)
const collegeOption = computed(() => {
  const rows = colleges.value
  if (!rows.length) return null
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 8, top: 30, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((c) => c.college_name),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#dbe3ee' } },
      axisLabel: {
        color: '#4a5870',
        interval: 0,
        rotate: rows.length > 6 ? 18 : 0,
        formatter: (v) => (v.length > 6 ? v.slice(0, 6) + '…' : v),
      },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#e9eef6' } },
    },
    series: [
      {
        type: 'bar',
        data: rows.map((c) => c.archive_count),
        barWidth: 26,
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#4f80f7' },
              { offset: 1, color: '#1f4fd6' },
            ],
          },
        },
        label: { show: true, position: 'top', color: '#324058', fontWeight: 600 },
      },
    ],
  }
})
useEChart(collegeChartEl, collegeOption)

// ---------------- 加载 ----------------
async function loadAll() {
  loading.value = true
  try {
    // 先取荣誉类别字典（by_category 的 key 为英文 value，需翻译成中文标签）
    const [, ov, hs, cs] = await Promise.all([
      getDict('honor_category'),
      getOverview(),
      getHonorStats(),
      getCollegeRanking(8),
    ])
    // 后端契约（API 规范第十章）：total_alumni/total_archives/total_honors/pending_reviews
    overview.value = {
      alumni_count: ov.data.total_alumni,
      archive_count: ov.data.total_archives,
      honor_count: ov.data.total_honors,
      pending_review: ov.data.pending_reviews,
      published_count: ov.data.published_count ?? 0,
    }
    // by_category/by_level 每项为 {key, count}；total 不在出参中，由类别计数求和（同口径：仅荣誉表）
    const byCategory = hs.data.by_category || []
    const byLevel = hs.data.by_level || []
    honorStats.value = {
      by_category: byCategory,
      by_level: byLevel,
      total: byCategory.reduce((s, c) => s + (c.count || 0), 0),
    }
    colleges.value = cs.data.list || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div v-loading="loading" class="stats-page">
    <!-- 总量卡片 -->
    <div class="card-row">
      <div
        v-for="c in cards"
        :key="c.key"
        class="stat-card"
        :class="[`accent-${c.accent}`, { clickable: Boolean(c.to) }]"
        @click="handleCardClick(c)"
      >
        <div class="stat-icon">
          <el-icon :size="22"><component :is="c.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-value">{{ c.value }}</div>
          <div class="stat-label">{{ c.label }}</div>
        </div>
        <span v-if="c.to" class="card-go">前往处理 →</span>
      </div>
    </div>

    <el-row :gutter="16" class="mt-12">
      <!-- 荣誉类别 · 条形图 -->
      <el-col :xs="24" :md="14">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">荣誉类别分布</span>
            <span class="text-sub">共 {{ honorStats.total }} 条（仅统计荣誉表）</span>
          </div>
          <div v-if="honorStats.by_category.length" ref="catChartEl" class="chart chart-md"></div>
          <el-empty v-else description="暂无荣誉数据" :image-size="60" />
        </div>
      </el-col>

      <!-- 荣誉级别 · 环形图 -->
      <el-col :xs="24" :md="10">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">荣誉级别占比</span>
          </div>
          <div v-if="honorStats.by_level.length" ref="levelChartEl" class="chart chart-md"></div>
          <el-empty v-else description="暂无数据" :image-size="60" />
        </div>
      </el-col>
    </el-row>

    <!-- 学院排行 · 柱状图 -->
    <div class="panel mt-12">
      <div class="panel-head">
        <span class="panel-title">学院档案排行</span>
        <span class="text-sub">按档案数 Top 8</span>
      </div>
      <div v-if="colleges.length" ref="collegeChartEl" class="chart chart-lg"></div>
      <el-empty v-else description="暂无档案数据" :image-size="60" />
    </div>
  </div>
</template>

<style scoped>
.stats-page {
  min-height: 100%;
}

.card-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
}

@media (max-width: 1100px) {
  .card-row {
    grid-template-columns: repeat(2, 1fr);
  }
}

.stat-card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  position: relative;
}

.stat-card.clickable {
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}
.stat-card.clickable:hover {
  box-shadow: 0 8px 24px rgba(224, 138, 60, 0.22);
  transform: translateY(-2px);
}

.card-go {
  position: absolute;
  right: 14px;
  bottom: 10px;
  font-size: 11px;
  color: #c76e24;
  opacity: 0.85;
}

.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: var(--radius-xl);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.accent-blue .stat-icon { background: linear-gradient(135deg, #2a4a78, #102a5c); }
.accent-gold .stat-icon { background: linear-gradient(135deg, #e0b95e, #c9a44a); }
.accent-green .stat-icon { background: linear-gradient(135deg, #3f9d6d, #2c7a52); }
.accent-orange .stat-icon { background: linear-gradient(135deg, #e08a3c, #c76e24); }
.accent-purple .stat-icon { background: linear-gradient(135deg, #7a6fc0, #5a4fa0); }

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--text-sub);
}

.panel {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: 18px;
  height: 100%;
}

.panel-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
}

.chart-md {
  height: 300px;
}

.chart-lg {
  height: 320px;
}
</style>
