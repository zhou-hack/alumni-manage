<script setup>
/**
 * 校友风采大屏（Day 2 F1②）
 * 轮播已审核（approved）+ 已上架 + 授权有效的内容（GET /screen/feed，免认证）
 * 支持学院筛选；每 30s 轮询刷新，保证上架后大屏可见
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { Monitor, Back, User, OfficeBuilding, Trophy } from '@element-plus/icons-vue'
import { getScreenFeed } from '@/api/showcase'
import { getDict } from '@/api/dict'

const router = useRouter()

const list = ref([])
const colleges = ref([])
const collegeId = ref('')
const activeIndex = ref(0)
const clock = ref('')

const CATEGORY_TEXT = {
  award: '获奖经历',
  certificate: '证书资质',
  experience: '实践经历',
  career: '就业履历',
  other: '其他资料',
}

const current = computed(() => list.value[activeIndex.value] || null)

let feedTimer = null
let clockTimer = null

/**
 * 返回上一页；如果历史栈为空（直接打开 / 新标签页 / 全屏进入）则回首页
 * 大屏为独立路由（不在 AdminLayout 下），常见场景下 router.back() 无效，
 * 这里给一个明确的兜底，保证始终能离开大屏。
 */
function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/home')
  }
}

function handleKeydown(e) {
  if (e.key === 'Escape') {
    e.preventDefault()
    goBack()
  }
}

function tickClock() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  clock.value = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function loadFeed() {
  try {
    const params = {}
    if (collegeId.value) params.college_id = collegeId.value
    const res = await getScreenFeed(params)
    list.value = res.data.list || []
    if (activeIndex.value >= list.value.length) activeIndex.value = 0
  } catch {
    // 大屏常驻展示，失败静默等下次轮询
  }
}

function next() {
  if (list.value.length) activeIndex.value = (activeIndex.value + 1) % list.value.length
}

function prev() {
  if (list.value.length) activeIndex.value = (activeIndex.value - 1 + list.value.length) % list.value.length
}

let switchTimer = null
function startAutoSwitch() {
  clearInterval(switchTimer)
  switchTimer = setInterval(next, 6000)
}

function manualGo(i) {
  activeIndex.value = i
  startAutoSwitch()
}

onMounted(async () => {
  tickClock()
  clockTimer = setInterval(tickClock, 1000)
  try {
    colleges.value = await getDict('college')
  } catch {
    // 字典失败不阻塞大屏
  }
  await loadFeed()
  startAutoSwitch()
  feedTimer = setInterval(loadFeed, 30000)
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  clearInterval(feedTimer)
  clearInterval(clockTimer)
  clearInterval(switchTimer)
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div class="screen-page">
    <!-- 顶栏 -->
    <header class="screen-head">
      <div class="head-left">
        <el-button
          class="back-btn-text"
          type="primary"
          size="default"
          :icon="Back"
          @click="goBack"
        >
          返回
        </el-button>
        <el-button class="back-btn" :icon="Back" circle @click="goBack" />
      </div>
      <div class="screen-title">
        <el-icon class="title-icon"><Monitor /></el-icon>
        <span>校友风采大屏</span>
      </div>
      <div class="screen-meta">
        <el-select
          v-model="collegeId"
          placeholder="全部学院"
          clearable
          size="small"
          class="college-select"
          @change="loadFeed"
        >
          <el-option v-for="c in colleges" :key="c.id" :label="c.label" :value="c.id" />
        </el-select>
        <span class="clock">{{ clock }}</span>
      </div>
    </header>

    <!-- 主轮播 -->
    <div v-if="current" class="screen-main" :key="current.id">
      <div class="show-card">
        <div class="show-cover">
          <img v-if="current.cover_url" :src="current.cover_url" :alt="current.title" />
          <div v-else class="cover-fallback">
            <el-icon :size="64"><Trophy /></el-icon>
          </div>
          <div class="cover-tag">{{ CATEGORY_TEXT[current.category] || current.category }}</div>
        </div>
        <div class="show-info">
          <h2 class="show-title">{{ current.title }}</h2>
          <p class="show-content">{{ current.content }}</p>
          <div class="show-owner">
            <span><el-icon><User /></el-icon>{{ current.owner }}</span>
            <span><el-icon><OfficeBuilding /></el-icon>{{ current.college }}</span>
            <span v-if="current.grad_year" class="grad">{{ current.grad_year }} 届</span>
          </div>
        </div>
      </div>

      <!-- 切换箭头 -->
      <button class="arrow arrow-left" @click="prev(); startAutoSwitch()">‹</button>
      <button class="arrow arrow-right" @click="next(); startAutoSwitch()">›</button>
    </div>
    <div v-else class="screen-empty">暂无已上架的展示内容</div>

    <!-- 底部缩略指示 -->
    <footer class="screen-foot">
      <div
        v-for="(item, i) in list"
        :key="item.id"
        class="thumb"
        :class="{ active: i === activeIndex }"
        @click="manualGo(i)"
      >
        <img v-if="item.cover_url" :src="item.cover_url" :alt="item.title" />
        <div v-else class="thumb-fallback"></div>
        <span class="thumb-title">{{ item.title }}</span>
      </div>
    </footer>

    <!-- 冗余入口：始终可见的退出按钮（按 ESC 或点击均可） -->
    <button class="exit-fab" title="按 ESC 退出大屏" @click="goBack">
      <el-icon :size="20"><Back /></el-icon>
      <span>退出大屏</span>
    </button>
  </div>
</template>

<style scoped>
.screen-page {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(1100px 500px at 50% -10%, rgba(47, 107, 176, 0.4), transparent 60%),
    linear-gradient(160deg, #0a1930 0%, #12253e 55%, #0a1930 100%);
  color: #fff;
  overflow: hidden;
}

.screen-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 28px;
  border-bottom: 1px solid rgba(47, 107, 255, 0.25);
}

.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.back-btn-text {
  background: linear-gradient(135deg, rgba(47, 107, 255, 0.85), rgba(64, 158, 255, 0.85));
  border-color: rgba(47, 107, 255, 0.6);
  color: #fff;
  font-weight: 600;
  letter-spacing: 2px;
  padding-left: 14px;
  padding-right: 18px;
}

.back-btn-text:hover {
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.95), rgba(47, 107, 255, 0.95));
  border-color: rgba(47, 107, 255, 0.9);
  transform: translateY(-1px);
}

.back-btn {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.screen-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 6px;
  text-indent: 6px;
  background: linear-gradient(180deg, #fff 20%, var(--brand-600) 90%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.title-icon {
  font-size: 26px;
  color: var(--brand-600);
}

.screen-meta {
  display: flex;
  align-items: center;
  gap: 14px;
}

.college-select {
  width: 150px;
}

.college-select :deep(.el-select__wrapper) {
  background: rgba(255, 255, 255, 0.08);
  box-shadow: none;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.college-select :deep(.el-select__placeholder),
.college-select :deep(.el-select__selected-item) {
  color: rgba(255, 255, 255, 0.85);
}

.clock {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.7);
  font-variant-numeric: tabular-nums;
}

/* 主展示区 */
.screen-main {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 90px;
  min-height: 0;
}

.show-card {
  display: flex;
  gap: 36px;
  width: min(1060px, 100%);
  max-height: 100%;
  padding: 28px;
  border-radius: var(--radius-xl);
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(16px);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.4);
  animation: cardIn 0.6s ease;
}

@keyframes cardIn {
  from {
    opacity: 0;
    transform: translateY(24px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.show-cover {
  position: relative;
  width: 46%;
  min-height: 320px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: rgba(255, 255, 255, 0.05);
  flex-shrink: 0;
}

.show-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(47, 107, 255, 0.5);
  background: linear-gradient(135deg, rgba(47, 107, 176, 0.3), rgba(18, 37, 62, 0.5));
}

.cover-tag {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 4px 12px;
  border-radius: var(--radius-full);
  background: rgba(18, 37, 62, 0.75);
  border: 1px solid rgba(47, 107, 255, 0.5);
  color: var(--brand-600);
  font-size: 12px;
  letter-spacing: 1px;
}

.show-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.show-title {
  margin: 0 0 18px;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 1px;
  line-height: 1.35;
}

.show-content {
  margin: 0 0 26px;
  font-size: 16px;
  line-height: 1.9;
  color: rgba(255, 255, 255, 0.82);
  display: -webkit-box;
  -webkit-line-clamp: 6;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.show-owner {
  display: flex;
  align-items: center;
  gap: 22px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.75);
}

.show-owner span {
  display: flex;
  align-items: center;
  gap: 6px;
}

.show-owner .grad {
  padding: 2px 12px;
  border-radius: var(--radius-full);
  background: rgba(47, 107, 255, 0.16);
  color: var(--brand-600);
  border: 1px solid rgba(47, 107, 255, 0.4);
}

.arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 52px;
  height: 52px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.25);
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  font-size: 26px;
  cursor: pointer;
  transition: all 0.25s;
}

.arrow:hover {
  background: rgba(47, 107, 255, 0.25);
  border-color: var(--brand-600);
}

.arrow-left {
  left: 24px;
}

.arrow-right {
  right: 24px;
}

.screen-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: rgba(255, 255, 255, 0.5);
  letter-spacing: 2px;
}

/* 底部缩略 */
.screen-foot {
  display: flex;
  gap: 12px;
  padding: 14px 28px 20px;
  overflow-x: auto;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.thumb {
  position: relative;
  flex-shrink: 0;
  width: 130px;
  height: 74px;
  border-radius: 0;
  overflow: hidden;
  cursor: pointer;
  border: 2px solid transparent;
  opacity: 0.55;
  transition: all 0.25s;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-fallback {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, rgba(47, 107, 176, 0.5), rgba(18, 37, 62, 0.7));
}

.thumb-title {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 3px 6px;
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  background: rgba(10, 25, 47, 0.75);
}

.thumb.active {
  border-color: var(--brand-600);
  opacity: 1;
  box-shadow: 0 0 18px rgba(47, 107, 255, 0.4);
}

.thumb:hover {
  opacity: 0.9;
}

/* 冗余退出 FAB（始终可见，按 ESC 或点击均可） */
.exit-fab {
  position: fixed;
  right: 28px;
  bottom: 110px;
  z-index: 99;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.4);
  background: rgba(10, 25, 47, 0.55);
  backdrop-filter: blur(10px);
  color: #fff;
  font-size: 14px;
  letter-spacing: 2px;
  cursor: pointer;
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.45);
  transition: transform 0.2s, background 0.2s, border-color 0.2s;
}

.exit-fab:hover {
  transform: translateY(-2px);
  background: linear-gradient(135deg, rgba(47, 107, 255, 0.9), rgba(64, 158, 255, 0.9));
  border-color: rgba(47, 107, 255, 0.9);
}
</style>
