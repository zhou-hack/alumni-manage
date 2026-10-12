<script setup>
/**
 * 校友首页（PC 端校友视角，PRD V2.3：PC 端全量功能）
 * - 状态概览：草稿 / 在审 / 已通过 / 已驳回 数量（拉我的档案列表本地统计）
 * - 快捷入口：提交资料 / 我的资料 / 荣誉录入 / 我的荣誉
 * - 授权提示：已通过未授权的档案提示可授权上大屏
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getMyArchives } from '@/api/archive'
import { getMyConsents } from '@/api/consents'
import { formatTime } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const archives = ref([])
const consentIds = ref(new Set())

const stats = computed(() => {
  const s = { draft: 0, pending: 0, approved: 0, rejected: 0 }
  for (const a of archives.value) {
    if (a.status === 'draft') s.draft++
    else if (['pending_first', 'pending_second'].includes(a.status)) s.pending++
    else if (a.status === 'approved') s.approved++
    else if (a.status === 'rejected') s.rejected++
  }
  return s
})

/** 已通过且未授权大屏的档案（提示授权） */
const unconsented = computed(() =>
  archives.value.filter((a) => a.status === 'approved' && !consentIds.value.has(a.id)),
)

async function load() {
  loading.value = true
  try {
    const [arcRes, cRes] = await Promise.all([
      getMyArchives({ page: 1, page_size: 100 }),
      getMyConsents().catch(() => ({ data: { list: [] } })),
    ])
    archives.value = arcRes.data.list || []
    consentIds.value = new Set((cRes.data.list || []).filter((c) => c.active).map((c) => c.archive_id))
  } finally {
    loading.value = false
  }
}

onMounted(load)

const quicks = [
  { key: 'submit', title: '提交资料', desc: '填写获奖、证书、实践等经历', to: '/archive-form', icon: 'EditPen' },
  { key: 'records', title: '我的资料', desc: '查看审核状态、驳回原因', to: '/my-archives', icon: 'Folder' },
  { key: 'honor', title: '荣誉录入', desc: '荣誉 / 证书 / 奖项统一录入', to: '/my-honors', icon: 'Medal' },
  { key: 'honors', title: '我的荣誉', desc: '查看荣誉审核进度', to: '/my-honors', icon: 'Trophy' },
]
</script>

<template>
  <div class="alumni-home" v-loading="loading">
    <div class="page-head">
      <div class="eyebrow">校友门户</div>
      <h1 class="page-title">你好，{{ userStore.user?.nickname || userStore.user?.real_name || '校友' }}</h1>
      <p class="page-desc">欢迎回到校友管理平台，在这里提交你的珍贵经历与荣誉，共建校友档案库。</p>
    </div>

    <!-- 状态概览 -->
    <div class="kpis">
      <div class="kpi" @click="router.push('/my-archives')">
        <div class="kpi-num">{{ stats.draft }}</div>
        <div class="kpi-label">草稿箱</div>
      </div>
      <div class="kpi kpi-pending" @click="router.push('/my-archives')">
        <div class="kpi-num">{{ stats.pending }}</div>
        <div class="kpi-label">审核中</div>
      </div>
      <div class="kpi kpi-ok" @click="router.push('/my-archives')">
        <div class="kpi-num">{{ stats.approved }}</div>
        <div class="kpi-label">已通过</div>
      </div>
      <div class="kpi kpi-rej" @click="router.push('/my-archives')">
        <div class="kpi-num">{{ stats.rejected }}</div>
        <div class="kpi-label">已驳回</div>
      </div>
    </div>

    <!-- 授权提示 -->
    <div v-if="unconsented.length" class="consent-tip">
      <span>
        你有 <b>{{ unconsented.length }}</b> 份已通过的资料尚未授权大屏展示，授权后经管理员上架即可在风采大屏展示。
      </span>
      <el-button type="primary" size="small" @click="router.push('/my-archives')">去授权</el-button>
    </div>

    <!-- 快捷入口 -->
    <div class="quicks">
      <div v-for="q in quicks" :key="q.key" class="quick-card" @click="router.push(q.to)">
        <el-icon class="q-icon"><component :is="q.icon" /></el-icon>
        <div class="q-title">{{ q.title }}</div>
        <div class="q-desc">{{ q.desc }}</div>
      </div>
    </div>

    <!-- 最近提交 -->
    <div v-if="archives.length" class="recent-card">
      <div class="rc-head">
        <span>最近提交</span>
        <el-button link type="primary" size="small" @click="router.push('/my-archives')">查看全部</el-button>
      </div>
      <div v-for="a in archives.slice(0, 5)" :key="a.id" class="rc-row" @click="router.push('/my-archives')">
        <span class="rc-title">{{ a.title }}</span>
        <span class="rc-time">{{ formatTime(a.created_at) }}</span>
      </div>
    </div>
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

.page-desc {
  margin: 0;
  font-size: 13.5px;
  color: var(--text-sub);
}

/* KPI */
.kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}

.kpi {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  padding: 18px 20px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.kpi:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.kpi-num {
  font-family: var(--font-display);
  font-size: 30px;
  font-weight: 700;
  color: var(--text-main);
}

.kpi-pending .kpi-num {
  color: var(--amber-500);
}

.kpi-ok .kpi-num {
  color: var(--green-600, #16a34a);
}

.kpi-rej .kpi-num {
  color: var(--red-500);
}

.kpi-label {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-sub);
}

/* 授权提示 */
.consent-tip {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  background: var(--brand-50);
  border: 1px solid var(--brand-100);
  border-radius: var(--radius-lg);
  padding: 12px 18px;
  font-size: 13.5px;
  color: var(--brand-800);
  margin-bottom: 18px;
}

/* 快捷入口 */
.quicks {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}

.quick-card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  padding: 20px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
}

.quick-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--brand-200);
}

.q-icon {
  font-size: 26px;
  color: var(--brand-500);
}

.q-title {
  margin-top: 10px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}

.q-desc {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--text-mute);
}

/* 最近提交 */
.recent-card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.rc-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
  border-bottom: 1px solid var(--line);
}

.rc-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 18px;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
  transition: background 0.15s;
}

.rc-row:last-child {
  border-bottom: none;
}

.rc-row:hover {
  background: var(--surface-2);
}

.rc-title {
  font-size: 13.5px;
  color: var(--text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rc-time {
  flex: none;
  font-size: 12.5px;
  color: var(--text-mute);
}

@media (max-width: 1100px) {
  .kpis {
    grid-template-columns: repeat(2, 1fr);
  }

  .quicks {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
