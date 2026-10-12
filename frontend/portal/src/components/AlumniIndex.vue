<script setup lang="ts">
// ============================================================================
// 校友档案索引（首页 · 位于"优秀校友"下方）
//
// 公开版档案列表：
//   1. 顶部搜索框——按姓名 / 学号 / 学院 / 当前身份 / 荣誉关键词即时过滤；
//   2. "筛选"按钮展开条件面板——姓名 / 院系 / 学号 组合过滤，条件即时生效；
//   4. 列表展示前 6 条已公开档案，行尾箭头打开"数字校友档案"弹窗（与优秀校友同款）。
//
// 数据：挂载时拉取 /alumni/directory?page_size=100（approved + 上架 + 授权），
//   name/studentId/college/gradYear 直接对应；current/awards 后端缺省给 '—'/空数组
//   （top_honor 作为"代表荣誉"列）。点击"查看更多"前调 /alumni/directory/{studentId}
//   取详情补充弹窗数据，失败用列表行数据兜底。
//
// 可信度修复（2026-09-25 审查报告 P0-3 / P1-6）：
//   - 删除 photoFallback 按姓名调文生图接口生成头像的逻辑；
//     无真实照片时统一用渐变底 + 姓氏首字占位，不再向第三方发送姓名。
//   - 接口失败不再回退 mock 演示数据，改为空态 + 重试按钮。
//   - 列表学号列改用 maskStudentId 脱敏。
//   - "查看全部档案"接 /alumni/directory 完整列表入口（不再是"功能建设中"提示）。
// ============================================================================
import { computed, onMounted, ref, watch } from 'vue';
import ModuleTitle from './ModuleTitle.vue';
import { Reveal, RevealGroup, RevealItem } from './Reveal';
import AlumniProfileModal from './AlumniProfileModal.vue';
import { type HonorCategory, type OutstandingAlumni } from '../mock/outstandingAlumni';
import { api, type DirectoryDetail, type DirectoryItem } from '../api';
import { maskStudentId } from '../utils/privacy';

// 头像底色循环（低饱和暖色系，贴合暖白主题）
const AVATAR_COLORS = ['#A63D2F', '#C19A3F', '#3E6B4F', '#3D5A73', '#2F5D50', '#8B5CF6'];

// 首页索引默认展示条数（完整名录预留"查看全部档案"入口）
const PAGE_SIZE = 6;

/** 名录列表项 → 行数据（OutstandingAlumni 结构，弹窗兜底可直接打开） */
const mapRow = (d: DirectoryItem): OutstandingAlumni => ({
  name: d.name ?? '',
  photo: d.photo_url || '', // 无真实照片时为 ''，由 Avatar 组件用渐变 + 姓氏首字兜底
  message: '',
  college: d.college ?? '',
  gradYear: d.grad_year ? `${d.grad_year}届` : '',
  current: d.current ?? '—',
  studentId: d.student_id ?? '',
  timeline: [],
  awards: d.top_honor ? [{ title: d.top_honor, year: '', level: '', category: '奖项' as HonorCategory }] : []
});

/**
 * 数据质量校验（审查报告 P1-6）：明显占位/脏数据（如学院 `?????`、单字符标题）
 * 在前端做标记，不直接渲染脏值；用户能看到"资料待完善"，运营也能反向定位问题。
 */
const isDirtyCollege = (c: string) => {
  const t = (c ?? '').trim();
  return !t || /^\?+$/.test(t) || t.length < 2;
};
const isDirtyName = (n: string) => {
  const t = (n ?? '').trim();
  return !t || t.length === 1 || /^\d+$/.test(t);
};

/** 详情接口数据合并到行数据（详情缺省字段回落行数据，保证弹窗完整可开） */
const mergeDetail = (d: DirectoryDetail, row: OutstandingAlumni): OutstandingAlumni => ({
  ...row,
  name: d.name ?? row.name,
  photo: d.photo_url || row.photo,
  message: d.message ?? row.message,
  college: d.college ?? row.college,
  gradYear: d.grad_year ? `${d.grad_year}届` : row.gradYear,
  current: d.current ?? row.current,
  timeline: (d.timeline ?? []).map((t) => ({ year: t.year ?? '', event: t.event ?? '' })),
  awards: (d.awards ?? []).map((w) => ({
    title: w.title ?? '',
    year: w.year ?? '',
    level: w.level ?? '',
    category: (w.category as HonorCategory | undefined) ?? '奖项'
  }))
});

// 名录数据：默认空，挂载后拉取；接口失败不展示 mock
const rows = ref<OutstandingAlumni[]>([]);
const loadState = ref<'loading' | 'api' | 'error'>('loading');
const reload = async () => {
  loadState.value = 'loading';
  try {
    const res = await api.alumniPublic.directory({ page: '1', page_size: '100' });
    rows.value = (res.list ?? []).map(mapRow);
    loadState.value = 'api';
  } catch (e) {
    console.warn('[alumni-index] 公开名录接口请求失败，保持空态：', e);
    rows.value = [];
    loadState.value = 'error';
  }
};
onMounted(reload);

const keyword = ref('');
const nameKw = ref(''); // 姓名筛选（包含匹配）
const college = ref(''); // 空串 = 全部学院
const sid = ref(''); // 学号筛选（包含匹配）
const filterOpen = ref(false);
const notice = ref('');
const profile = ref<OutstandingAlumni | null>(null);

// 提示语自动消失
watch(notice, (n, _prev, onCleanup) => {
  if (!n) return;
  const t = window.setTimeout(() => (notice.value = ''), 2200);
  onCleanup(() => window.clearTimeout(t));
});

// 院系筛选选项：取数据中实际出现的学院
const collegeOptions = computed(() => [...new Set(rows.value.map((a) => a.college))]);

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  const name = nameKw.value.trim();
  const sidKw = sid.value.trim();
  return rows.value.filter((a) => {
    if (name && !a.name.includes(name)) return false;
    if (college.value && a.college !== college.value) return false;
    if (sidKw && !a.studentId.includes(sidKw)) return false;
    if (!kw) return true;
    const hay = `${a.name}${a.studentId}${a.college}${a.current}${a.awards.map((w) => w.title).join('')}`.toLowerCase();
    return hay.includes(kw);
  });
});

// 筛选面板生效条件数（毕业年份已通过 chips 展示，不计入角标）
const activeCount = computed(() => (nameKw.value.trim() ? 1 : 0) + (college.value ? 1 : 0) + (sid.value.trim() ? 1 : 0));

// 重置筛选面板全部条件（搜索框内容保留）
const resetFilter = () => {
  nameKw.value = '';
  college.value = '';
  sid.value = '';
};

const shown = computed(() => filtered.value.slice(0, PAGE_SIZE));

// 打开数字校友档案：接口数据先调详情补充弹窗，失败用列表行数据兜底
const openProfile = async (row: OutstandingAlumni) => {
  if (loadState.value !== 'api') {
    profile.value = row;
    return;
  }
  try {
    const d = await api.alumniPublic.directoryDetail(row.studentId);
    profile.value = mergeDetail(d, row);
  } catch (e) {
    console.warn(`[alumni-index] 档案详情接口请求失败（studentId=${row.studentId}），使用列表数据兜底：`, e);
    profile.value = row;
  }
};

// 筛选面板展开/收起动画（替代 AnimatePresence 的 height: 0 ↔ auto，时长 0.28s easeOut）
const asEl = (el: Element) => el as HTMLElement;
const onPanelBeforeEnter = (el: Element) => {
  const e = asEl(el);
  e.style.height = '0px';
  e.style.opacity = '0';
};
const onPanelEnter = (el: Element, done: () => void) => {
  const e = asEl(el);
  e.style.transition = 'height 0.28s ease-out, opacity 0.28s ease-out';
  requestAnimationFrame(() => {
    e.style.height = `${e.scrollHeight}px`;
    e.style.opacity = '1';
  });
  window.setTimeout(done, 280);
};
const onPanelAfterEnter = (el: Element) => {
  asEl(el).style.cssText = ''; // 还原为 auto 高度，内容变化时自适应
};
const onPanelBeforeLeave = (el: Element) => {
  const e = asEl(el);
  e.style.height = `${e.scrollHeight}px`;
  e.style.opacity = '1';
};
const onPanelLeave = (el: Element, done: () => void) => {
  const e = asEl(el);
  void e.offsetHeight; // 强制回流，确保收起动画生效
  e.style.transition = 'height 0.28s ease-out, opacity 0.28s ease-out';
  e.style.height = '0px';
  e.style.opacity = '0';
  window.setTimeout(done, 280);
};
</script>

<template>
  <section id="alumni-index" class="mt-16 scroll-mt-24">
    <div class="relative overflow-hidden bg-warm py-14">
      <!-- 背景微光，与其它模块保持同一套点缀手法 -->
      <div class="pointer-events-none absolute left-1/2 top-0 h-40 w-[60%] -translate-x-1/2 rounded-full bg-lamp/10 blur-3xl" />

      <ModuleTitle
        eyebrow="ALUMNI ARCHIVES"
        title="校友档案索引"
        subtitle="检索姓名，找到你熟悉的身影"
        ornament
        glow
        class="mb-8"
      />

      <!-- 索引板块：与上方模块同宽（随屏收敛），卡片样式同留言墙 -->
      <Reveal :delay="0.08" class="relative mx-auto w-[92%] max-w-[1500px] md:w-[86%] xl:w-[80%]">
        <div class="rounded-[15px] border border-lamp/25 bg-white/55 p-5 md:p-6">
          <!-- 头部：查看全部入口（已对接全量列表，滚动到下方档案全表区块） -->
          <!-- 搜索 + 筛选 -->
          <div class="mt-3 flex gap-3">
            <label class="flex flex-1 items-center gap-2.5 rounded-xl border border-ink/10 bg-white px-4 py-2.5 transition focus-within:border-tech/50">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="shrink-0 text-slate-400" aria-hidden="true">
                <circle cx="11" cy="11" r="8" />
                <path d="m21 21-4.3-4.3" />
              </svg>
              <input
                v-model="keyword"
                placeholder="搜索姓名、学号或关键词"
                class="w-full bg-transparent text-sm text-ink outline-none placeholder:text-slate-400"
              />
              <button
                v-if="keyword"
                type="button"
                aria-label="清空搜索"
                class="shrink-0 text-slate-400 transition hover:text-ink"
                @click="keyword = ''"
              >
                ✕
              </button>
            </label>
            <button
              type="button"
              :aria-expanded="filterOpen"
              :class="`relative flex shrink-0 items-center gap-1.5 rounded-xl border px-4 py-2.5 text-[13px] font-semibold transition ${
                filterOpen
                  ? 'border-night bg-night text-warm'
                  : 'border-ink/10 bg-white text-slate-600 hover:border-ink/30 hover:text-ink'
              }`"
              @click="filterOpen = !filterOpen"
            >
              筛选
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <line x1="4" x2="20" y1="6" y2="6" />
                <line x1="7" x2="17" y1="12" y2="12" />
                <line x1="10" x2="14" y1="18" y2="18" />
              </svg>
              <!-- 生效条件角标 -->
              <span
                v-if="activeCount > 0"
                class="absolute -right-1.5 -top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-tech px-1 font-code text-[10px] font-bold text-white"
              >
                {{ activeCount }}
              </span>
            </button>
          </div>

          <!-- 筛选条件面板：毕业年份 / 院系 / 学号（即时生效） -->
          <Transition
            :css="false"
            @before-enter="onPanelBeforeEnter"
            @enter="onPanelEnter"
            @after-enter="onPanelAfterEnter"
            @before-leave="onPanelBeforeLeave"
            @leave="onPanelLeave"
          >
            <div v-if="filterOpen" class="overflow-hidden">
              <div class="mt-4 rounded-xl border border-ink/10 bg-[#FBF8F1] p-4">
                <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
                  <label class="block">
                    <span class="mb-1.5 block text-[11px] font-semibold tracking-wider text-slate-500">姓名</span>
                    <input
                      :value="nameKw"
                      placeholder="输入姓名查找"
                      class="w-full rounded-lg border border-ink/10 bg-white px-3 py-2 text-[13px] text-ink outline-none transition focus:border-tech/50"
                      @input="nameKw = ($event.target as HTMLInputElement).value.slice(0, 10)"
                    />
                  </label>
                  <label class="block">
                    <span class="mb-1.5 block text-[11px] font-semibold tracking-wider text-slate-500">院系</span>
                    <select
                      v-model="college"
                      class="w-full rounded-lg border border-ink/10 bg-white px-3 py-2 text-[13px] text-ink outline-none transition focus:border-tech/50"
                    >
                      <option value="">全部学院</option>
                      <option v-for="c in collegeOptions" :key="c" :value="c">{{ c }}</option>
                    </select>
                  </label>
                  <label class="block">
                    <span class="mb-1.5 block text-[11px] font-semibold tracking-wider text-slate-500">学号</span>
                    <input
                      :value="sid"
                      inputmode="numeric"
                      placeholder="输入学号查找"
                      class="w-full rounded-lg border border-ink/10 bg-white px-3 py-2 font-code text-[13px] text-ink outline-none transition focus:border-tech/50"
                      @input="sid = ($event.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 9)"
                    />
                  </label>
                </div>
                <div class="mt-3 flex items-center justify-between gap-3">
                  <p class="text-[11px] text-slate-400">条件即时生效，与搜索框叠加</p>
                  <button
                    type="button"
                    :disabled="!nameKw && !college && !sid"
                    class="shrink-0 rounded-lg border border-ink/10 bg-white px-3 py-1.5 text-[12px] font-semibold text-slate-600 transition hover:border-ink/30 hover:text-ink disabled:cursor-not-allowed disabled:opacity-40"
                    @click="resetFilter"
                  >
                    重置条件
                  </button>
                </div>
              </div>
            </div>
          </Transition>

          <!-- 档案列表 -->
          <div class="mt-5 overflow-hidden rounded-xl border border-ink/8 bg-white">
            <!-- 表头 -->
            <div class="grid grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_86px] items-center gap-3 border-b border-ink/8 bg-[#FBF8F1] px-4 py-2.5 text-[11px] tracking-wider text-slate-400 md:grid-cols-[1.2fr_1.1fr_1.2fr_1.5fr_86px]">
              <span>校友</span>
              <span>院系 · 届别</span>
              <span class="hidden md:block">当前身份</span>
              <span class="hidden md:block">代表荣誉</span>
              <span />
            </div>

            <p v-if="loadState === 'loading'" class="px-4 py-10 text-center text-sm text-slate-400">档案加载中…</p>
            <div v-else-if="loadState === 'error'" class="flex flex-col items-center gap-3 px-4 py-10 text-center">
              <p class="text-sm font-semibold text-ink">校友档案加载失败</p>
              <p class="max-w-md text-[12px] text-slate-500">我们暂时无法获取校友档案数据，请稍后重试。</p>
              <button
                type="button"
                class="rounded-lg border border-lamp/40 bg-white px-4 py-1.5 text-[12px] font-semibold text-ink transition hover:border-lamp hover:bg-lamp/10"
                @click="reload"
              >
                重新加载
              </button>
            </div>
            <p v-else-if="shown.length === 0" class="px-4 py-10 text-center text-sm text-slate-400">
              未找到匹配的校友档案，换个关键词试试
            </p>
            <!-- 行级联入场（二级动画）：筛选变化时重播，给出即时反馈 -->
            <RevealGroup v-else :amount="0.05">
              <RevealItem
                v-for="(a, i) in shown"
                :key="a.studentId || a.name"
                class="grid grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_86px] items-center gap-3 border-b border-ink/5 px-4 py-3.5 transition last:border-b-0 hover:bg-lamp/5 md:grid-cols-[1.2fr_1.1fr_1.2fr_1.5fr_86px]"
              >
                <!-- 校友：姓氏头像 + 姓名 -->
                <div class="flex min-w-0 items-center gap-2.5">
                  <span
                    class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full font-title text-[13px] font-bold text-white"
                    :style="{ background: AVATAR_COLORS[i % AVATAR_COLORS.length] }"
                  >
                    {{ (a.name || '·').charAt(0) }}
                  </span>
                  <span
                    class="truncate text-[13.5px] font-semibold"
                    :class="isDirtyName(a.name) ? 'text-slate-400' : 'text-ink'"
                    :title="isDirtyName(a.name) ? '姓名字段为占位值，资料待完善' : undefined"
                  >
                    {{ isDirtyName(a.name) ? '（姓名待完善）' : a.name }}
                  </span>
                </div>

                <!-- 院系 · 届别 · 学号（学号脱敏显示：公开页面不暴露完整学号） -->
                <div class="min-w-0 leading-tight">
                  <p
                    class="truncate text-[12px]"
                    :class="isDirtyCollege(a.college) ? 'text-slate-400' : 'text-slate-600'"
                    :title="isDirtyCollege(a.college) ? '学院字段为占位值，资料待完善' : undefined"
                  >
                    {{ isDirtyCollege(a.college) ? '（学院待完善）' : a.college }}
                  </p>
                  <p
                    v-if="a.studentId"
                    class="mt-0.5 truncate font-code text-[10px] text-slate-400"
                    :title="`公开仅展示脱敏学号，原值仅本人可见`"
                  >
                    {{ a.gradYear }} · {{ maskStudentId(a.studentId) }}
                  </p>
                  <p v-else class="mt-0.5 truncate font-code text-[10px] text-slate-400">{{ a.gradYear }}</p>
                </div>

                <!-- 当前身份 -->
                <p class="hidden truncate text-[12.5px] text-slate-600 md:block">{{ a.current }}</p>

                <!-- 代表荣誉 -->
                <p class="hidden truncate text-[12.5px] text-slate-600 md:block">{{ a.awards[0]?.title ?? '—' }}</p>

                <!-- 查看更多（原"档案状态"列位置）：打开数字校友档案 -->
                <button
                  type="button"
                  :aria-label="`查看 ${a.name} 的数字校友档案`"
                  class="group/more relative flex items-center justify-end gap-1 whitespace-nowrap pb-0.5 text-[12px] font-semibold text-ink/50 transition-colors duration-300 hover:text-ink"
                  @click="openProfile(a)"
                >
                  查看更多
                  <span class="text-[#a97b00]/60 transition-all duration-300 group-hover/more:translate-x-0.5 group-hover/more:text-[#a97b00]">→</span>
                  <!-- 悬停时自左侧生长的金色细线 -->
                  <span
                    aria-hidden="true"
                    class="absolute bottom-0 left-0 h-px w-0 bg-gradient-to-r from-[#a97b00]/80 to-transparent transition-all duration-300 group-hover/more:w-full"
                  />
                </button>
              </RevealItem>
            </RevealGroup>
          </div>

          <p class="mt-4 text-center text-xs tracking-widest text-slate-400">
            共收录 {{ filtered.length }} 份校友档案
            <template v-if="filtered.length > PAGE_SIZE"> · 当前展示前 {{ PAGE_SIZE }} 条，可用搜索与筛选查找</template>
          </p>
        </div>
      </Reveal>
    </div>

    <AlumniProfileModal :alumni="profile" @close="profile = null" />

    <!-- 未实现功能的统一提示（2.2s 自动消失） -->
    <div
      v-if="notice"
      role="status"
      class="fixed left-1/2 top-[84px] z-[60] -translate-x-1/2 rounded-full bg-ink/90 px-5 py-2.5 text-sm text-white shadow-xl"
    >
      {{ notice }}
    </div>
  </section>
</template>
