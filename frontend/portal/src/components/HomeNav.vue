<script setup lang="ts">
// ============================================================================
// 首页顶部导航栏（透明悬浮于首屏大图 · 下拉后转为纯蓝 #102a5c）
//
// 结构：左=校徽+平台名 · 中=主导航菜单 · 右=数据看板入口 + 登录态
// 登录态：未登录显示"登录 / 注册"（去 /login）；已登录显示昵称 + 下拉
// （个人资料/提交资料/我的记录/我的荣誉/退出登录），行为对齐旧版 F2 HomeNav。
// 菜单：
//   - 路由跳转：首页(/)、最新通知(/news)、个人资料(/profile)；
//   - 页内锚点：美丽校园 / 最新动态 / 优秀校友 / 荣誉墙 / 校友档案索引 / 校友留言墙，
//     点击平滑滚动到对应板块（id 锚点 + scroll-mt-24，跳转后不被顶部栏遮挡）。
// forceSolid：无首屏大图的页面（如个人中心）传入 true，导航恒为实底。
// ============================================================================
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import Lucide, { type LucideName } from './Lucide.vue';
import { authState, displayName, logout, ROLE_LABEL } from '../auth';

const props = withDefaults(defineProps<{ forceSolid?: boolean }>(), { forceSolid: false });

type ChildItem =
  | { label: string; to: string; soon?: undefined; anchor?: undefined } // 路由跳转
  | { label: string; anchor: string; soon?: undefined; to?: undefined } // 页内锚点
  | { label: string; soon: true; to?: undefined; anchor?: undefined }; // 尚未实现

type MenuItem =
  | { label: string; children?: undefined; to?: string; anchor?: string } // 顶级：单链接（兼容旧调用）
  | {
      label: string
      icon?: undefined
      children: ChildItem[]
    }; // 顶级：含二级菜单的分组

const MENU: readonly MenuItem[] = [
  { label: '首页', to: '/' },
  {
    label: '学校概览',
    children: [
      { label: '美丽校园', anchor: 'beautiful-campus' },
      { label: '最新动态', anchor: 'latest-news' },
      { label: '优秀校友', anchor: 'outstanding-alumni' }
    ]
  },
  {
    label: '校友风采',
    children: [
      { label: '荣誉墙', anchor: 'honor-wall' },
      { label: '档案索引', anchor: 'alumni-index' },
      { label: '留言墙', anchor: 'message-wall' }
    ]
  },
  {
    label: '通知中心',
    children: [
      { label: '最新通知', to: '/news' },
      { label: '我的消息', to: '/messages' }
    ]
  },
  {
    label: '个人中心',
    children: [
      { label: '个人资料', to: '/profile' },
      { label: '提交资料', to: '/profile/submit' },
      { label: '我的记录', to: '/profile/records' },
      { label: '我的荣誉', to: '/profile/honors' },
      { label: '注册审核', to: '/profile/register-progress' }
    ]
  }
];

/** 顶层 hover/focus 的二级面板 open 状态（按索引） */
const openIndex = ref(-1);
/**
 * 关闭二级面板的定时器：避免鼠标从一级滑到二级时立刻消失
 * 卸载时必须清理，否则组件销毁后回调仍可能写已卸载的 ref
 */
let closeTimer: number | undefined;
/**
 * 焦点计数器：进入任一二级面板区域（含子项 RouterLink）即 +1，离开 -1；
 * 只要计数 > 0，即使鼠标已经移出整个 hover 区也不关（焦点不丢出可见元素）
 */
const focusCount = ref(0);
const armClose = () => {
  if (focusCount.value > 0) return;
  window.clearTimeout(closeTimer);
  closeTimer = window.setTimeout(() => (openIndex.value = -1), 120);
};
const cancelClose = () => {
  window.clearTimeout(closeTimer);
};
const onPanelFocusIn = () => {
  focusCount.value += 1;
  cancelClose();
};
const onPanelFocusOut = () => {
  // 延后到下一个 tick 再减：focus 进入子元素前会先 out 当前再 in 子，
  // 这样可避免把"在同一面板内转移焦点"误判为"已离开面板"
  nextTick(() => {
    focusCount.value = Math.max(0, focusCount.value - 1);
    if (focusCount.value === 0 && openIndex.value >= 0) armClose();
  });
};
const clearCloseTimer = () => {
  window.clearTimeout(closeTimer);
  closeTimer = undefined;
};

/** 登录态下拉：个人中心功能入口（lucide 图标名） */
const USER_MENU: { label: string; to: string; icon: LucideName }[] = [
  { label: '个人资料', to: '/profile', icon: 'user' },
  { label: '提交资料', to: '/profile/submit', icon: 'pen-line' },
  { label: '我的记录', to: '/profile/records', icon: 'list' },
  { label: '我的荣誉', to: '/profile/honors', icon: 'award' }
];

/** 管理端角色（导员/院系/校级/系统管理员）：下拉额外显示「进入管理后台」 */
const canAdmin = computed(() =>
  ['counselor', 'college_admin', 'school_admin', 'system_admin'].includes(authState.user?.role ?? '')
);
/**
 * 进入管理后台
 *  - portal dev (5175) 直连：admin dev 在 5176 → 跳 :5176/admin/
 *  - nginx 80 / 同源：跳 /admin/（由 nginx 反代到 admin SPA，localStorage 同源共享 token）
 *  跨 origin 必须整页跳，router.push 仅在同 SPA 内有效
 */
const goAdmin = () => {
  userOpen.value = false;
  const port = window.location.port;
  if (port === '5175') {
    window.location.href = `${window.location.protocol}//${window.location.hostname}:5176/admin/`;
  } else {
    window.location.href = '/admin/';
  }
};

const ITEM =
  'relative whitespace-nowrap rounded-md px-2 py-2 text-sm transition-colors drop-shadow-[0_1px_4px_rgba(0,0,0,0.35)]';

const scrolled = ref(false);
const notice = ref('');
const route = useRoute();
const router = useRouter();

/** 当前路由对应的菜单高亮（首页精确匹配，其余按前缀，如 /news、/profile/*） */
const isActive = (to: string) =>
  to === '/' ? route.path === '/' : route.path === to || route.path.startsWith(`${to}/`);

// 恒为实底：个人中心等无首屏大图的页面
const solid = computed(() => props.forceSolid || scrolled.value);

/* ================= 登录态用户下拉 ================= */
const userOpen = ref(false);
const userMenuRef = ref<HTMLElement | null>(null);

// 点击用户下拉外部时收起
const onDocDown = (e: MouseEvent) => {
  if (userMenuRef.value && !userMenuRef.value.contains(e.target as Node)) userOpen.value = false;
};
watch(userOpen, (v) => {
  if (v) document.addEventListener('mousedown', onDocDown);
  else document.removeEventListener('mousedown', onDocDown);
});
onBeforeUnmount(() => document.removeEventListener('mousedown', onDocDown));

const onKeyDown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    userOpen.value = false;
    openIndex.value = -1;
    clearCloseTimer();
  }
};
onMounted(() => window.addEventListener('keydown', onKeyDown));
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeyDown);
  clearCloseTimer();
});

// 路由变化时收起下拉，并把焦点计数清零（避免上个面板的 focus 残留）
watch(
  () => route.path,
  () => {
    userOpen.value = false;
    openIndex.value = -1;
    focusCount.value = 0;
    clearCloseTimer();
  }
);

const userInitial = computed(() =>
  (displayName(authState.user) || '?').trim().charAt(0).toUpperCase()
);

const handleLogout = async () => {
  userOpen.value = false;
  await logout();
  router.push('/');
};

// 顶栏入场（y:-72 → 0 淡入）：不用 JS 动效库，挂载后加 shown 类走 CSS 过渡。
// CSS 过渡的终态由类名决定，即使动画帧被环境节流，顶栏最终也必定可见
const shown = ref(false);
onMounted(() => {
  // 双 rAF 确保 initial 帧先绘制再过渡；rAF 被节流时兜底 100ms 定时器
  let raf2 = 0;
  const fallback = window.setTimeout(() => (shown.value = true), 100);
  raf2 = requestAnimationFrame(() =>
    requestAnimationFrame(() => {
      window.clearTimeout(fallback);
      shown.value = true;
    })
  );
  onBeforeUnmount(() => {
    cancelAnimationFrame(raf2);
    window.clearTimeout(fallback);
  });
});

/** 二级面板键盘支持（hover 折叠 + esc 关闭由上文 onKeyDown 统一处理） */

// 滚动后加深底色与投影，让导航栏与下方内容分层；锚点锁定期间滚动停止 150ms 后解锁
const onScroll = () => {
  scrolled.value = window.scrollY > 8;
  if (spyLocked) {
    armSpyUnlock(150);
    return;
  }
  spySection();
};
onMounted(() => {
  onScroll();
  window.addEventListener('scroll', onScroll, { passive: true });
});
onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll);
  window.clearTimeout(unlockTimer);
});

/* ================= 锚点滚动监听（小黄条自动跟随） ================= */
/** 当前处于视口内的板块锚点（'' = 首屏大图区/非首页） */
const activeAnchor = ref('');
const ANCHORS = MENU.flatMap((m) =>
  m.children ? m.children.filter((c) => c.anchor).map((c) => c.anchor!) : m.anchor ? [m.anchor] : []
);

/**
 * 程序化平滑滚动期间锁定监听：点击锚点后平滑滚动会途经中间板块，
 * 不锁定的话小黄条会跟着途经板块连续闪动；滚动停止 150ms 后解锁并校正一次
 */
let spyLocked = false;
let unlockTimer: number | undefined;
const armSpyUnlock = (delay: number) => {
  window.clearTimeout(unlockTimer);
  unlockTimer = window.setTimeout(() => {
    spyLocked = false;
    spySection();
  }, delay);
};

/** 滚动监听：取「顶部已过导航栏下方 40% 视口线」的最后一个板块作为当前板块 */
const spySection = () => {
  if (route.path !== '/') {
    activeAnchor.value = '';
    return;
  }
  if (spyLocked) return;
  const line = window.scrollY + window.innerHeight * 0.4;
  let current = '';
  for (const id of ANCHORS) {
    const el = document.getElementById(id);
    if (el && el.offsetTop <= line) current = id;
  }
  activeAnchor.value = current;
};

/** 品牌区（校徽 + 平台名）与「首页」菜单点击：跳到 /#home（首屏锚点）；
 *  已在 #home 时手动平滑回顶部；其它页面走 RouterLink 正常跳回首页 */
const onBrandClick = (e: MouseEvent) => {
  if (route.path !== '/') return;
  e.preventDefault();
  if (route.hash === '#home') {
    activeAnchor.value = '';
    spyLocked = true;
    armSpyUnlock(900);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  } else {
    router.push('/#home'); // hash watcher 负责滚动与高亮
  }
};

/** 锚点项高亮：与路由项同一套视觉（加粗白字 + 金色下划线） */
const anchorActive = (anchor: string) => route.path === '/' && activeAnchor.value === anchor;

/* 注：当前版本无"一级单链接 + 锚点"组合（所有单链接项已纳入含子项菜单的分组或纯路由），
   上面的 anchorActive 仅保留供旧调用复用。 */

/** 路由项高亮：首页（/）仅在未滚入任何板块（首屏大图区）时点亮，滚入板块后让位给锚点小黄条 */
const routeItemActive = (to: string) => {
  if (to === '/') return isActive('/') && !activeAnchor.value;
  return isActive(to);
};

/** 分组高亮：只要当前路由是该分组任一子项，或当前激活锚点属于该分组 */
const groupActive = (children: ChildItem[]) => {
  if (route.path === '/') {
    if (children.some((c) => 'anchor' in c && c.anchor && activeAnchor.value === c.anchor)) return true;
  }
  return children.some(
    (c) => 'to' in c && c.to && (route.path === c.to || route.path.startsWith(c.to + '/')),
  );
};

/** 二级子项高亮 */
const childActive = (c: ChildItem) => {
  if ('soon' in c && c.soon) return false; // 未实现的子项不参与高亮
  if (c.anchor) return route.path === '/' && activeAnchor.value === c.anchor;
  if (c.to) return route.path === c.to || route.path.startsWith(c.to + '/');
  return false;
};

/**
 * 计算 RouterLink 的目标（路由跳转 / 锚点 hash / 暂未实现返回 '/' 兜底）
 * - 单一函数保证所有分支统一处理，防止 c.to / c.anchor 任一字段缺失时传 undefined 给 RouterLink
 */
const childTo = (c: ChildItem) => {
  if (c.anchor) return `/#${c.anchor}`;
  if (c.to) return c.to;
  return '/';
};

/** 二级面板点击 onClick：关面板 + 处理首页锚点 + 触发未读标记 */
const handleChildClick = (c: ChildItem, e: MouseEvent) => {
  openIndex.value = -1;
  if (c.to === '/') onBrandClick(e);
};

/** 我的消息未读数（与 /admin/user 下的 res.data.total 对齐；portal localStorage key: alumni_portal_unread） */
const unreadBadge = ref(0)
function refreshUnread() {
  try {
    const raw = window.localStorage.getItem('alumni_portal_unread')
    if (raw) unreadBadge.value = Math.max(0, Number(JSON.parse(raw)?.total || 0))
  } catch {
    unreadBadge.value = 0
  }
}
onMounted(() => {
  refreshUnread()
  window.addEventListener('storage', refreshUnread)
})
onBeforeUnmount(() => window.removeEventListener('storage', refreshUnread))

// 提示语自动消失
let noticeTimer: number | undefined;
watch(notice, (v) => {
  window.clearTimeout(noticeTimer);
  if (!v) return;
  noticeTimer = window.setTimeout(() => {
    notice.value = '';
  }, 2200);
});
onBeforeUnmount(() => window.clearTimeout(noticeTimer));

// 页内锚点跳转：统一走 /#{anchor}（地址栏可见、可分享）；hash 相同则手动平滑滚动
const scrollToAnchor = (anchor: string) => {
  if (route.path !== '/') {
    router.push(`/#${anchor}`);
    return;
  }
  if (route.hash === `#${anchor}`) {
    // hash 相同 watcher 不触发：手动切小黄条 + 锁定 + 滚动
    activeAnchor.value = anchor;
    spyLocked = true;
    armSpyUnlock(900);
    const el = document.getElementById(anchor);
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    return;
  }
  router.push(`/#${anchor}`); // hash watcher 负责滚动与高亮
};

// hash 变化（点锚点/品牌区、或从其它页带 hash 跳回）：等首页渲染后滚动到锚点。
// #home = 首屏大图区：无锚点小黄条（「首页」菜单亮），其余锚点小黄条跟随
let hashTimer: number | undefined;
watch(
  () => [route.path, route.hash],
  () => {
    window.clearTimeout(hashTimer);
    if (route.path !== '/' || !route.hash) return;
    const anchor = route.hash.slice(1);
    activeAnchor.value = anchor === 'home' ? '' : anchor;
    spyLocked = true;
    armSpyUnlock(1000);
    // 等首页各板块渲染完成再尝试滚动
    hashTimer = window.setTimeout(() => {
      const el = document.getElementById(anchor);
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 60);
  },
  { immediate: true }
);
onBeforeUnmount(() => window.clearTimeout(hashTimer));
</script>

<template>
  <header
    class="nav-enter fixed inset-x-0 top-0 z-50 transition-colors duration-300"
    :class="[shown ? 'nav-shown' : '', solid ? 'bg-[#102a5c]' : 'bg-transparent']"
  >
    <div
      class="mx-auto flex h-[72px] w-[92%] max-w-[1500px] items-center justify-between md:w-[86%] xl:w-[80%]"
    >
      <!-- 左：校徽 + 平台名（竖屏压缩尺寸，英文副标超小屏隐藏；首页内点击回到最上面） -->
      <RouterLink to="/" class="flex shrink-0 items-center gap-2 sm:gap-3" @click="onBrandClick">
        <span
          class="flex h-9 w-9 items-center justify-center overflow-hidden rounded-full bg-white ring-1 ring-white/60 sm:h-11 sm:w-11"
        >
          <img
            src="/bgcsvg/school-badge.png"
            alt="福州软件职业技术学院校徽"
            class="h-full w-full object-cover"
          />
        </span>
        <span class="leading-tight">
          <span
            class="block font-title text-base font-black tracking-wide text-white drop-shadow-[0_1px_6px_rgba(0,0,0,0.45)] sm:text-xl"
          >
            校友管理平台
          </span>
          <span class="mt-1 hidden font-code text-[9px] tracking-[0.22em] text-white/60 sm:block">
            FURUAN CASTLE · ALUMNI
          </span>
        </span>
      </RouterLink>

      <!-- 中：主导航菜单（≥1280px 才展示，避免与品牌/右侧按钮挤压） -->
      <nav class="hidden items-center gap-0.5 xl:flex">
        <template v-for="(m, idx) in MENU" :key="`${idx}-${m.label}`">
          <!-- 含二级菜单的分组 -->
          <div
            v-if="m.children"
            class="group/nav relative"
            @mouseenter="openIndex = idx; cancelClose()"
            @mouseleave="armClose()"
            @focusin="onPanelFocusIn()"
            @focusout="onPanelFocusOut()"
          >
            <button
              type="button"
              :class="`${ITEM} flex items-center gap-1 font-medium ${
                openIndex === idx || groupActive(m.children)
                  ? 'text-white'
                  : 'text-white/85 hover:text-white'
              }`"
            >
              {{ m.label }}
              <svg
                class="h-3 w-3 transition-transform duration-200"
                :class="openIndex === idx ? 'rotate-180' : ''"
                viewBox="0 0 12 12"
                fill="none"
              >
                <path d="M3 4.5L6 7.5L9 4.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>

            <Transition
              enter-active-class="transition duration-150 ease-out"
              enter-from-class="translate-y-1 opacity-0"
              enter-to-class="translate-y-0 opacity-100"
              leave-active-class="transition duration-100 ease-in"
              leave-from-class="translate-y-0 opacity-100"
              leave-to-class="translate-y-1 opacity-0"
            >
              <div
                v-show="openIndex === idx"
                class="absolute left-1/2 top-[calc(100%+14px)] z-50 min-w-[160px] -translate-x-1/2 origin-top rounded-xl border border-white/15 bg-[#102a5c]/95 py-1.5 shadow-[0_18px_42px_rgba(0,0,0,0.35)] backdrop-blur-md"
                @mouseenter="cancelClose()"
                @mouseleave="armClose()"
              >
                <!-- 指向一级的小三角 -->
                <span
                  class="absolute -top-1 left-1/2 h-2 w-2 -translate-x-1/2 rotate-45 border-l border-t border-white/15 bg-[#102a5c]/95"
                ></span>
                <RouterLink
                  v-for="c in m.children"
                  :key="`${idx}-${c.label}`"
                  :to="childTo(c)"
                  class="flex items-center justify-between gap-3 px-4 py-2 text-sm transition"
                  :class="
                    childActive(c)
                      ? 'bg-lamp/15 font-semibold text-lamp'
                      : 'text-white/85 hover:bg-white/10 hover:text-white'
                  "
                  @click="handleChildClick(c, $event)"
                >
                  <span>{{ c.label }}</span>
                  <span
                    v-if="c.to === '/messages' && unreadBadge > 0"
                    class="rounded-full bg-red-500 px-1.5 py-0.5 text-[10px] font-bold leading-none text-white"
                  >{{ unreadBadge > 99 ? '99+' : unreadBadge }}</span>
                </RouterLink>
              </div>
            </Transition>
          </div>

          <!-- 单链接（首页） -->
          <RouterLink
            v-else
            :to="m.to || '/'"
            :class="`${ITEM} font-medium ${
              routeItemActive(m.to || '/') ? 'text-white' : 'text-white/85 hover:text-white'
            }`"
            @click="m.to === '/' && onBrandClick($event)"
          >
            {{ m.label }}
            <span
              v-if="routeItemActive(m.to || '/')"
              class="absolute inset-x-3 -bottom-0.5 h-[2px] rounded-full bg-lamp"
            />
          </RouterLink>
        </template>
      </nav>

      <!-- 右：荣誉大屏入口 + 登录态（竖屏压缩按钮尺寸） -->
      <div class="flex shrink-0 items-center gap-2 sm:gap-3">
        <RouterLink
          to="/dashboard"
          class="group flex items-center gap-1.5 rounded-lg border border-white/35 bg-white/10 px-2.5 py-2 text-xs font-semibold text-white backdrop-blur-sm transition hover:border-white/70 hover:bg-white/20 sm:px-4 sm:py-2.5 sm:text-sm"
        >
          <span>🏅</span>
          荣誉大屏
          <span class="transition-transform duration-300 group-hover:translate-x-0.5">→</span>
        </RouterLink>

        <!-- 已登录：头像按钮 + 个人中心下拉 -->
        <div v-if="authState.user" ref="userMenuRef" class="relative">
          <button
            type="button"
            aria-label="个人中心菜单"
            :aria-expanded="userOpen"
            class="flex max-w-[10rem] items-center gap-2 rounded-lg px-3 py-2 text-sm font-bold transition sm:px-4"
            :class="
              userOpen
                ? 'bg-lamp text-night'
                : 'bg-lamp/90 text-night shadow-[0_6px_16px_rgba(245,179,1,0.4)] hover:bg-lamp'
            "
            @click="userOpen = !userOpen"
          >
            <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-night/85 font-title text-xs font-black text-lamp">
              {{ userInitial }}
            </span>
            <span class="hidden truncate sm:inline">{{ displayName(authState.user) || '我的' }}</span>
            <span
              class="hidden text-[10px] transition-transform duration-300 sm:inline"
              :class="userOpen ? 'rotate-180' : ''"
            >
              ▾
            </span>
          </button>

          <Transition
            enter-active-class="transition duration-150 ease-out"
            enter-from-class="translate-y-2 scale-95 opacity-0"
            enter-to-class="translate-y-0 scale-100 opacity-100"
            leave-active-class="transition duration-150 ease-in"
            leave-from-class="translate-y-0 scale-100 opacity-100"
            leave-to-class="translate-y-1.5 scale-95 opacity-0"
          >
            <div
              v-if="userOpen"
              class="absolute right-0 top-[calc(100%+10px)] w-52 origin-top-right overflow-hidden rounded-xl border border-slate-200/80 bg-white/90 shadow-[0_24px_60px_rgba(5,10,30,0.25)] backdrop-blur-md"
            >
              <div class="border-b border-slate-200/80 px-4 py-3">
                <p class="truncate text-sm font-bold text-slate-900">
                  {{ displayName(authState.user) || '校友' }}
                </p>
                <p class="mt-0.5 text-[11px] text-slate-500">
                  {{ ROLE_LABEL[authState.user.role] ?? authState.user.role }} · NO.{{ authState.user.student_id }}
                </p>
              </div>
              <nav class="py-1.5">
                <button
                  v-if="canAdmin"
                  type="button"
                  class="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-sm font-semibold text-tech transition hover:bg-tech/10"
                  @click="goAdmin"
                >
                  <Lucide name="layout-dashboard" :size="15" />
                  进入管理后台
                </button>
                <RouterLink
                  v-for="m in USER_MENU"
                  :key="`um-${m.to}`"
                  :to="m.to"
                  class="flex items-center gap-2.5 px-4 py-2.5 text-sm transition"
                  :class="
                    m.to === route.path
                      ? 'bg-tech/10 font-semibold text-tech'
                      : 'text-slate-700 hover:bg-slate-100 hover:text-slate-900'
                  "
                >
                  <Lucide :name="m.icon" :size="15" />
                  {{ m.label }}
                </RouterLink>
              </nav>
              <div class="border-t border-slate-200/80 py-1.5">
                <button
                  type="button"
                  class="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-sm text-red-500 transition hover:bg-red-50"
                  @click="handleLogout"
                >
                  <Lucide name="log-out" :size="15" />
                  退出登录
                </button>
              </div>
            </div>
          </Transition>
        </div>

        <!-- 未登录：登录 / 注册（手机端暂不显示登录入口，sm 起展示；无需 UA 检测，纯响应式） -->
        <RouterLink
          v-else
          to="/login"
          class="hidden rounded-lg bg-tech px-4 py-2.5 text-sm font-bold tracking-wide text-white ring-1 ring-white/25 shadow-[0_6px_16px_rgba(37,99,235,0.4)] transition hover:bg-blue-700 sm:block sm:px-6"
        >
          <span>登录 / 注册</span>
        </RouterLink>
      </div>
    </div>
  </header>

  <!-- 未实现功能的统一提示（2.2s 自动消失） -->
  <div
    v-if="notice"
    role="status"
    class="fixed left-1/2 top-[84px] z-[60] -translate-x-1/2 rounded-full bg-ink/90 px-5 py-2.5 text-sm text-white shadow-xl"
  >
    {{ notice }}
  </div>
</template>

<style scoped>
/* 顶栏入场：初始上移隐藏，挂载后（.nav-shown）0.6s 淡入下落；
   同时承担滚动换底色的 0.3s 过渡（覆盖 Tailwind transition-colors 的 property 列表） */
.nav-enter {
  opacity: 0;
  transform: translateY(-72px);
  transition:
    opacity 0.6s cubic-bezier(0.22, 0.61, 0.36, 1),
    transform 0.6s cubic-bezier(0.22, 0.61, 0.36, 1),
    background-color 0.3s;
}
.nav-shown {
  opacity: 1;
  transform: translateY(0);
}
</style>
