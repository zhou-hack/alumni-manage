<script setup lang="ts">
// 个人中心（/profile）：H5 校友端首页功能的门户版
// - 用户横幅（校园实景 + 校徽）+ 快捷入口（提交资料/我的记录/我的荣誉）
// - 档案与荣誉统计、最近提交（含驳回原因）、账号信息、退出登录
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import ProfileShell from '../components/profile/ProfileShell.vue';
import StatusTag from '../components/profile/StatusTag.vue';
import { categoryLabel, fmtDate } from '../components/profile/shared';
import Lucide from '../components/Lucide.vue';
import { api, type Archive } from '../api';
import { authState, displayName, logout, ROLE_LABEL } from '../auth';
import AlumniProfileSelfDialog from '../components/AlumniProfileSelfDialog.vue';

const router = useRouter();
const user = computed(() => authState.user);

const latest = ref<Archive | null>(null);
const archiveTotal = ref<number | null>(null);
const honorTotal = ref<number | null>(null);
const loaded = ref(false);

/* ============== V18：校友本人编辑"大屏展示字段"（头像/座右铭/简介/行业/人生信条） ============== */
/** 大屏展示字段（只读预览，让用户看到自己风采墙上展示的内容） */
interface ProfilePreview {
  photoUrl: string;
  industry: string;
  intro: string;
  quote: string;
  motto: string;
}
const profile = ref<ProfilePreview | null>(null);
const profileDialogOpen = ref(false);
function openProfileDialog() {
  profileDialogOpen.value = true;
}
async function onProfileSaved(updated: unknown) {
  // dialog 内部已 success 提示；这里刷新只读预览
  if (updated && typeof updated === 'object') {
    const u = updated as Partial<ProfilePreview>;
    profile.value = {
      photoUrl: u.photoUrl ?? profile.value?.photoUrl ?? '',
      industry: u.industry ?? profile.value?.industry ?? '',
      intro: u.intro ?? profile.value?.intro ?? '',
      quote: u.quote ?? profile.value?.quote ?? '',
      motto: u.motto ?? profile.value?.motto ?? '',
    };
  } else {
    await loadProfile();
  }
}
async function loadProfile() {
  try {
    const d = await api.alumniProfile.getMy();
    profile.value = {
      photoUrl: d.photoUrl || '',
      industry: d.industry || '',
      intro: d.intro || '',
      quote: d.quote || '',
      motto: d.motto || '',
    };
  } catch {
    /* /me/profile 对部分校友可能 404（如未激活），静默忽略 */
  }
}

/** 大屏展示字段是否至少有一项非空，用于决定是否渲染展示卡 */
const hasProfile = computed(() => {
  const p = profile.value;
  if (!p) return false;
  return Boolean(p.photoUrl || p.industry || p.intro || p.quote || p.motto);
});

let alive = true;
onMounted(async () => {
  try {
    // 并行拉取：最近一份档案 + 荣誉列表 + V18 大屏展示字段
    const [mine, honors, profileRes] = await Promise.allSettled([
      api.archives.my({ page: '1', page_size: '1' }),
      api.honors.list({ page: '1', page_size: '100' }),
      api.alumniProfile.getMy(),
    ]);
    if (!alive) return;
    if (mine.status === 'fulfilled') {
      latest.value = mine.value.list?.[0] ?? null;
      archiveTotal.value = mine.value.total ?? 0;
    }
    if (honors.status === 'fulfilled') {
      const mineHonors = honors.value.list.filter(
        (h) => h.user_id == null || h.user_id === user.value?.id
      );
      honorTotal.value = mineHonors.length;
    }
    if (profileRes.status === 'fulfilled') {
      const d = profileRes.value;
      profile.value = {
        photoUrl: d.photoUrl || '',
        industry: d.industry || '',
        intro: d.intro || '',
        quote: d.quote || '',
        motto: d.motto || '',
      };
    }
  } finally {
    if (alive) loaded.value = true;
  }
});
onBeforeUnmount(() => {
  alive = false;
});

const initial = computed(() => {
  const u = user.value;
  if (!u) return '?';
  return (displayName(u) || u.student_id || '?').trim().charAt(0).toUpperCase();
});

const handleLogout = async () => {
  await logout();
  router.replace('/');
};

/** 快捷入口配置 */
const QUICKS: { to?: string; onClick?: 'profile'; icon: string; title: string; desc: string; delay: number }[] = [
  {
    to: '/profile/submit',
    icon: 'pen-line',
    title: '提交资料',
    desc: '填写获奖、证书、实践等经历，上传证明材料，两级审核后进入校友档案库',
    delay: 0.08
  },
  {
    to: '/profile/records',
    icon: 'list',
    title: '我的记录',
    desc: '查看提交历史与审核进度，驳回可修改重提，支持授权大屏展示',
    delay: 0.16
  },
  {
    to: '/profile/honors',
    icon: 'award',
    title: '我的荣誉',
    desc: '荣誉墙管理：录入奖项与证书，按国家级/省级/市级/校级分级展示',
    delay: 0.24
  },
  {
    to: '/profile/photos',
    icon: 'images',
    title: '我的相册',
    desc: '上传校园生活照片，审核通过后校级可推送给更多校友',
    delay: 0.28
  },
  {
    onClick: 'profile',
    icon: 'pen-line',
    title: '我的风采 / 座右铭',
    desc: '上传头像、补所在行业 / 个人简介 / 人生信条 / 座右铭，校级大屏将同步展示',
    delay: 0.32
  }
];
</script>

<template>
  <ProfileShell v-if="user">
    <!-- ===== 用户横幅：校园实景 + 校徽 + 用户身份 ===== -->
    <section class="relative overflow-hidden rounded-[15px] shadow-[0_18px_50px_rgba(26,16,61,0.18)]">
      <img
        src="/bgcsvg/front.jpg"
        alt="福州软件职业技术学院"
        class="absolute inset-0 h-full w-full object-cover"
      />
      <div
        aria-hidden
        class="absolute inset-0"
        :style="{
          background:
            'linear-gradient(to right, rgba(26,16,61,0.96) 0%, rgba(26,16,61,0.72) 46%, rgba(26,16,61,0.30) 100%)'
        }"
      />
      <div class="relative z-10 flex flex-wrap items-center gap-5 p-6 sm:p-8">
        <span class="avatar-pop flex h-[72px] w-[72px] shrink-0 items-center justify-center rounded-2xl bg-lamp font-title text-3xl font-black text-night shadow-[0_0_24px_rgba(245,179,1,0.5)]">
          {{ initial }}
        </span>
        <div class="min-w-0 flex-1">
          <p class="font-title truncate text-2xl font-black tracking-wide text-warm sm:text-3xl">
            {{ displayName(user) || '校友' }}，你好
          </p>
          <div class="mt-2 flex flex-wrap items-center gap-2">
            <span class="rounded-full bg-tech/25 px-3 py-0.5 text-xs font-semibold text-[#9dc0ff] ring-1 ring-tech/40">
              {{ ROLE_LABEL[user.role] ?? user.role }}
            </span>
            <span class="font-code text-[11px] tracking-widest text-warm/60">NO.{{ user.student_id }}</span>
          </div>
        </div>
        <!-- 统计角标 -->
        <div class="flex gap-3 sm:gap-5">
          <div class="rounded-xl bg-white/10 px-4 py-2.5 text-center ring-1 ring-white/15 backdrop-blur-sm">
            <p class="font-title text-2xl font-black text-warm">
              {{ loaded ? (archiveTotal ?? '—') : '…' }}
            </p>
            <p class="mt-0.5 text-[11px] tracking-wide text-warm/60">我的档案</p>
          </div>
          <div class="rounded-xl bg-white/10 px-4 py-2.5 text-center ring-1 ring-white/15 backdrop-blur-sm">
            <p class="font-title text-2xl font-black text-warm">
              {{ loaded ? (honorTotal ?? '—') : '…' }}
            </p>
            <p class="mt-0.5 text-[11px] tracking-wide text-warm/60">我的荣誉</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 快捷入口 ===== -->
    <section class="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <div
        v-for="(q, idx) in QUICKS"
        :key="q.to || ((q.onClick ?? '') + idx)"
        class="quick-enter"
        :style="{ animationDelay: `${q.delay}s` }"
      >
        <!-- 普通链接 -->
        <RouterLink
          v-if="q.to"
          :to="q.to"
          class="group flex h-full flex-col gap-3 rounded-2xl border border-slate-100 bg-white p-5 shadow-[0_10px_26px_rgba(26,16,61,0.05)] transition-all duration-300 hover:-translate-y-1 hover:border-tech/30 hover:shadow-[0_18px_40px_rgba(37,99,235,0.12)]"
        >
          <span class="flex h-11 w-11 items-center justify-center rounded-xl bg-tech/10 text-tech transition-colors group-hover:bg-tech/15">
            <Lucide :name="(q.icon as any)" :size="20" />
          </span>
          <div>
            <p class="font-title text-base font-bold text-ink">{{ q.title }}</p>
            <p class="mt-1 text-xs leading-relaxed text-slate-500">{{ q.desc }}</p>
          </div>
          <span class="mt-auto flex items-center gap-1 text-xs font-semibold text-tech opacity-0 transition-opacity group-hover:opacity-100">
            进入 <span class="transition-transform duration-300 group-hover:translate-x-0.5">→</span>
          </span>
        </RouterLink>

        <!-- 触发弹窗的"我的风采"按钮（不走路由） -->
        <button
          v-else-if="q.onClick === 'profile'"
          type="button"
          class="group flex h-full w-full flex-col gap-3 rounded-2xl border border-slate-100 bg-white p-5 text-left shadow-[0_10px_26px_rgba(26,16,61,0.05)] transition-all duration-300 hover:-translate-y-1 hover:border-tech/30 hover:shadow-[0_18px_40px_rgba(37,99,235,0.12)]"
          @click="openProfileDialog"
        >
          <span class="flex h-11 w-11 items-center justify-center rounded-xl bg-tech/10 text-tech transition-colors group-hover:bg-tech/15">
            <Lucide :name="(q.icon as any)" :size="20" />
          </span>
          <div>
            <p class="font-title text-base font-bold text-ink">{{ q.title }}</p>
            <p class="mt-1 text-xs leading-relaxed text-slate-500">{{ q.desc }}</p>
          </div>
          <span class="mt-auto flex items-center gap-1 text-xs font-semibold text-tech opacity-0 transition-opacity group-hover:opacity-100">
            去设置 <span class="transition-transform duration-300 group-hover:translate-x-0.5">→</span>
          </span>
        </button>
      </div>
    </section>

    <!-- ===== V18 大屏展示字段（只读预览，让校友看到自己风采墙上展示的内容） ===== -->
    <section v-if="hasProfile" class="mt-6 overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-[0_10px_26px_rgba(26,16,61,0.05)]">
      <div class="flex items-center justify-between gap-4 border-b border-slate-100 px-6 py-4">
        <div>
          <h2 class="font-title text-lg font-bold text-ink">我的风采</h2>
          <p class="mt-0.5 text-xs text-slate-500">以下内容展示在校级荣誉大屏「校友风采」卡片中</p>
        </div>
        <button
          type="button"
          class="rounded-lg border border-tech/40 px-3 py-1.5 text-xs font-semibold text-tech transition hover:bg-tech/5"
          @click="openProfileDialog"
        >
          编辑
        </button>
      </div>
      <div class="flex flex-col gap-5 px-6 py-5 sm:flex-row">
        <!-- 头像 -->
        <div class="shrink-0">
          <div
            class="flex h-[88px] w-[88px] items-center justify-center overflow-hidden rounded-2xl bg-gradient-to-br from-tech/15 to-warm/20 font-title text-3xl font-black text-tech shadow-[0_8px_20px_rgba(37,99,235,0.15)]"
          >
            <img
              v-if="profile?.photoUrl"
              :src="profile.photoUrl"
              alt="我的头像"
              class="h-full w-full object-cover"
            />
            <span v-else>{{ initial }}</span>
          </div>
          <p v-if="profile?.industry" class="mt-2 text-center text-xs font-medium text-slate-500">{{ profile.industry }}</p>
        </div>
        <!-- 文案三件套 -->
        <div class="min-w-0 flex-1 space-y-3">
          <div v-if="profile?.motto" class="rounded-lg bg-tech/5 px-4 py-2.5">
            <p class="text-[11px] font-semibold uppercase tracking-wider text-tech">座右铭</p>
            <p class="mt-1 font-title text-sm font-bold leading-relaxed text-ink">「{{ profile.motto }}」</p>
          </div>
          <div v-if="profile?.intro" class="rounded-lg bg-slate-50 px-4 py-2.5">
            <p class="text-[11px] font-semibold uppercase tracking-wider text-slate-500">个人简介</p>
            <p class="mt-1 text-sm leading-relaxed text-ink">{{ profile.intro }}</p>
          </div>
          <div v-if="profile?.quote" class="rounded-lg bg-warm/5 px-4 py-2.5">
            <p class="text-[11px] font-semibold uppercase tracking-wider text-warm">人生信条</p>
            <p class="mt-1 text-sm italic leading-relaxed text-ink">{{ profile.quote }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 最近提交 ===== -->
    <section class="mt-6 rounded-2xl border border-slate-100 bg-white p-6 shadow-[0_10px_26px_rgba(26,16,61,0.05)]">
      <div class="flex items-center justify-between gap-4">
        <h2 class="font-title text-lg font-bold text-ink">最近提交</h2>
        <RouterLink
          to="/profile/records"
          class="text-xs font-semibold text-tech transition-colors hover:text-blue-700"
        >
          查看全部记录 →
        </RouterLink>
      </div>

      <p v-if="!loaded" class="mt-4 text-sm text-slate-400">加载中…</p>
      <div v-else-if="latest" class="mt-4">
        <div class="flex items-center justify-between gap-4">
          <div class="min-w-0">
            <p class="truncate font-semibold text-ink">{{ latest.title }}</p>
            <p class="mt-1 text-xs text-slate-500">
              {{ categoryLabel(latest.category) }} · {{ fmtDate(latest.created_at) }} 提交
              <template v-if="latest.version > 1"> · 第 {{ latest.version }} 版</template>
            </p>
          </div>
          <StatusTag :status="latest.status" />
        </div>
        <div
          v-if="latest.status === 'rejected' && latest.reject_reason"
          class="mt-3 rounded-lg bg-red-50 px-4 py-2.5 text-xs leading-relaxed text-red-600"
        >
          驳回原因:{{ latest.reject_reason }}
        </div>
      </div>
      <div v-else class="mt-4">
        <p class="text-sm leading-relaxed text-slate-500">
          还没有提交记录。填写获奖、证书、实践等经历，上传证明材料，经院系与校级两级审核后将进入校友档案库。
        </p>
        <RouterLink
          to="/profile/submit"
          class="mt-4 inline-flex items-center gap-1.5 rounded-lg bg-tech px-5 py-2.5 text-sm font-bold text-white shadow-[0_6px_18px_rgba(37,99,235,0.4)] transition hover:bg-blue-700"
        >
          立即提交第一份资料 →
        </RouterLink>
      </div>
    </section>

    <!-- ===== 账号信息 ===== -->
    <section class="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-5">
      <div class="rounded-2xl border border-slate-100 bg-white px-6 py-2 shadow-[0_10px_26px_rgba(26,16,61,0.05)] lg:col-span-3">
        <h2 class="border-b border-slate-100 py-4 font-title text-lg font-bold text-ink">账号信息</h2>
        <div class="px-1">
          <div
            v-for="row in [
              { label: '学号', value: user.student_id, mono: true },
              { label: '手机号', value: user.phone || '—', mono: true },
              { label: '平台角色', value: ROLE_LABEL[user.role] ?? user.role, mono: false },
              { label: '加入时间', value: fmtDate(user.created_at), mono: true }
            ]"
            :key="row.label"
            class="flex items-center justify-between gap-4 border-b border-slate-100 py-3.5 last:border-0"
          >
            <span class="shrink-0 text-sm text-slate-500">{{ row.label }}</span>
            <span
              class="min-w-0 truncate text-right text-sm font-medium text-ink"
              :class="row.mono ? 'font-code' : ''"
              :title="row.value"
            >
              {{ row.value }}
            </span>
          </div>
        </div>
      </div>

      <div class="flex flex-col gap-4 lg:col-span-2">
        <div class="flex-1 rounded-2xl border border-slate-100 bg-white p-6 shadow-[0_10px_26px_rgba(26,16,61,0.05)]">
          <h2 class="font-title text-lg font-bold text-ink">安全与退出</h2>
          <p class="mt-2 text-xs leading-relaxed text-slate-500">
            如需修改密码，可通过找回密码流程（短信验证后重置）。
          </p>
          <div class="mt-4 flex flex-col gap-2.5">
            <RouterLink
              to="/forgot"
              class="rounded-lg border border-tech/40 py-2.5 text-center text-sm font-semibold text-tech transition hover:bg-tech/5"
            >
              修改密码（短信验证）
            </RouterLink>
            <button
              type="button"
              class="rounded-lg border border-red-200 bg-red-50 py-2.5 text-sm font-semibold text-red-600 transition hover:bg-red-100"
              @click="handleLogout"
            >
              退出登录
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- V18：校友本人编辑"大屏展示字段"弹窗（头像/行业/简介/人生信条/座右铭） -->
    <AlumniProfileSelfDialog v-model:open="profileDialogOpen" @saved="onProfileSaved" />
  </ProfileShell>
</template>

<style scoped>
.avatar-pop {
  animation: avatarPop 0.45s cubic-bezier(0.22, 0.61, 0.36, 1) 0.1s both;
}
@keyframes avatarPop {
  from {
    opacity: 0;
    transform: scale(0.7);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
.quick-enter {
  animation: quickEnter 0.4s cubic-bezier(0.22, 0.61, 0.36, 1) both;
}
@keyframes quickEnter {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
