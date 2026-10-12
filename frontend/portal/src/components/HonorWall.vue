<script setup lang="ts">
// ============================================================================
// 荣誉墙（首页 · 位于"优秀校友"下方）
//
// 按类别陈列的证书/荣誉展示墙（PRD M03-04 / M05-02）——「图片 + 文字」形式：
//   1. 顶部 6 张类别精选大图卡片（图片为底 + 文字叠加），每张突出类别名 + 荣誉数量 + 最新一项荣誉；
//      类别字典与 PRD M03-03 对齐：奖学金 / 三好学生 / 优秀干部 / 证书 / 奖项 / 社会贡献。
//   2. 点击任一类别卡片，在下方平滑展开该类别的全部荣誉详情列表（文字行，含获奖人/学院/届别/级别/年份）；
//      再次点击同一卡片或点击"收起"可折叠。
//   3. 图片素材沿用项目统一的 textToImage 图源，按类别定制写实场景 prompt。
//
// 数据：优秀校友接口数据平铺（与 AlumniSection 共用 useOutstanding 模块级缓存，
//       awards 无 category 归"奖项"），接口失败展示真实错误态，不再回退 mock。
//
// 可信度修复（2026-09-25 审查报告 P0-1 / P0-2）：
//   - 移除 sample-certificate.svg 兜底（样例证书不能被当作真实原件）；
//     无附件时荣誉项不显示"荣誉证书"按钮，改为"文字记录"标注。
//   - 荣誉类别背景图用 textToImage 合成（不展示真实证书图片），类别卡只承载氛围。
// ============================================================================
import { computed, onMounted, ref } from 'vue';
import ModuleTitle from './ModuleTitle.vue';
import { Reveal, RevealGroup, RevealItem } from './Reveal';
import { Award } from './icons';
import AttachmentViewer from './AttachmentViewer.vue';
import { type HonorAttachment, type HonorCategory } from '../mock/outstandingAlumni';
import { textToImage } from '../utils/image';
import { useOutstanding } from './useOutstanding';
import { api, type ScreenFeedItem } from '../api';

const { alumni, state: outstandingState, reload } = useOutstanding();

/** 大屏轮播（荣誉墙管理 → 上架）数据源 */
const screenFeed = ref<ScreenFeedItem[]>([]);
const screenFeedLoaded = ref(false);
async function loadScreenFeed() {
  try {
    const res = await api.screen.feed();
    screenFeed.value = (res.list ?? []) as ScreenFeedItem[];
    screenFeedLoaded.value = true;
  } catch (e) {
    // 公开接口失败静默 —— 已有空态兜底
    console.warn('[HonorWall] /screen/feed 拉取失败：', e);
    screenFeed.value = [];
    screenFeedLoaded.value = true;
  }
}
onMounted(loadScreenFeed);

// ------------------------------ 数据平铺 ------------------------------
interface HonorItem {
  /** 唯一 key（校友学号 + 荣誉标题） */
  key: string;
  title: string;
  year: string;
  level: string;
  category: HonorCategory;
  /** 证书/获奖材料附件（可选，PRD M03-02） */
  attachments?: HonorAttachment[];
  /** 获奖人信息 */
  alumni: {
    name: string;
    college: string;
    gradYear: string;
  };
}

/** 根据 URL 后缀推断附件类型（image / pdf / word / excel） */
function detectKind(url: string): HonorAttachment['kind'] {
  const u = (url || '').toLowerCase().split('?')[0];
  if (/\.(png|jpe?g|gif|webp|bmp|svg|avif)$/.test(u)) return 'image';
  if (/\.pdf$/.test(u)) return 'pdf';
  if (/\.(docx?|rtf|odt)$/.test(u)) return 'word';
  if (/\.(xlsx?|csv|ods)$/.test(u)) return 'excel';
  // 兜底：有 URL 但无法识别 → 当作图片（覆盖大多数荣誉墙封面场景）
  return 'image';
}

/** 把 /screen/feed 项转成 HonorItem —— 缺省类别归"奖项"，年份取 grad_year */
function mapScreenFeedItem(item: ScreenFeedItem): HonorItem {
  const year = item.grad_year ? String(item.grad_year) : '';
  const atts: HonorAttachment[] = [];
  if (item.cover_url) {
    atts.push({
      url: item.cover_url,
      name: item.title ?? '封面',
      kind: detectKind(item.cover_url),
    });
  }
  return {
    key: `feed-${item.id}`,
    title: item.title ?? '',
    year,
    level: '',
    category: '奖项' as HonorCategory,
    attachments: atts,
    alumni: {
      name: item.owner ?? '',
      college: item.college ?? '',
      gradYear: year,
    },
  };
}

/** 平铺所有校友的荣誉 + 大屏轮播（荣誉墙管理 → 上架）的档案 */
const ALL_HONORS = computed<HonorItem[]>(() => {
  const fromAlumni = alumni.value.flatMap((a) =>
    a.awards.map((w) => ({
      key: `${a.studentId}-${w.title}`,
      title: w.title,
      year: w.year,
      level: w.level,
      category: (w.category ?? '奖项') as HonorCategory,
      attachments: w.attachments,
      alumni: { name: a.name, college: a.college, gradYear: a.gradYear },
    })),
  );
  const fromFeed = screenFeed.value.map(mapScreenFeedItem);
  // 简单合并；同名按 title 去重（保留先出现的）
  const seen = new Set<string>();
  const merged: HonorItem[] = [];
  for (const h of [...fromAlumni, ...fromFeed]) {
    const k = `${h.title}|${h.alumni.name}`;
    if (seen.has(k)) continue;
    seen.add(k);
    merged.push(h);
  }
  return merged;
});

/** PRD M03-03 类别字典（按业务排序：奖学金 → 三好 → 优秀干部 → 证书 → 奖项 → 社会贡献） */
const CATEGORY_ORDER: HonorCategory[] = ['奖学金', '三好学生', '优秀干部', '证书', '奖项', '社会贡献'];

// ------------------------------ 类别主题（图片 + 主色） ------------------------------
const CATEGORY_THEME: Record<
  HonorCategory,
  { bar: string; chip: string; label: string; en: string; img: string; desc: string }
> = {
  奖学金: {
    bar: '#C19A3F',
    chip: 'bg-[#C19A3F]/15 text-[#8A6B1F]',
    label: '奖学金',
    en: 'SCHOLARSHIP',
    img: textToImage(
      'Elegant golden scholarship certificate with red seal on wooden desk, warm library background with soft focus bookshelves, ceremonial academic atmosphere, realistic photography, soft natural light',
      'landscape_4_3'
    ),
    desc: '勤勉向学，荣誉加身'
  },
  三好学生: {
    bar: '#3E6B4F',
    chip: 'bg-[#3E6B4F]/15 text-[#2C4A37]',
    label: '三好学生',
    en: 'MERIT STUDENT',
    img: textToImage(
      'Three merit student certificate with green ribbon on podium, bright campus graduation ceremony background, warm sunlight, celebratory academic scene, realistic photography',
      'landscape_4_3'
    ),
    desc: '品学兼优，全面成长'
  },
  优秀干部: {
    bar: '#3D5A73',
    chip: 'bg-[#3D5A73]/15 text-[#2A4156]',
    label: '优秀干部',
    en: 'OUTSTANDING LEADER',
    img: textToImage(
      'Student leader medal with blue ribbon on podium with microphone, conference hall background with soft bokeh lights, leadership ceremony atmosphere, realistic photography',
      'landscape_4_3'
    ),
    desc: '勇担责任，服务同学'
  },
  证书: {
    bar: '#2563EB',
    chip: 'bg-[#2563EB]/15 text-[#1A46B8]',
    label: '证书',
    en: 'CERTIFICATE',
    img: textToImage(
      'Professional skill certificate with blue cover and gold emblem on modern office desk, laptop and technical books in soft focus, professional certification atmosphere, realistic photography',
      'landscape_4_3'
    ),
    desc: '技能傍身，持证上岗'
  },
  奖项: {
    bar: '#A63D2F',
    chip: 'bg-[#A63D2F]/15 text-[#7A2B1F]',
    label: '奖项',
    en: 'AWARDS',
    img: textToImage(
      'Golden trophy cup with red ribbon on stage, blurred competition audience and stage lights in background, victorious celebration atmosphere, dramatic lighting, realistic photography',
      'landscape_4_3'
    ),
    desc: '赛场争锋，载誉而归'
  },
  社会贡献: {
    bar: '#8B5CF6',
    chip: 'bg-[#8B5CF6]/15 text-[#6B3FD9]',
    label: '社会贡献',
    en: 'SOCIAL CONTRIBUTION',
    img: textToImage(
      'Volunteer service certificate with purple ribbon, hands holding sapling in soft focus background, warm community service atmosphere, golden hour light, realistic photography',
      'landscape_4_3'
    ),
    desc: '奉献社会，温暖他人'
  }
};

// ------------------------------ 主组件状态 ------------------------------
const open = ref<HonorCategory | ''>('');
// 当前正在查看的附件（点击荣誉项的"查看附件"按钮打开）
const attachment = ref<HonorAttachment | null>(null);

/** 有原件附件时返回第一件；无原件返回 null —— 调用方按 null 决定是否显示"文字记录"提示 */
const certOf = (h: HonorItem): HonorAttachment | null => h.attachments?.[0] ?? null;
/** 除首件外的其余附件（用于并列展示） */
const moreOf = (h: HonorItem) => (h.attachments ?? []).slice(1);

// 按类别分组（保持 CATEGORY_ORDER 顺序）
const groups = computed(() => {
  const m = new Map<HonorCategory, HonorItem[]>();
  ALL_HONORS.value.forEach((h) => {
    const list = m.get(h.category) ?? [];
    list.push(h);
    m.set(h.category, list);
  });
  return CATEGORY_ORDER.filter((c) => m.has(c)).map((c) => ({
    category: c,
    items: m.get(c)!.sort((a, b) => b.year.localeCompare(a.year)) // 同年份新→旧
  }));
});

const toggle = (c: HonorCategory) => {
  open.value = open.value === c ? '' : c;
};
const openGroup = computed(() => groups.value.find((g) => g.category === open.value));
</script>

<template>
  <section id="honor-wall" class="mt-16 scroll-mt-24">
    <div class="relative overflow-hidden bg-warm py-14">
      <!-- 背景微光，与其它模块保持同一套点缀手法 -->
      <div class="pointer-events-none absolute left-1/2 top-0 h-40 w-[60%] -translate-x-1/2 rounded-full bg-lamp/10 blur-3xl" />

      <ModuleTitle
        eyebrow="HONOR WALL"
        title="荣誉墙"
        subtitle="每一份荣誉，都是青春被点亮的证据"
        ornament
        glow
        class="mb-8"
      />

      <!-- 荣誉墙板块：与上方模块同宽（随屏收敛），卡片样式同留言墙 -->
      <Reveal :delay="0.08" class="relative mx-auto w-[92%] max-w-[1500px] md:w-[86%] xl:w-[80%]">
        <div class="rounded-[15px] border border-lamp/25 bg-white/55 p-5 md:p-6">
          <!-- 真实错误态/空态：接口失败或 0 条时不展示假数据（审查报告 P0-1） -->
          <div
            v-if="outstandingState === 'loading'"
            class="grid grid-cols-1 gap-4 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-6"
          >
            <div v-for="i in 6" :key="i" class="aspect-[4/3] animate-pulse rounded-2xl bg-ink/8" />
          </div>
          <div
            v-else-if="outstandingState === 'error'"
            class="flex flex-col items-center gap-3 py-12 text-center"
          >
            <p class="font-title text-base font-bold text-ink">荣誉墙数据加载失败</p>
            <p class="max-w-md text-[12px] leading-relaxed text-slate-500">
              我们没能获取到已公开的校友荣誉数据，请稍后重试。如多次出现，请联系平台管理员。
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
            v-else-if="ALL_HONORS.length === 0"
            class="flex flex-col items-center gap-2 py-12 text-center"
          >
            <p class="font-title text-base font-bold text-ink">暂无公开荣誉记录</p>
            <p class="max-w-md text-[12px] leading-relaxed text-slate-500">
              尚无审核通过并完成展示授权的荣誉。校友可在个人中心提交荣誉记录，待审核与授权后将自动出现在此处。
            </p>
          </div>
          <!-- 类别精选大图卡片：1 / 2 / 3 / 6 列自适应 -->
          <RevealGroup v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-6" :amount="0.1">
            <RevealItem v-for="g in groups" :key="g.category">
              <!-- 类别精选卡片 -->
              <button
                type="button"
                @click="toggle(g.category)"
                :aria-pressed="open === g.category"
                :class="[
                  'group relative block w-full overflow-hidden rounded-2xl text-left shadow-sm transition duration-300',
                  open === g.category
                    ? 'ring-2 ring-lamp ring-offset-2 ring-offset-warm'
                    : 'hover:-translate-y-1 hover:shadow-[0_12px_32px_rgba(245,179,1,0.22)]'
                ]"
              >
                <!-- 图片底（4:3） -->
                <div class="relative aspect-[4/3] w-full overflow-hidden">
                  <img
                    :src="CATEGORY_THEME[g.category].img"
                    :alt="CATEGORY_THEME[g.category].label"
                    loading="lazy"
                    class="absolute inset-0 h-full w-full object-cover transition duration-500 group-hover:scale-[1.06]"
                  />
                  <!-- 底部深色渐变（保证文字可读） -->
                  <div class="absolute inset-0 bg-gradient-to-t from-night/85 via-night/30 to-transparent" />
                  <!-- 顶部左侧类别色条 -->
                  <span
                    aria-hidden="true"
                    class="absolute left-0 top-0 h-1 w-0 transition-all duration-500 group-hover:w-full"
                    :style="{ backgroundColor: CATEGORY_THEME[g.category].bar }"
                  />
                  <!-- 数量徽标 -->
                  <span class="absolute right-3 top-3 rounded-full bg-night/60 px-2.5 py-1 font-code text-[11px] font-bold tracking-wider text-warm backdrop-blur">
                    {{ g.items.length }} 项
                  </span>
                  <!-- 文字叠加区 -->
                  <div class="absolute inset-x-0 bottom-0 px-4 pb-3.5 pt-10">
                    <p class="font-code text-[10px] tracking-[0.28em] text-warm/70">{{ CATEGORY_THEME[g.category].en }}</p>
                    <p class="mt-1 font-title text-lg font-black text-warm">{{ CATEGORY_THEME[g.category].label }}</p>
                    <p class="mt-0.5 text-[11px] tracking-wider text-warm/75">{{ CATEGORY_THEME[g.category].desc }}</p>
                    <p v-if="g.items[0]" class="mt-1.5 line-clamp-1 text-[11px] text-warm/85">
                      最新：<span class="font-medium">{{ g.items[0].title }}</span>
                    </p>
                  </div>
                </div>
              </button>
            </RevealItem>
          </RevealGroup>

          <!-- 类别详情展开面板（点击类别卡片后滑出） -->
          <Transition name="honor-panel">
            <div v-if="openGroup" :key="openGroup.category" class="honor-panel">
              <div class="min-h-0 overflow-hidden">
                <div class="rounded-xl border border-lamp/30 bg-white p-4 md:p-5">
                  <!-- 面板头 -->
                  <div class="flex flex-wrap items-center justify-between gap-2 border-b border-lamp/20 pb-3">
                    <div class="flex items-center gap-2">
                      <span
                        class="flex h-8 w-8 items-center justify-center rounded-lg"
                        :style="{
                          backgroundColor: `${CATEGORY_THEME[openGroup.category].bar}1A`,
                          color: CATEGORY_THEME[openGroup.category].bar
                        }"
                      >
                        <Award :size="16" />
                      </span>
                      <p class="font-title text-base font-bold text-ink">
                        {{ CATEGORY_THEME[openGroup.category].label }} · {{ openGroup.items.length }} 项
                      </p>
                    </div>
                    <button
                      type="button"
                      @click="open = ''"
                      class="rounded-lg border border-ink/10 px-3 py-1.5 text-xs font-medium text-slate-500 transition hover:border-lamp/60 hover:text-ink"
                    >
                      收起
                    </button>
                  </div>

                  <!-- 该类全部荣誉列表（1/2 列自适应） -->
                  <ul class="mt-3 grid gap-2.5 sm:grid-cols-2">
                    <li
                      v-for="h in openGroup.items"
                      :key="h.key"
                      class="flex items-start gap-3 rounded-lg border border-lamp/25 bg-white/80 px-3.5 py-3 transition hover:border-lamp/50 hover:bg-white"
                    >
                      <span
                        class="mt-1 h-1.5 w-1.5 shrink-0 rounded-full"
                        :style="{ backgroundColor: CATEGORY_THEME[openGroup.category].bar }"
                      />
                      <div class="min-w-0 flex-1">
                        <p class="text-sm font-semibold leading-snug text-ink">{{ h.title }}</p>
                        <p class="mt-1 truncate text-xs text-slate-500">
                          {{ h.alumni.name }} · {{ h.alumni.college }} · {{ h.alumni.gradYear }}
                        </p>
                        <div class="mt-2 flex flex-wrap items-center gap-2">
                          <span
                            :class="[
                              'rounded-full px-2 py-0.5 text-[10px] font-bold tracking-wider',
                              CATEGORY_THEME[openGroup.category].chip
                            ]"
                          >
                            {{ CATEGORY_THEME[openGroup.category].label }}
                          </span>
                          <span class="rounded-full bg-lamp/15 px-2 py-0.5 text-[10px] font-bold tracking-wider text-amber-700">
                            {{ h.level }}
                          </span>
                          <span class="ml-auto font-code text-[11px] text-slate-400">{{ h.year }}</span>
                        </div>

                        <!-- 证书查看：仅当存在真实原件附件时显示"查看原件"按钮；
                             无原件时显示"文字记录"提示，不展示样例证书（审查报告 P0-2） -->
                        <div class="mt-2.5 flex flex-wrap items-center gap-1.5 border-t border-lamp/15 pt-2.5">
                          <button
                            v-if="certOf(h)"
                            type="button"
                            @click="attachment = certOf(h)"
                            class="group/att flex items-center gap-1 rounded-md border border-lamp/30 bg-white px-2 py-1 text-[11px] font-medium text-slate-600 transition hover:border-lamp/60 hover:bg-lamp/5 hover:text-ink"
                            :title="`查看 ${h.alumni.name} 的荣誉原件`"
                          >
                            <Award :size="12" class="text-[#a97b00]" />
                            查看原件
                            <span class="text-[#a97b00]/60 transition group-hover/att:translate-x-0.5 group-hover/att:text-[#a97b00]">→</span>
                          </button>
                          <span
                            v-else
                            class="rounded-md border border-dashed border-ink/15 bg-warm/40 px-2 py-1 text-[11px] text-slate-500"
                            :title="`该荣誉暂未上传原件附件，仅以文字记录展示`"
                          >
                            <Award :size="12" class="-mt-0.5 mr-1 inline align-middle text-slate-400" />
                            文字记录，未附原件
                          </span>
                          <button
                            v-for="(att, i) in moreOf(h)"
                            :key="i"
                            type="button"
                            @click="attachment = att"
                            class="group/att flex items-center gap-1 rounded-md border border-lamp/30 bg-white px-2 py-1 text-[11px] font-medium text-slate-600 transition hover:border-lamp/60 hover:bg-lamp/5 hover:text-ink"
                            :title="`查看 ${att.name}（${att.kind === 'image' ? '图片' : att.kind === 'pdf' ? 'PDF' : att.kind === 'word' ? 'Word' : 'Excel'}）`"
                          >
                            <span
                              :class="[
                                'h-1.5 w-1.5 rounded-full',
                                att.kind === 'image' ? 'bg-[#3E6B4F]' : att.kind === 'pdf' ? 'bg-[#A63D2F]' : 'bg-[#2563EB]'
                              ]"
                            />
                            {{ att.name }}
                            <span class="text-[#a97b00]/60 transition group-hover/att:translate-x-0.5 group-hover/att:text-[#a97b00]">→</span>
                          </button>
                        </div>
                      </div>
                    </li>
                  </ul>
                </div>
              </div>
            </div>
          </Transition>

          <!-- 底部统计说明 -->
          <p class="mt-4 text-center text-xs tracking-wider text-slate-400">
            共收录 {{ ALL_HONORS.length }} 项荣誉 · 点击类别卡片查看该类全部荣誉
          </p>
        </div>
      </Reveal>
    </div>

    <!-- 附件查看器：点击"查看附件"按钮打开 -->
    <AttachmentViewer :attachment="attachment" @close="attachment = null" />
  </section>
</template>

<style>
/* 类别面板展开/收起：grid-template-rows 0fr→1fr 实现 height:auto 平滑过渡，
   同步过渡 opacity 与 margin-top（20px→0），时长 0.4s，
   缓动与原 framer 一致：cubic-bezier(0.22, 0.61, 0.36, 1) */
.honor-panel {
  display: grid;
  grid-template-rows: 1fr;
  margin-top: 20px;
}
.honor-panel-enter-active,
.honor-panel-leave-active {
  transition:
    grid-template-rows 0.4s cubic-bezier(0.22, 0.61, 0.36, 1),
    opacity 0.4s cubic-bezier(0.22, 0.61, 0.36, 1),
    margin-top 0.4s cubic-bezier(0.22, 0.61, 0.36, 1);
}
.honor-panel-enter-from,
.honor-panel-leave-to {
  grid-template-rows: 0fr;
  opacity: 0;
  margin-top: 0;
}
</style>
