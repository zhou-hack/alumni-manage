<script setup>
/**
 * 附件预览分流组件（PRD 9.3）
 * - 图片：原样预览（带鉴权拉取，非公开附件也可看）
 * - PDF：pdfjs-dist 在线渲染
 * - Word / Excel：展示文件名 + 下载（工期降级口径，与任务清单 3.2 一致）
 * - 403/404 统一错误提示
 */
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { View, Download, Delete } from '@element-plus/icons-vue'
import http from '@/api/request'
import { deleteAttachment } from '@/api/attachment'
import { formatSize } from '@/utils/format'

const props = defineProps({
  /** [{ id, name, size, mime }] */
  attachments: { type: Array, default: () => [] },
  /** 是否显示删除按钮（管理端详情用，删除后 emit deleted） */
  deletable: { type: Boolean, default: false },
})

const emit = defineEmits(['deleted'])

const BASE_URL = import.meta.env.VITE_API_BASE || '/api/v1'
const previewApiUrl = (id) => `${BASE_URL}/attachments/${id}/preview`

function kindOf(att) {
  const mime = att.mime || ''
  const name = att.name || ''
  if (/^image\//.test(mime)) return 'image'
  if (mime === 'application/pdf' || /\.pdf$/i.test(name)) return 'pdf'
  if (/word|officedocument\.word/.test(mime) || /\.docx?$/i.test(name)) return 'word'
  if (/excel|spreadsheet/.test(mime) || /\.xlsx?$/i.test(name)) return 'excel'
  return 'other'
}

const KIND_TEXT = { image: '图片', pdf: 'PDF', word: 'Word', excel: 'Excel', other: '文件' }

/** 带鉴权拉取附件 Blob（预览接口按公开条件判断，登录态兜底） */
async function fetchBlob(att) {
  const blob = await http.get(previewApiUrl(att.id), { responseType: 'blob' })
  return blob
}

// ---------------- 图片预览 ----------------
const imageViewerVisible = ref(false)
const imageUrls = ref([])

async function previewImage(att) {
  try {
    const blob = await fetchBlob(att)
    imageUrls.value = [URL.createObjectURL(blob)]
    imageViewerVisible.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// ---------------- PDF 预览 ----------------
const pdfVisible = ref(false)
const pdfLoading = ref(false)
const pdfTitle = ref('')
const pdfContainer = ref(null)

async function previewPdf(att) {
  pdfTitle.value = att.name
  pdfVisible.value = true
  pdfLoading.value = true
  try {
    const blob = await fetchBlob(att)
    const data = await blob.arrayBuffer()
    const pdfjsLib = await import('pdfjs-dist')
    const workerUrl = (await import('pdfjs-dist/build/pdf.worker.min.mjs?url')).default
    pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl
    const pdf = await pdfjsLib.getDocument({ data }).promise
    // 等待抽屉容器渲染完成后逐页绘制
    await new Promise((r) => setTimeout(r, 50))
    const container = pdfContainer.value
    if (container) {
      container.innerHTML = ''
      for (let i = 1; i <= pdf.numPages; i += 1) {
        const page = await pdf.getPage(i)
        const scale = Math.min(1.6, (container.clientWidth - 32) / page.getViewport({ scale: 1 }).width)
        const viewport = page.getViewport({ scale: Math.max(scale, 0.8) })
        const canvas = document.createElement('canvas')
        canvas.width = viewport.width
        canvas.height = viewport.height
        canvas.className = 'pdf-page'
        container.appendChild(canvas)
        await page.render({ canvasContext: canvas.getContext('2d'), viewport }).promise
      }
    }
  } catch (e) {
    ElMessage.error(`PDF 预览失败：${e.message}`)
    pdfVisible.value = false
  } finally {
    pdfLoading.value = false
  }
}

// ---------------- 下载（Word/Excel 降级口径） ----------------
const downloading = ref(false)

async function handleDownload(att) {
  if (downloading.value) return
  downloading.value = true
  try {
    const blob = await fetchBlob(att)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = att.name || `attachment-${att.id}`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    downloading.value = false
  }
}

const hasAttachments = computed(() => props.attachments.length > 0)

// ---------------- 删除（管理端） ----------------
const deletingId = ref(null)

async function handleDelete(att) {
  const { ElMessageBox } = await import('element-plus')
  try {
    await ElMessageBox.confirm(
      `确认删除附件「${att.name}」？该操作将从关联档案/荣誉中移除并删除文件，不可恢复。`,
      '删除附件',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  deletingId.value = att.id
  try {
    await deleteAttachment(att.id)
    ElMessage.success('附件已删除')
    emit('deleted', att)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    deletingId.value = null
  }
}
</script>

<template>
  <div v-if="hasAttachments" class="attachment-list">
    <div v-for="att in attachments" :key="att.id" class="attachment-item">
      <el-tag size="small" effect="plain" class="kind-tag">{{ KIND_TEXT[kindOf(att)] }}</el-tag>
      <div class="att-info">
        <div class="att-name" :title="att.name">{{ att.name }}</div>
        <div class="att-sub">{{ formatSize(att.size) }}</div>
      </div>
      <el-button
        v-if="kindOf(att) === 'image'"
        link
        type="primary"
        :icon="View"
        @click="previewImage(att)"
      >
        预览
      </el-button>
      <el-button
        v-else-if="kindOf(att) === 'pdf'"
        link
        type="primary"
        :icon="View"
        @click="previewPdf(att)"
      >
        预览
      </el-button>
      <el-button
        v-else
        link
        type="primary"
        :icon="Download"
        :loading="downloading"
        @click="handleDownload(att)"
      >
        下载
      </el-button>
      <el-button
        v-if="deletable"
        link
        type="danger"
        :icon="Delete"
        :loading="deletingId === att.id"
        @click="handleDelete(att)"
      >
        删除
      </el-button>
    </div>

    <!-- 图片查看器 -->
    <el-image-viewer
      v-if="imageViewerVisible"
      :url-list="imageUrls"
      @close="imageViewerVisible = false"
    />

    <!-- PDF 预览抽屉 -->
    <el-drawer v-model="pdfVisible" :title="pdfTitle" size="640px" destroy-on-close>
      <div v-loading="pdfLoading" ref="pdfContainer" class="pdf-container" />
    </el-drawer>
  </div>
  <div v-else class="text-sub">无附件</div>
</template>

<style scoped>
.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 0;
  background: var(--bg);
}

.kind-tag {
  flex-shrink: 0;
}

.att-info {
  flex: 1;
  min-width: 0;
}

.att-name {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.att-sub {
  font-size: 12px;
  color: var(--text-sub);
}

.pdf-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.pdf-page {
  max-width: 100%;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}
</style>
