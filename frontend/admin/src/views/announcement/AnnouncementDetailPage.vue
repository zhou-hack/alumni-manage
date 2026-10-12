<script setup>
/**
 * 公告详情页（清单第七章）
 * - 居中标题 + 来源/发布人/时间行
 * - 图文混排：[[alumni-img:base64|宽度]] 渲染为 <img>（按编辑时设置的宽度展示）
 * - 图片点击放大（全屏 viewer）
 * - 上一篇 / 下一篇、相关公告（接口返回 prev_id / next_id / related）
 */
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Clock } from '@element-plus/icons-vue'
import { getAnnouncement, renderContentHtml, ANNOUNCEMENT_STATUS } from '@/api/announcement'
import { formatTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const detail = ref(null)

const html = computed(() => renderContentHtml(detail.value?.content || ''))

async function loadDetail() {
  const id = Number(route.params.id)
  if (!id) return
  loading.value = true
  try {
    const res = await getAnnouncement(id)
    detail.value = res.data
    await nextTick()
    bindImageClick()
  } catch (e) {
    ElMessage.error(e.message)
    router.replace('/announcements')
  } finally {
    loading.value = false
  }
}

/** 事件委托：正文图片点击 → 全屏放大 */
const viewerVisible = ref(false)
const viewerSrc = ref('')
function bindImageClick() {
  const el = document.querySelector('.ann-body')
  if (!el) return
  el.onclick = (e) => {
    if (e.target.tagName === 'IMG') {
      viewerSrc.value = e.target.src
      viewerVisible.value = true
    }
  }
}

watch(() => route.params.id, loadDetail)
onMounted(loadDetail)
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <template v-if="detail">
      <!-- 正文区（居中版式） -->
      <article class="ann-paper">
        <header class="ann-head">
          <h1 class="ann-title">{{ detail.title }}</h1>
          <div class="ann-meta">
            <span>{{ detail.source }}</span>
            <span class="sep">|</span>
            <span>{{ detail.publisher }}</span>
            <span class="sep">|</span>
            <span class="meta-time"><el-icon><Clock /></el-icon>{{ formatTime(detail.published_at || detail.created_at) }}</span>
            <span v-if="detail.status !== 'published'" class="draft-badge">
              {{ ANNOUNCEMENT_STATUS[detail.status]?.text || detail.status }}（仅管理员可见）
            </span>
          </div>
        </header>

        <img
          v-if="detail.cover_image"
          :src="detail.cover_image"
          alt="封面图"
          class="ann-cover"
          @click="viewerSrc = detail.cover_image; viewerVisible = true"
        />
        <div class="ann-body" v-html="html"></div>
      </article>

      <!-- 上一篇 / 下一篇 -->
      <nav class="pn-nav">
        <button
          class="pn-btn"
          :disabled="!detail.prev_id"
          @click="detail.prev_id && router.push(`/announcements/view/${detail.prev_id}`)"
        >
          <el-icon><ArrowLeft /></el-icon>
          <span class="pn-label">上一篇</span>
          <span class="pn-title">{{ detail.prev_id ? `公告 #${detail.prev_id}` : '已是最新一篇' }}</span>
        </button>
        <button
          class="pn-btn pn-right"
          :disabled="!detail.next_id"
          @click="detail.next_id && router.push(`/announcements/view/${detail.next_id}`)"
        >
          <span class="pn-label">下一篇</span>
          <span class="pn-title">{{ detail.next_id ? `公告 #${detail.next_id}` : '已是最后一篇' }}</span>
          <el-icon><ArrowRight /></el-icon>
        </button>
      </nav>

      <!-- 相关公告 -->
      <section v-if="detail.related?.length" class="related">
        <h3 class="related-title">相关公告</h3>
        <div class="related-list">
          <div
            v-for="r in detail.related"
            :key="r.id"
            class="related-item"
            @click="router.push(`/announcements/view/${r.id}`)"
          >
            <div class="r-title">{{ r.title }}</div>
            <div class="r-time">{{ formatTime(r.published_at) }}</div>
          </div>
        </div>
      </section>
    </template>

    <!-- 图片放大查看器 -->
    <el-image-viewer
      v-if="viewerVisible"
      :url-list="[viewerSrc]"
      hide-on-click-modal
      teleported
      @close="viewerVisible = false"
    />
  </div>
</template>

<style scoped>
.detail-page {
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.ann-paper {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: 36px 48px 40px;
}

.ann-head {
  text-align: center;
  border-bottom: 1px solid var(--line);
  padding-bottom: 20px;
}

.ann-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.5;
  letter-spacing: 0.02em;
}

.ann-meta {
  margin-top: 12px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 13px;
  color: var(--text-sub);
}

.sep {
  opacity: 0.4;
}

.meta-time {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.draft-badge {
  padding: 2px 10px;
  border-radius: var(--radius-full);
  background: rgba(217, 119, 6, 0.12);
  color: #d97706;
  font-size: 12px;
}

.ann-cover {
  display: block;
  width: 100%;
  max-height: 420px;
  object-fit: cover;
  margin-top: 24px;
  cursor: zoom-in;
  box-shadow: 0 2px 12px rgba(15, 23, 42, 0.08);
}

.ann-body {
  padding-top: 24px;
  font-size: 15px;
  line-height: 2;
  color: var(--text-main);
  word-break: break-word;
}

.ann-body :deep(.rich-img) {
  display: block;
  margin: 16px auto;
  max-width: 100%;
  height: auto;
  border-radius: 0;
  cursor: zoom-in;
  box-shadow: 0 2px 12px rgba(15, 23, 42, 0.08);
}

/* 上一篇/下一篇 */
.pn-nav {
  display: flex;
  gap: 12px;
}

.pn-btn {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: 14px 18px;
  cursor: pointer;
  transition: all 0.2s;
  min-width: 0;
}
.pn-btn:hover:not(:disabled) {
  border-color: var(--brand);
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
}
.pn-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.pn-right {
  justify-content: flex-end;
}

.pn-label {
  font-size: 12px;
  color: var(--text-sub);
  flex-shrink: 0;
}

.pn-title {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 相关公告 */
.related {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: 20px 24px;
}

.related-title {
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 700;
}

.related-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 10px;
}

.related-item {
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 0;
  cursor: pointer;
  transition: all 0.15s;
}
.related-item:hover {
  border-color: var(--brand);
  background: rgba(79, 70, 229, 0.03);
}

.r-title {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.r-time {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-sub);
}
</style>
