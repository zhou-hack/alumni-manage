<script setup>
/**
 * 大屏统计分析（/honor-screen/stats · 仅 school_admin / system_admin）
 * - GET /admin/screen/stats?top_n=N → 4 维度聚合
 *   - overview（KPI：总校友/总荣誉/学院数/届数）
 *   - year_trend（按年荣誉条数）
 *   - category_dist / level_dist（key+name，后端已映射为中文）
 *   - top_owners（按校友获奖数排行）
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getScreenStats } from '@/api/screen'
import LucideIcon from '@/components/LucideIcon.vue'

const loading = ref(false)
const stats = ref(null)
const topN = ref(10)

async function load() {
  loading.value = true
  try {
    const res = await getScreenStats({ top_n: topN.value })
    stats.value = res.data || res
  } catch (e) {
    ElMessage.error(e.message || '统计加载失败')
  } finally {
    loading.value = false
  }
}

const maxTrend = computed(() =>
  (stats.value?.year_trend?.list || []).reduce((m, x) => Math.max(m, x.count || 0), 0),
)
const maxLevel = computed(() =>
  (stats.value?.level_dist?.list || []).reduce((m, x) => Math.max(m, x.count || 0), 0),
)
const maxCategory = computed(() =>
  (stats.value?.category_dist?.list || []).reduce((m, x) => Math.max(m, x.count || 0), 0),
)
const maxCollege = computed(() =>
  (stats.value?.top_owners?.list || []).reduce((m, x) => Math.max(m, x.honor_count || 0), 0),
)

const PIE_PALETTE = ['#3D7EBB', '#5f95c6', '#7BB0E0', '#5A9A8F', '#E08E31', '#9d3d3a', '#7B68A6', '#0F9B9B']
function pieColor(i) { return PIE_PALETTE[i % PIE_PALETTE.length] }
function ratioOf(max, v) { return max ? Math.max(0, Math.min(100, (v / max) * 100)) : 0 }

onMounted(load)
</script>

<template>
  <div class="stats-page" v-loading="loading">
    <div class="head">
      <div>
        <div class="eyebrow">大屏统计 · 概览</div>
        <h1 class="page-title">大屏统计分析</h1>
        <p class="page-sub">荣誉数据的多维度聚合（KPI / 年趋势 / 类别 / 级别 / 学院），驱动大屏左下与右下区块。</p>
      </div>
      <div class="head-actions">
        <el-select v-model="topN" style="width: 140px" @change="load">
          <el-option :value="5" label="前 5 名" />
          <el-option :value="10" label="前 10 名" />
          <el-option :value="20" label="前 20 名" />
        </el-select>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <!-- KPI -->
    <div class="kpi-row">
      <div class="kpi-card">
        <div class="kpi-label">总校友</div>
        <div class="kpi-val">{{ stats?.overview?.total_alumni ?? '—' }}</div>
        <div class="kpi-sub">所有校友数</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">总荣誉</div>
        <div class="kpi-val">{{ stats?.overview?.total_honors ?? '—' }}</div>
        <div class="kpi-sub">已上架 + 草稿</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">学院数</div>
        <div class="kpi-val">{{ stats?.overview?.college_count ?? '—' }}</div>
        <div class="kpi-sub">荣誉数据覆盖</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">届别数</div>
        <div class="kpi-val">{{ stats?.overview?.grad_year_count ?? '—' }}</div>
        <div class="kpi-sub">最远至最新届</div>
      </div>
    </div>

    <!-- 趋势 + 类别 + 级别 -->
    <div class="block-row">
      <section class="block">
        <header><h3>年度荣誉趋势</h3><p>按年份聚合（最近 N 年）</p></header>
        <ul class="bar-list">
          <li v-for="(item, idx) in (stats?.year_trend?.list || [])" :key="idx">
            <span class="bar-label">{{ item.year }}</span>
            <div class="bar-track"><div class="bar-fill" :style="{ width: ratioOf(maxTrend, item.count) + '%' }"></div></div>
            <span class="bar-count">{{ item.count }}</span>
          </li>
          <li v-if="!(stats?.year_trend?.list || []).length" class="empty-tip">暂无趋势数据</li>
        </ul>
      </section>

      <section class="block">
        <header><h3>按级别分布</h3><p>国家级 / 省级 / 市级 / 校级 / 国际级</p></header>
        <ul class="bar-list">
          <li v-for="(item, idx) in (stats?.level_dist?.list || [])" :key="idx">
            <span class="bar-label">{{ item.name }}</span>
            <div class="bar-track"><div class="bar-fill" :style="{ width: ratioOf(maxLevel, item.count) + '%' }"></div></div>
            <span class="bar-count">{{ item.count }}</span>
          </li>
          <li v-if="!(stats?.level_dist?.list || []).length" class="empty-tip">暂无级别数据</li>
        </ul>
      </section>

      <section class="block">
        <header><h3>按类别分布</h3><p>学科竞赛 / 文体活动 / 社会实践 ...</p></header>
        <ul class="pie-list">
          <li v-for="(item, idx) in (stats?.category_dist?.list || [])" :key="idx">
            <span class="pie-dot" :style="{ background: pieColor(idx) }"></span>
            <span class="bar-label">{{ item.name }}</span>
            <span class="pie-val">{{ item.count }} ({{ ratioOf(maxCategory, item.count).toFixed(1) }}%)</span>
          </li>
          <li v-if="!(stats?.category_dist?.list || []).length" class="empty-tip">暂无类别数据</li>
        </ul>
      </section>
    </div>

    <!-- TOP 校友 -->
    <section class="block wide">
      <header>
        <h3>校友荣誉贡献 前 {{ topN }} 名</h3>
        <p>按累计荣誉数排行的校友（驱动大屏右下"杰出校友"模块）</p>
      </header>
      <ul class="college-list">
        <li v-for="(c, idx) in (stats?.top_owners?.list || [])" :key="idx">
          <span class="c-rank">{{ idx + 1 }}</span>
          <span class="c-name">{{ c.name }}<span class="c-meta">{{ c.college ? ' · ' + c.college : '' }}</span></span>
          <div class="c-track"><div class="c-fill" :style="{ width: ratioOf(maxCollege, c.honor_count) + '%' }"></div></div>
          <span class="c-count">{{ c.honor_count }}</span>
        </li>
        <li v-if="!(stats?.top_owners?.list || []).length" class="empty-tip">暂无数据</li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.eyebrow {
  font-family: var(--font-mono, monospace);
  font-size: 11px;
  letter-spacing: 1.2px;
  color: var(--text-sub);
}
.page-title { font-size: 22px; font-weight: 700; margin: 4px 0 4px; color: var(--text-main); }
.page-sub { font-size: 13px; color: var(--text-sub); margin: 0; max-width: 720px; line-height: 1.6; }

.head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; }
.head-actions { display: flex; gap: 8px; align-items: center; }

.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}
.kpi-card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 14px 18px;
}
.kpi-label { font-size: 12px; color: var(--text-sub); }
.kpi-val {
  font-family: var(--font-display, sans-serif);
  font-size: 26px;
  font-weight: 700;
  color: var(--brand-700);
  margin-top: 4px;
  font-variant-numeric: tabular-nums;
}
.kpi-sub { font-size: 11px; color: var(--text-sub); margin-top: 4px; }

.block-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}

.block, .block.wide {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 16px 20px;
}
.block header h3 { font-size: 14px; font-weight: 700; margin: 0; color: var(--text-main); }
.block header p { font-size: 11px; color: var(--text-sub); margin: 4px 0 12px; }
.block.wide { margin-top: 0; }

.bar-list, .pie-list, .college-list { list-style: none; margin: 0; padding: 0; }
.bar-list li { display: grid; grid-template-columns: 110px 1fr 60px; align-items: center; gap: 10px; padding: 6px 0; font-size: 13px; }
.bar-label { color: var(--text-sub); }
.bar-track { height: 10px; background: var(--gray-100, #f1f5f9); border-radius: 5px; overflow: hidden; }
.bar-fill { height: 100%; background: linear-gradient(90deg, var(--brand-500), var(--brand-700)); border-radius: 5px; transition: width 0.3s; }
.bar-count { font-weight: 700; color: var(--text-main); text-align: right; font-variant-numeric: tabular-nums; }

.pie-list li { display: flex; align-items: center; gap: 8px; padding: 6px 0; font-size: 13px; }
.pie-dot { width: 12px; height: 12px; border-radius: 3px; flex-shrink: 0; }
.pie-val { margin-left: auto; color: var(--text-main); font-weight: 600; font-variant-numeric: tabular-nums; }

.college-list li {
  display: grid;
  grid-template-columns: 36px 1fr 2fr 80px;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  font-size: 13px;
  border-bottom: 1px dashed var(--line);
}
.college-list li:last-child { border-bottom: none; }
.c-rank { font-weight: 700; color: var(--brand-700); text-align: center; }
.c-name { color: var(--text-main); font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.c-meta { color: var(--text-sub); font-weight: 400; font-size: 12px; }
.c-track { height: 8px; background: var(--gray-100, #f1f5f9); border-radius: 4px; overflow: hidden; }
.c-fill { height: 100%; background: linear-gradient(90deg, var(--brand-400), var(--brand-700)); border-radius: 4px; transition: width 0.3s; }
.c-count { font-weight: 700; color: var(--text-main); text-align: right; font-variant-numeric: tabular-nums; }

.empty-tip { padding: 18px 0; text-align: center; font-size: 12px; color: var(--text-sub); }

@media (max-width: 960px) {
  .kpi-row { grid-template-columns: 1fr 1fr; }
  .block-row { grid-template-columns: 1fr; }
}
</style>
