<script setup lang="ts">
// ============================================================================
// 校友留言墙（位于"优秀校友"下方）
//
// 效果：
//   1. 页面内墙板：便签错落分布、逐张上浮入场 + 持续轻微浮动，可自由拖拽（限制在墙板内）。
//      留言很多时墙板只展示最新 18 张，全部留言在全屏幕布中查看。
//   2. 全屏幕布：以墙板为起点幕布式铺满整屏（transform 位移动画：展开时内容延迟淡入，
//      收拢时内容随面板一起收缩归位、末段淡出，全程无空白留白）。
//      这是一块随便签数量自动增长的"大画布"——
//      便签按顺序自动分配网格槽位（槽内带确定性抖动，保持手工张贴感），拖动空白处平移、
//      滚轮以光标为中心缩放（50%~275%）。只有视野附近的便签会被渲染（视口裁剪），
//      因此数千条留言也能流畅浏览；便签仍可拖动，拖出的位置在幕布关闭前一直有效。
//   3. 右上角"新建留言"——提交后进入审核，通过后上墙；
//      关闭幕布：ESC 或右上角"收拢幕布"。
//
// 数据：墙内便签全部来自 GET /wall/messages（仅 approved），每次刷新都重新请求，
//       不使用 localStorage 缓存（ legacy 缓存已在挂载时清除，避免脏乱数据）；
//       接口失败不再回退 NOTES 演示数据，改为空态 + 重试按钮（审查报告 P1-7 / P0-4）。
//       新建留言走 POST /wall/messages（需登录，新留言 pending 待审核，通过后上墙）。
// ============================================================================
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch, type CSSProperties } from 'vue';
import { useRouter } from 'vue-router';
import ModuleTitle from './ModuleTitle.vue';
import { Reveal } from './Reveal';
import { Maximize2 } from './icons';
import FullWall from './FullWall.vue';
import NotePaper from './NotePaper.vue';
import DraftModal from './DraftModal.vue';
import {
  INLINE_MAX,
  STORAGE_KEY,
  ZERO_OFFSET,
  fromPublic,
  type Draft,
  type Note
} from './messageWall';
import { api, type WallMyItem } from '../api';
import { authState } from '../auth';

const router = useRouter();

const drafting = ref(false);
const full = ref(false);
// 操作结果提示（2.2s 自动消失，与校友档案索引同一手法）
const notice = ref('');
watch(notice, (n, _prev, onCleanup) => {
  if (!n) return;
  const t = window.setTimeout(() => (notice.value = ''), 2200);
  onCleanup(() => window.clearTimeout(t));
});

// 公开留言 + 我的留言状态（审查报告 P0-4：失败不再回退 NOTES mock）
type RemoteState = 'loading' | 'ok' | 'empty' | 'error';
const remote = ref<Note[]>([]);
const remoteState = ref<RemoteState>('loading');
const reload = async () => {
  remoteState.value = 'loading';
  try {
    const res = await api.wall.list({ page: '1', page_size: '100' });
    const list = (res.list ?? []).map((m, i) => fromPublic(m, i));
    remote.value = list;
    remoteState.value = list.length ? 'ok' : 'empty';
  } catch (e) {
    console.warn('[message-wall] 留言墙接口请求失败，保持空态：', e);
    remote.value = [];
    remoteState.value = 'error';
  }
};
onMounted(async () => {
  // 清除 legacy 本地缓存便签（历史版本把用户新增便签存 localStorage，刷新后会产生脏乱数据）
  localStorage.removeItem(STORAGE_KEY);
  await reload();
  if (authState.user) void loadMine();
});

// 我的留言（审查报告 P2：留言提交后用户应能追踪状态/驳回原因/撤回）
const myList = ref<WallMyItem[]>([]);
const myLoaded = ref(false);
const myPanel = ref(false);
const loadMine = async () => {
  if (!authState.user) {
    myList.value = [];
    myLoaded.value = true;
    return;
  }
  try {
    const res = await api.wall.mine();
    myList.value = res.list ?? [];
  } catch (e) {
    console.warn('[message-wall] 我的留言加载失败：', e);
  } finally {
    myLoaded.value = true;
  }
};
const toggleMyPanel = () => {
  myPanel.value = !myPanel.value;
  if (myPanel.value && authState.user && !myLoaded.value) void loadMine();
};
const statusMeta: Record<string, { label: string; chip: string }> = {
  approved: { label: '已展示', chip: 'bg-green-50 text-green-600' },
  rejected: { label: '已驳回', chip: 'bg-red-50 text-red-600' },
  pending: { label: '待审核', chip: 'bg-amber-50 text-amber-600' },
  hidden: { label: '已下架', chip: 'bg-slate-100 text-slate-500' }
};
const removingId = ref<number | null>(null);
const removeMine = async (id: number) => {
  if (!window.confirm('确认撤回这条留言吗？撤回后将不再展示。')) return;
  removingId.value = id;
  try {
    await api.wall.remove(id);
    notice.value = '留言已撤回';
    await loadMine();
    void reload();
  } catch (e) {
    notice.value = e instanceof Error ? e.message : '撤回失败，请稍后重试';
  } finally {
    removingId.value = null;
  }
};

const notes = computed(() => remote.value);
// 幕布起点：页面板块的视口矩形换算为面板的 transform 参数（中心位移 + 缩放），
// 展开/收拢动画用 transform 实现（GPU 合成），不做逐帧裁剪重绘
const originRect = ref({ dx: 0, dy: 0, sx: 0.92, sy: 0.92 });
const inlineRef = ref<HTMLDivElement | null>(null);
// 页面内墙板只展示最新的若干张（用户新增的排在最后），全部留言在全屏幕布中查看
const inlineNotes = computed(() =>
  notes.value.length > INLINE_MAX ? notes.value.slice(notes.value.length - INLINE_MAX) : notes.value
);

const openFull = () => {
  const rect = inlineRef.value?.getBoundingClientRect();
  if (rect) {
    originRect.value = {
      dx: rect.left + rect.width / 2 - window.innerWidth / 2,
      dy: rect.top + rect.height / 2 - window.innerHeight / 2,
      sx: rect.width / window.innerWidth,
      sy: rect.height / window.innerHeight
    };
  }
  full.value = true;
};

// ------------------------------ 页面内墙板：入场 / 浮动 / 拖拽 ------------------------------
// 整块墙进入视口后再统一播放入场动画，避免每张便签各自判断导致节奏错乱
const wallShown = ref(false);
let wallIO: IntersectionObserver | null = null;

onMounted(() => {
  const el = inlineRef.value;
  if (!el) return;
  wallIO = new IntersectionObserver(
    (entries) => {
      if (entries.some((en) => en.isIntersecting)) {
        wallShown.value = true;
        wallIO?.disconnect();
        wallIO = null;
      }
    },
    { threshold: 0.25 }
  );
  wallIO.observe(el);
});

// 拖拽置顶：记录每张便签被拖动后的层级
const zMapInline = reactive<Record<number, number>>({});
let zCounterInline = 20 + inlineNotes.value.length;
const raiseInline = (id: number) => {
  zCounterInline += 1;
  zMapInline[id] = zCounterInline;
};

// 手写 PointerEvent 拖拽（对应 React 版 framer 的 drag + dragConstraints + dragElastic 0.14）：
// 位移保存在 dragOffsets（px），越界部分按 0.14 弹性衰减；松手后把越界部分收回墙板内（CSS 过渡回弹）
const dragOffsets = reactive<Record<number, { x: number; y: number }>>({});
const inlineDraggingId = ref<number | null>(null);
const snappingId = ref<number | null>(null);
let snapTimer: ReturnType<typeof setTimeout> | undefined;

interface InlineDrag {
  id: number;
  px: number;
  py: number;
  ox: number;
  oy: number;
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
}
let inlineDrag: InlineDrag | null = null;

// 弹性钳制：越界部分按 0.14 衰减（对应 framer 的 dragElastic）
const elastic = (v: number, min: number, max: number) =>
  v < min ? min + (v - min) * 0.14 : v > max ? max + (v - max) * 0.14 : v;

function onNoteDown(e: PointerEvent, n: Note) {
  if (e.button !== 0) return;
  const el = e.currentTarget as HTMLElement;
  const board = inlineRef.value;
  if (!board) return;
  const cur = dragOffsets[n.id] ?? ZERO_OFFSET;
  // 便签未位移时相对墙板的位置（墙板 relative 是定位基准），据此算出位移合法区间（限制在墙板内）
  inlineDrag = {
    id: n.id,
    px: e.clientX,
    py: e.clientY,
    ox: cur.x,
    oy: cur.y,
    minX: -el.offsetLeft,
    maxX: board.clientWidth - el.offsetLeft - el.offsetWidth,
    minY: -el.offsetTop,
    maxY: board.clientHeight - el.offsetTop - el.offsetHeight
  };
  el.setPointerCapture(e.pointerId);
  // 重新按下时取消上一次回弹过渡
  clearTimeout(snapTimer);
  snappingId.value = null;
  inlineDraggingId.value = n.id;
  raiseInline(n.id);
}
function onNoteMove(e: PointerEvent) {
  const s = inlineDrag;
  if (!s) return;
  dragOffsets[s.id] = {
    x: elastic(s.ox + e.clientX - s.px, s.minX, s.maxX),
    y: elastic(s.oy + e.clientY - s.py, s.minY, s.maxY)
  };
}
function onNoteUp() {
  const s = inlineDrag;
  if (!s) return;
  inlineDrag = null;
  inlineDraggingId.value = null;
  // 松手后把越界部分收回墙板内（对应 framer 约束回弹），收回过程走 CSS 过渡
  const cur = dragOffsets[s.id];
  if (cur) {
    const cx = Math.min(s.maxX, Math.max(s.minX, cur.x));
    const cy = Math.min(s.maxY, Math.max(s.minY, cur.y));
    if (cx !== cur.x || cy !== cur.y) {
      snappingId.value = s.id;
      dragOffsets[s.id] = { x: cx, y: cy };
      clearTimeout(snapTimer);
      snapTimer = setTimeout(() => {
        snappingId.value = null;
      }, 350);
    }
  }
}

// 便签外层：百分比坐标 + 拖动位移 + 角度（拖动中无过渡，保证 1:1 跟手；回弹时 0.3s 过渡）。
// 手机端便签 132px、桌面 176px：横坐标按当前便签宽度钳制在墙板内，窄屏右侧便签不被裁掉
function noteStyle(n: Note, i: number): CSSProperties {
  const off = dragOffsets[n.id] ?? ZERO_OFFSET;
  const noteW = typeof window !== 'undefined' && window.innerWidth < 768 ? 132 : 176;
  return {
    left: `min(${n.left}, calc(100% - ${noteW}px))`,
    top: n.top,
    zIndex: zMapInline[n.id] ?? 10 + i,
    transform: `translate(${off.x}px, ${off.y}px) rotate(${n.rotate}deg)`,
    transition: snappingId.value === n.id ? 'transform 0.3s cubic-bezier(0.22,0.72,0.28,1)' : 'none',
    touchAction: 'none' // framer 会为可拖拽元素设置 touch-action:none，保证触屏可拖
  };
}

// 拖拽态：放大 1.07 倍 + 加深阴影（独立于位移层，0.25s 过渡）
function dragFxStyle(n: Note): CSSProperties {
  const dragging = inlineDraggingId.value === n.id;
  return {
    transform: dragging ? 'scale(1.07)' : 'none',
    boxShadow: dragging ? '0 20px 44px rgba(26,16,61,0.24)' : '0 0px 0px rgba(26,16,61,0)',
    transition: 'transform 0.25s, box-shadow 0.25s'
  };
}

// 入场：逐张上浮出现（对应 framer 的 initial opacity 0 / y 40 / scale 0.9，0.55s，逐张错峰 0.05s，前 8 张封顶）
function enterStyle(i: number): CSSProperties {
  const delay = 0.05 * Math.min(i, 8);
  return {
    opacity: wallShown.value ? 1 : 0,
    transform: wallShown.value ? 'none' : 'translateY(40px) scale(0.9)',
    transition: `opacity 0.55s cubic-bezier(0.22,0.72,0.28,1) ${delay}s, transform 0.55s cubic-bezier(0.22,0.72,0.28,1) ${delay}s`
  };
}

// 常驻：极缓的上下浮动，营造墙面"呼吸"感（仅页面内墙板，数量少不影响性能）。
// framer 的 duration 是单程时长、reverse 往返，CSS 动画周期取 2 倍；进入视口后才开始
function floatStyle(i: number): CSSProperties {
  if (!wallShown.value) return {};
  return {
    animation: `mw-float ${2 * (3.4 + (i % 4) * 0.5)}s ease-in-out ${0.55 + (i % 6) * 0.18}s infinite`
  };
}

// ------------------------------ 幕布：滚动锁定 + ESC ------------------------------
// 全屏时锁定页面滚动：position:fixed + scrollTop 记录方案
//（不能用 overflow:hidden，Chrome 会重置滚动位置）；收拢动画结束后才恢复
//（见 Transition after-leave，避免滚动条在动画第一帧重新出现引发整页重排）
let savedScrollY = 0;
function lockScroll() {
  savedScrollY = window.scrollY;
  document.body.style.position = 'fixed';
  document.body.style.top = `-${savedScrollY}px`;
  document.body.style.left = '0';
  document.body.style.right = '0';
}
function restoreScroll() {
  document.body.style.position = '';
  document.body.style.top = '';
  document.body.style.left = '';
  document.body.style.right = '';
  window.scrollTo(0, savedScrollY);
}
const onKey = (e: KeyboardEvent) => {
  if (e.key === 'Escape') full.value = false;
};

watch(full, (v) => {
  if (v) {
    lockScroll();
    window.addEventListener('keydown', onKey);
  } else {
    window.removeEventListener('keydown', onKey);
  }
});

onBeforeUnmount(() => {
  wallIO?.disconnect();
  clearTimeout(snapTimer);
  window.removeEventListener('keydown', onKey);
  if (full.value) restoreScroll(); // 组件卸载时幕布仍开着，立即恢复滚动（无动画）
});

// 幕布动画的起点 transform 参数注入 CSS 变量（--mw-*），供 curtain 过渡类使用
const curtainVars = computed<CSSProperties>(() => ({
  '--mw-dx': `${originRect.value.dx}px`,
  '--mw-dy': `${originRect.value.dy}px`,
  '--mw-sx': String(originRect.value.sx),
  '--mw-sy': String(originRect.value.sy)
}));

// 新建留言：未登录 → 提示并跳登录页；已登录 → POST /wall/messages（仅 content，
// 姓名/届次/学院服务端从登录身份带出）。新留言 pending 待审核，通过后才上墙；
// 提交中禁用按钮防连点，成功后弹确认框（明确告知审核流程，避免重复提交）
const submitting = ref(false);
const submitted = ref(false); // 提交成功确认弹窗
const submitNote = async (draft: Draft) => {
  if (submitting.value) return;
  if (!authState.user) {
    drafting.value = false;
    notice.value = '请先登录';
    router.push({ path: '/login', query: { from: '/' } });
    return;
  }
  submitting.value = true;
  try {
    await api.wall.create({ content: draft.text, show_name: draft.showName });
    drafting.value = false;
    submitted.value = true;
  } catch (e) {
    console.warn('[message-wall] 提交留言失败：', e);
    notice.value = e instanceof Error ? e.message : '提交失败，请稍后重试';
  } finally {
    submitting.value = false;
  }
};

const btn = 'flex items-center gap-2 rounded-xl border px-4 py-2.5 text-sm font-semibold transition';
</script>

<template>
  <section id="message-wall" class="mt-16 scroll-mt-24">
    <div class="relative overflow-hidden bg-warm py-14">
      <!-- 背景微光，与其它模块保持同一套点缀手法 -->
      <div class="pointer-events-none absolute left-1/2 top-0 h-40 w-[60%] -translate-x-1/2 rounded-full bg-lamp/10 blur-3xl" />

      <ModuleTitle
        eyebrow="MESSAGE WALL"
        title="校友留言墙"
        subtitle="把想说的话，留在城堡的墙上"
        ornament
        glow
        class="mb-8"
      />

      <!-- 留言墙板块：与上方模块同宽（随屏收敛），便签在其中自由散布并可拖拽 -->
      <Reveal :delay="0.08" class="relative mx-auto w-[92%] max-w-[1500px] md:w-[86%] xl:w-[80%]">
        <!-- 操作按钮 -->
        <div class="mb-4 flex flex-wrap justify-end gap-3">
          <button
            v-if="authState.user"
            type="button"
            :class="`${btn} border-ink/10 bg-white text-ink/70 hover:border-ink/30 hover:text-ink`"
            @click="toggleMyPanel"
          >
            {{ myPanel ? '收起我的留言' : '我的留言' }}
            <span
              v-if="myList.length"
              class="rounded-full bg-tech/15 px-1.5 text-[10px] font-bold text-tech"
            >
              {{ myList.length }}
            </span>
          </button>
          <button
            type="button"
            :class="`${btn} border-tech/35 bg-tech/5 text-tech hover:border-tech hover:bg-tech hover:text-white`"
            @click="drafting = true"
          >
            <span class="text-base leading-none">＋</span>
            新建留言
          </button>
          <button
            type="button"
            :class="`${btn} border-lamp/50 bg-lamp/10 text-[#a97b00] hover:border-lamp hover:bg-lamp hover:text-night`"
            @click="openFull"
          >
            <Maximize2 :size="16" />
            全屏展开
          </button>
        </div>

        <!-- 我的留言折叠面板（审查报告 P2-3） -->
        <Transition
          enter-active-class="transition-all duration-200 ease-out"
          enter-from-class="opacity-0 -translate-y-2"
          enter-to-class="opacity-100 translate-y-0"
          leave-active-class="transition-all duration-150 ease-in"
          leave-from-class="opacity-100 translate-y-0"
          leave-to-class="opacity-0 -translate-y-2"
        >
          <section
            v-if="myPanel && authState.user"
            class="mb-4 rounded-2xl border border-tech/20 bg-tech/5 p-4 shadow-[0_6px_18px_rgba(5,10,30,0.06)]"
          >
            <div class="flex items-baseline justify-between gap-2">
              <h3 class="font-title text-sm font-bold text-ink">我的留言</h3>
              <p class="text-[11px] text-slate-500">查看状态、驳回原因或撤回</p>
            </div>
            <p v-if="!myLoaded" class="mt-2 text-[12px] text-slate-500">加载中…</p>
            <p v-else-if="!myList.length" class="mt-2 text-[12px] text-slate-500">尚未发表过留言</p>
            <ul v-else class="mt-3 space-y-2">
              <li
                v-for="m in myList"
                :key="m.id"
                class="rounded-lg bg-white px-3 py-2 shadow-sm ring-1 ring-slate-100"
              >
                <div class="flex items-start gap-2">
                  <span
                    class="mt-0.5 shrink-0 rounded-md px-2 py-0.5 text-[10px] font-bold"
                    :class="(statusMeta[m.status] ?? statusMeta.pending).chip"
                  >
                    {{ (statusMeta[m.status] ?? statusMeta.pending).label }}
                  </span>
                  <p class="min-w-0 flex-1 text-[13px] leading-relaxed text-ink">{{ m.content }}</p>
                </div>
                <p v-if="m.review_note" class="mt-1 text-[11px] text-red-500">
                  审核备注：{{ m.review_note }}
                </p>
                <div class="mt-1.5 flex items-center justify-between">
                  <p class="font-code text-[10px] text-slate-400">{{ m.created_at }}</p>
                  <button
                    type="button"
                    :disabled="removingId === m.id"
                    class="text-[11px] font-semibold text-tech transition disabled:opacity-50 hover:text-blue-700"
                    @click="removeMine(m.id)"
                  >
                    {{ removingId === m.id ? '撤回中…' : '撤回' }}
                  </button>
                </div>
              </li>
            </ul>
          </section>
        </Transition>

        <!-- 页面内墙板：墙板 relative 定位是便签百分比坐标与拖拽约束的基准 -->
        <div
          ref="inlineRef"
          class="relative h-[440px] overflow-hidden rounded-[15px] border border-lamp/25 bg-white/55 md:h-[540px]"
        >
          <!-- 加载 / 空 / 错误：真实状态展示，不再用 NOTES mock 兜底（审查报告 P0-4） -->
          <div
            v-if="remoteState === 'loading'"
            class="flex h-full items-center justify-center text-sm text-slate-400"
          >
            留言加载中…
          </div>
          <div
            v-else-if="remoteState === 'error'"
            class="flex h-full flex-col items-center justify-center gap-3 px-6 text-center"
          >
            <p class="text-sm font-semibold text-ink">留言墙加载失败</p>
            <p class="max-w-sm text-[12px] leading-relaxed text-slate-500">
              暂时无法获取校友留言，请稍后重试。
            </p>
            <button
              type="button"
              class="rounded-lg border border-lamp/40 bg-white px-4 py-1.5 text-[12px] font-semibold text-ink transition hover:border-lamp hover:bg-lamp/10"
              @click="reload()"
            >
              重新加载
            </button>
          </div>
          <div
            v-else-if="remoteState === 'empty' || notes.length === 0"
            class="flex h-full flex-col items-center justify-center gap-1 px-6 text-center"
          >
            <p class="text-sm font-semibold text-ink">校友留言墙暂为空</p>
            <p class="max-w-sm text-[12px] text-slate-500">
              暂无已审核通过的校友留言，欢迎成为第一位留言者。
            </p>
          </div>
          <div
            v-for="(n, i) in inlineNotes"
            :key="n.id"
            class="absolute cursor-grab active:cursor-grabbing"
            :style="noteStyle(n, i)"
            @pointerdown="onNoteDown($event, n)"
            @pointermove="onNoteMove"
            @pointerup="onNoteUp"
            @pointercancel="onNoteUp"
          >
            <!-- 拖拽态：置顶 + 放大 1.07 + 加深阴影 -->
            <div :style="dragFxStyle(n)">
              <!-- 入场：逐张上浮出现 -->
              <div :style="enterStyle(i)">
                <!-- 常驻浮动（呼吸感） -->
                <div :style="floatStyle(i)">
                  <NotePaper :n="n" class="h-[132px] w-[132px] md:h-[176px] md:w-[176px]" />
                </div>
              </div>
            </div>
          </div>
        </div>

        <p class="relative mt-6 text-center text-xs tracking-widest text-slate-400">
          {{
            notes.length > INLINE_MAX
              ? `墙内展示最新 ${INLINE_MAX} 张 · 已收录 ${notes.length} 条校友留言，全屏幕布可查看全部`
              : `按住便签即可拖动 · 已收录 ${notes.length} 条校友留言`
          }}
        </p>
      </Reveal>
    </div>

    <!-- ===== 全屏幕布：从墙面板块所在矩形展开到整屏 ===== -->
    <Transition name="curtain" :duration="{ enter: 600, leave: 380 }" @after-leave="restoreScroll">
      <div v-if="full" class="fixed inset-0 z-[80]" :style="curtainVars">
        <!-- 压暗背景：不加 backdrop-blur —— 大面积背景模糊与位移动画叠加会触发逐帧重采样（卡顿源） -->
        <div class="curtain-backdrop absolute inset-0 bg-night/75" @click="full = false" />
        <!-- 幕布面板：纯 transform 位移动画（GPU 合成），不动画 borderRadius ——
             圆角逐帧改写会让全屏面板每帧重绘。收拢时内容随面板一起收缩归位（窗口最小化式），
             不再先淡出留白；收缩更快（0.38s）且向墙板加速 -->
        <div class="curtain-panel absolute -inset-px overflow-hidden bg-warm will-change-transform">
          <!-- 幕布内容：展开时待面板铺开后再淡入；收拢时随面板一起收缩，
               仅在最后 25% 行程淡出，与墙板原位交接，全程无空白面板阶段 -->
          <div class="curtain-content absolute inset-0">
            <div class="pointer-events-none absolute left-1/2 top-0 h-40 w-[60%] -translate-x-1/2 rounded-full bg-lamp/10 blur-3xl" />

            <FullWall :notes="notes" @close="full = false" @new="drafting = true" />
          </div>
        </div>
      </div>
    </Transition>

    <!-- ===== 新建留言弹窗 ===== -->
    <Transition name="draft">
      <DraftModal v-if="drafting" :submitting="submitting" @close="drafting = false" @save="submitNote" />
    </Transition>

    <!-- ===== 提交成功确认弹窗：明确审核流程，防止误以为没提交上而反复提交 ===== -->
    <Transition name="draft">
      <div
        v-if="submitted"
        class="fixed inset-0 z-[95] flex items-center justify-center bg-night/70 px-4 backdrop-blur-sm"
        @click="submitted = false"
      >
        <div
          class="w-full max-w-sm rounded-3xl border border-lamp/40 bg-warm p-7 text-center shadow-2xl"
          role="alertdialog"
          aria-label="留言已提交"
          @click.stop
        >
          <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-tech/10 text-2xl">📝</div>
          <h3 class="font-title mt-4 text-xl font-black text-ink">留言已提交</h3>
          <p class="mt-2 text-sm leading-relaxed text-slate-500">
            你的留言已进入审核队列，请耐心等待审核结果。<br />
            审核通过后会自动展示在留言墙上，无需重复提交。
          </p>
          <button
            type="button"
            class="mt-6 w-full rounded-xl bg-tech px-6 py-2.5 text-sm font-bold text-white shadow-[0_6px_16px_rgba(37,99,235,0.32)] transition hover:bg-blue-700"
            @click="submitted = false"
          >
            知道了
          </button>
        </div>
      </div>
    </Transition>

    <!-- 操作结果提示（2.2s 自动消失） -->
    <div
      v-if="notice"
      role="status"
      class="fixed left-1/2 top-[84px] z-[100] -translate-x-1/2 rounded-full bg-ink/90 px-5 py-2.5 text-sm text-white shadow-xl"
    >
      {{ notice }}
    </div>
  </section>
</template>

<style>
/* 常驻浮动关键帧（对应 framer 的 y:0 → -9 往返）：scoped 样式会改写关键帧名导致内联
   animation 引用不到，因此放在非 scoped 样式块中 */
@keyframes mw-float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-9px);
  }
}

/* ===== 全屏幕布：展开/收拢动画（对应 React 版 AnimatePresence + motion） ===== */
/* 压暗背景：0.3s 淡入淡出 */
.curtain-enter-active .curtain-backdrop,
.curtain-leave-active .curtain-backdrop {
  transition: opacity 0.3s;
}
.curtain-enter-from .curtain-backdrop,
.curtain-leave-to .curtain-backdrop {
  opacity: 0;
}

/* 幕布面板：纯 transform 位移动画（GPU 合成），originRect 由 --mw-* 变量注入。
   展开 0.55s [0.22,0.72,0.28,1]；收拢 0.38s [0.5,0,0.75,0.4] 向墙板加速 */
.curtain-enter-active .curtain-panel {
  transition: transform 0.55s cubic-bezier(0.22, 0.72, 0.28, 1);
}
.curtain-leave-active .curtain-panel {
  transition: transform 0.38s cubic-bezier(0.5, 0, 0.75, 0.4);
}
.curtain-enter-from .curtain-panel,
.curtain-leave-to .curtain-panel {
  transform: translate(var(--mw-dx), var(--mw-dy)) scale(var(--mw-sx), var(--mw-sy));
}

/* 幕布内容：展开时延迟 0.32s 后 0.28s 淡入；收拢时前 75% 行程保持不透明、
   最后 25% 行程淡出（0.285s 延迟 + 0.095s 淡出，即 opacity [1,1,0] times [0,0.75,1]） */
.curtain-enter-active .curtain-content {
  transition: opacity 0.28s ease 0.32s;
}
.curtain-leave-active .curtain-content {
  transition: opacity 0.095s ease 0.285s;
}
.curtain-enter-from .curtain-content,
.curtain-leave-to .curtain-content {
  opacity: 0;
}
</style>
