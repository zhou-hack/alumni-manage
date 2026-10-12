<script setup lang="ts">
/**
 * 统一登录页（portal 与 admin 共用视觉与交互）
 * - 部署在 portal 工程的 /login 路由下，无需跨 origin 跳转
 * - 调用 portal 的 api.auth.login（SHA-256 散列 + 写入 tokenStore），与后端 /auth/login 对齐
 * - 角色分配：
 *   - counselor / college_admin / school_admin / system_admin：登录后整页跳到 /admin/，同步 admin localStorage
 *   - alumni / student：登录后留在 portal，跳转守卫 from 或 /
 *
 * 视觉与交互完全沿用 admin LoginPage（手写 scoped CSS，不依赖 Tailwind）：
 * - 左 70% 校园实景（front.jpg）+ 右白色登录卡
 * - 测试账号快捷登录（系统管理员 / 校级 / 院级 / 导员 / 学生 / 校友）
 * - 右上角：返回首页（portal 主页）+ 管理后台入口
 */
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { api, sha256Hex, tokenStore } from '../api';
import { authState, setUser, displayName } from '../auth';

/** 允许登录的角色（与 admin LoginPage 保持一致） */
const ALLOWED_ROLES = ['counselor', 'college_admin', 'school_admin', 'system_admin', 'alumni', 'student'];
/** 跳转到管理后台的角色（同 admin 端 ADMIN_ROLES） */
const ADMIN_ROLES = ['counselor', 'college_admin', 'school_admin', 'system_admin'];

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const remember = ref(true);
const form = reactive({
  account: '',
  password: ''
});

const REMEMBER_KEY = 'alumni_login_remember';

/**
 * 测试账号快捷登录（本地演示环境）：点击即填充并直接登录
 * 账号与后端 DataInitializer 一一对应：
 *   - 系统管理员：admin / admin
 *   - 校级 / 院系 / 导员 / 学生 / 校友：phone + 123456
 */
const demoAccounts = [
  { label: '系统管理员', account: 'admin', password: 'admin' },
  { label: '校级管理员', account: '13900000005', password: '123456' },
  { label: '院系管理员', account: '13900000004', password: '123456' },
  { label: '导员', account: '13900000003', password: '123456' },
  { label: '学生', account: '13900000001', password: '123456' },
  { label: '校友', account: '13900000002', password: '123456' }
];

/** 左侧品牌区特性条目 */
const features = [
  { title: '校友档案', desc: '一人一档，随时维护个人资料' },
  { title: '荣誉墙', desc: '记录每一份成长与高光时刻' },
  { title: '校友动态', desc: '校园资讯与活动永不断联' }
];

function fillDemo(acc: (typeof demoAccounts)[number]) {
  if (loading.value) return;
  form.account = acc.account;
  form.password = acc.password;
  handleLogin();
}

function onMountedInit() {
  try {
    const saved = localStorage.getItem(REMEMBER_KEY);
    if (saved) form.account = saved;
  } catch {
    /* 隐私模式忽略 */
  }
}
onMountedInit();

/**
 * 仅接受站内路径（防止开放重定向），否则回到默认
 */
function safeRedirect(raw: unknown, fallback: string): string {
  return typeof raw === 'string' && raw.startsWith('/') ? raw : fallback;
}

async function handleLogin() {
  if (!form.account.trim()) {
    ElMessage.warning('请输入用户名或手机号');
    return;
  }
  if (!form.password) {
    ElMessage.warning('请输入密码');
    return;
  }
  loading.value = true;
  try {
    const acc = form.account.trim();
    const hashed = await sha256Hex(form.password);
    const isPhone = /^1\d{10}$/.test(acc);
    const d = await api.auth.login(
      isPhone ? { phone: acc, password: hashed } : { username: acc, password: hashed }
    );

    if (!ALLOWED_ROLES.includes(d.user?.role)) {
      ElMessage.error('该账号无登录权限，请联系管理员开通账号');
      return;
    }

    if (remember.value) {
      try { localStorage.setItem(REMEMBER_KEY, acc); } catch {}
    } else {
      try { localStorage.removeItem(REMEMBER_KEY); } catch {}
    }

    // 写入 portal tokenStore + authState（用于 portal 自身的鉴权）
    tokenStore.set(d.access_token, d.refresh_token);
    setUser(d.user);

    ElMessage.success(`欢迎回来，${displayName(d.user) || d.user.nickname || '校友'}`);

    // 初始密码强制改密：跳到 change-password（统一入口；同源 admin 端与 portal 端一致）
    if ((d.user as any).must_change_password) {
      // 管理员 / 校友共用一个 change-password 页面：admin LoginPage 跳到 /change-password，
      // portal 这边没有对应页面，统一跳到 admin 端改密（同源时）
      const port = window.location.port;
      if (ADMIN_ROLES.includes(d.user.role) && (port === '5175' || !port)) {
        const target = port === '5175'
          ? `${window.location.protocol}//${window.location.hostname}:5176/admin/change-password`
          : '/admin/change-password';
        window.location.href = target;
        return;
      }
      // 学生 / 校友改密：暂未实现 portal 端页面，跳回登录首页提示
      ElMessage.warning('初始密码需修改，请联系管理员');
      router.replace('/');
      return;
    }

    // from 参数（admin 与 portal 都用 ?from= 携带登录后回跳）
    const from = safeRedirect(route.query.from, '');

    if (ADMIN_ROLES.includes(d.user.role)) {
      // 管理员 / 导员：跳到 admin（保留 from 透传）
      const port = window.location.port;
      let target: string;
      if (port === '5175') {
        target = `${window.location.protocol}//${window.location.hostname}:5176${from && from.startsWith('/admin') ? from : '/admin/'}`;
      } else {
        target = from && from.startsWith('/admin') ? from : '/admin/';
      }
      window.location.href = target;
      return;
    }

    // 学生 / 校友：留在 portal
    router.replace(from || '/');
  } catch (err: any) {
    ElMessage.error(err?.message || '登录失败，请稍后重试');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <!-- ============ 左侧：校园实景品牌区（占 70%） ============ -->
    <aside class="hero">
      <img class="hero-img" src="/bgcsvg/front.jpg" alt="福州软件职业技术学院" />
      <div class="hero-shade" aria-hidden="true"></div>
      <div class="hero-glow" aria-hidden="true"></div>

      <div class="hero-inner">
        <div class="brand">
          <span class="brand-badge">
            <img src="/bgcsvg/school-badge.png" alt="校徽" />
          </span>
          <div>
            <p class="brand-nm">福州软件职业技术学院</p>
            <p class="brand-en">FUZHOU SOFTWARE TECHNOLOGY VOCATIONAL COLLEGE</p>
          </div>
        </div>

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

        <p class="sign">FUZHOU SOFTWARE ALUMNI · 数字城堡</p>
      </div>
    </aside>

    <!-- ============ 右侧：登录表单区 ============ -->
    <main class="form-side">
      <div class="form-glow" aria-hidden="true"></div>

      <div class="card">
        <!-- 桌面端头部：校徽 + 欢迎语 -->
        <div class="head">
          <span class="head-badge"><img src="/bgcsvg/school-badge.png" alt="校徽" /></span>
          <div>
            <p class="head-nm">福州软件职业技术学院</p>
            <p class="head-en">UNIFIED LOGIN</p>
          </div>
        </div>

        <h1 class="title">欢迎回城堡</h1>
        <p class="sub">统一登录入口 · 一个账号通行门户与管理后台</p>

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

      <!-- 右上角：返回门户首页（按需保留单一互跳入口） -->
      <div class="corner-tags">
        <span class="corner-tag">校友管理平台 · 统一登录</span>
        <button
          type="button"
          class="corner-tag corner-link"
          title="回到门户首页"
          @click="router.push('/')"
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
  from { transform: scale(1.08); }
  to { transform: scale(1); }
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
  .hero-inner { padding: 40px 56px; }
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
.brand-badge img { width: 100%; height: 100%; object-fit: contain; }
.brand-nm { margin: 0; font-size: 20px; font-weight: 700; letter-spacing: 0.05em; color: #fff9f0; }
.brand-en { margin: 4px 0 0; font-size: 9px; letter-spacing: 0.22em; color: rgba(255, 249, 240, 0.6); }
.hero-main { animation: fadeUp 0.55s 0.35s both; }
.hero-title { margin: 0; font-size: 36px; font-weight: 900; letter-spacing: 0.06em; line-height: 1.35; color: #fff9f0; }
.hero-title .hl { color: #f5b301; }
.hero-sub { max-width: 384px; margin: 12px 0 0; font-size: 14px; line-height: 1.7; color: rgba(255, 249, 240, 0.7); }
.feat-list { margin-top: 32px; display: flex; flex-direction: column; gap: 14px; }
.feat { display: flex; align-items: flex-start; gap: 12px; }
.feat-ico {
  display: flex; width: 20px; height: 20px; flex-shrink: 0; margin-top: 2px;
  align-items: center; justify-content: center; border-radius: 50%;
  background: rgba(245, 179, 1, 0.2); color: #f5b301; font-size: 10px; font-weight: 700;
}
.feat-txt { margin: 0; font-size: 14px; color: rgba(255, 249, 240, 0.85); }
.feat-txt b { color: #fff9f0; font-weight: 600; }
.feat-txt span { margin-left: 8px; color: rgba(255, 249, 240, 0.55); }
.sign { margin: 0; font-size: 10px; letter-spacing: 0.32em; color: rgba(255, 249, 240, 0.4); }

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
@media (min-width: 640px) { .form-side { padding: 40px 32px; } }
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
@media (min-width: 640px) { .card { padding: 36px; } }
.head { display: none; align-items: center; gap: 12px; }
@media (min-width: 1024px) { .head { display: flex; } }
.head-badge {
  display: flex; width: 48px; height: 48px; flex-shrink: 0;
  align-items: center; justify-content: center; padding: 2px; border-radius: 12px;
  background: #fff9f0;
  box-shadow: 0 1px 3px rgba(31, 41, 55, 0.12), 0 0 0 1px rgba(241, 245, 249, 0.9);
}
.head-badge img { width: 100%; height: 100%; object-fit: contain; }
.head-nm { margin: 0; font-size: 16px; font-weight: 700; color: #1f2937; }
.head-en { margin: 2px 0 0; font-size: 9px; letter-spacing: 0.3em; color: #2563eb; }
.title { margin: 20px 0 0; font-size: 30px; font-weight: 900; letter-spacing: 0.06em; color: #1f2937; }
.sub { margin: 8px 0 0; font-size: 14px; color: #64748b; }

/* 表单 */
.form { margin-top: 24px; display: flex; flex-direction: column; gap: 16px; }
.f-label { display: block; margin-bottom: 6px; font-size: 14px; font-weight: 500; color: #1f2937; }
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
.f-input::placeholder { color: #94a3b8; }
.f-input:focus { border-color: #2563eb; box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15); }
.f-row { display: flex; align-items: center; justify-content: space-between; font-size: 12.5px; }
.remember { display: flex; align-items: center; gap: 6px; color: #4a5870; cursor: pointer; }
.remember input { accent-color: #2563eb; }
.f-tip { color: #94a3b8; }
.btn-login {
  width: 100%; height: 46px; border: none; border-radius: 8px; cursor: pointer;
  font-size: 15px; font-weight: 600; letter-spacing: 0.2em; text-indent: 0.2em;
  color: #fff; background: #2563eb;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.35);
  transition: background 0.2s, transform 0.2s, box-shadow 0.2s;
}
.btn-login:hover:not(:disabled) { background: #1d4ed8; box-shadow: 0 12px 26px rgba(37, 99, 235, 0.45); }
.btn-login:active:not(:disabled) { transform: scale(0.99); }
.btn-login:disabled { opacity: 0.7; cursor: wait; }

.demo-box { margin-top: 22px; padding-top: 16px; border-top: 1px dashed #e2e8f0; }
.demo-title { margin-bottom: 10px; text-align: center; font-size: 11px; letter-spacing: 0.1em; color: #94a3b8; }
.demo-chips { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; }
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
.demo-chip:hover { border-color: #2563eb; background: #eef3ff; color: #2563eb; }
.demo-chip:active { transform: scale(0.95); }

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
.corner-link:active { transform: scale(0.97); }

@keyframes fadeUp {
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes fadeDown {
  from { opacity: 0; transform: scale(0.9); }
  to { opacity: 1; transform: scale(1); }
}
</style>