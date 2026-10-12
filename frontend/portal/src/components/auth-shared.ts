// 认证页共用：输入框/按钮样式常量 + 60s 发码倒计时 composable
// （对应旧版 F2 前台 AuthShell.tsx 的样式导出与 auth-widgets.tsx 的 useCountdown）
import { onBeforeUnmount, ref } from 'vue';

/** 认证表单通用输入框样式 */
export const INPUT =
  'w-full rounded-lg border border-slate-200 bg-warm/70 px-4 py-3 text-base text-ink placeholder:text-slate-400 outline-none transition focus:border-tech focus:bg-white focus:ring-2 focus:ring-tech/20';

/** 认证表单主按钮样式 */
export const BTN_PRIMARY =
  'w-full rounded-lg bg-tech px-6 py-3 text-sm font-bold tracking-widest text-white shadow-[0_6px_18px_rgba(37,99,235,0.45)] transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60';

/** 次要按钮（描边） */
export const BTN_GHOST =
  'w-full rounded-lg border border-white/30 bg-white/5 px-6 py-3 text-sm font-semibold tracking-widest text-warm backdrop-blur-sm transition hover:border-white/60 hover:bg-white/15';

/** 发码后 60 秒倒计时，到 0 可重发 */
export function useCountdown() {
  const left = ref(0);
  let timer: number | undefined;

  const start = () => {
    left.value = 60;
    window.clearInterval(timer);
    timer = window.setInterval(() => {
      left.value -= 1;
      if (left.value <= 0) window.clearInterval(timer);
    }, 1000);
  };

  onBeforeUnmount(() => window.clearInterval(timer));
  return { left, start };
}
