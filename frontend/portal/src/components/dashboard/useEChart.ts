// ============================================================================
// ECharts 统一封装（档案馆版驾驶舱 · Vue composable）
//   - 按需引入图表类型，控制打包体积
//   - resetKey 变化时销毁重建（用于"刷新"重播入场动画）
//   - ResizeObserver + window resize 监听容器尺寸变化（窗口缩放 / 全屏切换自适应）
//   - 卸载即 dispose，避免重复初始化与内存泄漏
//
// 用法：
//   const chartEl = useEChart(option);   // option 必须是 computed（引用稳定）
//   <div ref="chartEl" class="h-[300px] w-full" />
// ============================================================================
import { isRef, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue';
import * as echarts from 'echarts/core';
import { BarChart, LineChart, PieChart, RadarChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import type { EChartsCoreOption } from 'echarts/core';

echarts.use([BarChart, LineChart, PieChart, RadarChart, GridComponent, TooltipComponent, CanvasRenderer]);

type CoreChart = ReturnType<typeof echarts.init>;

// 统一缓动（字面量类型，直接用于 animationEasing，避免被推断成 string）
export const ANIM_EASING = 'cubicOut' as const;

/**
 * 绑定一个图表容器 div，返回模板 ref。
 *   - option：必须 computed 保持引用稳定；引用变化时才 setOption（引用相同则跳过）
 *   - resetKey：变化时销毁并重建图表（重播入场动画），可传 Ref 或字面量
 */
export function useEChart(option: Ref<EChartsCoreOption>, resetKey: Ref<number> | number = 0) {
  const el = ref<HTMLDivElement | null>(null);
  const keyRef: Ref<number> = isRef(resetKey) ? resetKey : ref(resetKey);
  let chart: CoreChart | null = null;
  let applied: EChartsCoreOption | null = null;
  let ro: ResizeObserver | null = null;

  const onWindowResize = () => {
    if (chart && !chart.isDisposed()) chart.resize();
  };

  function dispose() {
    ro?.disconnect();
    ro = null;
    window.removeEventListener('resize', onWindowResize);
    if (chart && !chart.isDisposed()) chart.dispose();
    chart = null;
    applied = null;
  }

  // 初始化（resetKey 变化时整体重建，重播动画）
  function init() {
    const dom = el.value;
    if (!dom) return;
    if (dom.clientWidth === 0 || dom.clientHeight === 0) return; // 尺寸为 0 时初始化会告警
    const instance = echarts.init(dom, undefined, { renderer: 'canvas' });
    chart = instance;
    instance.setOption(option.value, true); // 首次渲染带动画
    applied = option.value;

    ro = new ResizeObserver(() => {
      if (!instance.isDisposed()) instance.resize();
    });
    ro.observe(dom);
    window.addEventListener('resize', onWindowResize);
  }

  onMounted(init);
  onBeforeUnmount(dispose);

  // resetKey 变化：销毁后重建
  watch(keyRef, () => {
    dispose();
    init();
  });

  // 数据 / 配置更新（如时间范围切换；option 需 computed 保持引用稳定）
  watch(option, (opt) => {
    if (!chart || chart.isDisposed() || applied === opt) return;
    chart.setOption(opt, true);
    applied = opt;
  });

  return el;
}
