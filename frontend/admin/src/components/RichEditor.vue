<script setup>
/**
 * Word 式富文本编辑器（公告模块专用，清单第七章）
 * - 块级内容模型：文本块 + 图片块，回车换段
 * - 图片：光标处插入（选中的图片块之后；无选中则追加末尾）、点击选中、
 *   拖角等比缩放（右下手柄）、拖拽移动到任意块前后、删除
 * - 图片静态资源存储：上传 POST /api/v1/announcements/images → 正文只存
 *   [[alumni-img:URL|宽度]] 标记（旧内容为 base64，兼容渲染）
 *   上传前 canvas 压缩（最长边 ≤1280px / jpeg 0.85，单张 ≤10MB）
 * - Word 导入：mammoth.js 浏览器端解析 .docx（文字 + 内嵌图片，逐张上传后引用 URL），懒加载分包
 * - 粘贴：拦截富文本，按纯文本行拆块；支持直接粘贴截图（image/*，同样上传）
 */
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture, Document, Delete, Top, Bottom } from '@element-plus/icons-vue'
import { IMG_TOKEN, imgSrcOf, uploadAnnouncementImage } from '@/api/announcement'

const props = defineProps({
  modelValue: { type: String, default: '' },
  /** 编辑器占位提示 */
  placeholder: { type: String, default: '输入公告正文，或通过工具栏插入图片 / 导入 Word 文档' },
})
const emit = defineEmits(['update:modelValue'])

const root = ref(null)
const selectedImg = ref(null) // 当前选中的图片块 DOM
let internal = false // 内部反序列化时跳过回写

// ---------------- 块构建 ----------------
function textBlock(text) {
  const div = document.createElement('div')
  div.className = 're-text'
  div.textContent = text
  return div
}

function imgBlock(src, width) {
  const wrap = document.createElement('div')
  wrap.className = 're-img'
  wrap.contentEditable = 'false'
  wrap.draggable = 'true'
  const img = document.createElement('img')
  img.src = imgSrcOf(src)
  img.style.width = `${width}px`
  img.dataset.src = src
  img.dataset.w = width
  wrap.appendChild(img)
  // 缩放手柄
  const handle = document.createElement('span')
  handle.className = 're-resize'
  wrap.appendChild(handle)
  return wrap
}

// ---------------- 序列化 / 反序列化 ----------------
function serialize() {
  // 浏览器回车会产生无类名的新 div/p，非图片块一律按文本块处理
  const lines = []
  for (const b of root.value.children) {
    if (b.classList?.contains('re-img')) {
      const img = b.querySelector('img')
      lines.push(IMG_TOKEN.encode(img.dataset.src, img.dataset.w || Math.round(img.getBoundingClientRect().width)))
    } else if (b.tagName === 'BR') {
      lines.push('')
    } else {
      lines.push(b.innerText.replace(/\u00a0/g, ' '))
    }
  }
  return lines.join('\n')
}

function deserialize(content) {
  internal = true
  root.value.innerHTML = ''
  const lines = String(content || '').split('\n')
  for (const line of lines) {
    const m = line.match(/^\[\[alumni-img:([^\]|]+)\|(\d+)\]\]$/)
    if (m) root.value.appendChild(imgBlock(m[1], Number(m[2]) || 480))
    else root.value.appendChild(textBlock(line))
  }
  if (!root.value.children.length) root.value.appendChild(textBlock(''))
  clearSelection()
  internal = false
}

function emitChange() {
  if (internal) return
  emit('update:modelValue', serialize())
}

// 外部值变化（如加载详情）→ 重建 DOM；编辑器自身输入不触发（避免光标跳动）
watch(
  () => props.modelValue,
  (v) => {
    if (root.value && v !== serialize()) deserialize(v)
  },
)

onMounted(() => {
  deserialize(props.modelValue)
})

// ---------------- 图片选中 / 缩放 / 拖拽 ----------------
function clearSelection() {
  if (selectedImg.value) selectedImg.value.classList.remove('is-selected')
  selectedImg.value = null
}

function onRootClick(e) {
  const block = e.target.closest('.re-img')
  if (block) {
    if (selectedImg.value !== block) clearSelection()
    selectedImg.value = block
    block.classList.add('is-selected')
  } else {
    clearSelection()
  }
}

/** 拖角等比缩放（只改宽度，高度自适应） */
let resizing = null
function onRootMouseDown(e) {
  const handle = e.target.closest('.re-resize')
  if (!handle) return
  e.preventDefault()
  const block = handle.closest('.re-img')
  const img = block.querySelector('img')
  resizing = { img, startX: e.clientX, startW: img.getBoundingClientRect().width }
  document.body.style.cursor = 'nwse-resize'
}
function onWindowMouseMove(e) {
  if (!resizing) return
  const w = Math.max(80, Math.min(960, Math.round(resizing.startW + (e.clientX - resizing.startX))))
  resizing.img.style.width = `${w}px`
}
function onWindowMouseUp() {
  if (!resizing) return
  resizing.img.dataset.w = Math.round(resizing.img.getBoundingClientRect().width)
  resizing = null
  document.body.style.cursor = ''
  emitChange()
}

/** 拖拽移动图片块到任意块前后 */
let dragging = null
function onDragStart(e) {
  const block = e.target.closest('.re-img')
  if (!block) return
  dragging = block
  e.dataTransfer.effectAllowed = 'move'
}
function onDragOver(e) {
  if (!dragging) return
  e.preventDefault()
  const target = e.target.closest('.re-text, .re-img')
  if (!target || target === dragging) return
  const rect = target.getBoundingClientRect()
  const before = e.clientY < rect.top + rect.height / 2
  const ref = before ? target : target.nextSibling
  if (dragging.nextSibling === target && !before) return
  root.value.insertBefore(dragging, ref)
}
function onDragEnd() {
  dragging = null
  emitChange()
}

/** 图片块位置微调按钮（拖拽的补充，触屏/精确场景） */
function moveSelected(dir) {
  const b = selectedImg.value
  if (!b) return
  if (dir === 'up' && b.previousElementSibling) root.value.insertBefore(b, b.previousElementSibling)
  if (dir === 'down' && b.nextElementSibling) root.value.insertBefore(b.nextElementSibling, b)
  emitChange()
}

function removeSelected() {
  if (!selectedImg.value) return
  selectedImg.value.remove()
  clearSelection()
  emitChange()
}

// ---------------- 图片插入（压缩） ----------------
const fileInput = ref(null)
function pickImages() {
  fileInput.value?.click()
}

async function onFilesPicked(e) {
  const files = [...(e.target.files || [])]
  e.target.value = ''
  for (const f of files) {
    if (!/^image\//.test(f.type)) {
      ElMessage.warning(`「${f.name}」不是图片文件`)
      continue
    }
    try {
      const { blob, width } = await compressImage(f)
      const url = await uploadImageBlob(blob, f.name)
      insertImg(url, width)
    } catch (err) {
      ElMessage.error(`「${f.name}」上传失败：` + (err.message || '请重试'))
    }
  }
  emitChange()
}

/** 压缩后的 Blob 上传为静态资源，返回正文标记引用的 URL */
async function uploadImageBlob(blob, name = 'image.jpg') {
  const ext = (blob.type.split('/')[1] || 'jpeg').replace('jpeg', 'jpg')
  const file = new File([blob], name.replace(/\.\w+$/, '') + '.' + ext, { type: blob.type })
  const res = await uploadAnnouncementImage(file)
  return res.data.url
}

/** canvas 压缩：最长边 ≤720，jpeg 0.85；仍超 100KB 逐级降质量；输出 Blob 供上传 */
function compressImage(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const img = new Image()
      img.onload = () => {
        const scale = Math.min(1, 720 / Math.max(img.width, img.height))
        const w = Math.max(1, Math.round(img.width * scale))
        const h = Math.max(1, Math.round(img.height * scale))
        const canvas = document.createElement('canvas')
        canvas.width = w
        canvas.height = h
        canvas.getContext('2d').drawImage(img, 0, 0, w, h)
        let q = 0.85
        let dataUrl = canvas.toDataURL('image/jpeg', q)
        while (dataUrl.length > 100 * 1024 * 1.37 && q > 0.4) {
          q -= 0.15
          dataUrl = canvas.toDataURL('image/jpeg', q)
        }
        resolve({ blob: dataUrlToBlob(dataUrl), width: w })
      }
      img.onerror = () => reject(new Error('图片解析失败'))
      img.src = reader.result
    }
    reader.onerror = () => reject(new Error('图片读取失败'))
    reader.readAsDataURL(file)
  })
}

/** dataURL → Blob（上传用） */
function dataUrlToBlob(dataUrl) {
  const [head, b64] = dataUrl.split(',')
  const mime = head.match(/^data:([^;]+)/)?.[1] || 'image/jpeg'
  const bin = atob(b64)
  const bytes = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
  return new Blob([bytes], { type: mime })
}

/** 插入位置：选中的图片块之后；否则追加到末尾 */
function insertImg(src, width) {
  const block = imgBlock(src, width)
  if (selectedImg.value && selectedImg.value.parentNode === root.value) {
    selectedImg.value.after(block)
  } else {
    root.value.appendChild(block)
  }
  root.value.scrollTop = root.value.scrollHeight
}

// ---------------- Word 导入（mammoth 懒加载） ----------------
const importing = ref(false)
const wordInput = ref(null)
function pickWord() {
  wordInput.value?.click()
}

async function onWordPicked(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (!/\.docx$/i.test(file.name)) {
    ElMessage.warning('仅支持 .docx 格式（旧版 .doc 请先另存为 .docx）')
    return
  }
  importing.value = true
  try {
    const mod = await import('mammoth/mammoth.browser.min.js')
    const mammoth = mod.default || mod
    const arrayBuffer = await file.arrayBuffer()
    const result = await mammoth.convertToHtml({ arrayBuffer })
    const doc = new DOMParser().parseFromString(result.value, 'text/html')
    let inserted = 0
    for (const el of doc.body.children) {
      const imgs = el.querySelectorAll('img')
      const text = el.textContent.replace(/\s+/g, ' ').trim()
      if (text) {
        root.value.appendChild(textBlock(text))
        inserted++
      }
      for (const im of imgs) {
        const src = im.getAttribute('src') || ''
        const m = src.match(/^data:([^;]+);base64,(.+)$/)
        if (m) {
          // Word 内嵌图片：解码后上传为静态资源，正文只引用 URL，不再内嵌 base64
          const blob = dataUrlToBlob(src)
          const ext = m[1].split('/')[1] || 'png'
          const url = await uploadImageBlob(blob, `word-image.${ext}`)
          root.value.appendChild(imgBlock(url, 480))
          inserted++
        }
      }
    }
    if (!root.value.children.length) root.value.appendChild(textBlock(''))
    emitChange()
    ElMessage.success(inserted ? `已导入 ${file.name}（文字与图片已提取）` : '文档中没有可导入的内容')
  } catch (err) {
    console.error(err)
    ElMessage.error('Word 解析失败：' + (err.message || '文件可能已损坏'))
  } finally {
    importing.value = false
  }
}

// ---------------- 粘贴（纯文本 + 截图） ----------------
function onPaste(e) {
  e.preventDefault()
  const items = [...(e.clipboardData?.items || [])]
  const imgs = items.filter((i) => i.type.startsWith('image/'))
  if (imgs.length) {
    for (const it of imgs) {
      const f = it.getAsFile()
      if (!f) continue
      compressImage(f)
        .then(({ blob, width }) => uploadImageBlob(blob, f.name || 'paste').then((url) => insertImg(url, width)))
        .then(() => emitChange())
        .catch((err) => ElMessage.error('粘贴图片上传失败：' + (err.message || '请重试')))
    }
    return
  }
  const text = e.clipboardData?.getData('text/plain') || ''
  if (!text) return
  // 插入到当前光标（块内插入保留块结构；多行由浏览器拆分）
  const sel = window.getSelection()
  if (!sel.rangeCount) return
  document.execCommand('insertText', false, text)
  emitChange()
}

// ---------------- 生命周期 ----------------
onMounted(() => {
  window.addEventListener('mousemove', onWindowMouseMove)
  window.addEventListener('mouseup', onWindowMouseUp)
})
onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onWindowMouseMove)
  window.removeEventListener('mouseup', onWindowMouseUp)
})
</script>

<template>
  <div class="rich-editor">
    <!-- 工具栏 -->
    <div class="re-toolbar">
      <button type="button" class="re-btn" title="插入图片（光标处 / 选中的图片之后）" @click="pickImages">
        <el-icon><Picture /></el-icon>插入图片
      </button>
      <button type="button" class="re-btn" :disabled="importing" title="导入 .docx（提取文字与内嵌图片）" @click="pickWord">
        <el-icon><Document /></el-icon>{{ importing ? '解析中…' : '导入 Word' }}
      </button>
      <template v-if="selectedImg">
        <span class="re-sep" />
        <button type="button" class="re-btn" title="上移一位" @click="moveSelected('up')"><el-icon><Top /></el-icon>上移</button>
        <button type="button" class="re-btn" title="下移一位" @click="moveSelected('down')"><el-icon><Bottom /></el-icon>下移</button>
        <button type="button" class="re-btn re-danger" title="删除该图片" @click="removeSelected"><el-icon><Delete /></el-icon>删除图片</button>
        <span class="re-hint">拖动右下角手柄可等比缩放，长按图片可拖拽到任意位置</span>
      </template>
    </div>

    <!-- 编辑区 -->
    <div
      ref="root"
      class="re-area"
      contenteditable="true"
      spellcheck="false"
      :data-placeholder="placeholder"
      @input="emitChange"
      @click="onRootClick"
      @mousedown="onRootMouseDown"
      @dragstart="onDragStart"
      @dragover="onDragOver"
      @drop.prevent="onDragEnd"
      @dragend="onDragEnd"
      @paste="onPaste"
    />

    <input ref="fileInput" type="file" accept="image/*" multiple hidden @change="onFilesPicked" />
    <input ref="wordInput" type="file" accept=".docx" hidden @change="onWordPicked" />
  </div>
</template>

<style scoped>
.rich-editor {
  border: 1px solid var(--line);
  border-radius: 0;
  overflow: hidden;
  background: var(--card);
}

.re-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 12px;
  border-bottom: 1px solid var(--line);
  background: var(--bg-soft, #f7f8fa);
}

.re-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 0;
  padding: 5px 10px;
  font-size: 13px;
  color: var(--text-main, #303133);
  cursor: pointer;
  transition: all 0.15s;
}
.re-btn:hover:not(:disabled) {
  border-color: var(--brand, #102a5c);
  color: var(--brand, #102a5c);
}
.re-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.re-danger:hover:not(:disabled) {
  border-color: #ef4444;
  color: #ef4444;
}

.re-sep {
  width: 1px;
  height: 18px;
  background: var(--line);
}

.re-hint {
  font-size: 12px;
  color: var(--text-sub, #909399);
}

.re-area {
  min-height: 320px;
  max-height: 560px;
  overflow-y: auto;
  padding: 14px 18px;
  font-size: 14px;
  line-height: 1.9;
  outline: none;
  color: var(--text-main, #303133);
}
.re-area:empty::before,
.re-area > .re-text:only-child:empty::before {
  content: attr(data-placeholder);
  color: var(--text-sub, #a8abb2);
  pointer-events: none;
}

/* 文本块 */
.re-area :deep(.re-text) {
  min-height: 1.9em;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 图片块 */
.re-area :deep(.re-img) {
  position: relative;
  display: inline-block;
  margin: 6px 0;
  padding: 4px;
  border: 1px dashed transparent;
  border-radius: 0;
  max-width: 100%;
  vertical-align: top;
}
.re-area :deep(.re-img img) {
  display: block;
  max-width: 100%;
  height: auto;
  border-radius: 0;
  pointer-events: none; /* 点击命中包裹层，避免原生图片拖拽 */
  user-select: none;
}
.re-area :deep(.re-img.is-selected) {
  border-color: var(--brand, #102a5c);
  background: rgba(79, 70, 229, 0.04);
}
.re-area :deep(.re-img.is-selected::after) {
  content: '已选中';
  position: absolute;
  top: -10px;
  left: 8px;
  font-size: 11px;
  line-height: 1;
  padding: 3px 8px;
  border-radius: var(--radius-full);
  background: var(--brand, #102a5c);
  color: #fff;
}

/* 缩放手柄 */
.re-area :deep(.re-resize) {
  position: absolute;
  right: -4px;
  bottom: -4px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid var(--brand, #102a5c);
  cursor: nwse-resize;
  display: none;
  z-index: 2;
}
.re-area :deep(.re-img.is-selected .re-resize) {
  display: block;
}
</style>
