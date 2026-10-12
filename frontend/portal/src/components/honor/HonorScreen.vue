<script setup lang="ts">
/**
 * 校友荣誉大屏 · 桌面端（路由 /dashboard · Vue 3 Composition API）
 *
 * 适配自 [原荣誉大屏.html] 的视觉与交互：
 * - 1920×1080 基准等比缩放
 * - Header / 三栏主体 / Footer
 * - 4 张 KPI（数字 easeOutCubic 滚动） + 2 张 ECharts（荣誉类型饼图 / 行业条形图）
 * - 荣誉榜单（垂直无限滚动）+ 校友荣誉长廊（双排对向连续轮播）
 * - 校友风采（带 dots 的轮播切换）
 * - 时钟 + 60 秒自动刷新 + 手动刷新
 *
 * 数据来源：portal api.dashboardHonor() → GET /api/v1/dashboard-honor
 * 该接口在后端 honors 模块的 HonorScreenController 实现，
 * 仅聚合 approved 状态的荣誉（与公开门户口径一致）。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import * as echarts from 'echarts/core';
import { PieChart, BarChart } from 'echarts/charts';
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { api, type DashboardHonorData } from '../../api';

// 按需注册 ECharts 模块（减小包体积；与 portal vite.config 的 manualChunks 配合）
echarts.use([
  PieChart,
  BarChart,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  CanvasRenderer
]);

/* ================ 响应式数据 ================ */

const router = useRouter();
/**
 * 返回首页
 * 大屏常作为独立路由 / 全屏入口被打开，window.history 经常为空；
 * 这里给一个明确 fallback，确保始终能离开大屏。
 */
function goHome() {
  if (window.history.length > 1) {
    router.back();
  } else {
    router.replace('/');
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    e.preventDefault();
    goHome();
  }
}

const loading = ref(true);
const loadError = ref<string | null>(null);
const autoPlay = ref(true);

// 用 reactive + Partial 让初值可空
const data = reactive<DashboardHonorData>({
  stats: { total_alumni: 0, total_awards: 0, college_count: 0, grad_year_count: 0 },
  honor_by_type: [],
  industry_top: [],
  province_dist: [],
  honors: [],
  alumni: [],
  screen_config: {
    name: 'default',
    layout: 'grid-3',
    themeColor: 'tech',
    refreshSec: 30,
    showFields: { title: true, level: true, category: true, granted_by: true, awarded_at: true, owner: false },
    sortBy: 'awarded_at_desc',
    maxItems: 50
  },
  updated_at: ''
});

/**
 * 主题预设映射（与 admin 后台大屏配置的 themeColor 保持一致）。
 * 这里改主色（金色 / 深邃黑 / 暖阳 / 科技蓝青），覆盖大屏色系。
 */
interface ThemePalette {
  bg: string;          // 整屏背景
  panel: string;       // 面板底色
  panelBd: string;     // 面板边框
  gold: string;        // 主色（金色）
  goldLight: string;   // 主色文字
  blue: string;        // 副色
  red: string;         // 点缀色
  text: string;        // 主文字
  textDim: string;     // 次文字
  serif: string;       // 字体
  numFont: string;     // 数字字体
  pieColors: string[]; // 饼图色板
  barGradFrom: string; // 条形图渐变起
  barGradTo: string;   // 条形图渐变止
  barBg: string;       // 条形图背景
}
const THEME_PALETTES: Record<string, ThemePalette> = {
  tech: {
    bg: '#faf6f0', panel: 'rgba(255,255,255,0.6)', panelBd: 'rgba(122,74,43,0.22)',
    gold: '#b8860b', goldLight: '#8a6508', blue: '#4a6b4a', red: '#8b3a3a',
    text: '#33302b', textDim: '#7a756d',
    serif: "'Noto Serif SC', 'Source Han Serif SC', 'STZhongsong', 'STSong', 'SimSun', serif",
    numFont: "'Georgia', 'Times New Roman', serif",
    pieColors: ['#7A4A2B', '#B8860B', '#4A6B4A', '#C08552'],
    barGradFrom: 'rgba(122,74,43,.2)', barGradTo: '#7A4A2B', barBg: 'rgba(122,74,43,.06)'
  },
  gold: {
    bg: '#fff8e8', panel: 'rgba(255,255,255,0.7)', panelBd: 'rgba(217,119,6,0.30)',
    gold: '#d97706', goldLight: '#92400e', blue: '#a16207', red: '#b91c1c',
    text: '#3f2a0a', textDim: '#a16207',
    serif: "'Noto Serif SC', 'Source Han Serif SC', 'STZhongsong', serif",
    numFont: "'Georgia', serif",
    pieColors: ['#92400e', '#d97706', '#f59e0b', '#fbbf24'],
    barGradFrom: 'rgba(217,119,6,.2)', barGradTo: '#d97706', barBg: 'rgba(217,119,6,.08)'
  },
  dark: {
    bg: '#0a1428', panel: 'rgba(255,255,255,0.05)', panelBd: 'rgba(96,165,250,0.30)',
    gold: '#60a5fa', goldLight: '#93c5fd', blue: '#22d3ee', red: '#fb7185',
    text: '#e2e8f0', textDim: '#94a3b8',
    serif: "'Noto Serif SC', 'Source Han Serif SC', serif",
    numFont: "'Georgia', serif",
    pieColors: ['#60a5fa', '#22d3ee', '#a78bfa', '#fb7185'],
    barGradFrom: 'rgba(96,165,250,.2)', barGradTo: '#60a5fa', barBg: 'rgba(96,165,250,.08)'
  },
  warm: {
    bg: '#fff1e6', panel: 'rgba(255,255,255,0.7)', panelBd: 'rgba(249,115,22,0.28)',
    gold: '#f97316', goldLight: '#c2410c', blue: '#dc2626', red: '#dc2626',
    text: '#3f1e0a', textDim: '#9a3412',
    serif: "'Noto Serif SC', 'Source Han Serif SC', serif",
    numFont: "'Georgia', serif",
    pieColors: ['#f97316', '#ef4444', '#fb923c', '#fbbf24'],
    barGradFrom: 'rgba(249,115,22,.2)', barGradTo: '#f97316', barBg: 'rgba(249,115,22,.08)'
  }
};
/** 当前主题（默认 tech）；响应式切换：写入 CSS 变量后整个大屏跟着变 */
const theme = computed<ThemePalette>(() => THEME_PALETTES[data.screen_config.themeColor] || THEME_PALETTES.tech);
const themeStyle = computed(() => ({
  '--bg': theme.value.bg,
  '--panel-bg': theme.value.panel,
  '--panel-bd': theme.value.panelBd,
  '--gold': theme.value.gold,
  '--gold-light': theme.value.goldLight,
  '--blue': theme.value.blue,
  '--red': theme.value.red,
  '--text': theme.value.text,
  '--text-dim': theme.value.textDim,
  '--serif': theme.value.serif,
  '--num-font': theme.value.numFont
}));

/* ================ 时钟 ================ */

const now = ref(new Date());
const weekMap = ['日', '一', '二', '三', '四', '五', '六'];
const clockStr = computed(() => {
  const d = now.value;
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
});
const todayStr = computed(() => {
  const d = now.value;
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 · 星期${weekMap[d.getDay()]}`;
});

const fmtTime = (iso: string): string => {
  if (!iso) return '—';
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
};

/* ================ 大屏缩放（按 layout 选基准分辨率） ================ */

/** layout 对应基准分辨率（与 admin 大屏配置保持一致） */
const LAYOUT_RESOLUTION: Record<string, { w: number; h: number }> = {
  'grid-3': { w: 1920, h: 1080 },
  'grid-4': { w: 1920, h: 1080 },
  'waterfall': { w: 1600, h: 1000 }
};
const scale = ref(1);
const screenStyle = computed(() => ({
  transform: `translate(-50%,-50%) scale(${scale.value})`
}));
function fit() {
  const r = LAYOUT_RESOLUTION[data.screen_config.layout] || LAYOUT_RESOLUTION['grid-3'];
  scale.value = Math.min(window.innerWidth / r.w, window.innerHeight / r.h);
}

/* ================ KPI + 数字滚动 ================ */

interface StatCard {
  key: 'alumni' | 'awards' | 'industry' | 'province';
  label: string;
  unit: string;
  color: string;
  target: number;
  display: number;
}

const statCards = reactive<StatCard[]>([
  { key: 'alumni', label: '校友总人数', unit: '人', color: '#7A4A2B', target: 0, display: 0 },
  { key: 'awards', label: '累计获奖人次', unit: '项', color: '#B8860B', target: 0, display: 0 },
  { key: 'industry', label: '覆盖学院', unit: '个', color: '#4A6B4A', target: 0, display: 0 },
  { key: 'province', label: '覆盖届别', unit: '个', color: '#8B3A3A', target: 0, display: 0 }
]);
const fmt = (n: number) => Math.round(n).toLocaleString('zh-CN');
const rafMap = new Map<string, number>();
function countUp(card: StatCard) {
  const prev = rafMap.get(card.key);
  if (prev) cancelAnimationFrame(prev);
  const from = card.display;
  const to = card.target;
  const start = performance.now();
  const dur = 1600;
  const step = (now: number) => {
    const p = Math.min((now - start) / dur, 1);
    const eased = 1 - Math.pow(1 - p, 3); // easeOutCubic
    card.display = from + (to - from) * eased;
    if (p < 1) rafMap.set(card.key, requestAnimationFrame(step));
  };
  rafMap.set(card.key, requestAnimationFrame(step));
}
function applyStats() {
  const map: Record<StatCard['key'], keyof DashboardHonorData['stats']> = {
    alumni: 'total_alumni',
    awards: 'total_awards',
    industry: 'college_count',
    province: 'grad_year_count'
  };
  statCards.forEach((c) => {
    c.target = Number(data.stats[map[c.key]] || 0);
    countUp(c);
  });
}

/* ================ 荣誉榜单（无限滚动） ================ */

const LEVEL_ORDER: Record<string, number> = {
  国际级: 0,
  国家级: 1,
  省部级: 2,
  市级: 3,
  校级: 4
};
const filteredHonors = computed(() => {
  // 按 screen_config.sortBy 排序；maxItems 截断
  const arr = data.honors.slice();
  const sb = data.screen_config.sortBy;
  if (sb === 'awarded_at_desc') {
    arr.sort((a, b) => (b.year ?? 0) - (a.year ?? 0));
  } else if (sb === 'level_asc') {
    arr.sort((a, b) => (LEVEL_ORDER[a.level] ?? 99) - (LEVEL_ORDER[b.level] ?? 99));
  } else if (sb === 'pinned_first') {
    // 当前数据未含 isPinned，按级别+年份组合排序作为近似
    arr.sort((a, b) => (LEVEL_ORDER[a.level] ?? 99) - (LEVEL_ORDER[b.level] ?? 99) || (b.year ?? 0) - (a.year ?? 0));
  }
  return arr.slice(0, data.screen_config.maxItems);
});
// 复制一份用于无缝循环
const loopHonors = computed(() =>
  filteredHonors.value.length > 4 ? filteredHonors.value.concat(filteredHonors.value) : filteredHonors.value
);

/* ================ 校友荣誉长廊（双排对向连续轮播） ================ */

const lvColor: Record<string, string> = {
  国际级: '#8B3A3A',
  国家级: '#B8860B',
  省部级: '#4A6B4A',
  市级: '#946B4A',
  校级: '#7A756D'
};
const galleryAlumni = computed(() =>
  data.alumni.map((a) => ({
    ...a,
    honors: data.honors.filter((h) => h.alumnusName === a.name).slice(0, 3)
  }))
);
const galleryRows = computed(() => {
  const rows: typeof galleryAlumni.value[] = [[], []];
  galleryAlumni.value.forEach((a, i) => rows[i % 2].push(a));
  return rows;
});
function onPhotoError(a: { photo: string }) {
  a.photo = '';
}

/* ================ 校友风采轮播 ================ */

const alumniIdx = ref(0);
const curAlumni = computed(() => data.alumni[alumniIdx.value] || {
  id: 'placeholder',
  name: '—',
  photo: '',
  graduationYear: '',
  college: '',
  industry: '',
  intro: '',
  highlight: '',
  awardCount: 0,
  colorA: '#333',
  colorB: '#666'
});
function jumpAlumni(i: number) {
  alumniIdx.value = i;
  resetAlumniTimer();
}

/* ================ 图表：饼图（荣誉类型）+ 条形图（行业 Top 8） ================ */

let pieChart: echarts.ECharts | null = null;
let barChart: echarts.ECharts | null = null;

function renderPie() {
  const t = theme.value;
  pieChart?.setOption({
    color: t.pieColors,
    tooltip: {
      backgroundColor: 'rgba(255,253,248,.97)',
      borderColor: t.panelBd,
      textStyle: { color: t.text },
      borderWidth: 1
    },
    legend: {
      bottom: 0,
      textStyle: { color: t.textDim, fontSize: 13, fontFamily: 'Microsoft YaHei' },
      itemWidth: 12,
      itemHeight: 8,
      icon: 'roundRect'
    },
    series: [
      {
        type: 'pie',
        radius: ['44%', '68%'],
        center: ['50%', '44%'],
        itemStyle: { borderColor: t.bg, borderWidth: 2 },
        label: { color: t.text, fontSize: 13, fontFamily: 'Microsoft YaHei', formatter: '{b}  {d}%' },
        labelLine: { lineStyle: { color: t.panelBd }, length: 10, length2: 12 },
        data: data.honor_by_type.map((x) => ({ name: x.name, value: x.value })),
        animationType: 'scale',
        animationEasing: 'cubicOut',
        animationDuration: 1200
      }
    ]
  });
}

function renderBar() {
  const t = theme.value;
  const top = data.industry_top.slice(0, 8).slice().reverse();
  barChart?.setOption({
    tooltip: {
      backgroundColor: 'rgba(255,253,248,.97)',
      borderColor: t.panelBd,
      textStyle: { color: t.text },
      borderWidth: 1
    },
    grid: { left: 86, right: 56, top: 14, bottom: 10 },
    xAxis: { type: 'value', show: false },
    yAxis: {
      type: 'category',
      data: top.map((x) => x.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: t.text, fontSize: 14, fontFamily: 'Microsoft YaHei' }
    },
    series: [
      {
        type: 'bar',
        data: top.map((x) => x.value),
        barWidth: 10,
        itemStyle: {
          borderRadius: [0, 2, 2, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: t.barGradFrom },
            { offset: 1, color: t.barGradTo }
          ])
        },
        showBackground: true,
        backgroundStyle: { color: t.barBg, borderRadius: [0, 2, 2, 0] },
        label: { show: true, position: 'right', color: t.gold, fontFamily: 'Georgia', fontSize: 14 },
        animationDelay: (i: number) => i * 90,
        animationDuration: 1000,
        animationEasing: 'cubicOut'
      }
    ]
  });
}

/* ================ 拉数据 + 定时器 ================ */

let clockTimer: number | null = null;
let refreshTimer: number | null = null;
let alumniTimer: number | null = null;

/** 按当前 screen_config.refreshSec 重建定时器（0 = 关闭自动刷新） */
function restartAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }
  const sec = data.screen_config.refreshSec;
  if (sec > 0) {
    refreshTimer = window.setInterval(() => loadData(), sec * 1000);
  }
}

async function loadData() {
  loading.value = true;
  loadError.value = null;
  try {
    const d = await api.dashboardHonor();
    // 增量合并：避免把 screen_config 整个覆盖为空（防御性）
    const next = JSON.parse(JSON.stringify(d));
    if (!next.screen_config) {
      next.screen_config = data.screen_config;
    }
    Object.assign(data, next);
    applyStats();
    await nextTick();
    renderPie();
    renderBar();
    // 配置刷新间隔变更 → 重置定时器
    restartAutoRefresh();
  } catch (e: any) {
    loadError.value = e?.message || '荣誉大屏数据加载失败';
    // 即使失败也保留之前的渲染（避免空白屏）
  } finally {
    loading.value = false;
  }
}
function manualRefresh() {
  loadData();
}
function togglePlay() {
  autoPlay.value = !autoPlay.value;
  resetAlumniTimer();
}
function resetAlumniTimer() {
  if (alumniTimer) {
    clearInterval(alumniTimer);
    alumniTimer = null;
  }
  if (!autoPlay.value || !data.alumni.length) return;
  alumniTimer = window.setInterval(() => {
    alumniIdx.value = (alumniIdx.value + 1) % data.alumni.length;
  }, 6000);
}

/* ================ 生命周期 ================ */

onMounted(async () => {
  fit();
  window.addEventListener('resize', fit);
  window.addEventListener('keydown', handleKeydown);
  clockTimer = window.setInterval(() => (now.value = new Date()), 1000);

  await loadData();
  await nextTick();
  pieChart = echarts.init(document.getElementById('pieChart') as HTMLElement);
  barChart = echarts.init(document.getElementById('barChart') as HTMLElement);
  renderPie();
  renderBar();

  resetAlumniTimer();
  // 自动刷新间隔由 screen_config.refreshSec 控制（admin 后台可调）
  restartAutoRefresh();
});

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer);
  if (refreshTimer) clearInterval(refreshTimer);
  if (alumniTimer) clearInterval(alumniTimer);
  rafMap.forEach((id) => cancelAnimationFrame(id));
  rafMap.clear();
  window.removeEventListener('resize', fit);
  window.removeEventListener('keydown', handleKeydown);
  pieChart?.dispose();
  barChart?.dispose();
});
</script>

<template>
  <div class="honor-screen-root" :style="themeStyle">
    <div class="screen" :style="screenStyle">
      <!-- ═══ Header ═══ -->
      <header class="hd">
        <div class="hd-side">
          <button class="hd-back" type="button" title="返回首页（ESC）" @click="goHome">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <line x1="19" y1="12" x2="5" y2="12"></line>
              <polyline points="12 19 5 12 12 5"></polyline>
            </svg>
            <span>返回首页</span>
          </button>
          <div class="hd-date">{{ todayStr }}</div>
        </div>
        <div class="hd-title">
          <h1>校友<em>荣誉</em>大屏</h1>
          <div class="sub"><i>弘毅致远 · 桃李芬芳</i></div>
        </div>
        <div class="hd-side right">
          <span class="clock">{{ clockStr }}</span>
        </div>
      </header>

      <!-- ═══ 主体 ═══ -->
      <main class="main">
        <!-- ── 左栏 ── -->
        <div class="col side">
          <section class="panel" :style="{ flex: 1.35, animationDelay: '.1s' }">
            <div class="panel-hd">
              <span class="hd-seal"></span>
              <h3>校友荣誉榜单</h3>
              <span class="en">HONOR ROLL</span>
            </div>
            <div class="panel-bd">
              <div class="honor-scroll">
                <div
                  class="honor-list"
                  :style="{ '--dur': Math.max(8, filteredHonors.length * 2.4) + 's' }"
                >
                  <div class="honor-item" v-for="(h, i) in loopHonors" :key="i">
                    <span class="rank" v-if="i < filteredHonors.length">{{ (i % filteredHonors.length) + 1 }}</span>
                    <span class="rank" v-else>&nbsp;</span>
                    <div class="info">
                      <div class="t"><b>{{ h.alumnusName }}</b> · {{ h.title }}</div>
                      <div class="s">{{ h.year || '—' }} 年获评</div>
                    </div>
                    <span class="lv-badge" :class="'lv-' + (h.levelKey || h.level)">{{ h.level }}</span>
                  </div>
                  <div v-if="!filteredHonors.length" class="empty-tip">暂无荣誉记录</div>
                </div>
              </div>
            </div>
          </section>

          <section class="panel" :style="{ flex: 1, animationDelay: '.2s' }">
            <div class="panel-hd">
              <span class="hd-seal"></span>
              <h3>荣誉类型分布</h3>
              <span class="en">DISTRIBUTION</span>
            </div>
            <div class="panel-bd"><div id="pieChart" class="chart-fill"></div></div>
          </section>
        </div>

        <!-- ── 中央 ── -->
        <div class="col center">
          <div class="stats-row">
            <div
              class="stat-card"
              v-for="(s, i) in statCards"
              :key="s.key"
              :style="{ animationDelay: 0.1 + i * 0.08 + 's', '--c': s.color }"
            >
              <div class="num">{{ fmt(s.display) }}<small v-if="s.unit">{{ s.unit }}</small></div>
              <div class="label">{{ s.label }}</div>
            </div>
          </div>

          <section class="panel" :style="{ flex: 1.9, animationDelay: '.25s' }">
            <div class="panel-hd">
              <span class="hd-seal"></span>
              <h3>校友荣誉长廊</h3>
              <span class="en">HONOR GALLERY</span>
            </div>
            <div class="panel-bd">
              <div class="gallery-scroll">
                <div
                  class="g-row"
                  v-for="(row, r) in galleryRows"
                  :key="r"
                  :class="{ reverse: r === 1 }"
                >
                  <div class="g-track" :style="{ '--dur': Math.max(20, row.length * 7) + 's' }">
                    <div class="g-card" v-for="(a, i) in row.concat(row)" :key="i">
                      <div class="g-top">
                        <div
                          class="g-photo"
                          :style="a.photo ? {} : { background: 'linear-gradient(135deg,' + a.colorA + ',' + a.colorB + ')' }"
                        >
                          <img v-if="a.photo" :src="a.photo" :alt="a.name" @error="onPhotoError(a)" />
                          <span v-else>{{ a.name.charAt(0) }}</span>
                        </div>
                        <div class="g-info">
                          <div class="g-name">{{ a.name }}</div>
                          <div class="g-meta">{{ a.graduationYear }} 届 · {{ a.college || '—' }}</div>
                          <div class="g-quote">{{ a.quote || a.intro || '—' }}</div>
                        </div>
                      </div>
                      <div class="g-honors">
                        <div class="g-honor" v-for="h in a.honors" :key="h.title + h.year">
                          <i :style="{ background: lvColor[h.level] || '#B8860B' }"></i>
                          <span>{{ h.title }}<template v-if="h.year"> · {{ h.year }}</template></span>
                        </div>
                        <div class="g-honor" v-if="!a.honors.length && a.highlight">
                          <i style="background:#B8860B"></i>{{ a.highlight }}
                        </div>
                      </div>
                      <div class="g-motto"><b>◆ 座右铭</b>{{ a.motto || '—' }}</div>
                    </div>
                  </div>
                </div>
                <div v-if="!galleryAlumni.length" class="empty-tip">暂无校友荣誉</div>
              </div>
            </div>
          </section>
        </div>

        <!-- ── 右栏 ── -->
        <div class="col side">
          <section class="panel" :style="{ flex: 1.35, animationDelay: '.35s' }">
            <div class="panel-hd">
              <span class="hd-seal"></span>
              <h3>校友风采</h3>
              <span class="en">ALUMNI PROFILES</span>
            </div>
            <div class="panel-bd">
              <transition name="fade" mode="out-in">
                <div class="alumni-stage" :key="curAlumni.id">
                  <div class="alumni-card">
                    <div
                      class="avatar"
                      :style="curAlumni.photo ? {} : { background: 'linear-gradient(135deg,' + curAlumni.colorA + ',' + curAlumni.colorB + ')' }"
                    >
                      <img v-if="curAlumni.photo" :src="curAlumni.photo" :alt="curAlumni.name" @error="onPhotoError(curAlumni as any)" />
                      <span v-else>{{ curAlumni.name.charAt(0) }}</span>
                    </div>
                    <div class="name">{{ curAlumni.name }}</div>
                    <div class="meta">
                      {{ curAlumni.graduationYear }} 届 ·
                      {{ curAlumni.college || '—' }}{{ curAlumni.industry ? ' · ' + curAlumni.industry : '' }}
                    </div>
                    <div class="intro">{{ curAlumni.intro || '—' }}</div>
                    <div class="hl">{{ curAlumni.highlight }} · 累计获奖 {{ curAlumni.awardCount }} 项</div>
                  </div>
                  <div class="dots">
                    <i
                      v-for="(a, i) in data.alumni"
                      :key="i"
                      :class="{ on: i === alumniIdx }"
                      @click="jumpAlumni(i)"
                    ></i>
                  </div>
                </div>
              </transition>
            </div>
          </section>

          <section class="panel" :style="{ flex: 1, animationDelay: '.45s' }">
            <div class="panel-hd">
              <span class="hd-seal"></span>
              <h3>学院分布</h3>
              <span class="en">COLLEGES · TOP 8</span>
            </div>
            <div class="panel-bd"><div id="barChart" class="chart-fill"></div></div>
          </section>
        </div>
      </main>

      <!-- ═══ Footer ═══ -->
      <footer class="ft">
        <button class="btn" @click="togglePlay">{{ autoPlay ? '暂停轮播' : '开启轮播' }}</button>
        <button class="btn" @click="manualRefresh">刷新数据</button>
        <span>数据更新于 {{ fmtTime(data.updated_at) }}</span>
        <span v-if="loadError" class="ft-err">⚠ {{ loadError }}</span>
        <div class="spacer"></div>
        <span class="ft-cfg">配置「{{ data.screen_config.name }}」· {{ data.screen_config.layout }} · 主题 {{ data.screen_config.themeColor }} · {{ data.screen_config.refreshSec === 0 ? '手动刷新' : '每 ' + data.screen_config.refreshSec + ' 秒更新' }}</span>
      </footer>
    </div>
  </div>
</template>

<style scoped>
/* ===== 颜色变量与字体（与原 HTML 同款"年鉴风"暖色调） ===== */
.honor-screen-root {
  --bg: #faf6f0;
  --panel-bg: rgba(255, 255, 255, 0.6);
  --panel-bd: rgba(122, 74, 43, 0.22);
  --gold: #b8860b;
  --gold-light: #8a6508;
  --blue: #4a6b4a;
  --red: #8b3a3a;
  --text: #33302b;
  --text-dim: #7a756d;
  --serif: 'Noto Serif SC', 'Source Han Serif SC', 'STZhongsong', 'STSong', 'SimSun', serif;
  --num-font: 'Georgia', 'Times New Roman', serif;

  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  background: var(--bg);
  color: var(--text);
  overflow: hidden;
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
}
.honor-screen-root::before {
  content: '';
  position: fixed;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(ellipse 1200px 600px at 50% -5%, rgba(122, 74, 43, 0.07), transparent 62%);
}
.honor-screen-root::after {
  content: '';
  position: fixed;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(ellipse 1600px 900px at 50% 55%, transparent 60%, rgba(122, 74, 43, 0.06) 100%);
}

/* ===== 缩放舞台 ===== */
.screen {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 1920px;
  height: 1080px;
  transform-origin: center;
  display: flex;
  flex-direction: column;
  padding: 0 26px 12px;
}

/* ===== Header ===== */
.hd {
  height: 88px;
  flex: none;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding-bottom: 12px;
  position: relative;
}
.hd::after {
  content: '';
  position: absolute;
  left: 26px;
  right: 26px;
  bottom: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(122, 74, 43, 0.55) 22%, rgba(122, 74, 43, 0.55) 78%, transparent);
}
.hd-side {
  width: 380px;
  font-size: 16px;
  color: var(--text-dim);
  letter-spacing: 2px;
  font-family: var(--serif);
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}
.hd-side.right {
  text-align: right;
  align-items: flex-end;
}
.hd-date {
  font-size: 14px;
  letter-spacing: 2px;
  color: var(--text-dim);
}
.hd-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  font-size: 14px;
  letter-spacing: 3px;
  color: var(--gold);
  background: rgba(122, 74, 43, 0.08);
  border: 1px solid rgba(122, 74, 43, 0.45);
  border-radius: 999px;
  cursor: pointer;
  font-family: var(--serif);
  transition: background 0.2s, color 0.2s, transform 0.2s, border-color 0.2s;
}
.hd-back:hover {
  background: rgba(122, 74, 43, 0.18);
  border-color: var(--gold);
  color: var(--text);
  transform: translateX(-2px);
}
.hd-back:active {
  transform: translateX(0) scale(0.98);
}
.hd-side .clock {
  font-family: var(--num-font);
  font-size: 24px;
  color: var(--gold-light);
  letter-spacing: 1px;
}
.hd-title {
  position: absolute;
  left: 50%;
  bottom: 14px;
  transform: translateX(-50%);
  text-align: center;
}
.hd-title h1 {
  font-family: var(--serif);
  font-size: 38px;
  letter-spacing: 14px;
  font-weight: 600;
  color: var(--text);
  text-indent: 14px;
}
.hd-title h1 em {
  font-style: normal;
  color: var(--gold-light);
}
.hd-title .sub {
  margin-top: 6px;
  font-size: 12px;
  letter-spacing: 5px;
  color: var(--text-dim);
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: center;
}
.hd-title .sub::before,
.hd-title .sub::after {
  content: '';
  width: 56px;
  height: 1px;
  background: rgba(122, 74, 43, 0.4);
}
.hd-title .sub i {
  font-style: normal;
  color: var(--gold);
}

/* ===== 主体三栏 ===== */
.main {
  flex: 1;
  display: flex;
  gap: 18px;
  padding: 16px 0 12px;
  min-height: 0;
}
.col {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 0;
}
.col.side {
  width: 452px;
  flex: none;
}
.col.center {
  flex: 1;
  min-width: 0;
}

/* ===== 通用面板 ===== */
.panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  position: relative;
  background: var(--panel-bg);
  border: 1px solid var(--panel-bd);
  border-radius: 6px;
  animation: panelIn 0.9s ease both;
}
@keyframes panelIn {
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: none; }
}
.panel-hd {
  flex: none;
  height: 50px;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 18px;
  position: relative;
}
.panel-hd::after {
  content: '';
  position: absolute;
  left: 18px;
  right: 18px;
  bottom: 0;
  height: 1px;
  background: linear-gradient(90deg, rgba(122, 74, 43, 0.45), rgba(122, 74, 43, 0.06));
}
.hd-seal {
  width: 8px;
  height: 8px;
  background: var(--gold);
  transform: rotate(45deg);
  flex: none;
}
.panel-hd h3 {
  font-family: var(--serif);
  font-size: 20px;
  letter-spacing: 4px;
  color: var(--gold-light);
  font-weight: 600;
}
.panel-hd .en {
  font-size: 11px;
  letter-spacing: 2px;
  color: var(--text-dim);
  margin-top: 4px;
}
.panel-bd {
  flex: 1;
  min-height: 0;
  position: relative;
  padding: 10px 16px 12px;
}
.chart-fill {
  width: 100%;
  height: 100%;
}

/* ===== KPI ===== */
.stats-row {
  flex: none;
  height: 126px;
  display: flex;
  gap: 18px;
}
.stat-card {
  flex: 1;
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background: linear-gradient(175deg, rgba(255, 255, 255, 0.55), rgba(250, 244, 235, 0.4));
  border: 1px solid rgba(122, 74, 43, 0.18);
  border-radius: 6px;
  animation: panelIn 0.9s ease both;
  overflow: hidden;
}
.stat-card::before {
  content: '';
  position: absolute;
  left: 22%;
  right: 22%;
  top: 0;
  height: 2px;
  background: var(--c, var(--gold));
}
.stat-card .num {
  font-family: var(--num-font);
  font-size: 48px;
  font-weight: 600;
  color: var(--c, var(--gold-light));
  letter-spacing: 1px;
  line-height: 1.05;
}
.stat-card .num small {
  font-size: 17px;
  margin-left: 3px;
  color: var(--text-dim);
  font-family: var(--serif);
}
.stat-card .label {
  font-size: 15px;
  color: var(--text-dim);
  letter-spacing: 4px;
  margin-top: 7px;
  font-family: var(--serif);
}

/* ===== 荣誉榜单 ===== */
.honor-scroll {
  height: 100%;
  overflow: hidden;
  position: relative;
  -webkit-mask-image: linear-gradient(180deg, transparent, #000 26px, #000 calc(100% - 26px), transparent);
  mask-image: linear-gradient(180deg, transparent, #000 26px, #000 calc(100% - 26px), transparent);
}
.honor-list {
  animation: scrollY var(--dur, 24s) linear infinite;
}
.honor-scroll:hover .honor-list {
  animation-play-state: paused;
}
@keyframes scrollY {
  to { transform: translateY(-50%); }
}
.honor-item {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 58px;
  padding: 0 8px;
  border-bottom: 1px solid rgba(122, 117, 109, 0.1);
}
.honor-item .rank {
  flex: none;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--num-font);
  font-size: 15px;
  font-style: italic;
  color: var(--text-dim);
}
.honor-item:nth-child(1) .rank { color: var(--gold); font-weight: 700; }
.honor-item:nth-child(2) .rank { color: #8a8378; font-weight: 700; }
.honor-item:nth-child(3) .rank { color: #a0623d; font-weight: 700; }
.honor-item .info { flex: 1; min-width: 0; }
.honor-item .t {
  font-size: 15px;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.honor-item .t b { color: var(--gold-light); font-weight: 600; }
.honor-item .s {
  font-size: 12px;
  color: var(--text-dim);
  margin-top: 3px;
  letter-spacing: 1px;
}
.lv-badge {
  flex: none;
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 2px;
  letter-spacing: 2px;
  border: 1px solid;
  font-family: var(--serif);
}
.lv-international { color: #8b3a3a; border-color: rgba(139, 58, 58, 0.5); background: rgba(139, 58, 58, 0.08); }
.lv-national { color: var(--gold); border-color: rgba(184, 134, 11, 0.5); background: rgba(184, 134, 11, 0.08); }
.lv-provincial { color: var(--blue); border-color: rgba(74, 107, 74, 0.5); background: rgba(74, 107, 74, 0.08); }
.lv-city { color: #946b4a; border-color: rgba(148, 107, 74, 0.5); background: rgba(148, 107, 74, 0.08); }
.lv-school { color: var(--text-dim); border-color: rgba(122, 117, 109, 0.4); background: rgba(122, 117, 109, 0.06); }

/* ===== 校友荣誉长廊 ===== */
.gallery-scroll {
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 2px 0;
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 40px, #000 calc(100% - 40px), transparent);
  mask-image: linear-gradient(90deg, transparent, #000 40px, #000 calc(100% - 40px), transparent);
}
.g-row {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  align-items: center;
}
.g-track {
  display: flex;
  animation: scrollX var(--dur, 30s) linear infinite;
  will-change: transform;
}
.g-row.reverse .g-track { animation-direction: reverse; }
.gallery-scroll:hover .g-track { animation-play-state: paused; }
@keyframes scrollX { to { transform: translateX(-50%); } }
.g-card {
  flex: none;
  width: 292px;
  margin-right: 16px;
  padding: 13px 14px 11px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(122, 74, 43, 0.2);
  border-radius: 5px;
}
.g-top { display: flex; gap: 12px; align-items: center; margin-bottom: 9px; }
.g-photo {
  flex: none;
  width: 104px;
  height: 132px;
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--serif);
  font-size: 38px;
  color: #fff;
}
.g-photo img { width: 100%; height: 100%; object-fit: cover; object-position: center top; display: block; }
.g-info { flex: 1; min-width: 0; }
.g-name {
  font-family: var(--serif);
  font-size: 19px;
  letter-spacing: 3px;
  color: var(--text);
  font-weight: 600;
  text-align: left;
}
.g-meta {
  font-size: 11.5px;
  color: var(--text-dim);
  margin: 5px 0 6px;
  letter-spacing: 1px;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.g-quote {
  font-family: var(--serif);
  font-style: italic;
  font-size: 12.5px;
  color: var(--gold-light);
  line-height: 1.6;
  text-align: left;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.g-honors {
  display: flex;
  flex-direction: column;
  gap: 5px;
  text-align: left;
  border-top: 1px dashed rgba(122, 74, 43, 0.25);
  padding-top: 8px;
}
.g-honor {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  color: var(--text);
  line-height: 1.45;
}
.g-honor i { flex: none; width: 6px; height: 6px; transform: rotate(45deg); }
.g-motto {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-dim);
  font-family: var(--serif);
  text-align: left;
  line-height: 1.6;
  letter-spacing: 0.5px;
}
.g-motto b { color: var(--gold); font-weight: 600; margin-right: 8px; flex: none; }

/* ===== 杰出校友轮播 ===== */
.alumni-stage { height: 100%; display: flex; flex-direction: column; }
.fade-enter-active, .fade-leave-active { transition: all 0.7s ease; }
.fade-enter-from { opacity: 0; transform: translateX(28px); }
.fade-leave-to { opacity: 0; transform: translateX(-28px); }
.alumni-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 8px 10px 2px;
}
.avatar {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 42px;
  font-weight: 600;
  color: #fff;
  font-family: var(--serif);
  margin-bottom: 14px;
  position: relative;
  box-shadow: 0 0 0 1px rgba(122, 74, 43, 0.6), 0 6px 20px rgba(0, 0, 0, 0.35);
}
.avatar img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; display: block; }
.alumni-card .name {
  font-family: var(--serif);
  font-size: 26px;
  color: var(--text);
  letter-spacing: 6px;
  font-weight: 600;
  text-indent: 6px;
}
.alumni-card .meta {
  font-size: 13px;
  color: var(--blue);
  margin: 8px 0;
  letter-spacing: 1px;
}
.alumni-card .intro {
  font-size: 13px;
  color: var(--text-dim);
  line-height: 1.8;
  max-width: 380px;
}
.alumni-card .hl {
  font-size: 14px;
  color: var(--gold-light);
  background: rgba(122, 74, 43, 0.1);
  border: 1px solid rgba(122, 74, 43, 0.35);
  border-radius: 3px;
  padding: 7px 14px;
  line-height: 1.5;
  margin-top: 10px;
  letter-spacing: 1px;
}
.dots {
  flex: none;
  display: flex;
  gap: 9px;
  justify-content: center;
  padding: 12px 0 2px;
}
.dots i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(122, 74, 43, 0.25);
  cursor: pointer;
  transition: 0.3s;
}
.dots i.on { background: var(--gold); transform: scale(1.15); }

/* ===== Footer ===== */
.ft {
  flex: none;
  height: 44px;
  display: flex;
  align-items: center;
  gap: 20px;
  font-size: 13px;
  color: var(--text-dim);
  border-top: 1px solid rgba(122, 74, 43, 0.18);
  letter-spacing: 1px;
}
.ft .btn {
  background: transparent;
  border: 1px solid rgba(122, 74, 43, 0.4);
  color: var(--text);
  font-size: 13px;
  padding: 5px 18px;
  border-radius: 3px;
  cursor: pointer;
  transition: 0.25s;
  letter-spacing: 2px;
  font-family: var(--serif);
}
.ft .btn:hover {
  background: rgba(122, 74, 43, 0.15);
  color: var(--gold-light);
}
.ft .spacer { flex: 1; }
.ft .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #4a6b4a;
}
.ft-err {
  color: #8b3a3a;
  font-size: 12px;
}
.ft-cfg {
  font-size: 12px;
  color: var(--gold-light);
  letter-spacing: 1px;
  opacity: 0.75;
}

/* ===== 空状态 ===== */
.empty-tip {
  padding: 20px;
  text-align: center;
  color: var(--text-dim);
  font-size: 13px;
  letter-spacing: 1px;
}
</style>
