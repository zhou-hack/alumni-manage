<script setup>
/**
 * 校友动态页（PC 端校友视角，清单 /student/feed）
 * - 大屏公开档案轮播：定时自动切换卡片，支持暂停/手动切换
 * - 荣誉/档案申报发布入口：快捷跳转提交页
 * 数据源：/screen/feed（approved + published + 有效授权的公开口径）
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getScreenFeed } from '@/api/showcase'
import { CATEGORY_TEXT } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const feed = ref([])

// ---------------- 轮播 ----------------
const current = ref(0)
const playing = ref(true)
let timer = null
const INTERVAL = 5000

function next() {
  if (!feed.value.length) return
  current.value = (current.value + 1) % feed.value.length
}

function prev() {
  if (!feed.value.length) return
  current.value = (current.value - 1 + feed.value.length) % feed.value.length
}

function pick(i) {
  current.value = i
}

function togglePlay() {
  playing.value = !playing.value
  schedule()
}

function schedule() {
  clearInterval(timer)
  if (playing.value && feed.value.length > 1) {
    timer = setInterval(next, INTERVAL)
  }
}

const currentCard = computed(() => feed.value[current.value] || null)

async function load() {
  loading.value = true
  try {
    const res = await getScreenFeed()
    feed.value = res.data.list || []
    current.value = 0
    schedule()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => clearInterval(timer))

// ---------------- 申报入口 ----------------
const entries = [
  { key: 'archive', title: '档案资料申报', desc: '获奖经历、证书资质、实践与就业履历', to: '/archive-form', icon: 'EditPen' },
  { key: 'honor', title: '荣誉证书申报', desc: '奖学金、三好学生、竞赛奖项等荣誉录入', to: '/my-honors', icon: 'Medal' },
  { key: 'records', title: '我的提交记录', desc: '查看审核进度、驳回原因、重新提交', to: '/my-archives', icon: 'Folder' },
  { key: 'directory', title: '校友通讯录', desc: '浏览已公开展示的校友风采档案', to: '/directory', icon: 'Avatar' },
]
</script>

<template>
  <div class="feed-page" v-loading="loading">
    <div class="page-head">
      <div>
        <div class="eyebrow">校友动态</div>
        <h1 class="page-title">校友动态</h1>
        <p class="page-sub">来自风采大屏的公开档案轮播，看看校友们的精彩经历。</p>
      </div>
    </div>

    <!-- 大屏公开档案轮播 -->
    <div v-if="feed.length" class="carousel">
      <transition name="fade-slide" mode="out-in">
        <div :key="current" class="feed-card" :class="{ 'no-cover': !currentCard?.cover_url }">
          <div class="cover-col">
            <img v-if="currentCard?.cover_url" :src="currentCard.cover_url" :alt="currentCard.title" />
            <div v-else class="cover-fallback">
              <el-icon :size="44"><component :is="'Picture'" /></el-icon>
              <span>{{ CATEGORY_TEXT[currentCard?.category] || '档案' }}</span>
            </div>
          </div>
          <div class="info-col">
            <div class="feed-cat">{{ CATEGORY_TEXT[currentCard.category] || currentCard.category }}</div>
            <h2 class="feed-title">{{ currentCard.title }}</h2>
            <p class="feed-content">{{ currentCard.content }}</p>
            <div class="feed-meta">
              <span class="meta-owner">{{ currentCard.owner }}</span>
              <span class="meta-dot">·</span>
              <span>{{ currentCard.college }}</span>
              <span class="meta-dot">·</span>
              <span>{{ currentCard.grad_year ? `${currentCard.grad_year} 届` : '' }}</span>
            </div>
          </div>
        </div>
      </transition>

      <!-- 控制条 -->
      <div class="carousel-bar">
        <div class="dots">
          <button
            v-for="(f, i) in feed"
            :key="f.id"
            class="dot"
            :class="{ active: i === current }"
            :title="f.title"
            @click="pick(i)"
          />
        </div>
        <div class="ctrl-btns">
          <button class="ctrl" @click="prev">‹</button>
          <button class="ctrl" @click="togglePlay">{{ playing ? '❚❚' : '▶' }}</button>
          <button class="ctrl" @click="next">›</button>
        </div>
      </div>
    </div>
    <el-empty v-else-if="!loading" description="暂无公开档案，等你授权上架后就会出现在这里" :image-size="90" />

    <!-- 荣誉 / 档案申报发布入口 -->
    <div class="section-head">
      <span class="section-title">申报发布</span>
      <span class="text-sub">提交后将进入两级审核，通过并授权后在大屏轮播展示</span>
    </div>
    <div class="entries">
      <div v-for="e in entries" :key="e.key" class="entry-card" @click="router.push(e.to)">
        <el-icon class="e-icon"><component :is="e.icon" /></el-icon>
        <div class="e-title">{{ e.title }}</div>
        <div class="e-desc">{{ e.desc }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-head {
  margin-bottom: 22px;
}

.eyebrow {
  font-size: 11px;
  letter-spacing: 0.22em;
  color: var(--text-mute);
  margin-bottom: 6px;
}

.page-title {
  margin: 0 0 6px;
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  color: var(--text-main);
}

.page-sub {
  margin: 0;
  font-size: 13.5px;
  color: var(--text-sub);
}

/* ---------- 轮播 ---------- */
.carousel {
  margin-bottom: 26px;
}

.feed-card {
  display: flex;
  gap: 0;
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  overflow: hidden;
  min-height: 240px;
  box-shadow: var(--shadow-sm);
}

.cover-col {
  flex: 0 0 320px;
  background: var(--surface-2);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.cover-col img {
  width: 100%;
  height: 100%;
  min-height: 240px;
  object-fit: cover;
}

.cover-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: var(--text-mute);
  font-size: 13px;
}

.info-col {
  flex: 1;
  padding: 26px 30px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.feed-cat {
  display: inline-flex;
  align-self: flex-start;
  padding: 3px 12px;
  border-radius: var(--radius-full);
  background: var(--brand-soft, rgba(16, 42, 92, 0.08));
  color: var(--brand-600, #102a5c);
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 12px;
}

.feed-title {
  margin: 0 0 12px;
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.35;
}

.feed-content {
  margin: 0 0 16px;
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--text-sub);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.feed-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
  color: var(--text-mute);
}

.meta-owner {
  font-weight: 600;
  color: var(--text-main);
}

.meta-dot {
  opacity: 0.5;
}

.carousel-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14px;
}

.dots {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  border: none;
  background: var(--line);
  cursor: pointer;
  transition: all 0.2s;
  padding: 0;
}

.dot.active {
  width: 26px;
  border-radius: 0;
  background: var(--brand-600, #102a5c);
}

.ctrl-btns {
  display: flex;
  gap: 8px;
}

.ctrl {
  width: 30px;
  height: 30px;
  border-radius: 0;
  border: 1px solid var(--line);
  background: var(--surface-1);
  color: var(--text-sub);
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s;
}

.ctrl:hover {
  border-color: var(--brand-600, #102a5c);
  color: var(--brand-600, #102a5c);
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.35s ease, transform 0.35s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(16px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-16px);
}

/* ---------- 申报入口 ---------- */
.section-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 14px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}

.entries {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.entry-card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  padding: 20px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
}

.entry-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--brand-200);
}

.e-icon {
  font-size: 26px;
  color: var(--brand-500);
}

.e-title {
  margin-top: 10px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}

.e-desc {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--text-mute);
}

@media (max-width: 1100px) {
  .cover-col {
    flex-basis: 220px;
  }

  .entries {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 720px) {
  .feed-card {
    flex-direction: column;
  }

  .cover-col img {
    min-height: 170px;
    max-height: 190px;
  }
}
</style>
