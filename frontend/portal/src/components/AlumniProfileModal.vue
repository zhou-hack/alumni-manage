<script setup lang="ts">
// ============================================================================
// 数字校友档案弹窗（加大版）
//
// 布局：加大头部（大照片 + 姓名 + 学院届别 + 当前身份/学号 + 寄语）
//   → 在校履历：横向"微曲线"时间线（SVG 平滑曲线贯穿全部节点，节点上下交错排布），
//     内容超出可左右拖拽横移，下方配一条可拖拽的滑条（拖拽滑块/点击轨道均可控制）
//   → 获奖情况：卡片网格，位于在校履历下方
//
// 可信度修复（2026-09-25 审查报告 P0-2 / P0-3）：
//   - 移除 sample-certificate.svg 兜底：无原件附件时不显示"查看原件"按钮，
//     改为"文字记录，未附原件"占位。
//   - 头部学号改用 maskStudentId 脱敏（公开页面不暴露完整学号）。
// ============================================================================
import { ref, watch } from 'vue';
import { X, Award, GraduationCap } from './icons';
import CurveTimeline from './CurveTimeline.vue';
import AttachmentViewer from './AttachmentViewer.vue';
import type { HonorAttachment, OutstandingAlumni } from '../mock/outstandingAlumni';
import { maskStudentId } from '../utils/privacy';

/** 获奖条目类型（取 OutstandingAlumni.awards 元素） */
type AwardItem = OutstandingAlumni['awards'][number];

const props = defineProps<{ alumni: OutstandingAlumni | null }>();
const emit = defineEmits<{ close: [] }>();

// ------------------------------ 证书查看 ------------------------------
// 仅在存在真实原件附件时显示"查看原件"按钮
const cert = ref<HonorAttachment | null>(null);
const certOf = (w: AwardItem): HonorAttachment | null => w.attachments?.[0] ?? null;
const moreOf = (w: AwardItem) => (w.attachments ?? []).slice(1);

// 弹窗打开期间锁定背景页面滚动，关闭后回到原位置。
// 用 position:fixed + 负 top 锁定（body overflow:hidden 会把滚动位置重置为 0，
// 导致关闭弹窗后页面停在顶部）；paddingRight 补偿滚动条宽度，避免页面左右抖动。
watch(
  () => props.alumni,
  (a, _prev, onCleanup) => {
    if (!a) return;
    const scrollY = window.scrollY;
    const body = document.body;
    const prev = {
      position: body.style.position,
      top: body.style.top,
      width: body.style.width,
      overflow: body.style.overflow,
      paddingRight: body.style.paddingRight
    };
    const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;
    body.style.position = 'fixed';
    body.style.top = `-${scrollY}px`;
    body.style.width = '100%';
    body.style.overflow = 'hidden';
    if (scrollbarWidth > 0) body.style.paddingRight = `${scrollbarWidth}px`;
    onCleanup(() => {
      body.style.position = prev.position;
      body.style.top = prev.top;
      body.style.width = prev.width;
      body.style.overflow = prev.overflow;
      body.style.paddingRight = prev.paddingRight;
      window.scrollTo(0, scrollY);
    });
  }
);
// 弹窗关闭时若证书查看器还开着，一并关闭
watch(
  () => props.alumni,
  (a) => {
    if (!a) cert.value = null;
  }
);
</script>

<template>
  <Transition name="modal">
    <div
      v-if="alumni"
      class="fixed inset-0 z-50 flex items-center justify-center bg-night/70 px-4 backdrop-blur-sm"
      @click="emit('close')"
    >
      <div
        class="modal-panel relative max-h-[88vh] w-full max-w-4xl overflow-y-auto rounded-3xl border border-lamp/40 bg-warm shadow-2xl [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        @click.stop
      >
        <!-- 头部：大照片 + 姓名 + 学院届别 + 当前身份/学号 + 寄语 -->
        <div class="relative bg-gradient-to-r from-night to-[#2a1a55] px-6 py-8 md:px-10">
          <div class="flex flex-col items-center gap-6 text-center sm:flex-row sm:text-left">
            <!-- 真实照片 -->
            <img
              v-if="alumni.photo"
              :src="alumni.photo"
              :alt="alumni.name"
              class="h-28 w-28 shrink-0 rounded-2xl border-2 border-lamp/70 object-cover object-top shadow-[0_0_28px_rgba(245,179,1,0.45)] md:h-32 md:w-32"
              loading="lazy"
              decoding="async"
            />
            <!-- 占位：渐变底 + 姓氏首字（无 photo 时） -->
            <div
              v-else
              aria-hidden="true"
              class="relative flex h-28 w-28 shrink-0 items-center justify-center overflow-hidden rounded-2xl border-2 border-lamp/70 bg-gradient-to-br from-night via-tech to-night/85 shadow-[0_0_28px_rgba(245,179,1,0.45)] md:h-32 md:w-32"
            >
              <span
                class="absolute inset-0 bg-[radial-gradient(ellipse_at_top_left,rgba(255,255,255,0.18),transparent_55%)]"
              />
              <span
                class="font-title relative text-5xl font-black text-white/95 drop-shadow-[0_4px_18px_rgba(0,0,0,0.4)] md:text-6xl"
              >
                {{ (alumni.name || '·').slice(0, 1) }}
              </span>
            </div>
            <div class="min-w-0 flex-1">
              <h3 class="font-title text-3xl font-black text-warm md:text-4xl">{{ alumni.name }}</h3>
              <p class="mt-2 text-xs tracking-widest text-lamp md:text-sm">{{ alumni.college }} · {{ alumni.gradYear }}</p>
              <div class="mt-3 flex flex-wrap justify-center gap-2 sm:justify-start">
                <span class="rounded-full border border-warm/25 bg-warm/10 px-3 py-1 text-[11px] text-warm/85">
                  {{ alumni.current }}
                </span>
                <span
                v-if="alumni.studentId"
                class="rounded-full border border-warm/25 bg-warm/10 px-3 py-1 font-code text-[11px] text-warm/85"
                :title="`公开仅展示脱敏学号，原值仅本人可见`"
              >
                  学号 {{ maskStudentId(alumni.studentId) }}
                </span>
              </div>
              <p class="mt-4 text-sm leading-7 text-warm/80 md:text-[15px]">“{{ alumni.message }}”</p>
            </div>
          </div>
          <button
            type="button"
            aria-label="关闭"
            class="absolute right-4 top-4 rounded-full bg-white/10 p-2 text-warm/70 transition hover:bg-lamp hover:text-night"
            @click="emit('close')"
          >
            <X :size="18" />
          </button>
        </div>

        <!-- 在校履历：横向微曲线 + 可横移 + 可拖拽滑条 -->
        <div class="px-6 pt-7 md:px-10">
          <div class="flex flex-wrap items-baseline justify-between gap-2">
            <h4 class="flex items-center gap-2 font-title text-lg font-bold text-ink">
              <GraduationCap :size="19" class="text-tech" /> 在校履历
            </h4>
            <p class="text-[11px] tracking-wider text-slate-400">滚轮 / 拖拽画面或下方滑条，左右横移查看完整履历</p>
          </div>
          <div class="mt-2">
            <CurveTimeline :items="alumni.timeline" />
          </div>
        </div>

        <!-- 获奖情况（位于在校履历下方） -->
        <div class="px-6 pb-9 pt-6 md:px-10">
          <h4 class="flex items-center gap-2 font-title text-lg font-bold text-ink">
            <Award :size="19" class="text-lamp" /> 获奖情况
          </h4>
          <div class="mt-5 grid gap-3 sm:grid-cols-2">
            <div
              v-for="(w, i) in alumni.awards"
              :key="w.title"
              class="award-card rounded-2xl border border-lamp/40 border-t-4 border-t-tech bg-white px-4 py-3 shadow-sm transition hover:-translate-y-0.5 hover:shadow-[0_0_16px_rgba(245,179,1,0.3)]"
              :style="{ animationDelay: `${0.25 + i * 0.15}s` }"
            >
              <div class="flex items-center justify-between gap-3">
                <p class="text-sm font-bold text-ink">{{ w.title }}</p>
                <span class="shrink-0 rounded-full bg-lamp/15 px-2.5 py-0.5 text-[10px] font-bold tracking-widest text-amber-700">
                  {{ w.level }}
                </span>
              </div>
              <p class="font-code mt-1 text-xs text-slate-400">{{ w.year }}</p>
              <!-- 证书查看：仅当存在真实原件附件时显示"查看原件"按钮；无原件显示"文字记录"占位（审查报告 P0-2） -->
              <div class="mt-2.5 flex flex-wrap items-center gap-1.5 border-t border-lamp/20 pt-2.5">
                <button
                  v-if="certOf(w)"
                  type="button"
                  :title="`查看 ${w.title} 的荣誉原件`"
                  class="group/att flex items-center gap-1 rounded-md border border-lamp/30 bg-white px-2 py-1 text-[11px] font-medium text-slate-600 transition hover:border-lamp/60 hover:bg-lamp/5 hover:text-ink"
                  @click.stop="cert = certOf(w)"
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
                  v-for="(att, i) in moreOf(w)"
                  :key="i"
                  type="button"
                  :title="`查看 ${att.name}`"
                  class="group/att flex items-center gap-1 rounded-md border border-lamp/30 bg-white px-2 py-1 text-[11px] font-medium text-slate-600 transition hover:border-lamp/60 hover:bg-lamp/5 hover:text-ink"
                  @click.stop="cert = att"
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
          </div>
        </div>
      </div>
    </div>
  </Transition>

  <!-- 证书原件查看器（图片灯箱 / PDF 在线预览；z-90 浮于本弹窗之上） -->
  <AttachmentViewer :attachment="cert" @close="cert = null" />
</template>

<style scoped>
/* 弹窗进出场（原 AnimatePresence：enter scale .92/y24 → 1，exit → scale .94/y16，0.3s easeOut） */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.3s ease-out;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
.modal-enter-active .modal-panel,
.modal-leave-active .modal-panel {
  transition:
    transform 0.3s ease-out,
    opacity 0.3s ease-out;
}
.modal-enter-from .modal-panel {
  transform: scale(0.92) translateY(24px);
  opacity: 0;
}
.modal-leave-to .modal-panel {
  transform: scale(0.94) translateY(16px);
  opacity: 0;
}
/* 获奖卡片入场（原 motion：opacity 0/y16/scale .96 → 1，0.5s，延迟 0.25 + i*0.15）。
   基础样式不预置 opacity:0 —— 延迟期间由 backwards 填充 from 帧（隐藏），
   动画结束后回到自然样式（可见且 transform 交还给 hover 位移），避免播完又消失 */
.award-card {
  animation: award-in 0.5s ease-out backwards;
}
@keyframes award-in {
  from {
    opacity: 0;
    transform: translateY(16px) scale(0.96);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
</style>
