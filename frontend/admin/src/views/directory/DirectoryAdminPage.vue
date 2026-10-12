<script setup>
/**
 * 校友档案索引管理（/directory-admin · school_admin / system_admin）
 *
 * 「档案索引」不是手工维护的目录表 —— 后端 SQL 实时聚合：
 *   users ⋈ alumni_base ⋈ archives(status=approved) ⋈ showcase ⋈ consents(scope=screen)
 * 任意一条不满足，**自动不出现在门户索引**（红线同 /screen/feed）。
 *
 * 本页提供：
 *  1) 收录流程说明（顶部 3 步卡）
 *  2) Tab 1 已收录：拉取 /api/v1/alumni/directory（门户公开列表的同源口径）
 *  3) Tab 2 待收录诊断：status=approved 的档案逐个查详情，对比
 *        - published（是否已上架到荣誉墙）
 *        - consentActive（大屏授权是否有效）
 *     给出"缺哪一步"提示与跳转入口
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search, CircleClose } from '@element-plus/icons-vue'
import { getDirectory } from '@/api/alumniAdmin'
import { getArchives, getArchive } from '@/api/archive'
import LucideIcon from '@/components/LucideIcon.vue'

const AVATAR_COLORS = ['#A63D2F', '#C19A3F', '#3E6B4F', '#3D5A73', '#2F5D50', '#8B5CF6']

const activeTab = ref('indexed')

/* ============ 字典（学院筛选） ============ */
const colleges = ref([])
async function loadColleges() {
  try {
    const mod = await import('@/api/dict')
    colleges.value = await mod.getDict('college')
  } catch {
    /* 字典失败不阻塞 */
  }
}

/* ============ Tab 1：已收录 ============ */
const indexedList = ref([])
const indexedLoading = ref(false)
const indexedKeyword = ref('')
const indexedCollege = ref('')
const indexedGradYear = ref('')

async function loadIndexed() {
  indexedLoading.value = true
  try {
    const params = { page: 1, page_size: 100 }
    if (indexedKeyword.value) params.keyword = indexedKeyword.value.trim()
    if (indexedCollege.value) params.college_id = Number(indexedCollege.value)
    if (indexedGradYear.value) params.grad_year = Number(indexedGradYear.value)
    const res = await getDirectory(params)
    indexedList.value = res.data && res.data.list ? res.data.list : []
  } catch (e) {
    ElMessage.error((e && e.message) || '索引列表加载失败')
  } finally {
    indexedLoading.value = false
  }
}

/* ============ Tab 2：待收录诊断 ============ */
const candidatesLoading = ref(false)
const candidates = ref([])
const diagnoseMap = ref({})
const candidateKeyword = ref('')
const candidateCollege = ref('')

async function loadCandidates() {
  candidatesLoading.value = true
  try {
    const params = { page: 1, page_size: 50, status: 'approved' }
    if (candidateKeyword.value) params.keyword = candidateKeyword.value.trim()
    if (candidateCollege.value) params.college_id = Number(candidateCollege.value)
    const res = await getArchives(params)
    candidates.value = res.data && res.data.list ? res.data.list : []
    diagnoseMap.value = {}
    await diagnoseAll()
  } catch (e) {
    ElMessage.error((e && e.message) || '已审核档案加载失败')
  } finally {
    candidatesLoading.value = false
  }
}

async function diagnoseAll() {
  const tasks = candidates.value.map(async (row) => {
    try {
      const r = await getArchive(row.id)
      const d = r.data || {}
      const reasons = []
      if (!d.consentActive) reasons.push('缺大屏授权')
      if (!d.published) reasons.push('未上架到荣誉墙')
      diagnoseMap.value = Object.assign({}, diagnoseMap.value, {
        [row.id]: {
          published: !!d.published,
          consentActive: !!d.consentActive,
          reasons: reasons,
        },
      })
    } catch (_e) {
      diagnoseMap.value = Object.assign({}, diagnoseMap.value, {
        [row.id]: { published: false, consentActive: false, reasons: ['详情获取失败'] },
      })
    }
  })
  await Promise.all(tasks)
}

const missingCandidates = computed(function () {
  return candidates.value.filter(function (c) {
    const d = diagnoseMap.value[c.id]
    return d && d.reasons && d.reasons.length > 0
  })
})

/* ============ 跳转 ============ */
function goHonorWall() {
  window.location.hash = '#/honor-wall'
}
function goArchives() {
  window.location.hash = '#/archives'
}

async function refreshAll() {
  if (activeTab.value === 'indexed') await loadIndexed()
  else await loadCandidates()
}

onMounted(async function () {
  await loadColleges()
  await loadIndexed()
})

async function handleTabChange(name) {
  if (name === 'candidate' && candidates.value.length === 0) {
    await loadCandidates()
  }
}
</script>

<template>
  <div class="dir-admin">
    <div class="page-head">
      <div>
        <div class="eyebrow">校友档案索引</div>
        <h1 class="page-title">校友档案索引收录</h1>
        <p class="page-sub">
          门户「校友档案索引」实时聚合于后端 SQL，不建表、不维护名单 —— 满足下方三个条件
          的校友会自动出现在公开名录中。本页可查看收录情况，并定位"应该收录但未收录"的档案。
        </p>
      </div>
      <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
    </div>

    <div class="flow-card">
      <div class="flow-title">
        <LucideIcon name="InfoFilled" :size="16" />
        <span>如何被档案索引收录（3 步）</span>
      </div>
      <div class="flow-steps">
        <div class="step">
          <div class="step-no">1</div>
          <div class="step-body">
            <div class="step-title">提交档案</div>
            <div class="step-desc">校友在「提交资料」提交档案 → 进入审核队列。</div>
          </div>
        </div>
        <div class="step-arrow">›</div>
        <div class="step">
          <div class="step-no">2</div>
          <div class="step-body">
            <div class="step-title">审核通过</div>
            <div class="step-desc">导员 / 院级 / 校级审核通过，状态变为 <b>approved</b>。</div>
          </div>
        </div>
        <div class="step-arrow">›</div>
        <div class="step">
          <div class="step-no">3</div>
          <div class="step-body">
            <div class="step-title">勾选授权 + 上架</div>
            <div class="step-desc">
              档案详情勾选<b>大屏授权</b> → 校级管理员在
              <el-link type="primary" :underline="false" @click="goHonorWall">荣誉墙管理</el-link>
              里点「上架」。
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="dir-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="已收录校友" name="indexed">
        <div class="toolbar">
          <el-input
            v-model="indexedKeyword"
            placeholder="搜索 姓名 / 学号 / 学院 / 荣誉 / 职位"
            clearable
            style="width: 280px"
            @keyup.enter="loadIndexed"
            @clear="loadIndexed"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select
            v-model="indexedCollege"
            placeholder="学院"
            clearable
            style="width: 160px"
            @change="loadIndexed"
          >
            <el-option
              v-for="c in colleges"
              :key="c.id"
              :label="c.label"
              :value="c.value"
            />
          </el-select>
          <el-input
            v-model="indexedGradYear"
            placeholder="届别"
            clearable
            style="width: 110px"
            @keyup.enter="loadIndexed"
            @clear="loadIndexed"
          />
          <el-button type="primary" :icon="Search" @click="loadIndexed">查询</el-button>
          <div class="toolbar-spacer"></div>
          <span class="result-count">共 {{ indexedList.length }} 人</span>
        </div>

        <div class="grid" v-loading="indexedLoading">
          <article v-for="(a, i) in indexedList" :key="a.student_id" class="card">
            <div
              class="av"
              :style="{ background: AVATAR_COLORS[i % AVATAR_COLORS.length] }"
            >
              {{ (a.name || '?').slice(0, 1) }}
            </div>
            <div class="meta">
              <div class="name">{{ a.name }}</div>
              <div class="sid">{{ a.student_id }}</div>
              <div class="line">
                <span>{{ a.college || '—' }}</span>
                <span v-if="a.grad_year" class="dot">·</span>
                <span v-if="a.grad_year">{{ a.grad_year }} 届</span>
              </div>
              <div v-if="a.current" class="line sub">{{ a.current }}</div>
              <div class="line sub">代表荣誉：{{ a.top_honor || '—' }}</div>
              <div class="line sub">公开档案：{{ a.archive_count || 0 }} 份</div>
            </div>
          </article>
          <div v-if="!indexedLoading && indexedList.length === 0" class="empty">
            <LucideIcon name="DocumentRemove" :size="32" />
            <p>当前无已收录校友</p>
            <p class="empty-sub">
              没有档案满足「approved + 授权有效 + 已上架」三条件。切到下一个 Tab 看看待收录档案。
            </p>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="'待收录诊断（' + missingCandidates.length + '）'" name="candidate">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          class="tip"
        >
          <template #title>
            <span>
              以下档案已审核通过，但因缺少<b>大屏授权</b>或<b>未上架</b>而未出现在门户索引中。
              点击对应按钮可跳到操作位置。
            </span>
          </template>
        </el-alert>

        <div class="toolbar">
          <el-input
            v-model="candidateKeyword"
            placeholder="档案标题 / 学号 / 姓名"
            clearable
            style="width: 240px"
            @keyup.enter="loadCandidates"
            @clear="loadCandidates"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select
            v-model="candidateCollege"
            placeholder="学院"
            clearable
            style="width: 160px"
            @change="loadCandidates"
          >
            <el-option
              v-for="c in colleges"
              :key="c.id"
              :label="c.label"
              :value="c.value"
            />
          </el-select>
          <el-button type="primary" :icon="Search" @click="loadCandidates">查询</el-button>
        </div>

        <el-card shadow="never" class="table-card">
          <el-table :data="missingCandidates" v-loading="candidatesLoading" stripe>
            <el-table-column prop="id" label="档案ID" width="80" />
            <el-table-column label="档案标题" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.title }}</template>
            </el-table-column>
            <el-table-column label="获得者" width="140">
              <template #default="{ row }">
                {{ row.real_name || '—' }}<span class="sid">（{{ row.student_id || '—' }}）</span>
              </template>
            </el-table-column>
            <el-table-column label="学院 · 届别" width="170">
              <template #default="{ row }">
                {{ row.college || '—' }}
                <span v-if="row.grad_year" class="sid">· {{ row.grad_year }} 届</span>
              </template>
            </el-table-column>
            <el-table-column label="诊断结果" min-width="240">
              <template #default="{ row }">
                <template v-if="diagnoseMap[row.id]">
                  <el-tag
                    v-for="r in diagnoseMap[row.id].reasons"
                    :key="r"
                    type="warning"
                    size="small"
                    effect="light"
                    class="diag-tag"
                  >
                    <el-icon style="margin-right: 2px"><CircleClose /></el-icon>
                    {{ r }}
                  </el-tag>
                </template>
                <span v-else class="sid">诊断中…</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <template v-if="diagnoseMap[row.id] && diagnoseMap[row.id].reasons.indexOf('缺大屏授权') !== -1">
                  <el-button link type="primary" @click="goArchives">去档案详情授权</el-button>
                </template>
                <template v-else-if="diagnoseMap[row.id] && diagnoseMap[row.id].reasons.indexOf('未上架到荣誉墙') !== -1">
                  <el-button link type="primary" @click="goHonorWall">去荣誉墙上架</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!candidatesLoading && missingCandidates.length === 0" class="empty">
            <LucideIcon name="CircleCheckFilled" :size="32" />
            <p>所有已审核档案均已收录 ✨</p>
            <p class="empty-sub">保持这种状态，门户索引会持续展示这些校友。</p>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.dir-admin { padding: 0; }

.eyebrow {
  font-family: var(--font-mono, monospace);
  font-size: 11px;
  letter-spacing: 1.2px;
  color: var(--text-sub);
  text-transform: uppercase;
}
.page-title { font-size: 26px; font-weight: 700; margin: 6px 0 6px; color: var(--text-main); }
.page-sub { font-size: 13px; color: var(--text-sub); margin: 0; max-width: 720px; line-height: 1.6; }

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}

.flow-card {
  background: linear-gradient(180deg, #f5f9ff, #ffffff);
  border: 1px solid #d6e4f5;
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 18px;
}
.flow-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--brand-700);
  margin-bottom: 14px;
}
.flow-steps {
  display: flex;
  align-items: stretch;
  gap: 12px;
}
.step {
  flex: 1;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid #e8eef9;
  border-radius: 10px;
  padding: 12px 14px;
}
.step-no {
  width: 26px; height: 26px;
  display: grid; place-items: center;
  background: linear-gradient(135deg, var(--brand-500), var(--brand-700));
  color: #fff;
  border-radius: 8px;
  font-weight: 700;
  flex-shrink: 0;
  font-size: 13px;
}
.step-body { min-width: 0; }
.step-title { font-size: 13.5px; font-weight: 700; color: var(--text-main); }
.step-desc { font-size: 12px; color: var(--text-sub); margin-top: 4px; line-height: 1.6; }
.step-arrow {
  display: grid; place-items: center;
  font-size: 24px; color: #c2d3e8;
  font-weight: 700;
  align-self: center;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 14px 0;
}
.toolbar-spacer { flex: 1; }
.result-count {
  font-size: 12px;
  color: var(--text-sub);
  padding: 4px 10px;
  background: var(--surface-2);
  border-radius: 999px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}
.card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 14px;
  display: flex;
  gap: 12px;
  align-items: flex-start;
  transition: transform 0.12s, box-shadow 0.12s;
}
.card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(22, 55, 96, 0.10);
}
.av {
  width: 44px; height: 44px;
  border-radius: 50%;
  display: grid; place-items: center;
  color: #fff;
  font-weight: 700;
  font-size: 17px;
  flex-shrink: 0;
}
.meta { flex: 1; min-width: 0; }
.name { font-size: 14.5px; font-weight: 700; color: var(--text-main); }
.sid { font-size: 11px; color: var(--text-sub); }
.line {
  font-size: 12.5px;
  color: var(--text-main);
  margin-top: 2px;
}
.line.sub { color: var(--text-sub); }
.dot { margin: 0 4px; color: var(--line); }

.empty {
  grid-column: 1 / -1;
  text-align: center;
  padding: 60px 20px;
  color: var(--text-sub);
}
.empty p { margin: 8px 0 0; font-size: 13px; }
.empty .empty-sub { font-size: 12px; color: #a8b3c0; margin-top: 4px; }

.tip { border-radius: 10px; margin: 8px 0 14px; }
.diag-tag { margin-right: 6px; margin-bottom: 4px; }
.table-card { border-radius: var(--radius-xl); }

.dir-tabs { margin-top: 4px; }
</style>
