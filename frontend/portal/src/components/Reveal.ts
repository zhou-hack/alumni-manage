// ============================================================================
// 站点统一的滚动入场动效（Vue 版，与新闻列表同一套节奏：0.6s / [0.22,0.61,0.36,1]）
//
// 一级动画（板块入场）：<Reveal>——进入视口后整体上浮淡入，用于卡片、展板等大元素；
// 二级动画（级联入场）：<RevealGroup> + <RevealItem>——父容器进入视口后，
//   子元素按 80ms 错峰依次上浮，用于列表行、指标卡、按钮组等成组元素。
//
// 均只播一次（once），滚动回看不重复触发，避免打扰阅读。
// 实现：IntersectionObserver + CSS transition，无第三方动效依赖。
// ============================================================================
import {
  defineComponent,
  h,
  inject,
  onBeforeUnmount,
  onMounted,
  provide,
  ref,
  type InjectionKey,
  type Ref
} from 'vue';

// 统一缓动（与全站动效一致）
const EASE = 'cubic-bezier(0.22, 0.61, 0.36, 1)';

/** 进入视口（amount 比例）后把 shown 置 true；once 触发后断开观察 */
function useInViewOnce(amount: number) {
  const el = ref<HTMLElement | null>(null);
  const shown = ref(false);
  let io: IntersectionObserver | null = null;

  onMounted(() => {
    if (!el.value) return;
    io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          shown.value = true;
          io?.disconnect();
          io = null;
        }
      },
      { threshold: amount }
    );
    io.observe(el.value);
  });
  onBeforeUnmount(() => io?.disconnect());

  return { el, shown };
}

// ------------------------------ 一级：板块入场 ------------------------------
export const Reveal = defineComponent({
  name: 'Reveal',
  props: {
    /** 延迟（s），同级多个板块可按 0.05~0.1 递增制造先后 */
    delay: { type: Number, default: 0 },
    /** 初始下偏移（px） */
    y: { type: Number, default: 18 }
  },
  setup(props, { slots }) {
    const { el, shown } = useInViewOnce(0.15);
    return () =>
      h(
        'div',
        {
          ref: el,
          style: {
            opacity: shown.value ? 1 : 0,
            transform: shown.value ? 'translateY(0px)' : `translateY(${props.y}px)`,
            transition: `opacity 0.6s ${EASE} ${props.delay}s, transform 0.6s ${EASE} ${props.delay}s`,
            willChange: 'opacity, transform'
          }
        },
        slots.default?.()
      );
  }
});

// ------------------------------ 二级：组内级联 ------------------------------
interface RevealGroupCtx {
  shown: Ref<boolean>;
  /** 子项注册序号（决定错峰延迟） */
  register: () => number;
}
const GROUP_KEY: InjectionKey<RevealGroupCtx> = Symbol('RevealGroup');

export const RevealGroup = defineComponent({
  name: 'RevealGroup',
  props: {
    /** 触发时机（组进入视口比例），列表较高时调小 */
    amount: { type: Number, default: 0.15 }
  },
  setup(props, { slots }) {
    const { el, shown } = useInViewOnce(props.amount);
    let seq = 0;
    provide<RevealGroupCtx>(GROUP_KEY, { shown, register: () => seq++ });
    return () => h('div', { ref: el }, slots.default?.());
  }
});

export const RevealItem = defineComponent({
  name: 'RevealItem',
  setup(_, { slots }) {
    const group = inject<RevealGroupCtx | null>(GROUP_KEY, null);
    const index = group ? group.register() : 0;
    // 组内级联：0.05s 基础延迟 + 80ms 错峰；无组时立即可见（兜底）
    const fallbackShown = ref(!group);
    return () => {
      const shown = group ? group.shown.value : fallbackShown.value;
      const delay = 0.05 + index * 0.08;
      return h(
        'div',
        {
          style: {
            opacity: shown ? 1 : 0,
            transform: shown ? 'translateY(0px)' : 'translateY(14px)',
            transition: `opacity 0.5s ${EASE} ${delay}s, transform 0.5s ${EASE} ${delay}s`,
            willChange: 'opacity, transform'
          }
        },
        slots.default?.()
      );
    };
  }
});
