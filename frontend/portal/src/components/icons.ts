// 站点内联图标（Vue 版）：只保留实际用到的 10 个，替代图标库整包。
// 路径、viewBox(24×24)、stroke 参数与 lucide 原图标一致，渲染结果完全相同。
// 用法与原来一致：<ChevronRight :size="15" class="text-slate-300" />
import { defineComponent, h, type PropType, type SVGAttributes } from 'vue';

interface IconProps {
  size?: number;
  class?: unknown;
}

function makeIcon(name: string, children: () => ReturnType<typeof h>[]) {
  return defineComponent({
    name,
    props: {
      size: { type: Number, default: 24 }
    },
    setup(props, { attrs }) {
      return () =>
        h(
          'svg',
          {
            xmlns: 'http://www.w3.org/2000/svg',
            width: props.size,
            height: props.size,
            viewBox: '0 0 24 24',
            fill: 'none',
            stroke: 'currentColor',
            'stroke-width': 2,
            'stroke-linecap': 'round',
            'stroke-linejoin': 'round',
            'aria-hidden': 'true',
            ...(attrs as SVGAttributes)
          },
          children()
        );
    }
  });
}

const path = (d: string, extra: Record<string, string> = {}) => h('path', { d, ...extra });

export const X = makeIcon('X', () => [path('M18 6 6 18'), path('m6 6 12 12')]);

export const Award = makeIcon('Award', () => [
  path(
    'm15.477 12.89 1.515 8.526a.5.5 0 0 1-.81.47l-3.58-2.687a1 1 0 0 0-1.197 0l-3.586 2.686a.5.5 0 0 1-.81-.469l1.514-8.526'
  ),
  h('circle', { cx: 12, cy: 8, r: 6 })
]);

export const GraduationCap = makeIcon('GraduationCap', () => [
  path(
    'M21.42 10.922a1 1 0 0 0-.019-1.838L12.83 5.18a2 2 0 0 0-1.66 0L2.6 9.08a1 1 0 0 0 0 1.832l8.57 3.908a2 2 0 0 0 1.66 0z'
  ),
  path('M22 10v6'),
  path('M6 12.5V16a6 3 0 0 0 12 0v-3.5')
]);

export const ArrowLeft = makeIcon('ArrowLeft', () => [path('m12 19-7-7 7-7'), path('M19 12H5')]);

export const ArrowRight = makeIcon('ArrowRight', () => [path('M5 12h14'), path('m12 5 7 7-7 7')]);

export const ChevronLeft = makeIcon('ChevronLeft', () => [path('m15 18-6-6 6-6')]);

export const ChevronRight = makeIcon('ChevronRight', () => [path('m9 18 6-6-6-6')]);

export const Maximize2 = makeIcon('Maximize2', () => [
  h('polyline', { points: '15 3 21 3 21 9' }),
  h('polyline', { points: '9 21 3 21 3 15' }),
  h('line', { x1: 21, x2: 14, y1: 3, y2: 10 }),
  h('line', { x1: 3, x2: 10, y1: 21, y2: 14 })
]);

export const Minimize2 = makeIcon('Minimize2', () => [
  h('polyline', { points: '4 14 10 14 10 20' }),
  h('polyline', { points: '20 10 14 10 14 4' }),
  h('line', { x1: 14, x2: 21, y1: 10, y2: 3 }),
  h('line', { x1: 3, x2: 10, y1: 21, y2: 14 })
]);

export const RefreshCw = makeIcon('RefreshCw', () => [
  path('M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8'),
  path('M21 3v5h-5'),
  path('M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16'),
  path('M8 16H3v5')
]);
