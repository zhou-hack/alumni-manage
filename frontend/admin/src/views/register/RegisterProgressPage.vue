<script setup>
/**
 * 注册审核通知（P4 体验类 · 后端暂未提供注册审核端点）
 * - 校友视角：展示当前账号的注册 / 激活状态、待办事项、消息中心入口
 * - 校级 / 系统管理员视角：展示注册待审核入口占位（后端审核接口就绪后即可联通）
 * 所有数据来自现有 API（user/me、notifications/unread-count）
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { Refresh, Bell, Right, CircleCheck, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getMe } from '@/api/auth'
import { getUnreadCount } from '@/api/notifications'
import { formatTime } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const unread = ref({ total: 0, by_type: {} })

const isAlumni = computed(() => ['alumni', 'student'].includes(userStore.user?.role))
const isReviewer = computed(() =>
  ['school_admin', 'system_admin', 'college_admin'].includes(userStore.user?.role),
)

async function refreshMe() {
  loading.value = true
  try {
    const res = await getMe()
    userStore.setLoginUser(res.data)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function refreshUnread() {
  try {
    const res = await getUnreadCount()
    unread.value = res.data || { total: 0, by_type: {} }
  } catch {
    /* 静默 */
  }
}

let timer = null
onMounted(async () => {
  await Promise.all([refreshMe(), refreshUnread()])
  timer = setInterval(refreshUnread, 30000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

function goNotifications() {
  router.push('/notifications').then(() => refreshUnread())
}

const registrationSteps = [
  { key: 'register', label: '提交注册', desc: '已通过手机号完成注册' },
  { key: 'activate', label: '激活账号', desc: '首次登录自动激活' },
  { key: 'archive', label: '补充资料', desc: '为荣誉展示、校友通讯录录入资料' },
  { key: 'honor', label: '录入荣誉', desc: '申报或由导员批量导入荣誉' },
]

/** 当前账号的注册进度（按角色粗略判断） */
function stepStateOf(step) {
  if (step.key === 'register') return 'done'
  if (step.key === 'activate') return userStore.user?.is_active || userStore.user?.status !== 'pending' ? 'done' : 'todo'
  if (step.key === 'archive') return 'todo'
  if (step.key === 'honor') return 'todo'
  return 'todo'
}

const TYPE_TEXT = {
  review: '审核',
  honor: '荣誉',
  archive: '档案',
  announcement: '公告',
  system: '系统',
  wall: '留言墙',
  consent: '授权',
}
</script>

<template>
  <div class="reg-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">注册审核通知</h2>
        <p class="page-sub">
          {{ isReviewer ? '注册审核待办与消息提醒集中入口' : '账号注册 / 激活 / 审核状态 · 消息提醒集中入口' }}
        </p>
      </div>
      <el-button :icon="Refresh" @click="refreshMe">刷新</el-button>
    </div>

    <!-- 校友视角：注册进度 -->
    <el-card v-if="isAlumni" shadow="never" class="block">
      <template #header>
        <span class="block-title">注册进度</span>
      </template>
      <div class="steps">
        <div
          v-for="(step, idx) in registrationSteps"
          :key="step.key"
          class="step"
          :class="{ done: stepStateOf(step) === 'done' }"
        >
          <div class="step-num">{{ stepStateOf(step) === 'done' ? '✓' : idx + 1 }}</div>
          <div class="step-body">
            <div class="step-label">{{ step.label }}</div>
            <div class="step-desc">{{ step.desc }}</div>
          </div>
        </div>
      </div>
      <div class="meta-line">
        注册时间：{{ formatTime(userStore.user?.created_at) }} · 角色：{{ userStore.roleText }}
      </div>
    </el-card>

    <!-- 管理员视角：注册审核占位（后端审核端点就绪后即可联通） -->
    <el-card v-if="isReviewer" shadow="never" class="block">
      <template #header>
        <span class="block-title">待审核注册</span>
      </template>
      <div class="placeholder">
        <div class="ph-ico"><Lock :size="28" /></div>
        <div class="ph-title">注册审核端点正在建设</div>
        <div class="ph-desc">
          后端 <code>/auth/register-review</code> 接口就绪后，此处将列出待审核的新校友注册申请，
          管理员可点击"通过 / 驳回"完成审核，结果会自动写入消息中心通知校友。
        </div>
        <el-button type="primary" plain @click="goNotifications">前往消息中心</el-button>
      </div>
    </el-card>

    <!-- 实时未读消息概览 -->
    <el-card shadow="never" class="block">
      <template #header>
        <div class="head-row">
          <span class="block-title">
            <el-icon><Bell /></el-icon>
            未读消息
          </span>
          <el-button link type="primary" @click="goNotifications">查看全部 <el-icon><Right /></el-icon></el-button>
        </div>
      </template>
      <div v-if="unread.total === 0" class="empty">
        <div class="empty-ico"><CircleCheck :size="36" /></div>
        <div>暂无未读消息</div>
      </div>
      <div v-else class="type-grid">
        <div class="type-card">
          <div class="type-num">{{ unread.total }}</div>
          <div class="type-lbl">全部未读</div>
        </div>
        <div
          v-for="(count, t) in unread.by_type"
          :key="t"
          class="type-card"
          @click="goNotifications"
        >
          <div class="type-num">{{ count }}</div>
          <div class="type-lbl">{{ TYPE_TEXT[t] || t }}</div>
        </div>
      </div>
    </el-card>

    <div class="info-bar">
      <div class="info-text">
        <strong>消息中心</strong>统一收纳审核结果、荣誉证书确认、注册进度等站内通知。点击右上角铃铛查看实时未读数。
      </div>
    </div>
  </div>
</template>

<style scoped>
.reg-page {
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
.block {
  border-radius: var(--radius-xl);
  border: 1px solid var(--line);
}
.block-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}
.head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 注册进度 */
.steps {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.step {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: var(--card);
}
.step.done {
  border-color: rgba(5, 150, 105, 0.3);
  background: rgba(5, 150, 105, 0.06);
}
.step-num {
  width: 28px;
  height: 28px;
  flex: none;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: rgba(15, 23, 42, 0.05);
  color: var(--text-sub);
  font-weight: 700;
  font-size: 13px;
}
.step.done .step-num {
  background: #059669;
  color: #fff;
}
.step-label {
  font-weight: 600;
  font-size: 13px;
}
.step-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-sub);
}
.meta-line {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
  font-size: 12px;
  color: var(--text-sub);
}

/* 占位 */
.placeholder {
  padding: 24px 12px;
  text-align: center;
  color: var(--text-sub);
}
.ph-ico {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: rgba(15, 23, 42, 0.05);
  margin: 0 auto 12px;
}
.ph-title {
  font-weight: 600;
  font-size: 15px;
  color: var(--text-main);
  margin-bottom: 6px;
}
.ph-desc {
  font-size: 13px;
  line-height: 1.7;
  margin: 0 auto 14px;
  max-width: 520px;
}
.ph-desc code {
  background: rgba(15, 23, 42, 0.05);
  padding: 1px 6px;
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

/* 未读网格 */
.type-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 10px;
}
.type-card {
  padding: 14px 16px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.04);
  border: 1px solid var(--line);
  cursor: pointer;
  transition: all 0.15s;
}
.type-card:hover {
  background: rgba(47, 107, 255, 0.06);
  border-color: var(--brand);
  transform: translateY(-1px);
}
.type-num {
  font-size: 22px;
  font-weight: 700;
  color: var(--brand);
}
.type-lbl {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 4px;
}

.empty {
  padding: 24px 12px;
  text-align: center;
  color: var(--text-sub);
}
.empty-ico {
  color: #059669;
  margin-bottom: 6px;
}

.info-bar {
  padding: 12px 16px;
  border-radius: 10px;
  background: rgba(47, 107, 255, 0.06);
  border: 1px dashed rgba(47, 107, 255, 0.3);
  font-size: 13px;
  line-height: 1.6;
}
</style>