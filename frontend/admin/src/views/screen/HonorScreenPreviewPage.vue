<script setup>
/**
 * 荣誉大屏 · 长廊·风采·学院（/honor-screen/preview）
 *
 * 用途：Tab 2，只读预览大屏上对外展示的三块内容 + 荣誉类型分布迷你。
 * 真正的"管理 / 上下架"在 Tab 1 的荣誉管理页。
 *
 * 数据来源：
 *   - /api/v1/dashboard-honor（公开，honors / alumni / industry_top / honorByType）
 *   - /api/v1/admin/screen/stats?top_n=10
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Promotion, RemoveFilled, UserFilled } from '@element-plus/icons-vue'
import LucideIcon from '@/components/LucideIcon.vue'
import { getScreenStats } from '@/api/screen'
import { dashboardHonor } from '@/api/showcase'
import { formatTime } from '@/utils/format'
import AlumniProfileEditDialog from '@/components/AlumniProfileEditDialog.vue'

const router = useRouter()
const loading = ref(false)
const previewData = ref(null)
const previewLoading = ref(false)
const statsData = ref(null)
const themeSwatch = 'linear-gradient(135deg, #3D7EBB, #1F497D)'

/** 级别 key → 中文标签（与 api/honor.js 对齐） */
const LEVEL_LABEL = {
  national: '国家级',
  provincial: '省级',
  city: '市级',
  school: '校级',
  international: '国际级',
}
function levelText(l) { return LEVEL_LABEL[l] || l || '—' }

/* ============== 校友风采编辑弹窗（座右铭 / 头像 / 简介） ============== */
/* Tab 2 里"校友风采"卡片每张可点"编辑"按钮打开 AlumniProfileEditDialog：
   1) 弹窗里管理员为该校友补全 / 修改 头像 / 行业 / 简介 / 人生信条 / 座右铭
   2) 写入 alumni_base 5 个 V18 字段 → 大屏"校友风采"轮播立刻展示新内容 */
const profileDialogVisible = ref(false)
const profileTargetAlumniId = ref(null)
const profileTargetName = ref('')

/** Tab 2 校友风采卡片点"编辑"：直接用后端给的 alumniBaseId（mode='admin'）打开弹窗 */
function openAlumniProfileById(alumniBaseId, name) {
  if (!alumniBaseId) {
    ElMessage.warning('该校友未关联校友库记录，无法编辑')
    return
  }
  profileTargetAlumniId.value = alumniBaseId
  profileTargetName.value = name || ''
  profileDialogVisible.value = true
}

function onProfileSaved() {
  ElMessage.success(`「${profileTargetName.value}」的风采已更新`)
  loadPreview()
}

const PIE_PALETTE = ['#3D7EBB', '#5f95c6', '#7BB0E0', '#5A9A8F', '#E08E31', '#9d3d3a', '#7B68A6', '#0F9B9B']
function pieColor(i) { return PIE_PALETTE[i % PIE_PALETTE.length] }
function ratioOf(max, v) {
  if (!max) return 0
  return Math.max(0, Math.min(100, (v / max) * 100))
}

const previewHonors = computed(() => (previewData.value?.honors || []).slice(0, 12))
const maxLevel = computed(() => {
  const list = statsData.value?.level_dist?.list || []
  return list.reduce((m, x) => Math.max(m, x.count || 0), 0)
})
const maxCategory = computed(() => {
  const list = statsData.value?.category_dist?.list || []
  return list.reduce((m, x) => Math.max(m, x.count || 0), 0)
})
const maxCollege = computed(() => {
  const list = previewData.value?.industry_top || []
  return list.reduce((m, x) => Math.max(m, x.count || 0), 0)
})

/** 跳转荣誉管理并带上"按荣誉 ID"过滤参数（在 /honors 里精确定位该条） */
function goManageWithHonor(id) {
  router.push({ path: '/honors', query: id ? { honor_id: id } : {} })
}

async function loadPreview() {
  previewLoading.value = true
  try {
    const res = await dashboardHonor()
    previewData.value = res.data || res
  } catch (e) { /* 静默 */ }
  finally { previewLoading.value = false }
}
async function loadStats() {
  try {
    const res = await getScreenStats({ top_n: 10 })
    statsData.value = res.data || res
  } catch (e) { /* 静默 */ }
}

onMounted(() => {
  loading.value = true
  Promise.all([loadPreview(), loadStats()]).finally(() => { loading.value = false })
})
</script>

<template>
  <div class="preview-page" v-loading="loading">

    <!-- 区块 0：荣誉类型分布（Tab 2 端迷你 · 与 Tab 1 同步） -->
    <section class="block">
      <header class="block-head">
        <span class="block-no">1</span>
        <div>
          <h3>荣誉类型分布</h3>
          <p>按级别（柱状）与按类别（饼图）双视图，驱动大屏左下"分布"模块</p>
        </div>
      </header>
      <div class="dist-row">
        <div class="dist-card">
          <h4>按级别分布</h4>
          <div v-if="!statsData" class="empty-tip">加载中…</div>
          <ul v-else class="bar-list">
            <li v-for="(item, idx) in statsData?.level_dist?.list || []" :key="idx">
              <span class="bar-label">{{ item.name }}</span>
              <div class="bar-track"><div class="bar-fill" :style="{ width: ratioOf(maxLevel, item.count) + '%' }"></div></div>
              <span class="bar-count">{{ item.count }}</span>
            </li>
            <li v-if="!(statsData?.level_dist?.list || []).length" class="empty-tip">暂无级别数据</li>
          </ul>
        </div>
        <div class="dist-card">
          <h4>按类别分布</h4>
          <div v-if="!statsData" class="empty-tip">加载中…</div>
          <ul v-else class="pie-list">
            <li v-for="(item, idx) in statsData?.category_dist?.list || []" :key="idx">
              <span class="pie-dot" :style="{ background: pieColor(idx) }"></span>
              <span class="bar-label">{{ item.name }}</span>
              <span class="pie-val">{{ item.count }} ({{ ratioOf(maxCategory, item.count).toFixed(1) }}%)</span>
            </li>
            <li v-if="!(statsData?.category_dist?.list || []).length" class="empty-tip">暂无类别数据</li>
          </ul>
        </div>
      </div>
    </section>

    <!-- 区块 2：校友荣誉长廊（仅只读预览，每张卡可跳转 Tab 1 定位该荣誉） -->
    <section class="block">
      <header class="block-head">
        <span class="block-no">2</span>
        <div>
          <h3>校友荣誉长廊</h3>
          <p>大屏中部长条幅：前 12 条已通过荣誉滚动展示（点击单张卡可去 Tab 1 管理）</p>
        </div>
      </header>
      <div v-if="previewLoading && !previewData" class="empty-tip">正在拉取实时大屏数据…</div>
      <div v-else-if="!previewData" class="empty-tip">⚠ 实时数据加载失败</div>
      <div v-else class="corridor" :style="{ '--theme-gradient': themeSwatch }">
        <div
          v-for="h in previewHonors"
          :key="h.id"
          class="corridor-card"
          role="button"
          tabindex="0"
          @click="goManageWithHonor(h.id)"
          @keyup.enter="goManageWithHonor(h.id)"
        >
          <div class="cc-level">{{ levelText(h.level) }}</div>
          <div class="cc-title">{{ h.title }}</div>
          <div class="cc-name">{{ h.alumnusName }}</div>
          <div class="cc-year">{{ h.year || '—' }} 年</div>
          <div class="cc-cta">
            <el-button size="small" type="primary" plain :icon="Promotion" @click.stop="goManageWithHonor(h.id)">去管理</el-button>
          </div>
        </div>
        <div v-if="!previewHonors.length" class="empty-tip">暂无荣誉数据</div>
      </div>
    </section>

    <!-- 区块 3：校友风采 -->
    <section class="block">
      <header class="block-head">
        <span class="block-no">3</span>
        <div>
          <h3>校友风采</h3>
          <p>按累计荣誉数排序的前 8 位杰出校友（大屏右下轮播）</p>
        </div>
      </header>
      <div v-if="!previewData" class="empty-tip">加载中…</div>
      <div v-else class="alumni-row">
        <div v-for="(a, idx) in (previewData?.alumni || []).slice(0, 8)" :key="idx" class="alumni-card">
          <div class="alumni-avatar">{{ (a.name || '校').slice(0, 1) }}</div>
          <div class="alumni-info">
            <div class="alumni-name">{{ a.name }}</div>
            <div class="alumni-meta">{{ a.college || '—' }} · {{ a.graduationYear || '—' }} 届</div>
            <div v-if="a.motto" class="alumni-motto">「{{ a.motto }}」</div>
            <div v-else class="alumni-motto empty">尚未设置座右铭</div>
          </div>
          <div class="alumni-stat">
            <span class="alumni-num">{{ a.awardCount ?? a.honorCount ?? 0 }}</span>
            <span class="alumni-lab">荣誉数</span>
          </div>
          <el-button
            size="small"
            type="primary"
            plain
            :icon="UserFilled"
            class="alumni-edit-btn"
            @click="openAlumniProfileById(a.alumniBaseId, a.name)"
          >编辑风采</el-button>
        </div>
        <div v-if="!(previewData?.alumni || []).length" class="empty-tip">暂无校友风采数据</div>
      </div>
    </section>

    <!-- 区块 4：学院分布 -->
    <section class="block">
      <header class="block-head">
        <span class="block-no">4</span>
        <div>
          <h3>学院分布</h3>
          <p>各学院累计荣誉贡献排行（大屏左下"学院"模块）</p>
        </div>
      </header>
      <div v-if="!previewData" class="empty-tip">加载中…</div>
      <ul v-else class="college-list">
        <li v-for="(c, idx) in (previewData?.industry_top || []).slice(0, 10)" :key="idx">
          <span class="c-rank">{{ idx + 1 }}</span>
          <span class="c-name">{{ c.name }}</span>
          <div class="c-track"><div class="c-fill" :style="{ width: ratioOf(maxCollege, c.count) + '%' }"></div></div>
          <span class="c-count">{{ c.count }}</span>
        </li>
        <li v-if="!(previewData?.industry_top || []).length" class="empty-tip">暂无学院数据</li>
      </ul>
    </section>

    <div class="footer-meta">
      数据更新时间：{{ formatTime(previewData?.updated_at) || '—' }}
      <span class="footer-hint">校友风采卡片可直接点「编辑风采」补全头像/简介/座右铭；上下架请到「荣誉管理」</span>
    </div>

    <!-- 校友风采编辑弹窗：座右铭 / 头像 / 简介 / 行业 / 人生信条 -->
    <AlumniProfileEditDialog
      v-if="profileDialogVisible && profileTargetAlumniId != null"
      v-model="profileDialogVisible"
      mode="admin"
      :alumni-id="profileTargetAlumniId"
      @saved="onProfileSaved"
    />
  </div>
</template>

<style scoped>
.preview-page { padding: 0; }
.block {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: 20px 24px 22px;
  margin-bottom: 18px;
}
.block-head { display: flex; gap: 14px; align-items: center; margin-bottom: 16px; }
.block-no {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, var(--brand-500), var(--brand-700));
  color: #fff;
  font-weight: 700;
  border-radius: 8px;
  flex-shrink: 0;
  font-size: 14px;
}
.block-head h3 { font-size: 17px; font-weight: 700; margin: 0; color: var(--text-main); }
.block-head p { font-size: 12.5px; color: var(--text-sub); margin: 4px 0 0; }

/* 分布 */
.dist-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.dist-card {
  padding: 14px 16px;
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
}
.dist-card h4 { font-size: 14px; font-weight: 700; margin: 0 0 10px; color: var(--text-main); }
.bar-list, .pie-list, .college-list { list-style: none; margin: 0; padding: 0; }
.bar-list li { display: grid; grid-template-columns: 100px 1fr 60px; align-items: center; gap: 10px; padding: 6px 0; font-size: 13px; }
.bar-label { color: var(--text-sub); }
.bar-track { height: 10px; background: var(--gray-100); border-radius: 5px; overflow: hidden; }
.bar-fill { height: 100%; background: linear-gradient(90deg, var(--brand-500), var(--brand-700)); border-radius: 5px; transition: width 0.3s; }
.bar-count { font-weight: 700; color: var(--text-main); text-align: right; font-variant-numeric: tabular-nums; }
.pie-list li { display: flex; align-items: center; gap: 8px; padding: 6px 0; font-size: 13px; }
.pie-dot { width: 12px; height: 12px; border-radius: 3px; flex-shrink: 0; }
.pie-val { margin-left: auto; color: var(--text-main); font-weight: 600; font-variant-numeric: tabular-nums; }

/* 长廊 */
.corridor {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  padding: 14px;
  background: linear-gradient(180deg, transparent, var(--surface-2));
  border-radius: var(--radius-lg);
}
.corridor-card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 12px;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 4px;
  cursor: pointer;
  transition: transform 0.12s, box-shadow 0.12s;
}
.corridor-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(22, 55, 96, 0.12);
}
.corridor-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 3px;
  background: var(--theme-gradient);
  opacity: 0.7;
}
.cc-level { display: inline-block; font-size: 11px; color: var(--brand-700); background: var(--brand-50); padding: 1px 6px; border-radius: 3px; align-self: flex-start; font-weight: 600; }
.cc-title { font-size: 13px; font-weight: 600; color: var(--text-main); line-height: 1.4; }
.cc-name { font-size: 12px; color: var(--brand-700); }
.cc-year { font-size: 11px; color: var(--text-sub); }
.cc-cta { margin-top: 4px; }

/* 校友 */
.alumni-row { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.alumni-card {
  display: flex; flex-wrap: wrap; align-items: center; gap: 10px;
  padding: 12px; background: var(--surface-2); border: 1px solid var(--line); border-radius: var(--radius-lg);
}
.alumni-avatar { width: 44px; height: 44px; border-radius: 50%; background: linear-gradient(135deg, var(--brand-500), var(--brand-700)); color: #fff; display: grid; place-items: center; font-weight: 700; font-size: 17px; flex-shrink: 0; overflow: hidden; }
.alumni-avatar img { width: 100%; height: 100%; object-fit: cover; }
.alumni-info { flex: 1; min-width: 0; }
.alumni-name { font-size: 14px; font-weight: 600; color: var(--text-main); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.alumni-meta { font-size: 11px; color: var(--text-sub); margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.alumni-motto { font-size: 11.5px; color: var(--brand-700); margin-top: 4px; font-style: italic; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.alumni-motto.empty { color: var(--text-sub); font-style: normal; }
.alumni-stat { display: flex; flex-direction: column; align-items: center; flex-shrink: 0; }
.alumni-num { font-size: 16px; font-weight: 700; color: var(--brand-700); font-variant-numeric: tabular-nums; }
.alumni-lab { font-size: 10px; color: var(--text-sub); }
.alumni-edit-btn { margin-left: auto; align-self: flex-end; }

/* 学院 */
.college-list li {
  display: grid;
  grid-template-columns: 32px 1fr 2fr 60px;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  font-size: 13px;
  border-bottom: 1px dashed var(--line);
}
.college-list li:last-child { border-bottom: none; }
.c-rank { font-weight: 700; color: var(--brand-700); text-align: center; }
.c-name { color: var(--text-main); font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.c-track { height: 8px; background: var(--gray-100); border-radius: 4px; overflow: hidden; }
.c-fill { height: 100%; background: linear-gradient(90deg, var(--brand-400), var(--brand-700)); border-radius: 4px; transition: width 0.3s; }
.c-count { font-weight: 700; color: var(--text-main); text-align: right; font-variant-numeric: tabular-nums; }

.empty-tip { padding: 18px 0; text-align: center; font-size: 12px; color: var(--text-sub); }
.footer-meta {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-sub);
  text-align: right;
  display: flex;
  justify-content: flex-end;
  gap: 14px;
  align-items: center;
}
.footer-hint { color: var(--brand-700); }

@media (max-width: 1080px) {
  .dist-row, .corridor, .alumni-row { grid-template-columns: 1fr 1fr; }
}
</style>
