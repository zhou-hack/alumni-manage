<script setup>
/**
 * 批量上传组件 · PC 版（M02-02 / M02-03，对齐 H5 端 components/BatchUploader.vue）
 * - 多选文件（图片/PDF/Word/Excel，单文件 ≤10MB）
 * - 批量统一备注（多文件共享一个说明）
 * - 进度条 + 失败重试（单条/全部，原始 File 保留）
 * - v-model:files 已上传附件列表 { id, name, size, mime }
 * - v-model:remark 统一备注
 */
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, Document as DocIcon } from '@element-plus/icons-vue'
import { uploadAttachments, ACCEPT, MAX_FILE_SIZE } from '@/api/attachment'
import { formatSize } from '@/utils/format'

const props = defineProps({
  files: { type: Array, default: () => [] }, // 已上传附件
  remark: { type: String, default: '' },
  bizType: { type: String, default: 'archive' },
  maxCount: { type: Number, default: 9 },
})
const emit = defineEmits(['update:files', 'update:remark', 'change'])

const uploading = ref(false)
const progress = ref(0)
const failedFiles = ref([]) // { name, size, reason, file }
const inputRef = ref(null)

const total = computed(() => props.files.length + failedFiles.value.length)

/** 是否图片（列表缩略展示用） */
const isImage = (f) => f.mime?.startsWith('image/') || /\.(jpe?g|png|gif|webp)$/i.test(f.name || '')

function pickFiles() {
  if (total.value >= props.maxCount) {
    ElMessage.warning(`最多上传 ${props.maxCount} 份材料`)
    return
  }
  inputRef.value?.click()
}

function onInputChange(e) {
  const picked = Array.from(e.target.files || [])
  e.target.value = '' // 允许重复选择同一文件
  if (!picked.length) return
  if (total.value + picked.length > props.maxCount) {
    ElMessage.warning(`最多上传 ${props.maxCount} 份材料，已自动截取前 ${props.maxCount - total.value} 个`)
  }
  const files = picked.slice(0, props.maxCount - total.value)
  const tooLarge = files.filter((f) => f.size > MAX_FILE_SIZE)
  if (tooLarge.length) {
    ElMessage.warning(`${tooLarge.map((f) => f.name).join('、')} 超过 10MB 限制，已跳过`)
  }
  const okFiles = files.filter((f) => f.size <= MAX_FILE_SIZE)
  if (okFiles.length) doUpload(okFiles)
}

/** 批量上传核心：成功入列，失败保留 File 进失败队列。返回本次成功的文件名集合 */
async function doUpload(files) {
  uploading.value = true
  progress.value = 0
  const successNames = new Set()
  try {
    const res = await uploadAttachments(files, props.bizType, props.remark, (p) => {
      progress.value = p
    })
    progress.value = 100
    const { success, failed } = res.data
    if (success.length) {
      success.forEach((s) => successNames.add(s.name))
      emit('update:files', [...props.files, ...success])
      emit('change')
    }
    if (failed.length) {
      const fileMap = new Map(files.map((f) => [f.name, f]))
      const additions = failed.map((f) => ({
        name: f.name,
        size: fileMap.get(f.name)?.size ?? 0,
        reason: f.reason || '上传失败',
        file: fileMap.get(f.name) || null,
      }))
      failedFiles.value = [...failedFiles.value, ...additions]
      ElMessage.warning(`${failed.length} 个文件上传失败，可在下方重试`)
    } else if (success.length) {
      ElMessage.success(`已上传 ${success.length} 个附件`)
    }
  } catch (e) {
    // 整批请求失败（网络/超时）：全部进入失败队列，保留 File 供重试
    failedFiles.value = [
      ...failedFiles.value,
      ...files.map((f) => ({ name: f.name, size: f.size, reason: e.message || '上传失败', file: f })),
    ]
    ElMessage.error('上传失败，可在下方重试')
  } finally {
    uploading.value = false
  }
  return successNames
}

/** 单条重试 */
async function retryOne(index) {
  const item = failedFiles.value[index]
  if (!item?.file) {
    ElMessage.warning('该文件需重新选择上传')
    failedFiles.value = failedFiles.value.filter((_, i) => i !== index)
    return
  }
  const successNames = await doUpload([item.file])
  if (successNames.has(item.name)) {
    failedFiles.value = failedFiles.value.filter((_, i) => i !== index)
  }
}

/** 全部重试 */
async function retryAll() {
  const items = failedFiles.value
  const retryable = items.filter((x) => x.file)
  const lost = items.filter((x) => !x.file)
  if (!retryable.length) {
    ElMessage.warning('没有可重试的文件')
    return
  }
  const successNames = await doUpload(retryable.map((x) => x.file))
  failedFiles.value = [...lost, ...retryable.filter((x) => !successNames.has(x.name))]
}

/** 移除已上传附件 */
function removeFile(index) {
  const next = props.files.filter((_, i) => i !== index)
  emit('update:files', next)
  emit('change')
}
</script>

<template>
  <div class="batch-uploader">
    <!-- 上传区 -->
    <div class="upload-zone" :class="{ disabled: total >= maxCount }" @click="pickFiles">
      <el-icon class="up-icon"><UploadFilled /></el-icon>
      <div class="up-main">点击选择或拖入文件</div>
      <div class="up-sub">支持图片 / PDF / Word / Excel，单文件 ≤10MB，最多 {{ maxCount }} 份</div>
      <input
        ref="inputRef"
        type="file"
        :accept="ACCEPT"
        multiple
        hidden
        @change="onInputChange"
      />
    </div>

    <!-- 统一备注（批量共享） -->
    <div class="remark-box">
      <el-input
        :model-value="remark"
        placeholder="统一备注：为本次上传的多份材料填写一个说明（可选）"
        maxlength="100"
        @update:model-value="(v) => emit('update:remark', v)"
      />
    </div>

    <!-- 上传进度 -->
    <div v-if="uploading" class="progress-box">
      <el-progress :percentage="progress" :stroke-width="8" />
    </div>

    <!-- 已上传列表 -->
    <div v-if="files.length" class="file-list">
      <div v-for="(f, i) in files" :key="f.id" class="file-item">
        <el-icon class="f-icon"><DocIcon /></el-icon>
        <span class="f-name" :title="f.name">{{ f.name }}</span>
        <span class="f-size">{{ formatSize(f.size) }}</span>
        <el-button link type="danger" size="small" @click="removeFile(i)">删除</el-button>
      </div>
    </div>

    <!-- 失败列表（可重试） -->
    <div v-if="failedFiles.length" class="failed-box">
      <div class="failed-head">
        <span>上传失败（{{ failedFiles.length }}）</span>
        <el-button link type="primary" size="small" :disabled="uploading" @click="retryAll">
          全部重试
        </el-button>
      </div>
      <div v-for="(f, i) in failedFiles" :key="f.name + i" class="file-item failed">
        <el-icon class="f-icon"><DocIcon /></el-icon>
        <span class="f-name" :title="f.name">{{ f.name }}</span>
        <span class="f-reason" :title="f.reason">{{ f.reason }}</span>
        <el-button link type="primary" size="small" :disabled="uploading" @click="retryOne(i)">
          重试
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.upload-zone {
  border: 1.5px dashed var(--line-strong);
  border-radius: var(--radius-md);
  padding: 22px 16px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.upload-zone:hover:not(.disabled) {
  border-color: var(--brand-400);
  background: var(--brand-50);
}

.upload-zone.disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.up-icon {
  font-size: 26px;
  color: var(--brand-400);
}

.up-main {
  margin-top: 6px;
  font-size: 14px;
  color: var(--text-main);
}

.up-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-mute);
}

.remark-box {
  margin-top: 12px;
}

.progress-box {
  margin-top: 12px;
}

.file-list,
.failed-box {
  margin-top: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  font-size: 13px;
  border-bottom: 1px solid var(--line);
}

.file-item:last-child {
  border-bottom: none;
}

.f-icon {
  color: var(--brand-500);
  flex: none;
}

.f-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-main);
}

.f-size,
.f-reason {
  flex: none;
  font-size: 12px;
  color: var(--text-mute);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.failed-box {
  background: var(--red-50, #fef2f2);
}

.failed-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--red-700, #b91c1c);
  border-bottom: 1px solid var(--line);
}

.file-item.failed .f-reason {
  color: var(--red-500, #ef4444);
}
</style>
