<script setup>
/**
 * 站内消息中心（M14）
 * - 顶部筛选：类型 / 仅未读 / 全部已读
 * - 列表：未读高亮、点击进抽屉看详情、点"标已读"
 * - 抽屉：详情 + 关联跳转（bizType/bizId → 路由）
 * - 抽屉内"订阅设置"：勾选要接收的消息类型
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, Setting } from '@element-plus/icons-vue'
import {
  getNotifications,
  getUnreadCount,
  markRead,
  markAllRead,
  getSubscriptions,
  saveSubscriptions,
  typeOf,
  SUBSCRIPTION_SCENES,
} from '@/api/notifications'
import { formatTime } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const unreadOnly = ref(false)
const typeFilter = ref('')

// 抽屉
const drawerOpen = ref(false)
const active = ref(null)

// 未读红点
const unread = ref({ total: 0, by_type: {} })

// 订阅弹窗（短信 / 邮箱 双渠道，每渠道 enabled + scenes）
const subOpen = ref(false)
const subSaving = ref(false)
/** 邮箱基础校验（与后端 SubscriptionService.normalizeAddress 同步：开 enable 必填合法地址） */
const EMAIL_RE = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/
const subForm = ref(emptySub())
function emptySub() {
  return {
    sms: { enabled: false, scenes: [] },
    email: { enabled: false, address: '', scenes: [] },
  }
}

/* ---------- 数据加载 ---------- */

async function loadList() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize }
    if (typeFilter.value) params.type = typeFilter.value
    if (unreadOnly.value) params.unread = true
    const res = await getNotifications(params)
    list.value = res.data.list || []
    total.value = res.data.total
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadUnread() {
  try {
    const res = await getUnreadCount()
    unread.value = res.data || { total: 0, by_type: {} }
  } catch {
    // 静默：未读数失败不影响主功能
  }
}

function onFilterChange() {
  page.value = 1
  loadList()
}

async function refreshAll() {
  await Promise.all([loadList(), loadUnread()])
}

/* ---------- 标已读 ---------- */

async function handleMarkRead(row) {
  if (row.read) return
  try {
    await markRead(row.id)
    row.read = true
    unread.value = {
      total: Math.max(0, unread.value.total - 1),
      by_type: {
        ...(unread.value.by_type || {}),
        [row.type]: Math.max(0, (unread.value.by_type?.[row.type] || 1) - 1),
      },
    }
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function handleMarkAllRead() {
  try {
    await ElMessageBox.confirm('确认将当前筛选下的全部消息标记为已读？', '全部已读', { type: 'info' })
  } catch {
    return
  }
  try {
    await markAllRead(typeFilter.value || undefined)
    ElMessage.success('已全部标为已读')
    await refreshAll()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/* ---------- 抽屉：详情 + 跳转 ---------- */

function openDetail(row) {
  active.value = row
  drawerOpen.value = true
  handleMarkRead(row)
}

function bizRouteOf(row) {
  if (!row?.bizType || !row?.bizId) return ''
  const map = {
    review: { name: 'Reviews', query: { id: row.bizId } },
    honor: { name: 'HonorDraft' },
    archive: { name: 'Archives' },
    announcement: { name: 'AnnouncementDetail', params: { id: row.bizId } },
  }
  return map[row.bizType]
}

function goBiz() {
  const r = bizRouteOf(active.value)
  if (!r) {
    ElMessage.info('该消息未关联可跳转的业务')
    return
  }
  drawerOpen.value = false
  router.push(r)
}

/** 跳到独立订阅设置页 */
function goPrefs() {
  router.push('/notifications/prefs')
}

/* ---------- 订阅设置 ---------- */

async function openSubs() {
  subOpen.value = true
  subForm.value = emptySub()
  try {
    const res = await getSubscriptions()
    const d = res.data || {}
    subForm.value = {
      sms: {
        enabled: Boolean(d.sms?.enabled),
        scenes: Array.isArray(d.sms?.scenes) ? [...d.sms.scenes] : [],
      },
      email: {
        enabled: Boolean(d.email?.enabled),
        address: d.email?.address || '',
        scenes: Array.isArray(d.email?.scenes) ? [...d.email.scenes] : [],
      },
    }
  } catch {
    /* 默认全关 */
  }
}

async function saveSubs() {
  // 客户端校验：开启邮箱订阅时必填合法邮箱
  if (subForm.value.email.enabled) {
    if (!subForm.value.email.address || !EMAIL_RE.test(subForm.value.email.address)) {
      ElMessage.warning('开启邮箱订阅时需填写合法邮箱地址')
      return
    }
  }
  // 任一渠道开启但未选场景 → 提醒（业务上可空，但默认提示一下）
  if (
    (subForm.value.sms.enabled && !subForm.value.sms.scenes.length) ||
    (subForm.value.email.enabled && !subForm.value.email.scenes.length)
  ) {
    try {
      await ElMessageBox.confirm(
        '已开启订阅但未勾选任何场景，将不会收到任何通知。确认保存？',
        '未选择场景',
        { type: 'warning', confirmButtonText: '仍然保存', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
  }
  subSaving.value = true
  try {
    await saveSubscriptions({
      sms: {
        enabled: subForm.value.sms.enabled,
        scenes: subForm.value.sms.scenes,
      },
      email: {
        enabled: subForm.value.email.enabled,
        address: subForm.value.email.address.trim(),
        scenes: subForm.value.email.scenes,
      },
    })
    ElMessage.success('订阅已保存')
    subOpen.value = false
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    subSaving.value = false
  }
}

/* ---------- 定时轮询未读数（每 30s） ---------- */

let timer = null
onMounted(async () => {
  await refreshAll()
  timer = setInterval(loadUnread, 30000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

/* ---------- 派生 ---------- */

const tBadge = (t) => typeOf(t)
const empty = computed(() => !loading.value && !list.value.length)
</script>

<template>
  <div class="ntf-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">
          <el-icon style="vertical-align: -2px; margin-right: 6px"><Bell /></el-icon>
          消息中心
          <el-badge v-if="unread.total > 0" :value="unread.total" :max="99" class="title-badge" />
        </h2>
        <p class="page-sub">系统消息、审核结果、荣誉证书确认等站内通知集中查看</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Setting" @click="openSubs">快速订阅</el-button>
        <el-button @click="goPrefs">订阅设置页 →</el-button>
        <el-button type="primary" :disabled="unread.total === 0" @click="handleMarkAllRead">
          全部已读
        </el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-radio-group v-model="typeFilter" @change="onFilterChange">
        <el-radio-button value="">全部类型</el-radio-button>
        <el-radio-button v-for="(v, k) in typeOf" :key="k" :value="k">{{ v.text }}</el-radio-button>
      </el-radio-group>
      <el-checkbox v-model="unreadOnly" @change="onFilterChange" class="unread-toggle">
        仅未读
      </el-checkbox>
    </div>

    <div v-loading="loading" class="ntf-list">
      <div
        v-for="row in list"
        :key="row.id"
        class="ntf-item"
        :class="{ unread: !row.read }"
        @click="openDetail(row)"
      >
        <div class="ntf-dot" aria-hidden="true"></div>
        <div class="ntf-body">
          <div class="ntf-row1">
            <span class="ntf-type" :class="'tone-' + tBadge(row.type).tone">{{ tBadge(row.type).text }}</span>
            <span class="ntf-title">{{ row.title }}</span>
          </div>
          <div class="ntf-content">{{ row.content }}</div>
          <div class="ntf-meta">
            <span>{{ formatTime(row.created_at) }}</span>
            <span v-if="row.bizType" class="biz">· {{ row.bizType }}#{{ row.bizId }}</span>
          </div>
        </div>
      </div>
      <el-empty v-if="empty" description="暂无消息" />
    </div>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        :total="total"
        :page-size="pageSize"
        layout="total, prev, pager, next"
        @current-change="loadList"
      />
    </div>

    <!-- ================= 详情抽屉 ================= -->
    <el-drawer v-model="drawerOpen" direction="rtl" size="420px" title="消息详情">
      <template v-if="active">
        <div class="detail">
          <span class="ntf-type" :class="'tone-' + tBadge(active.type).tone">
            {{ tBadge(active.type).text }}
          </span>
          <h3 class="detail-title">{{ active.title }}</h3>
          <div class="detail-time">{{ formatTime(active.created_at) }}</div>
          <div class="detail-content">{{ active.content }}</div>
          <el-button v-if="bizRouteOf(active)" type="primary" plain class="biz-btn" @click="goBiz">
            前往查看
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- ================= 订阅弹窗 ================= -->
    <el-dialog v-model="subOpen" title="通知订阅" width="520px" :close-on-click-modal="false">
      <p class="sub-tip">分别开启短信 / 邮箱订阅，并勾选要接收的通知场景。未开启的渠道不会推送。</p>

      <div class="sub-channels">
        <!-- SMS -->
        <div class="sub-card">
          <div class="sub-card-head">
            <el-checkbox v-model="subForm.sms.enabled">短信订阅</el-checkbox>
            <span class="sub-card-hint">手机号沿用账号绑定号</span>
          </div>
          <el-checkbox-group v-model="subForm.sms.scenes" :disabled="!subForm.sms.enabled" class="sub-scenes">
            <el-checkbox v-for="s in SUBSCRIPTION_SCENES" :key="s.key" :value="s.key">
              {{ s.label }}
            </el-checkbox>
          </el-checkbox-group>
        </div>

        <!-- Email -->
        <div class="sub-card">
          <div class="sub-card-head">
            <el-checkbox v-model="subForm.email.enabled">邮箱订阅</el-checkbox>
            <span class="sub-card-hint">开启需填写合法邮箱</span>
          </div>
          <el-input
            v-model="subForm.email.address"
            placeholder="your@example.com"
            :disabled="!subForm.email.enabled"
            clearable
            class="sub-email"
          />
          <el-checkbox-group v-model="subForm.email.scenes" :disabled="!subForm.email.enabled" class="sub-scenes">
            <el-checkbox v-for="s in SUBSCRIPTION_SCENES" :key="s.key" :value="s.key">
              {{ s.label }}
            </el-checkbox>
          </el-checkbox-group>
        </div>
      </div>

      <template #footer>
        <el-button @click="subOpen = false">取消</el-button>
        <el-button type="primary" :loading="subSaving" @click="saveSubs">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ntf-page {
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
  display: flex;
  align-items: center;
}
.title-badge {
  margin-left: 10px;
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
  gap: 16px;
}
.unread-toggle {
  margin-left: auto;
}
.ntf-list {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  overflow: hidden;
  min-height: 200px;
}
.ntf-item {
  display: flex;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
  transition: background 0.18s;
}
.ntf-item:hover {
  background: rgba(15, 23, 42, 0.03);
}
.ntf-item:last-child {
  border-bottom: none;
}
.ntf-item.unread {
  background: rgba(64, 158, 255, 0.05);
}
.ntf-item.unread:hover {
  background: rgba(64, 158, 255, 0.1);
}
.ntf-dot {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: transparent;
  margin-top: 8px;
}
.ntf-item.unread .ntf-dot {
  background: var(--brand);
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.15);
}
.ntf-body {
  flex: 1;
  min-width: 0;
}
.ntf-row1 {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ntf-title {
  font-weight: 600;
  font-size: 14px;
}
.ntf-content {
  margin-top: 4px;
  color: var(--text-sub);
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.ntf-meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-sub);
  display: flex;
  gap: 6px;
}
.biz {
  opacity: 0.8;
}

/* 类型徽标 */
.ntf-type {
  display: inline-block;
  padding: 1px 8px;
  border-radius: var(--radius-full);
  font-size: 12px;
  flex: none;
}
.tone-warning { color: #d97706; background: rgba(217, 119, 6, 0.12); }
.tone-success { color: #059669; background: rgba(5, 150, 105, 0.12); }
.tone-primary { color: #2563eb; background: rgba(37, 99, 235, 0.12); }
.tone-info    { color: #64748b; background: rgba(100, 116, 139, 0.12); }
.tone-danger  { color: #dc2626; background: rgba(220, 38, 38, 0.12); }

.pager {
  display: flex;
  justify-content: flex-end;
}

/* 抽屉 */
.detail {
  padding: 4px 4px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.detail-title {
  margin: 4px 0 0;
  font-size: 17px;
  font-weight: 700;
}
.detail-time {
  font-size: 12px;
  color: var(--text-sub);
}
.detail-content {
  margin-top: 6px;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}
.biz-btn {
  align-self: flex-start;
  margin-top: 8px;
}

/* 订阅 */
.sub-tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--text-sub);
  line-height: 1.6;
}
.sub-channels {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.sub-card {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 14px 16px;
  background: var(--surface-2, rgba(15, 23, 42, 0.02));
}
.sub-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.sub-card-hint {
  font-size: 12px;
  color: var(--text-sub);
  opacity: 0.8;
}
.sub-email {
  margin-bottom: 10px;
}
.sub-scenes {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
</style>