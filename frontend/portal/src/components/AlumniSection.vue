<script setup lang="ts">
// 优秀校友 · 数字校友弧形展厅（Circular Gallery 风格）：全宽夜色展厅带 + 亮色三段式标题
// 点击校友 = 查看数字校友档案（时间线 + 获奖动画弹窗）
// 数据：挂载时拉取 /alumni/outstanding（见 useOutstanding.ts），接口失败回退 mock 演示数据
import { ref } from 'vue';
import CircularGallery from './CircularGallery.vue';
import AlumniProfileModal from './AlumniProfileModal.vue';
import ModuleTitle from './ModuleTitle.vue';
import { Reveal } from './Reveal';
import type { OutstandingAlumni } from '../mock/outstandingAlumni';
import { useOutstanding } from './useOutstanding';

const { alumni, state } = useOutstanding();
const profile = ref<OutstandingAlumni | null>(null);
</script>

<template>
  <section id="outstanding-alumni" class="mt-16 scroll-mt-24">
    <!-- 全宽展厅带：与页面一致的暖白背景 #FFF9F0 -->
    <div class="relative overflow-hidden bg-warm py-14">
      <!-- 背景微光，柔和点缀 -->
      <div class="pointer-events-none absolute left-1/2 top-0 h-40 w-[60%] -translate-x-1/2 rounded-full bg-lamp/10 blur-3xl" />

      <!-- 模块标题：英文眉标 + 加大衬线主标题 + 城堡垛口装饰线 + 副标题 -->
      <ModuleTitle
        eyebrow="OUTSTANDING ALUMNI"
        title="优秀校友"
        subtitle="汇聚校友力量，记录青春足迹"
        ornament
        glow
        class="mb-10"
      />

      <!-- 弧形展厅（横向弧形 + 无限循环 + 拖拽/滚轮 + 惯性）
           宽度与上方校园风光轮播一致（随屏收敛），并限制最大宽度，
           保证同屏最多展示 5 位校友（中心 1 位 + 两侧各 2 位） -->
      <Reveal :delay="0.08" class="relative mx-auto w-[92%] max-w-[1500px] md:w-[86%] xl:w-[80%]">
        <!-- 弹窗打开期间暂停巡航（paused），关闭后自动恢复 -->
        <CircularGallery v-if="alumni.length" :items="alumni" :paused="profile !== null" @select="profile = $event" />
        <!-- 接口成功但暂无已发布优秀校友时的空态 -->
        <p v-else-if="state !== 'loading'" class="py-16 text-center text-sm tracking-widest text-slate-400">
          暂无优秀校友数据，敬请期待
        </p>
      </Reveal>

      <p class="relative mt-6 text-center text-xs tracking-widest text-slate-400">
        拖拽 / 滚轮浏览展厅 · 点击校友照片查看数字档案
      </p>
    </div>

    <!-- 数字校友档案弹窗 -->
    <AlumniProfileModal :alumni="profile" @close="profile = null" />
  </section>
</template>
