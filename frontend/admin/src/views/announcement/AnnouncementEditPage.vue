<script setup>
/**
 * 公告编辑页（清单第七章）
 * - 新建：/announcements/edit（无 id）→ 存草稿（POST）/ 直接发布（POST + PATCH status）
 * - 编辑：/announcements/edit/:id → 保存修改（PATCH title/content）
 * - 正文双模式：RichEditor 富文本（Word 式：图片插入/缩放/拖拽、.docx 导入）或
 *   Markdown（编辑 / 预览 / 分屏，marked 渲染，自动识别既有内容格式）
 * - 正文上限 10MB；状态机：draft → published ↔ withdrawn（撤下/重新发布在列表页操作）
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getAnnouncement, createAnnouncement, updateAnnouncement, uploadAnnouncementImage } from '@/api/announcement'
import RichEditor from '@/components/RichEditor.vue'
import { renderMarkdown, looksLikeMarkdown } from '@/utils/markdown'

/** 正文上限：10MB（字符数，与后端 CONTENT_MAX 一致） */
const CONTENT_MAX = 10 * 1024 * 1024

const route = useRoute()
const router = useRouter()

const editId = computed(() => (route.params.id ? Number(route.params.id) : null))
const pageTitle = computed(() => (editId.value ? '编辑公告' : '新建公告'))

const title = ref('')
const content = ref('')
const cover = ref('') // 封面图 URL（可空；空串保存即清除）
const coverUploading = ref(false)
const status = ref('draft') // 当前公告状态（编辑模式加载）
const saving = ref(false)
const publishing = ref(false)
const loading = ref(false)

/* ===== 封面图：上传静态资源，门户列表/详情/首页头条展示 ===== */
const coverInput = ref(null)
function pickCover() {
  coverInput.value?.click()
}
async function onCoverPicked(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (!/^image\//.test(file.type)) {
    ElMessage.warning('请选择图片文件')
    return
  }
  coverUploading.value = true
  try {
    const res = await uploadAnnouncementImage(file)
    cover.value = res.data.url
    ElMessage.success('封面已上传')
  } catch (err) {
    ElMessage.error('封面上传失败：' + (err.message || '请重试'))
  } finally {
    coverUploading.value = false
  }
}

/* ===== 编辑模式：rich 富文本 / md Markdown；mdView: edit 编辑 / preview 预览 / split 分屏 ===== */
const mode = ref('rich')
const mdView = ref('split')

/** Markdown 预览 HTML（marked 渲染 + 基础 XSS 清理） */
const mdHtml = computed(() => renderMarkdown(content.value))

/** 正文中的图片数量（提示用，兼容富文本占位符与 Markdown 图片语法） */
const imgCount = computed(() => {
  const rich = (content.value.match(/\[\[alumni-img:/g) || []).length
  const md = (content.value.match(/!\[[^\]]*\]\(/g) || []).length
  return rich + md
})

async function loadDetail() {
  if (!editId.value) return
  loading.value = true
  try {
    const res = await getAnnouncement(editId.value)
    title.value = res.data.title
    content.value = res.data.content || ''
    cover.value = res.data.cover_image || ''
    status.value = res.data.status
    // 既有内容自动识别格式：Markdown 内容直接进入 md 模式
    mode.value = looksLikeMarkdown(content.value) ? 'md' : 'rich'
  } catch (e) {
    ElMessage.error(e.message)
    router.replace('/announcements')
  } finally {
    loading.value = false
  }
}

function validate() {
  if (!title.value.trim()) {
    ElMessage.warning('请填写公告标题')
    return false
  }
  if (!content.value.trim()) {
    ElMessage.warning('请填写公告正文')
    return false
  }
  if (content.value.length > CONTENT_MAX) {
    ElMessage.warning('正文超过 10MB 上限，请压缩或减少图片')
    return false
  }
  return true
}

/** 存草稿 / 保存修改 */
async function handleSave(publish = false) {
  if (saving.value || publishing.value) return
  if (!validate()) return
  if (publish) {
    try {
      await ElMessageBox.confirm(
        editId.value && status.value === 'withdrawn'
          ? '发布后公告将重新对所有用户可见，确认发布？'
          : '发布后公告对所有用户可见，确认发布？',
        '发布公告',
        { type: 'info' },
      )
    } catch {
      return
    }
  }
  const busy = publish ? publishing : saving
  busy.value = true
  try {
    if (!editId.value) {
      // 新建：先建草稿
      const res = await createAnnouncement({
        title: title.value.trim(),
        content: content.value,
        cover_image: cover.value || undefined,
      })
      if (publish) {
        await updateAnnouncement(res.data.id, { status: 'published' })
        ElMessage.success('公告已发布')
      } else {
        ElMessage.success('草稿已保存')
      }
    } else {
      // 编辑：保存内容（已发布内容更新即时生效，提示文案区分）；cover_image 空串即清除封面
      await updateAnnouncement(editId.value, {
        title: title.value.trim(),
        content: content.value,
        cover_image: cover.value,
      })
      if (publish && status.value !== 'published') {
        await updateAnnouncement(editId.value, { status: 'published' })
        ElMessage.success('公告已发布')
      } else {
        ElMessage.success(status.value === 'published' ? '修改已保存并即时生效' : '修改已保存')
      }
    }
    router.replace('/announcements')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

onMounted(loadDetail)
</script>

<template>
  <div class="edit-page" v-loading="loading">
    <div class="page-head">
      <el-button :icon="ArrowLeft" text @click="router.back()">返回</el-button>
      <div class="head-text">
        <h2 class="page-title">{{ pageTitle }}</h2>
        <p class="page-sub">支持富文本图文混排与 Markdown 格式（编辑 / 预览 / 分屏）；正文上限 10MB</p>
      </div>
    </div>

    <div class="form-card">
      <el-form label-position="top">
        <el-form-item label="公告标题" required>
          <el-input v-model="title" placeholder="请输入公告标题" maxlength="60" show-word-limit size="large" />
        </el-form-item>
        <el-form-item label="封面图（可选，门户列表 / 详情 / 首页头条展示）">
          <div class="cover-box">
            <div v-if="cover" class="cover-preview">
              <img :src="cover" alt="封面预览" />
              <div class="cover-ops">
                <el-button size="small" :loading="coverUploading" @click="pickCover">更换</el-button>
                <el-button size="small" type="danger" plain @click="cover = ''">移除</el-button>
              </div>
            </div>
            <el-button v-else :loading="coverUploading" @click="pickCover">
              {{ coverUploading ? '上传中…' : '上传封面图' }}
            </el-button>
            <input ref="coverInput" type="file" accept="image/*" hidden @change="onCoverPicked" />
          </div>
        </el-form-item>
        <el-form-item required>
          <template #label>
            <div class="content-label">
              <span>公告正文</span>
              <el-radio-group v-model="mode" size="small">
                <el-radio-button value="rich">富文本</el-radio-button>
                <el-radio-button value="md">Markdown</el-radio-button>
              </el-radio-group>
            </div>
          </template>

          <!-- 富文本模式 -->
          <RichEditor v-if="mode === 'rich'" v-model="content" />

          <!-- Markdown 模式：编辑 / 预览 / 分屏 -->
          <div v-else class="md-wrap">
            <div class="md-toolbar">
              <el-radio-group v-model="mdView" size="small">
                <el-radio-button value="edit">编辑</el-radio-button>
                <el-radio-button value="preview">预览</el-radio-button>
                <el-radio-button value="split">分屏</el-radio-button>
              </el-radio-group>
              <span class="md-tip">支持 GFM：标题 / 列表 / 表格 / 代码块 / 图片 / 链接</span>
            </div>
            <div class="md-panes" :class="`is-${mdView}`">
              <textarea
                v-show="mdView !== 'preview'"
                v-model="content"
                class="md-input"
                placeholder="# 标题&#10;&#10;支持 **加粗**、列表、表格、代码块、![图片](URL) 等 Markdown 语法…"
              ></textarea>
              <div v-show="mdView !== 'edit'" class="md-preview markdown-body" v-html="mdHtml"></div>
            </div>
          </div>

          <div class="editor-foot">
            <span>图片 {{ imgCount }} 张 · 正文 {{ content.length }} / {{ CONTENT_MAX }} 字符</span>
          </div>
        </el-form-item>
      </el-form>
    </div>

    <div class="action-bar">
      <el-button :loading="saving" @click="handleSave(false)">
        {{ editId ? '保存修改' : '存草稿' }}
      </el-button>
      <el-button
        v-if="!editId || status !== 'published'"
        type="primary"
        :loading="publishing"
        @click="handleSave(true)"
      >
        {{ editId && status === 'withdrawn' ? '重新发布' : '保存并发布' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.edit-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.head-text {
  flex: 1;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.page-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-sub);
}

.form-card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: 20px 24px;
}

.editor-foot {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-sub);
  text-align: right;
}

/* ===== 封面图 ===== */
.cover-box {
  width: 100%;
}

.cover-preview {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.cover-preview img {
  width: 240px;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  border: 1px solid var(--line);
  border-radius: var(--radius-md, 8px);
}

.cover-ops {
  display: flex;
  gap: 8px;
}

/* ===== Markdown 编辑器 ===== */
.content-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.md-wrap {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.md-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--line);
  background: var(--surface-2);
}

.md-tip {
  font-size: 12px;
  color: var(--text-mute);
}

.md-panes {
  display: grid;
  grid-template-columns: 1fr;
  min-height: 420px;
}

.md-panes.is-split {
  grid-template-columns: 1fr 1fr;
}

.md-input {
  width: 100%;
  min-height: 420px;
  padding: 14px 16px;
  border: none;
  outline: none;
  resize: vertical;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--text-main);
  background: var(--card);
}

.md-panes.is-split .md-input {
  border-right: 1px solid var(--line);
}

.md-preview {
  min-height: 420px;
  max-height: 70vh;
  overflow-y: auto;
  padding: 14px 18px;
  background: var(--card);
}

/* Markdown 预览排版 */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 18px 0 10px;
  font-weight: 700;
  line-height: 1.4;
}

.markdown-body :deep(h1) { font-size: 22px; }
.markdown-body :deep(h2) { font-size: 19px; }
.markdown-body :deep(h3) { font-size: 16px; }

.markdown-body :deep(p) {
  margin: 10px 0;
  font-size: 14px;
  line-height: 1.9;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 10px 0;
  padding-left: 24px;
  font-size: 14px;
  line-height: 1.9;
}

.markdown-body :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 14px;
  border-left: 3px solid var(--brand-500, #2f6bff);
  background: var(--surface-2);
  color: var(--text-sub);
}

.markdown-body :deep(code) {
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--surface-3);
  font-family: Consolas, monospace;
  font-size: 13px;
}

.markdown-body :deep(pre) {
  margin: 12px 0;
  padding: 12px 14px;
  border-radius: 8px;
  background: #1e293b;
  color: #e2e8f0;
  overflow-x: auto;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: none;
  color: inherit;
}

.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: 8px;
}

.markdown-body :deep(table) {
  margin: 12px 0;
  border-collapse: collapse;
  font-size: 13.5px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 6px 12px;
  border: 1px solid var(--line);
}

.markdown-body :deep(a) {
  color: var(--brand-500, #2f6bff);
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-bottom: 8px;
}
</style>
