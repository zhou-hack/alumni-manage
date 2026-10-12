/**
 * Vue 版 ECharts 统一封装（对齐 portal/useEChart.ts 口径）
 * - 按需引入图表类型（Bar/Pie/Line + Grid/Tooltip/Legend），控制打包体积
 * - 容器挂载后 init，option 变化 setOption，尺寸变化 ResizeObserver 自动 resize
 * - 卸载即 dispose，避免重复初始化与内存泄漏
 * - option 传 null 时不渲染（供"暂无数据"空态分支使用）
 */
import { nextTick, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([BarChart, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

/**
 * @param {import('vue').Ref<HTMLElement|null>} elRef 图表容器 ref（v-if 挂载后自动 init）
 * @param {import('vue').Ref<object|null>} optionRef 响应式 option（null = 不渲染）
 */
export function useEChart(elRef, optionRef) {
  let chart = null
  const ro = new ResizeObserver(() => {
    if (chart && !chart.isDisposed()) chart.resize()
  })

  function render() {
    const el = elRef.value
    const opt = optionRef.value
    if (!el || !opt || el.clientWidth === 0 || el.clientHeight === 0) return
    if (!chart) {
      chart = echarts.init(el, undefined, { renderer: 'canvas' })
      ro.observe(el)
    }
    chart.setOption(opt, true)
  }

  function dispose() {
    ro.disconnect()
    if (chart && !chart.isDisposed()) chart.dispose()
    chart = null
  }

  watch(elRef, (el) => {
    if (el) nextTick(render)
    else dispose()
  })
  watch(optionRef, () => nextTick(render), { deep: true })
  onBeforeUnmount(dispose)

  return { render, dispose }
}
