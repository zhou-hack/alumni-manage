<script setup lang="ts">
// ============================================================================
// 数据看板（独立页面 · 路由 /dashboard）—— 面向全体师生公开展示
//
// 结构：顶栏（品牌 / 数据在线·返回首页） → 标题区（眉题 / 大标题 / 全屏播放）
//       → 3 张 KPI 指标卡（在校人数 / 校友人数 / 总荣誉数量）
//       → 01 各学院在校人数（条形图） + 02 各学院校友人数（条形图）
//       → 03 各学院所获荣誉数量（环形图 + 图例） → 底部数据说明
//
// 数据（2026-09-25 健康审计 P0-2 修复）：
//   全部改为真实接口 `/stats/overview`、`/stats/colleges`、`/stats/honors/distribution`
//   与 `/stats/honors`，不再使用 mock/dashboard.ts（写死的"虚拟校情"数据）。
//   接口失败保留空列表 + 真实错误态，由调用方决定是否需要兜底。
// ============================================================================
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { Maximize2, Minimize2, ArrowLeft } from '../icons';
import { Reveal, RevealGroup, RevealItem } from '../Reveal';
import { api } from '../../api';
import { ANIM_EASING, useEChart } from './useEChart';

const BOARD_COLORS = { blue: '#2D5A8E', red: '#A63D2F', green: '#3E6B4F', gold: '#C19A3F', slate: '#5A6470', violet: '#8B5CF6' };
const DONUT_COLORS = ['#C19A3F', '#3E6B4F', '#A63D2F', '#2D5A8E', '#8B5CF6', '#5A6470', '#3D5A73'];

// 页面元信息（标题 / 数据来源 / 版本）
const meta = ref({ brand: '校友管理平台', brandSub: 'ALUMNI PLATFORM', eyebrow: 'OPEN DATA · DASHBOARD', title: '数据看板', subtitle: '面向全体师生校友的校情公开窗口', dataSource: '已审核 + 已上架 + 已授权公开的校友档案', version: 'v1' });

// KPI 指标（在校人数 / 校友人数 / 总荣誉数量）
interface KpiItem { key: string; label: string; tag: string; value: number; suffix: string; delta: string; hint: string; up: boolean; accent: string; spark?: number[] }
const kpis = ref<KpiItem[]>([
  { key: 'alumni', label: '校友总人数', tag: 'alumni', value: 0, suffix: '人', delta: '—', hint: '累计认证校友', up: true, accent: BOARD_COLORS.red, spark: [20, 35, 45, 60, 80, 100] },
  { key: 'archives', label: '档案总数', tag: 'archives', value: 0, suffix: '份', delta: '—', hint: '已提交档案', up: true, accent: BOARD_COLORS.blue, spark: [10, 25, 40, 55, 70, 90] },
  { key: 'honors', label: '总荣誉数量', tag: 'honors', value: 0, suffix: '项', delta: '—', hint: '已审核荣誉', up: true, accent: BOARD_COLORS.gold, spark: [15, 30, 50, 65, 80, 95] }
]);

// 学院维度数据
interface NameValue { name: string; value: number }
const enrollment = ref<NameValue[]>([]);     // 暂未提供接口（前端暂无在校人数学院维度）
const alumniByCollege = ref<NameValue[]>([]); // /stats/colleges
const honorsByCollege = ref<NameValue[]>([]); // /stats/honors/distribution?dimension=college
const honorTotal = computed(() => honorsByCollege.value.reduce((s, d) => s + d.value, 0));

const loadState = ref<'loading' | 'ok' | 'error'>('loading');
const load = async () => {
  loadState.value = 'loading';
  try {
    const [overview, colleges] = await Promise.all([
      api.stats.overview(),
      api.stats.colleges(20)
    ]);
    const o = overview;
    kpis.value[0].value = o.total_alumni ?? 0;
    kpis.value[1].value = o.total_archives ?? 0;
    kpis.value[2].value = o.total_honors ?? 0;
    // 学院维度（按档案数与荣誉数拼装）
    alumniByCollege.value = (colleges.list ?? [])
      .filter((c): c is { college_id: number | null; college_name: string; archive_count: number } => Boolean(c.college_name))
      .map((c) => ({ name: c.college_name, value: c.archive_count ?? 0 }));
    try {
      const dist = await api.stats.honors('college');
      honorsByCollege.value = dist.list.map((d) => ({ name: d.key, value: d.count }));
    } catch {
      honorsByCollege.value = [];
    }
    loadState.value = 'ok';
  } catch (e) {
    console.warn('[dashboard] 接口请求失败：', e);
    loadState.value = 'error';
  }
};
onMounted(load);

// ------------------------------ 图表共用样式（浅色报表） ------------------------------
const MONO = 'JetBrains Mono, Consolas, monospace';
const TOOLTIP = {
  confine: true, // 面板为 overflow-hidden，提示框限制在图表内，避免被裁切
  backgroundColor: 'rgba(32, 38, 43, 0.95)',
  borderWidth: 0,
  padding: [10, 12] as [number, number],
  textStyle: { color: '#F5F2E9', fontSize: 12 },
  extraCssText: 'border-radius:8px; box-shadow:0 12px 28px rgba(32,38,43,0.28);'
};

const pad = (n: number) => String(n).padStart(2, '0');
const fmtTime = (d: Date) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;

// ------------------------------ 学院横向条形图（在校人数 / 校友人数共用） ------------------------------
// option 需 computed 保持引用稳定，useEChart 按引用比较决定何时 setOption
function buildBarOption(data: NameValue[], unit: string, color: string) {
  return {
    animationDuration: 800,
    animationEasing: ANIM_EASING,
    tooltip: { ...TOOLTIP, formatter: `{b}　{c} ${unit}` },
    // ECharts 6：containLabel 已迁移为 outerBoundsMode + outerBoundsContain（'same' + 'auto' 等价原 containLabel:true）
    grid: { left: 8, right: 48, top: 8, bottom: 8, outerBoundsMode: 'same', outerBoundsContain: 'auto' },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#EAE4D6', type: 'dashed' } },
      axisLabel: { color: '#9A9484', fontSize: 10, fontFamily: MONO }
    },
    yAxis: {
      type: 'category',
      // 倒序填入，让数值最大的学院显示在最上方
      data: data.map((d) => d.name).reverse(),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: '#3A3E44', fontSize: 11 }
    },
    series: [
      {
        type: 'bar',
        data: data.map((d) => d.value).reverse(),
        barWidth: 12,
        itemStyle: { color, borderRadius: [0, 6, 6, 0] },
        label: {
          show: true,
          position: 'right',
          color: '#6B6558',
          fontSize: 10,
          fontFamily: MONO,
          formatter: (p: { value: unknown }) => Number(p.value).toLocaleString()
        }
      }
    ]
  };
}

const enrollmentOption = computed(() => buildBarOption(enrollment.value, '人', BOARD_COLORS.blue));
const alumniOption = computed(() => buildBarOption(alumniByCollege.value, '人', BOARD_COLORS.red));
const enrollmentChartEl = useEChart(enrollmentOption);
const alumniChartEl = useEChart(alumniOption);

// ------------------------------ 各学院所获荣誉数量（环形图） ------------------------------
const donutOption = computed(() => ({
  animationDuration: 800,
  animationEasing: ANIM_EASING,
  tooltip: { ...TOOLTIP, formatter: '{b}　{c} 项（{d}%）' },
  series: [
    {
      type: 'pie',
      radius: ['56%', '76%'],
      center: ['50%', '50%'],
      label: { show: false },
      labelLine: { show: false },
      itemStyle: { borderColor: '#FFFFFF', borderWidth: 2 },
      emphasis: { scale: true, scaleSize: 4 },
      data: honorsByCollege.value.map((d, i) => ({
        name: d.name,
        value: d.value,
        itemStyle: { color: DONUT_COLORS[i % DONUT_COLORS.length] }
      }))
    }
  ]
}));
const donutChartEl = useEChart(donutOption);

// ------------------------------ 全屏播放与数据时间 ------------------------------
const bandRef = ref<HTMLDivElement | null>(null);
const isFs = ref(false);
// 数据更新时间：进入页面时记录一次
const updatedAt = new Date();

// 全屏能力检测与状态同步
const fsSupported =
  typeof document !== 'undefined' &&
  document.fullscreenEnabled === true &&
  typeof document.documentElement.requestFullscreen === 'function';

const onFullscreenChange = () => {
  isFs.value = document.fullscreenElement === bandRef.value;
};
onMounted(() => document.addEventListener('fullscreenchange', onFullscreenChange));
onBeforeUnmount(() => document.removeEventListener('fullscreenchange', onFullscreenChange));

const toggleFullscreen = async () => {
  const el = bandRef.value;
  if (!el || !fsSupported) return;
  try {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await el.requestFullscreen();
  } catch {
    /* 浏览器拒绝（非用户手势触发等）时静默忽略，不影响页面 */
  }
};
</script>

<template>
  <section id="alumni-dashboard" class="relative">
    <div ref="bandRef" class="flex min-h-screen flex-col bg-[#F4F1E9] text-[#2A2E33] [&:fullscreen]:overflow-y-auto">
      <!-- ===== 顶栏：品牌 / 数据在线 ===== -->
      <header class="shrink-0 border-b border-[#E3DED2] bg-[#FBF9F3]">
        <div class="mx-auto flex h-16 w-full max-w-[1500px] items-center justify-between px-5 md:px-8">
          <!-- 左：校徽 + 平台名（点击返回首页） -->
          <RouterLink to="/" title="返回首页" class="flex shrink-0 items-center gap-3">
            <span class="flex h-10 w-10 items-center justify-center overflow-hidden rounded-full bg-white ring-1 ring-[#E3DED2]">
              <img src="/bgcsvg/school-badge.png" alt="福州软件职业技术学院校徽" class="h-full w-full object-cover" />
            </span>
            <span class="leading-tight">
              <span class="block font-title text-[17px] font-black tracking-wide text-[#1F2328]">{{ meta.brand }}</span>
              <span class="mt-0.5 block font-code text-[9px] tracking-[0.24em] text-[#9A9484]">{{ meta.brandSub }}</span>
            </span>
          </RouterLink>

          <!-- 右：数据状态 + 返回首页 -->
          <div class="flex shrink-0 items-center gap-3">
            <span class="hidden items-center gap-1.5 sm:flex">
              <span class="relative flex h-2 w-2">
                <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-[#3E6B4F] opacity-60" />
                <span class="relative inline-flex h-2 w-2 rounded-full bg-[#3E6B4F]" />
              </span>
              <span class="text-[12px] text-[#3E6B4F]">数据在线</span>
            </span>
            <RouterLink
              to="/"
              class="flex items-center gap-1.5 rounded-lg border border-[#E3DED2] bg-white px-3.5 py-2 text-[12px] font-semibold text-[#3A3E44] transition hover:border-[#C9C2B0] hover:bg-[#FAF7EF]"
            >
              <ArrowLeft :size="13" />
              返回首页
            </RouterLink>
          </div>
        </div>
      </header>

      <!-- ===== 主体 ===== -->
      <main class="mx-auto w-full max-w-[1500px] flex-1 px-5 md:px-8">
        <!-- 标题区 -->
        <div class="mt-7 flex flex-wrap items-end justify-between gap-4">
          <div>
            <p class="font-code text-[11px] tracking-[0.35em] text-[#A08C5B]">{{ meta.eyebrow }}</p>
            <h1 class="mt-2 font-title text-[26px] font-black leading-tight text-[#1F2328] md:text-[34px]">{{ meta.title }}</h1>
            <p class="mt-1.5 text-[13px] text-[#8B8577]">{{ meta.subtitle }}</p>
          </div>

          <div class="flex flex-wrap items-center gap-2.5">
            <button
              type="button"
              :disabled="!fsSupported"
              :title="fsSupported ? '全屏播放' : '当前浏览器不支持全屏'"
              class="flex items-center gap-1.5 rounded-lg bg-[#23282D] px-4 py-2.5 text-[13px] font-semibold text-white transition hover:bg-[#3A4046] disabled:cursor-not-allowed disabled:opacity-40"
              @click="toggleFullscreen"
            >
              <Minimize2 v-if="isFs" :size="13" />
              <Maximize2 v-else :size="13" />
              {{ isFs ? '退出全屏' : '全屏播放' }}
            </button>
          </div>
        </div>

        <!-- KPI 指标卡 ×3（级联入场，二级动画） -->
        <RevealGroup class="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-3" :amount="0.1">
          <RevealItem v-for="item in kpis" :key="item.key">
            <div
              class="relative overflow-hidden rounded-xl border border-[#E6E1D4] bg-white py-4 pl-5 pr-4 shadow-[0_1px_2px_rgba(31,41,55,0.05)]"
            >
              <!-- 左侧色条 -->
              <span class="absolute bottom-3 left-0 top-3 w-[3px] rounded-full" :style="{ background: item.accent }" />

              <div class="flex items-center justify-between gap-2">
                <p class="text-[12px] text-[#8B8577]">{{ item.label }}</p>
                <span class="shrink-0 rounded border border-[#EAE4D6] bg-[#FAF7EF] px-1.5 py-0.5 text-[10px] text-[#9A9484]">
                  {{ item.tag }}
                </span>
              </div>

              <p class="mt-2.5 font-code text-[30px] font-bold leading-none tracking-tight text-[#1F2328]">
                {{ item.value.toLocaleString() }}
                <span class="ml-1 text-[13px] font-normal text-[#9A9484]">{{ item.suffix }}</span>
              </p>

              <p class="mt-2 text-[11px]">
                <span class="font-code font-semibold" :style="{ color: item.up ? BOARD_COLORS.green : BOARD_COLORS.red }">
                  {{ item.delta }}
                </span>
                <span class="ml-1.5 text-[#9A9484]">{{ item.hint }}</span>
              </p>

              <!-- 底部迷你柱状 -->
              <div v-if="item.spark" class="mt-3 flex h-[26px] items-end gap-[3px]">
                <span
                  v-for="(v, i) in item.spark"
                  :key="i"
                  class="w-[7px] rounded-[1px]"
                  :style="{ height: `${v}%`, background: item.accent, opacity: 0.22 + (0.78 * i) / (item.spark.length - 1) }"
                />
              </div>
            </div>
          </RevealItem>
        </RevealGroup>

        <!-- 01 各学院在校人数 + 02 各学院校友人数 -->
        <div class="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-2">
          <Reveal :delay="0.05">
            <section class="rounded-xl border border-[#E6E1D4] bg-white p-5 shadow-[0_1px_2px_rgba(31,41,55,0.05)] h-full">
              <header class="flex items-start justify-between gap-3">
                <div class="flex items-center gap-2.5">
                  <span
                    class="flex h-6 w-6 shrink-0 items-center justify-center rounded border border-[#E3DED2] font-code text-[10px] font-semibold text-[#9A9484]"
                  >
                    01
                  </span>
                  <div>
                    <h3 class="font-title text-[15px] font-black leading-tight text-[#22262B]">各学院在校人数</h3>
                    <p class="mt-0.5 text-[11px] text-[#9A9484]">按学院统计当前在校学生规模</p>
                  </div>
                </div>
                <span class="pt-1 text-[10px] text-[#9A9484]">单位：人</span>
              </header>
              <div ref="enrollmentChartEl" class="mt-2 h-[300px] w-full" />
            </section>
          </Reveal>
          <Reveal :delay="0.13">
            <section class="rounded-xl border border-[#E6E1D4] bg-white p-5 shadow-[0_1px_2px_rgba(31,41,55,0.05)] h-full">
              <header class="flex items-start justify-between gap-3">
                <div class="flex items-center gap-2.5">
                  <span
                    class="flex h-6 w-6 shrink-0 items-center justify-center rounded border border-[#E3DED2] font-code text-[10px] font-semibold text-[#9A9484]"
                  >
                    02
                  </span>
                  <div>
                    <h3 class="font-title text-[15px] font-black leading-tight text-[#22262B]">各学院校友人数</h3>
                    <p class="mt-0.5 text-[11px] text-[#9A9484]">按学院统计已毕业校友总量</p>
                  </div>
                </div>
                <span class="pt-1 text-[10px] text-[#9A9484]">单位：人</span>
              </header>
              <div ref="alumniChartEl" class="mt-2 h-[300px] w-full" />
            </section>
          </Reveal>
        </div>

        <!-- 03 各学院所获荣誉数量 -->
        <div class="mt-4">
          <Reveal :delay="0.05">
            <section class="rounded-xl border border-[#E6E1D4] bg-white p-5 shadow-[0_1px_2px_rgba(31,41,55,0.05)]">
              <header class="flex items-start justify-between gap-3">
                <div class="flex items-center gap-2.5">
                  <span
                    class="flex h-6 w-6 shrink-0 items-center justify-center rounded border border-[#E3DED2] font-code text-[10px] font-semibold text-[#9A9484]"
                  >
                    03
                  </span>
                  <div>
                    <h3 class="font-title text-[15px] font-black leading-tight text-[#22262B]">各学院所获荣誉数量</h3>
                    <p class="mt-0.5 text-[11px] text-[#9A9484]">按学院统计累计获得荣誉</p>
                  </div>
                </div>
              </header>
              <div class="mt-2 flex flex-col items-center gap-6 md:flex-row md:gap-8">
                <div class="relative h-[280px] w-[280px] shrink-0">
                  <div ref="donutChartEl" class="h-full w-full" />
                  <!-- 环形中心总量（HTML 覆盖层，不阻挡 hover） -->
                  <div class="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                    <p class="font-code text-[24px] font-bold leading-none text-[#22262B]">{{ honorTotal.toLocaleString() }}</p>
                    <p class="mt-1.5 text-[11px] text-[#9A9484]">总荣誉数量</p>
                  </div>
                </div>
                <!-- 自定义图例：色点 + 学院 + 数值 + 占比（两列排布） -->
                <ul class="grid flex-1 grid-cols-1 gap-x-8 gap-y-3.5 sm:grid-cols-2">
                  <li v-for="(d, i) in honorsByCollege" :key="d.name" class="flex items-center gap-2 text-[12px]">
                    <span class="h-2 w-2 shrink-0 rounded-[2px]" :style="{ background: DONUT_COLORS[i % DONUT_COLORS.length] }" />
                    <span class="truncate text-[#3A3E44]">{{ d.name }}</span>
                    <span class="ml-auto font-code font-semibold text-[#2A2E33]">{{ d.value.toLocaleString() }}</span>
                    <span class="w-8 shrink-0 text-right font-code text-[10px] text-[#9A9484]">
                      {{ Math.round((d.value / honorTotal) * 100) }}%
                    </span>
                  </li>
                </ul>
              </div>
            </section>
          </Reveal>
        </div>

        <!-- ===== 底部数据说明 ===== -->
        <footer class="mt-6 flex flex-wrap items-center justify-between gap-2 border-t border-[#E3DED2] py-4 text-[11px] text-[#9A9484]">
          <span class="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span class="flex items-center gap-1.5">
              <span class="h-1.5 w-1.5 rounded-full bg-[#3E6B4F]" />
              数据更新时间 <span class="font-code">{{ fmtTime(updatedAt) }}</span>
            </span>
            <span>数据来源：{{ meta.dataSource }}</span>
          </span>
          <span>
            指标口径版本 <span class="font-code">{{ meta.version }}</span>
          </span>
        </footer>
      </main>
    </div>
  </section>
</template>
