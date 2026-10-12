<script setup>
/**
 * 后台登录页 · 与前台门户 /login 同款视觉（左 70% 校园实景 + 右白色登录卡）
 * - 独立入口：后台登录走 /auth/login（账号 + 密码，SHA-256 散列，API 规范 1.7）
 * - 与前台仅共用设计，账号体系 / 令牌存储完全独立
 */
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { sha256Hex } from '@/utils/crypto'
import { setTokens, clearTokens } from '@/utils/token'
import { useUserStore } from '@/stores/user'

/** 后台允许登录的角色
 *  - 三级审核角色：counselor / college_admin / school_admin / system_admin
 *  - 学生 / 校友 PRD V2.3「PC 端面向所有角色」：允许进入后台校友侧页面
 *    （提交资料 / 我的资料 / 我的荣誉 / 通知公告），与 router meta ALUMNI_ROLES 一致
 */
const ADMIN_LOGIN_ROLES = ['counselor', 'college_admin', 'school_admin', 'system_admin', 'alumni', 'student']

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const remember = ref(true)
const form = reactive({
  account: '',
  password: '',
})

const REMEMBER_KEY = 'alumni_admin_remember'

/** 测试账号快捷登录（本地演示环境）：点击即填充并直接登录
 *  账号与 DataInitializer 中 seedRoleUser / seedSystemAdmin 一一对应：
 *    - 系统管理员：admin / admin
 *    - 校级 / 院系 / 导员 / 学生 / 校友：phone + 123456（后端 BCrypt 散列）
 */
const demoAccounts = [
  { label: '系统管理员', account: 'admin', password: 'admin' },
  { label: '校级管理员', account: '13900000005', password: '123456' },
  { label: '院系管理员', account: '13900000004', password: '123456' },
  { label: '导员', account: '13900000003', password: '123456' },
  { label: '学生', account: '13900000001', password: '123456' },
  { label: '校友', account: '13900000002', password: '123456' },
]

/** 左侧品牌区特性条目（与前台一致） */
const features = [
  { title: '校友档案', desc: '一人一档，随时维护个人资料' },
  { title: '荣誉墙', desc: '记录每一份成长与高光时刻' },
  { title: '校友动态', desc: '校园资讯与活动永不断线' },
]

function fillDemo(acc) {
  if (loading.value) return
  form.account = acc.account
  form.password = acc.password
  handleLogin()
}

/**
 * 跳到门户首页（与门户登录页互通的入口）。
 * - nginx（80 端口）下：直接 / 
 * - admin dev (5176) 直连：portal dev server 在 5175
 * - portal dev (5175) 直连：admin dev server 在 5176
 */
function goPortal() {
  const port = window.location.port
  if (port === '5176') {
    // admin dev 直连，跳到 portal dev
    window.location.href = `${window.location.protocol}//${window.location.hostname}:5175/`
  } else {
    // nginx 80 入口或同源场景
    window.location.href = '/'
  }
}

function onMountedInit() {
  const saved = localStorage.getItem(REMEMBER_KEY)
  if (saved) form.account = saved
}
onMountedInit()

async function handleLogin() {
  if (!form.account.trim()) {
    ElMessage.warning('请输入用户名或手机号')
    return
  }
  if (!form.password) {
    ElMessage.warning('请输入密码')
    return
  }
  loading.value = true
  try {
    const hashed = await sha256Hex(form.password)
    const res = await login({ account: form.account.trim(), password: hashed })

    // 后台角色门槛：仅导员/校级/院系/系统管理员/学生/校友可进入（其余角色清除令牌并提示）
    if (!ADMIN_LOGIN_ROLES.includes(res.data.user?.role)) {
      clearTokens()
      userStore.user = null
      ElMessage.error('该账号无后台访问权限，请联系管理员开通账号')
      return
    }

    if (remember.value) localStorage.setItem(REMEMBER_KEY, form.account.trim())
    else localStorage.removeItem(REMEMBER_KEY)

    setTokens(res.data)
    userStore.setLoginUser(res.data.user)
    // 登录后异步加载动态菜单（校级/系统管理员按学院展开荣誉管理）
    userStore.loadMenus()
    ElMessage.success('登录成功')
    // 初始密码强制改密（PRD M01-03）：优先跳改密页
    if (res.data.user?.must_change_password) {
      router.replace('/change-password')
      return
    }
    // 仅接受站内路径，防止开放重定向；默认进入角色首页
    // 同时兼容 ?redirect= 和 ?from=（门户链接风格）
    const raw = route.query.redirect || route.query.from
    const redirect =
      typeof raw === 'string' && raw.startsWith('/')
        ? raw
        : userStore.defaultHome
    router.replace(redirect)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- ============ 左侧：校园实景品牌区（占 70%，1024px 以上显示） ============ -->
    <aside class="hero">
      <img class="hero-img" src="/bgcsvg/front.jpg" alt="福州软件职业技术学院" />
      <div class="hero-shade" aria-hidden="true"></div>
      <div class="hero-glow" aria-hidden="true"></div>

      <div class="hero-inner">
        <!-- 品牌区：校徽 + 校名 -->
        <div class="brand">
          <span class="brand-badge">
            <img src="/bgcsvg/school-badge.png" alt="校徽" />
          </span>
          <div>
            <p class="brand-nm">福州软件职业技术学院</p>
            <p class="brand-en">FUZHOU SOFTWARE TECHNOLOGY VOCATIONAL COLLEGE</p>
          </div>
        </div>

        <!-- 主文案 + 特性 -->
        <div class="hero-main">
          <h2 class="hero-title">欢迎回<span class="hl">城堡</span></h2>
          <p class="hero-sub">无论走多远，城堡永远为你亮灯。登录后可维护个人资料、提交档案与荣誉。</p>

          <div class="feat-list">
            <div v-for="f in features" :key="f.title" class="feat">
              <span class="feat-ico">✓</span>
              <p class="feat-txt">
                <b>{{ f.title }}</b>
                <span>{{ f.desc }}</span>
              </p>
            </div>
          </div>
        </div>

        <!-- 底部签名 -->
        <p class="sign">FUZHOU SOFTWARE ALUMNI · 数字城堡</p>
      </div>
    </aside>

    <!-- ============ 右侧：登录表单区 ============ -->
    <main class="form-side">
      <div class="form-glow" aria-hidden="true"></div>

      <div class="card">
        <!-- 移动端顶部横幅（1024px 以下显示） -->
        <div class="m-banner">
          <img src="/bgcsvg/front.jpg" alt="福州软件职业技术学院" />
          <div class="m-banner-shade" aria-hidden="true"></div>
          <div class="m-banner-brand">
            <span class="m-badge"><img src="/bgcsvg/school-badge.png" alt="校徽" /></span>
            <div>
              <p class="m-nm">福州软件职业技术学院</p>
              <p class="m-en">FUZHOU SOFTWARE TECHNOLOGY VOCATIONAL COLLEGE</p>
            </div>
          </div>
        </div>

        <!-- 桌面端头部：校徽 + 欢迎语 -->
        <div class="head">
          <span class="head-badge"><img src="/bgcsvg/school-badge.png" alt="校徽" /></span>
          <div>
            <p class="head-nm">福州软件职业技术学院</p>
            <p class="head-en">ADMIN PORTAL</p>
          </div>
        </div>

        <h1 class="title">欢迎回城堡</h1>
        <p class="sub">管理后台登录 · 账号由校级管理员统一开通</p>

        <!-- 表单 -->
        <form class="form" @submit.prevent="handleLogin">
          <div class="f-item">
            <label class="f-label">账号</label>
            <input
              v-model="form.account"
              class="f-input"
              type="text"
              placeholder="请输入用户名 / 手机号"
              autocomplete="username"
            />
          </div>

          <div class="f-item">
            <label class="f-label">密码</label>
            <input
              v-model="form.password"
              class="f-input"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
              @keyup.enter="handleLogin"
            />
          </div>

          <div class="f-row">
            <label class="remember"><input v-model="remember" type="checkbox" />记住我</label>
            <span class="f-tip">校内专网部署 · 建议 Chrome / Edge</span>
          </div>

          <button type="submit" class="btn-login" :disabled="loading">
            {{ loading ? '登录中…' : '登 录' }}
          </button>
        </form>

        <!-- 测试账号快捷登录（演示环境） -->
        <div class="demo-box">
          <div class="demo-title">测试账号快捷登录（点击直接登录）</div>
          <div class="demo-chips">
            <button
              v-for="acc in demoAccounts"
              :key="acc.account"
              type="button"
              class="demo-chip"
              :title="`${acc.account} / ${acc.password}`"
              @click="fillDemo(acc)"
            >
              {{ acc.label }}
            </button>
          </div>
        </div>
      </div>

      <!-- 右上角标识 + 互跳入口（与门户登录页互通） -->
      <div class="corner-tags">
        <span class="corner-tag">校友管理平台 · 管理后台</span>
        <button
          type="button"
          class="corner-tag corner-link"
          title="回到门户首页（同源 /）"
          @click="goPortal"
        >
          ← 门户首页
        </button>
      </div>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  background: #1a103d;
  font-family: 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
}

/* ================= 左侧品牌区（70%） ================= */
.hero {
  position: relative;
  display: none;
  width: 70%;
  overflow: hidden;
}

@media (min-width: 1024px) {
  .hero {
    display: block;
  }
}

.hero-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  animation: heroZoom 1.6s cubic-bezier(0.22, 0.61, 0.36, 1) both;
}

@keyframes heroZoom {
  from {
    transform: scale(1.08);
  }
  to {
    transform: scale(1);
  }
}

.hero-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(26, 16, 61, 0.96) 0%, rgba(26, 16, 61, 0.42) 42%, rgba(26, 16, 61, 0.18) 100%);
}

.hero-glow {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(640px 360px at 14% -6%, rgba(37, 99, 235, 0.3), transparent 65%),
    radial-gradient(480px 320px at 90% 8%, rgba(245, 179, 1, 0.12), transparent 60%);
}

.hero-inner {
  position: relative;
  z-index: 10;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 100%;
  padding: 40px;
  color: #fff9f0;
}

@media (min-width: 1280px) {
  .hero-inner {
    padding: 40px 56px;
  }
}

.brand {
  display: flex;
  align-items: center;
  gap: 16px;
  animation: fadeDown 0.6s 0.15s both;
}

.brand-badge {
  display: flex;
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.35), 0 0 0 1px rgba(255, 255, 255, 0.6);
}

.brand-badge img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.brand-nm {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.05em;
  color: #fff9f0;
}

.brand-en {
  margin: 4px 0 0;
  font-size: 9px;
  letter-spacing: 0.22em;
  color: rgba(255, 249, 240, 0.6);
}

.hero-main {
  animation: fadeUp 0.55s 0.35s both;
}

.hero-title {
  margin: 0;
  font-size: 36px;
  font-weight: 900;
  letter-spacing: 0.06em;
  line-height: 1.35;
  color: #fff9f0;
}

.hero-title .hl {
  color: #f5b301;
}

.hero-sub {
  max-width: 384px;
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: rgba(255, 249, 240, 0.7);
}

.feat-list {
  margin-top: 32px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.feat {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.feat-ico {
  display: flex;
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  margin-top: 2px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(245, 179, 1, 0.2);
  color: #f5b301;
  font-size: 10px;
  font-weight: 700;
}

.feat-txt {
  margin: 0;
  font-size: 14px;
  color: rgba(255, 249, 240, 0.85);
}

.feat-txt b {
  color: #fff9f0;
  font-weight: 600;
}

.feat-txt span {
  margin-left: 8px;
  color: rgba(255, 249, 240, 0.55);
}

.sign {
  margin: 0;
  font-size: 10px;
  letter-spacing: 0.32em;
  color: rgba(255, 249, 240, 0.4);
}

/* ================= 右侧表单区 ================= */
.form-side {
  position: relative;
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 40px 16px;
}

@media (min-width: 640px) {
  .form-side {
    padding: 40px 32px;
  }
}

.form-glow {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(560px 380px at 82% -8%, rgba(37, 99, 235, 0.3), transparent 65%),
    radial-gradient(520px 360px at 12% 106%, rgba(245, 179, 1, 0.1), transparent 60%);
}

.card {
  position: relative;
  z-index: 10;
  width: 100%;
  max-width: 448px;
  padding: 28px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 24px 60px rgba(10, 6, 30, 0.5);
  animation: fadeUp 0.45s both;
}

@media (min-width: 640px) {
  .card {
    padding: 36px;
  }
}

/* 移动端横幅 */
.m-banner {
  position: relative;
  display: block;
  height: 144px;
  margin-bottom: 28px;
  overflow: hidden;
  border-radius: 12px;
}

@media (min-width: 1024px) {
  .m-banner {
    display: none;
  }
}

.m-banner img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.m-banner-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(26, 16, 61, 0.92) 0%, rgba(26, 16, 61, 0.3) 100%);
}

.m-banner-brand {
  position: absolute;
  bottom: 12px;
  left: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.m-badge {
  display: flex;
  width: 44px;
  height: 44px;
  align-items: center;
  justify-content: center;
  padding: 2px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.6);
}

.m-badge img {
  position: static;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.m-nm {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #fff9f0;
}

.m-en {
  margin: 2px 0 0;
  font-size: 8px;
  letter-spacing: 0.2em;
  color: rgba(255, 249, 240, 0.6);
}

/* 桌面端头部 */
.head {
  display: none;
  align-items: center;
  gap: 12px;
}

@media (min-width: 1024px) {
  .head {
    display: flex;
  }
}

.head-badge {
  display: flex;
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  padding: 2px;
  border-radius: 12px;
  background: #fff9f0;
  box-shadow: 0 1px 3px rgba(31, 41, 55, 0.12), 0 0 0 1px rgba(241, 245, 249, 0.9);
}

.head-badge img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.head-nm {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
}

.head-en {
  margin: 2px 0 0;
  font-size: 9px;
  letter-spacing: 0.3em;
  color: #2563eb;
}

.title {
  margin: 20px 0 0;
  font-size: 30px;
  font-weight: 900;
  letter-spacing: 0.06em;
  color: #1f2937;
}

.sub {
  margin: 8px 0 0;
  font-size: 14px;
  color: #64748b;
}

/* 表单 */
.form {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.f-label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #1f2937;
}

.f-input {
  width: 100%;
  height: 44px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  outline: none;
  font-size: 14px;
  color: #1f2937;
  background: #fff;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.f-input::placeholder {
  color: #94a3b8;
}

.f-input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

.f-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12.5px;
}

.remember {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #4a5870;
  cursor: pointer;
}

.remember input {
  accent-color: #2563eb;
}

.f-tip {
  color: #94a3b8;
}

.btn-login {
  width: 100%;
  height: 46px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.2em;
  text-indent: 0.2em;
  color: #fff;
  background: #2563eb;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.35);
  transition: background 0.2s, transform 0.2s, box-shadow 0.2s;
}

.btn-login:hover:not(:disabled) {
  background: #1d4ed8;
  box-shadow: 0 12px 26px rgba(37, 99, 235, 0.45);
}

.btn-login:active:not(:disabled) {
  transform: scale(0.99);
}

.btn-login:disabled {
  opacity: 0.7;
  cursor: wait;
}

/* 测试账号（白卡适配版） */
.demo-box {
  margin-top: 22px;
  padding-top: 16px;
  border-top: 1px dashed #e2e8f0;
}

.demo-title {
  margin-bottom: 10px;
  text-align: center;
  font-size: 11px;
  letter-spacing: 0.1em;
  color: #94a3b8;
}

.demo-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.demo-chip {
  padding: 5px 12px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #f8fafc;
  color: #4a5870;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.demo-chip:hover {
  border-color: #2563eb;
  background: #eef3ff;
  color: #2563eb;
}

.demo-chip:active {
  transform: scale(0.95);
}

/* 右上角标识 + 互跳按钮组 */
.corner-tags {
  position: absolute;
  top: 24px;
  right: 24px;
  z-index: 10;
  display: flex;
  gap: 8px;
}
.corner-tag {
  padding: 6px 14px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 249, 240, 0.7);
  font-size: 12px;
  backdrop-filter: blur(4px);
}
.corner-link {
  cursor: pointer;
  transition: all 0.2s;
}
.corner-link:hover {
  border-color: rgba(255, 255, 255, 0.5);
  color: rgba(255, 249, 240, 0.95);
  background: rgba(255, 255, 255, 0.12);
}
.corner-link:active {
  transform: scale(0.97);
}

/* 入场动画 */
@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fadeDown {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
