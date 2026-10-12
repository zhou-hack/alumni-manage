// ============================================================================
// 优秀校友 · 共享数据源（AlumniSection 环形展厅 + HonorWall 荣誉墙 共用）
//
// 数据源：GET /alumni/outstanding?limit=16（仅 published），映射为组件既有的
//   OutstandingAlumni 结构。
//
// 可信度修复（2026-09-25 审查报告 P0-1 / P1-7）：
//   - 接口失败不再回退 mock 演示数据（mock 含 AI 肖像 + 样例证书，会被误读为真实记录）；
//     改为 state='error'，由各组件展示真实"加载失败/暂无"错误态 + 重试按钮。
//   - 头像缺省时为 '' —— 由渲染层使用 Avatar 组件或渐变占位兜底（已不按姓名生成 AI 肖像）。
//   - 模块级缓存：两组件共用一份请求，重复挂载不重复请求。
// ============================================================================
import { onMounted, ref } from 'vue';
import { api, type OutstandingItem } from '../api';
import {
  type HonorCategory,
  type OutstandingAlumni
} from '../mock/outstandingAlumni';

export type OutstandingState = 'loading' | 'ok' | 'empty' | 'error';

/** 头像缺省时为 '' —— 由渲染层使用 Avatar 组件或渐变占位兜底 */
const EMPTY_PHOTO = '';

/** OutstandingItem → OutstandingAlumni（组件层结构不变） */
export function mapOutstanding(o: OutstandingItem): OutstandingAlumni {
  return {
    name: o.name ?? '',
    photo: o.photo_url || EMPTY_PHOTO,
    message: o.message ?? '',
    college: o.college ?? '',
    gradYear: o.grad_year ? `${o.grad_year}届` : '',
    current: o.current ?? '',
    studentId: o.student_id ?? '',
    timeline: (o.timeline ?? []).map((t) => ({ year: t.year ?? '', event: t.event ?? '' })),
    awards: (o.awards ?? []).map((w) => ({
      title: w.title ?? '',
      year: w.year ?? '',
      level: w.level ?? '',
      category: (w.category as HonorCategory | undefined) ?? '奖项'
    }))
  };
}

// ------------------------------ 模块级缓存 ------------------------------
const list = ref<OutstandingAlumni[]>([]);
const state = ref<OutstandingState>('loading');
let inflight: Promise<void> | null = null;

async function fetchOutstanding(): Promise<void> {
  if (inflight) return inflight;
  state.value = 'loading';
  inflight = (async () => {
    try {
      const res = await api.alumniPublic.outstanding(16);
      const mapped = (res.list ?? []).map(mapOutstanding);
      list.value = mapped;
      state.value = mapped.length ? 'ok' : 'empty';
    } catch (e) {
      console.warn('[outstanding] 优秀校友接口请求失败，保持空列表显示错误态：', e);
      list.value = [];
      state.value = 'error';
    }
  })();
  try {
    await inflight;
  } finally {
    inflight = null;
  }
}

/** AlumniSection / HonorWall 各自调用：模块级缓存保证一次请求 */
export function useOutstanding() {
  onMounted(() => {
    void fetchOutstanding();
  });
  return { alumni: list, state, reload: fetchOutstanding };
}
