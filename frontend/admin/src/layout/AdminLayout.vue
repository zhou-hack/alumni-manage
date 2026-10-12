<script setup>
/**
 * 管理后台布局（对齐 ui-design/review-workbench.html 设计范式）
 * - 侧边栏：Clean Light Expanded · 浮动白色圆角卡片（展开 268px / 收起 76px）
 * - 顶栏：浮动岛屿（胶囊形，毛玻璃，面包屑 + 顶栏搜索 + 用户菜单）
 * - 功能不变：角色菜单过滤（Pinia）、登出、强制改密跳转由路由守卫负责
 */
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Bell, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getUnreadCount } from '@/api/notifications'
import { suggest } from '@/api/search'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
const menus = computed(() => userStore.menus)
const collapsed = ref(false)

// 顶栏铃铛 + 未读数（30s 轮询）
const unreadCount = ref(0)
async function refreshUnread() {
  try {
    const res = await getUnreadCount()
    unreadCount.value = res.data?.total || 0
  } catch {
    // 静默
  }
}
let unreadTimer = null
onMounted(() => {
  refreshUnread()
  unreadTimer = setInterval(refreshUnread, 30000)
})
onBeforeUnmount(() => {
  if (unreadTimer) clearInterval(unreadTimer)
})
function goNotifications() {
  router.push('/notifications').then(() => refreshUnread())
}

/* ============ 顶栏全局搜索（关键词联想 + 跳转 /search） ============ */
const SUGGEST_TYPE_LABEL = {
  honor: '荣誉',
  archive: '档案',
  alumni: '校友',
  college: '学院',
  tag: '标签',
}
function suggestTypeOf(t) {
  return SUGGEST_TYPE_LABEL[t] || t || '其他'
}

const searchKw = ref('')
const suggestions = ref([])
const suggestOpen = ref(false)
const suggestLoading = ref(false)
let suggestTimer = null
let suggestSeq = 0

function onSearchInput(v) {
  searchKw.value = v
  if (suggestTimer) clearTimeout(suggestTimer)
  const kw = (v || '').trim()
  if (kw.length < 1) {
    suggestions.value = []
    suggestOpen.value = false
    return
  }
  suggestTimer = setTimeout(() => doSuggest(kw), 220)
}

async function doSuggest(kw) {
  const seq = ++suggestSeq
  suggestLoading.value = true
  try {
    const res = await suggest(kw, 10)
    if (seq !== suggestSeq) return // 过期响应丢弃
    suggestions.value = res.data?.list || []
    suggestOpen.value = suggestions.value.length > 0
  } catch {
    if (seq !== suggestSeq) return
    suggestions.value = []
    suggestOpen.value = false
  } finally {
    if (seq === suggestSeq) suggestLoading.value = false
  }
}

function pickSuggestion(item) {
  // 整个联想词作为 keyword 跳到 /search
  router.push({ path: '/search', query: { keyword: item.text } })
  suggestOpen.value = false
  searchKw.value = ''
  suggestions.value = []
}

function submitSearch() {
  const kw = searchKw.value.trim()
  if (!kw) return
  router.push({ path: '/search', query: { keyword: kw } })
  suggestOpen.value = false
  suggestions.value = []
}

function onSearchBlur() {
  // 延迟关闭，让点击联想词能触发
  setTimeout(() => {
    suggestOpen.value = false
  }, 180)
}
function onSearchFocus() {
  if (suggestions.value.length > 0) suggestOpen.value = true
}

/** 全局点击外部关闭联想下拉（仅在已展开时监听） */
function onDocClick(e) {
  const root = document.querySelector('.topbar-search')
  if (root && !root.contains(e.target)) {
    suggestOpen.value = false
  }
}
onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))

/**
 * 解析菜单 path 字符串（如 "/honors?college_id=123"），返回 router-link 接受的 location 对象。
 * 同时提供一个 pathOf() 用来比较 active 高亮（只取路径部分）。
 */
function parseMenuPath(p) {
  if (!p || typeof p !== 'string') return p
  const qi = p.indexOf('?')
  if (qi === -1) return p
  const path = p.slice(0, qi)
  const queryStr = p.slice(qi + 1)
  const query = {}
  for (const part of queryStr.split('&')) {
    if (!part) continue
    const eq = part.indexOf('=')
    const k = eq === -1 ? part : part.slice(0, eq)
    const v = eq === -1 ? '' : part.slice(eq + 1)
    query[decodeURIComponent(k)] = decodeURIComponent(v)
  }
  return { path, query }
}
function pathOf(p) {
  if (!p || typeof p !== 'string') return p
  const qi = p.indexOf('?')
  return qi === -1 ? p : p.slice(0, qi)
}

/** 门户主页地址（按访问来源动态选择）
 *  - nginx 80 / 同源：返回 '/'，整页跳到 portal 主页（localStorage 同源共享 token）
 *  - admin dev (5176) 直连：跳到 portal dev (5175)，避免 nginx 80 端口不可用时 502
 */
const portalHomeUrl = computed(() => {
  const { protocol, hostname, port } = window.location
  if (port === '5176') {
    // admin dev 直连 → portal dev
    return `${protocol}//${hostname}:5175/`
  }
  return '/'
})

/** 二级菜单组展开状态（未手动点过时：当前路由落在组内则自动展开） */
const manualOpen = ref({})
function groupOpen(m) {
  if (m.path in manualOpen.value) return manualOpen.value[m.path]
  return (m.children || []).some((c) => pathOf(c.path) === route.path)
}
function toggleGroup(m) {
  manualOpen.value = { ...manualOpen.value, [m.path]: !groupOpen(m) }
}
function isGroupActive(m) {
  return (m.children || []).some((c) => pathOf(c.path) === route.path)
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  await userStore.logout()
  ElMessage.success('已退出登录')
  router.replace('/login')
}
</script>

<template>
  <div class="app" :class="{ 'is-collapsed': collapsed }">
    <!-- ===== 侧边栏 · 浮动白色卡片 ===== -->
    <aside class="sidebar">
      <div class="side-card">
        <div class="side-head">
          <div class="logo" aria-hidden="true">校</div>
          <div class="brand-txt">
            <div class="nm">校友管理平台</div>
            <div class="rl">ALUMNI · ADMIN</div>
          </div>
          <button
            class="icon-mini collapse-btn"
            :title="collapsed ? '展开侧边栏' : '收起侧边栏'"
            @click="collapsed = !collapsed"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="4.5" width="18" height="15" rx="2.5" />
              <path d="M9.2 4.5v15" />
            </svg>
          </button>
        </div>

        <nav class="nav" aria-label="主导航">
          <template v-for="m in menus" :key="m.path">
            <!-- 二级菜单组：点击展开/收起，子项可点击跳转 -->
            <div v-if="m.children && m.children.length" class="nav-subwrap">
              <button
                type="button"
                class="nav-item nav-group-btn"
                :class="{ active: isGroupActive(m) }"
                :title="collapsed ? m.title : ''"
                @click="toggleGroup(m)"
              >
                <span class="ic"><el-icon :size="19"><component :is="m.icon" /></el-icon></span>
                <span class="lbl">{{ m.title }}</span>
                <span class="arrow" :class="{ open: groupOpen(m) }">‹</span>
              </button>
              <div v-show="groupOpen(m)" class="nav-sub">
                <router-link
                  v-for="c in m.children"
                  :key="c.path"
                  :to="parseMenuPath(c.path)"
                  class="nav-item nav-sub-item"
                  :class="{ active: activeMenu === pathOf(c.path) }"
                  :title="collapsed ? c.title : ''"
                >
                  <span class="sub-dot" aria-hidden="true"></span>
                  <span class="lbl">{{ c.title }}</span>
                </router-link>
              </div>
            </div>
            <!-- 一级菜单项 -->
            <router-link
              v-else
              :to="parseMenuPath(m.path)"
              class="nav-item"
              :class="{ active: activeMenu === pathOf(m.path) }"
              :title="collapsed ? m.title : ''"
            >
              <span class="ic"><el-icon :size="19"><component :is="m.icon" /></el-icon></span>
              <span class="lbl">{{ m.title }}</span>
            </router-link>
          </template>
        </nav>

        <div class="side-sep"></div>
      </div>
    </aside>

    <!-- ===== 主区：通栏顶栏 + 内容 ===== -->
    <div class="main">
      <header class="island">
        <div class="crumbs">管理后台 / <b>{{ route.meta.title || '首页' }}</b></div>
        <div class="isl-spacer"></div>
        <!-- 顶栏全局搜索：联想 + 跳转 /search -->
        <div class="topbar-search">
          <el-input
            v-model="searchKw"
            placeholder="搜索荣誉 / 档案 / 校友"
            clearable
            class="search-input"
            :prefix-icon="Search"
            @input="onSearchInput"
            @keyup.enter="submitSearch"
            @focus="onSearchFocus"
            @blur="onSearchBlur"
          />
          <div v-if="suggestOpen" class="suggest-pop">
            <div v-if="suggestLoading" class="suggest-empty">联想中…</div>
            <div
              v-for="s in suggestions"
              :key="s.type + ':' + s.text"
              class="suggest-item"
              @mousedown.prevent="pickSuggestion(s)"
            >
              <el-tag size="small" effect="plain" class="suggest-type">{{ suggestTypeOf(s.type) }}</el-tag>
              <span class="suggest-text">{{ s.text }}</span>
              <span v-if="s.count" class="suggest-count">{{ s.count }}</span>
            </div>
          </div>
        </div>

        <a :href="portalHomeUrl" class="home-btn" title="返回门户主页（nginx 80 入口）">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 10.5 12 3l9 7.5" />
            <path d="M5.5 9.5V21h13V9.5" />
          </svg>
          <span class="home-btn-txt">回到主页</span>
        </a>
        <!-- 站内消息铃铛 -->
        <el-badge :value="unreadCount" :max="99" :hidden="unreadCount === 0" class="bell-badge">
          <button class="icon-mini bell-btn" title="消息中心" @click="goNotifications">
            <el-icon :size="20"><Bell /></el-icon>
          </button>
        </el-badge>
        <!-- 管理员身份卡（右上角）：头像 + 昵称/角色，点击展开退出登录 -->
        <el-dropdown trigger="click" @command="handleLogout">
          <div class="id-chip">
            <span class="id-av">{{ (userStore.user?.nickname || '管').slice(0, 1) }}</span>
            <span class="id-txt">
              <span class="id-nm">{{ userStore.user?.nickname || '管理员' }}</span>
              <span class="id-rl">{{ userStore.roleText }}</span>
            </span>
            <svg class="id-caret" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="m6 9 6 6 6-6" />
            </svg>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <div class="page">
        <router-view />
      </div>
    </div>
  </div>
</template>

<style scoped>
.app {
  display: grid;
  grid-template-columns: 256px minmax(0, 1fr);
  min-height: 100vh;
  background: var(--bg);
  transition: grid-template-columns 0.26s cubic-bezier(0.22, 1, 0.36, 1);
}

.app.is-collapsed {
  grid-template-columns: 76px minmax(0, 1fr);
}

/* ===== 侧边栏浮动卡片（贴住左边缘，不留白边） ===== */
.sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  padding: 0;
  display: flex;
  z-index: 50;
}

/* 收起态：向右扩出一小段透明热区，鼠标更容易触发展开（视觉上不可见） */
.app.is-collapsed .sidebar::after {
  content: '';
  position: absolute;
  top: 0;
  left: 100%;
  width: 8px;
  height: 100%;
}

.side-card {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: linear-gradient(165deg, var(--side), var(--side-2));
  border: 1px solid var(--side-line);
  border-radius: 0;
  box-shadow: var(--shadow-side);
  padding: 16px 12px;
  overflow: hidden;
}

.side-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 4px 16px;
}

.logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  flex: none;
  display: grid;
  place-items: center;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 16px;
  color: #fff;
  background: linear-gradient(140deg, var(--brand-500), var(--brand-700));
  box-shadow: 0 2px 6px rgba(31, 73, 125, 0.32);
}

.brand-txt {
  min-width: 0;
  flex: 1;
}

.brand-txt .nm {
  font-family: var(--font-display);
  font-size: 15.5px;
  font-weight: 700;
  letter-spacing: 0.3px;
  line-height: 1.25;
  white-space: nowrap;
  color: var(--side-ink);
}

.brand-txt .rl {
  font-size: 10.5px;
  letter-spacing: 1.4px;
  font-weight: 700;
  color: var(--side-muted);
  margin-top: 2px;
  white-space: nowrap;
  opacity: 0.8;
}

/* ===== 导航 ===== */
.nav {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav::-webkit-scrollbar {
  width: 4px;
}
.nav::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.08);
  border-radius: 4px;
}
.nav::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.16);
}

.nav-group {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.4px;
  color: var(--side-muted);
  /* 与一级项文字左边缘对齐（12px 内边距 + 19px 图标 + 12px 间距 ≈ 43px） */
  padding: 12px 12px 6px 43px;
  opacity: 0.75;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  height: 42px;
  padding: 0 14px;
  border-radius: 8px;
  color: var(--side-muted);
  font-size: 15.5px;
  font-weight: 500;
  text-decoration: none;
  transition: background 0.15s ease, color 0.15s ease;
  position: relative;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: var(--side-ink);
}

.nav-item.active {
  background: linear-gradient(135deg, var(--brand-600), var(--brand-700));
  color: #fff;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(31, 73, 125, 0.34);
}
.nav-item.active .arrow,
.nav-item.active .ic {
  color: #fff;
}
.nav-item.active .lbl {
  letter-spacing: 0.2px;
}

/* ===== 二级菜单组 ===== */
.nav-group-btn {
  cursor: pointer;
  background: none;
  border: none;
  font-family: inherit;
  text-align: left;
}

.nav-group-btn .arrow {
  margin-left: auto;
  font-size: 12px;
  font-weight: 700;
  color: var(--side-muted);
  opacity: 0.6;
  transition: transform 0.18s ease;
}

.nav-group-btn .arrow.open {
  transform: rotate(90deg);
}

.nav-subwrap {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin: 1px 0 4px;
}

.nav-sub {
  display: flex;
  flex-direction: column;
  gap: 1px;
  /* 缩进对齐：子项文字左边缘与一级项文字左边缘同列
     （12px 卡片内边距 + 19px 图标列 + 12px 间距 = 43px），仅一层缩进 */
  padding-left: 43px;
  margin: 2px 0 4px;
  position: relative;
}

/* 层级引导线：落在上级图标列的视觉中轴上 */
.nav-sub::before {
  content: '';
  position: absolute;
  left: 21px;
  top: 2px;
  bottom: 4px;
  width: 1px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 1px;
}

.nav-sub-item {
  height: 36px;
  font-size: 14.5px;
  color: var(--side-ink);
  opacity: 0.92;
  position: relative;
  /* 子项左右内边距与一级项对齐 */
  padding: 0 14px 0 0;
}

.nav-sub-item:hover {
  opacity: 1;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.nav-sub-item.active {
  background: transparent;
  color: #ffffff;
  opacity: 1;
  font-weight: 600;
}
/* 激活指示条：从引导线位置起，与圆点形成连续视觉 */
.nav-sub-item.active::before {
  content: '';
  position: absolute;
  left: -21px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 16px;
  border-radius: 2px;
  background: var(--accent-300, #96badc);
}

.sub-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.5;
  flex-shrink: 0;
  margin-right: 10px;
  transition: opacity 0.15s, background 0.15s;
}
.nav-sub-item.active .sub-dot {
  opacity: 1;
  background: var(--accent-500);
}

.nav-item .ic {
  /* 与内部 el-icon :size="19" 同宽同高，避免图标溢出容器导致与文字基线错位 */
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  flex: none;
}

.nav-item .lbl {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ===== 分隔线 ===== */
.side-sep {
  height: 1px;
  background: var(--side-line);
  margin: 8px 4px;
  opacity: 0.6;
}

/* ===== 收起态 ===== */
.app.is-collapsed .brand-txt,
.app.is-collapsed .nav-group,
.app.is-collapsed .nav-item .lbl,
.app.is-collapsed .nav-group-btn .arrow,
.app.is-collapsed .nav-sub {
  display: none;
}

/* 展开态下文字淡入，避免从窄条恢复时生硬跳变
   （display:none 无法 transition，故用入场动画；仅在悬停展开时触发） */
.app:not(.is-collapsed) .brand-txt,
.app:not(.is-collapsed) .nav-item .lbl,
.app:not(.is-collapsed) .nav-group,
.app:not(.is-collapsed) .nav-sub {
  animation: side-fade-in 0.22s cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes side-fade-in {
  from {
    opacity: 0;
    transform: translateX(-4px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .app {
    transition: none;
  }
  .app:not(.is-collapsed) .brand-txt,
  .app:not(.is-collapsed) .nav-item .lbl,
  .app:not(.is-collapsed) .nav-group,
  .app:not(.is-collapsed) .nav-sub {
    animation: none;
  }
}

.app.is-collapsed .side-head {
  justify-content: center;
  padding: 2px 0 12px;
}

/* 收起侧栏时只保留折叠控制，避免校徽与按钮并排显示 */
.app.is-collapsed .logo {
  display: none;
}

.app.is-collapsed .side-card {
  padding: 14px 8px;
  align-items: center;
}

.app.is-collapsed .nav-item {
  justify-content: center;
  padding: 0;
  width: 44px;
  height: 44px;
  margin: 0 auto;
  border-radius: 10px;
}

.app.is-collapsed .nav-subwrap {
  margin: 1px auto 4px;
}

.app.is-collapsed .nav-sub-item {
  width: 36px;
  height: 36px;
  padding: 0;
  justify-content: center;
}
.app.is-collapsed .nav-sub-item .sub-dot {
  margin-right: 0;
}

/* ===== 主区 ===== */
.main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 0 22px 48px 18px;
}

/* 顶栏：贴住页面顶部与左右边缘，无边框 */
.island {
  position: sticky;
  top: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 58px;
  margin: 0 -22px 0 -18px;
  padding: 0 22px 0 18px;
  border: none;
  border-radius: 0;
  background: color-mix(in oklab, var(--surface-1) 92%, transparent);
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(14px);
}

.crumbs {
  font-size: 13px;
  color: var(--text-sub);
  white-space: nowrap;
}

.crumbs b {
  color: var(--text-main);
  font-weight: 600;
}

.isl-spacer {
  flex: 1;
}

/* 回到主页按钮（顶栏右侧）— 极简图标胶囊（与通知铃铛同尺寸） */
.home-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 42px;
  padding: 0 14px 0 10px;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: var(--surface-1);
  color: var(--text-sub);
  text-decoration: none;
  transition: background 0.16s ease, color 0.16s ease, border-color 0.16s ease;
  gap: 6px;
}

/* ===== 顶栏图标按钮（共用的 icon-mini 基类 + 铃铛/折叠变体） ===== */
.icon-mini {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  padding: 0;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: var(--surface-1);
  color: var(--text-sub);
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease, border-color 0.16s ease;
}
.icon-mini:hover {
  border-color: var(--brand-500);
  color: var(--brand-700);
  background: var(--brand-50);
}
.icon-mini svg {
  width: 20px;
  height: 20px;
}

/* 侧边栏内嵌的折叠按钮：去掉外圈边框，融入深色侧栏 */
.collapse-btn {
  border: none;
  background: rgba(255, 255, 255, 0.06);
  color: var(--side-muted);
  border-radius: 8px;
  flex: none;
}
.collapse-btn:hover {
  background: rgba(255, 255, 255, 0.14);
  color: var(--side-ink);
}

/* 顶栏铃铛按钮 */
.bell-btn {
  position: relative;
}
.bell-btn :deep(.el-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

/* Element Plus Badge 包裹：避免 badge 数字影响按钮点击区 */
.bell-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.bell-badge :deep(.el-badge__content) {
  top: 6px;
  right: 6px;
}

/* 顶栏搜索：胶囊形容器 + 联想下拉 */
.topbar-search {
  position: relative;
  width: 280px;
}
.topbar-search .search-input :deep(.el-input__wrapper) {
  border-radius: 999px;
  background: var(--surface-1);
  box-shadow: 0 0 0 1px var(--line);
  padding: 2px 12px;
  transition: box-shadow 0.16s ease;
}
.topbar-search .search-input :deep(.el-input__wrapper):hover,
.topbar-search .search-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--brand-500);
}
.suggest-pop {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: 10px;
  box-shadow: var(--shadow-md);
  padding: 4px 0;
  z-index: 60;
  max-height: 360px;
  overflow-y: auto;
}
.suggest-empty {
  padding: 14px;
  text-align: center;
  font-size: 12px;
  color: var(--text-sub);
}
.suggest-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.12s;
}
.suggest-item:hover {
  background: rgba(15, 23, 42, 0.04);
}
.suggest-type {
  flex: none;
}
.suggest-text {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.suggest-count {
  font-size: 11px;
  color: var(--text-sub);
  font-family: var(--font-mono, monospace);
}
/* 文字与图标基线对齐：与铃铛按钮垂直中心对齐，字号与顶栏一致 */
.home-btn-txt {
  display: inline-flex;
  align-items: center;
  line-height: 1;
  font-size: 13px;
  font-weight: 500;
  color: inherit;
}

.home-btn:hover {
  border-color: var(--brand-500);
  color: var(--brand-700);
  background: var(--brand-50);
}

.home-btn svg {
  width: 20px;
  height: 20px;
}

/* ===== 侧栏底部 · 共享行样式（与 nav-item 同款节奏） ===== */
.side-foot {
  padding: 6px 0 2px;
}

/* 共享基类：所有 foot-row / foot-btn / foot-link 共用的几何参数 */
.side-foot .foot-row,
.side-foot .foot-btn,
.side-foot .foot-link {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  height: 44px;
  padding: 0 14px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--side-muted);
  font-family: inherit;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
  text-align: left;
  white-space: nowrap;
  text-decoration: none;
}
.side-foot .foot-row:hover,
.side-foot .foot-btn:hover,
.side-foot .foot-link:hover {
  background: rgba(255, 255, 255, 0.08);
  color: var(--side-ink);
}
.side-foot .foot-row:active,
.side-foot .foot-btn:active,
.side-foot .foot-link:active {
  background: rgba(255, 255, 255, 0.14);
}
.side-foot .foot-row svg,
.side-foot .foot-btn svg,
.side-foot .foot-link svg {
  width: 20px;
  height: 20px;
  flex: none;
}
.side-foot .foot-row .lbl,
.side-foot .foot-btn .lbl,
.side-foot .foot-link .lbl {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 主题切换按钮：hover 时图标轻微旋转 12°（暗示"切换"动作） */
.side-foot .theme-btn svg {
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}
.side-foot .theme-btn:hover svg {
  transform: rotate(-12deg);
}

/* 退出登录按钮：hover 时辅以暗红提示风险语义 */
.side-foot .logout-btn:hover {
  background: rgba(220, 38, 38, 0.18);
  color: #f5b8b6;
}
.side-foot .logout-btn:active {
  background: rgba(220, 38, 38, 0.28);
}
.side-foot .logout-btn svg {
  transition: transform 0.25s cubic-bezier(0.22, 1, 0.36, 1);
}
.side-foot .logout-btn:hover svg {
  transform: translateX(2px);
}

/* 身份卡 + 行之间的细分隔线 */
.side-foot .foot-sep {
  height: 1px;
  margin: 8px 12px 6px;
  background: var(--side-line);
  opacity: 0.55;
}

/* 收起态（76px）：所有行居中显示图标、隐藏文字 */
.app.is-collapsed .side-foot .foot-row,
.app.is-collapsed .side-foot .foot-btn,
.app.is-collapsed .side-foot .foot-link {
  justify-content: center;
  padding: 0;
  width: 44px;
  height: 44px;
  margin: 0 auto;
  border-radius: 10px;
}
.app.is-collapsed .side-foot .foot-row .lbl,
.app.is-collapsed .side-foot .foot-btn .lbl,
.app.is-collapsed .side-foot .foot-link .lbl {
  display: none;
}
.app.is-collapsed .side-foot .foot-sep {
  margin: 6px auto;
  width: 32px;
}

/* 管理员身份卡：胶囊形容器 + 渐变头像 + 昵称/角色双行（主题感知：亮/暗皆可见） */
.id-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 42px;
  padding: 0 14px 0 5px;
  border-radius: var(--radius-full);
  border: 1px solid var(--line);
  background: var(--surface-1);
  cursor: default;
  white-space: nowrap;
  /* 不参与顶栏挤压收缩：宁可让搜索框缩，也不把昵称/角色压成省略号 */
  flex: none;
}

/* 侧栏底部变体：去掉胶囊形边线，使用稍暗的对比底色，更融入深色侧栏 */
.side-foot .id-chip--sidebar {
  border: none;
  background: rgba(255, 255, 255, 0.06);
  height: 44px;
  padding: 0 12px 0 6px;
  border-radius: 10px;
  margin: 0 2px;
}
.side-foot .id-chip--sidebar:hover {
  background: rgba(255, 255, 255, 0.1);
}

.id-av {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  flex: none;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  background: linear-gradient(140deg, var(--brand-500), var(--brand-700));
  box-shadow: 0 0 0 2px color-mix(in oklab, var(--brand-500) 18%, transparent);
  overflow: hidden;
}
.id-av svg {
  width: 16px;
  height: 16px;
  color: #fff;
}

/* 下拉箭头：SVG 本体无 width/height 属性，必须显式约束尺寸，
   否则 flex 布局下会被解成 47×47（挤压昵称/角色成"校…"）或 0×0（箭头消失） */
.id-caret {
  width: 16px;
  height: 16px;
  flex: none;
  color: var(--text-sub);
}

.id-txt {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
  min-width: 0;
  overflow: hidden;
}

.id-nm {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.id-rl {
  font-size: 13px;
  color: var(--brand-700);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 侧栏底部身份卡的文本颜色：取侧栏 ink / muted（覆盖默认主题感知） */
.side-foot .id-chip--sidebar .id-nm {
  color: var(--side-ink);
}
.side-foot .id-chip--sidebar .id-rl {
  color: var(--accent-300, #96badc);
}

/* 收起态：身份卡仅显示居中头像，昵称 / 角色隐藏 */
.app.is-collapsed .side-foot .id-chip--sidebar {
  justify-content: center;
  padding: 0;
  width: 44px;
  height: 44px;
  margin: 0 auto;
  border-radius: 50%;
}
.app.is-collapsed .side-foot .id-chip--sidebar .id-txt {
  display: none;
}

.page {
  padding-top: 24px;
  flex: 1;
  min-width: 0;
}

/* ===== 中屏（≤ 1100px）：顶栏空间开始紧张 =====
   面包屑 + 搜索框 + 回到主页 + 铃铛 + 身份卡总宽约 1040px（含 256px 侧栏），
   再往下身份卡文字会被挤成省略号（"校…"）——按需求：窗口缩小后直接隐藏
   昵称/角色文字（含"校级管理员"），仅保留头像 + caret，下拉仍可点出退出登录 */
@media (max-width: 1100px) {
  .id-txt {
    display: none;
  }
  /* 退出登录命令式下拉触发：文字隐藏后仍保留 caret 可见、可点击 */
  .id-chip {
    padding: 0 8px;
  }
}

@media (max-width: 860px) {
  .app,
  .app.is-collapsed {
    grid-template-columns: 76px minmax(0, 1fr);
  }

  .main {
    padding: 0 14px 40px;
  }

  .island {
    margin: 0 -14px;
    padding: 0 14px;
  }

  .topbar-search {
    width: 180px;
  }
  .home-btn-txt {
    display: none;
  }
}
</style>
