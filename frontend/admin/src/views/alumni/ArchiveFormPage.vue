<script setup>
/**
 * 校友资料提交页（PC 端，PRD M02-01 ~ M02-05）
 * 逻辑对齐 H5 端 ArchiveFormPage：
 * - 基本信息（标题/类别/内容）必填校验
 * - 批量上传（多选/进度条/失败重试/统一备注）
 * - 草稿：本地主存 + 1 分钟定时云端同步（乐观锁）+ 换设备恢复
 * - 编辑模式（?id=）：draft 可存草稿/提交；rejected 保存后 resubmit 生成 version+1
 */
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ARCHIVE_CATEGORIES, createArchive, getArchive, updateArchive, resubmitArchive } from '@/api/archive'
import { syncDraft, getLocalDraft, setLocalDraft, clearLocalDraft, getMyDrafts, deleteDraftById } from '@/api/draft'
import BatchUploader from '@/components/BatchUploader.vue'

const route = useRoute()
const router = useRouter()

// ---------------- 编辑模式 ----------------
const editId = computed(() => (route.query.id ? Number(route.query.id) : null))
const isEdit = computed(() => Boolean(editId.value))
const editStatus = ref('')
const rejectReason = ref('')
const loadingDetail = ref(false)

// ---------------- 表单 ----------------
const form = reactive({ title: '', category: '', content: '' })
const batchRemark = ref('')
const submitting = ref(false)
const saving = ref(false)

// ---------------- 附件（BatchUploader 管理） ----------------
const attachments = ref([])

// ---------------- 草稿（localStorage 主存 + 云端定时同步） ----------------
const draftVersion = ref(0)
const draftDirty = ref(false)
const draftSavedAt = ref('')
let syncTimer = null

function buildPayload() {
  return {
    title: form.title,
    category: form.category,
    content: form.content,
    remark: batchRemark.value,
    attachment_ids: attachments.value.map((a) => a.id),
    // 草稿专用：附件完整对象（恢复时回填列表展示用，提交时后端忽略该字段）
    draft_attachments: attachments.value.map((a) => ({ id: a.id, name: a.name, size: a.size, mime: a.mime })),
  }
}

function restorePayload(payload) {
  form.title = payload.title || ''
  form.category = payload.category || ''
  form.content = payload.content || ''
  batchRemark.value = payload.remark || ''
  if (Array.isArray(payload.draft_attachments) && payload.draft_attachments.length) {
    attachments.value = payload.draft_attachments.map((a) => ({ ...a, url: `/api/v1/attachments/${a.id}/preview` }))
  } else if (Array.isArray(payload.attachment_ids)) {
    attachments.value = payload.attachment_ids.map((id) => ({
      id,
      name: `附件 ${id}`,
      size: 0,
      url: `/api/v1/attachments/${id}/preview`,
    }))
  }
}

/** 本地落盘：表单每次变更即写（编辑已有档案时不启用，避免与新建草稿混写） */
function saveLocalDraft() {
  if (isEdit.value) return
  setLocalDraft({ version: draftVersion.value, payload: buildPayload(), updatedAt: Date.now() })
  draftDirty.value = true
  draftSavedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

/** 云端同步：版本号乐观锁，冲突时服务端最新覆盖本地（仅新建模式） */
async function doSync(showTip = false) {
  if (isEdit.value || !draftDirty.value) return
  try {
    const res = await syncDraft({ biz_type: 'archive', version: draftVersion.value, payload: buildPayload() })
    draftVersion.value = res.data.version
    draftDirty.value = false
    if (showTip) ElMessage.success('草稿已同步')
  } catch (e) {
    if (e.code === 'DRAFT_VERSION_CONFLICT') {
      draftVersion.value = e.data.version
      restorePayload(e.data.payload)
      saveLocalDraft()
      draftDirty.value = false
      ElMessage.info('检测到其他设备的更新，已同步最新草稿')
    } else if (showTip) {
      ElMessage.warning('草稿同步失败，已保存在本机')
    }
  }
}

watch(
  () => [form.title, form.category, form.content, batchRemark.value],
  () => saveLocalDraft(),
)

function onAttachmentsChange() {
  saveLocalDraft()
}

function submitPayload() {
  const p = buildPayload()
  delete p.draft_attachments
  return p
}

// ---------------- 校验 ----------------
function validateForm() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写资料标题')
    return false
  }
  if (!form.category) {
    ElMessage.warning('请选择资料类别')
    return false
  }
  if (!form.content.trim()) {
    ElMessage.warning('请填写详细内容')
    return false
  }
  return true
}

// ---------------- 提交 / 存草稿 ----------------
/**
 * @param {boolean} submit false=存草稿；true=提交进入待初审
 * 新建：POST /archives；编辑：PATCH（draft 支持 submit=true；rejected 保存后 resubmit）
 */
async function handleSubmit(submit) {
  if (submitting.value || saving.value) return
  if (!validateForm()) return

  // ---- 编辑模式 ----
  if (isEdit.value) {
    if (editStatus.value === 'draft') {
      if (!submit) {
        saving.value = true
        try {
          await updateArchive(editId.value, { ...submitPayload(), submit: false })
          ElMessage.success('草稿已更新')
          router.replace('/my-archives')
        } catch (e) {
          ElMessage.error(e.message)
        } finally {
          saving.value = false
        }
        return
      }
      submitting.value = true
      try {
        const res = await updateArchive(editId.value, { ...submitPayload(), submit: true })
        if (res.data.status === 'pending_first') {
          ElMessage.success('已提交，等待院系初审')
          router.replace('/my-archives')
        } else {
          // 后端未支持 PATCH submit（契约缺口）：降级提示
          ElMessage.success('修改已保存')
          ElMessage.warning('当前后端暂不支持草稿直接提交，请在列表页提交')
          router.replace('/my-archives')
        }
      } catch (e) {
        ElMessage.error(e.message)
      } finally {
        submitting.value = false
      }
      return
    }
    if (editStatus.value === 'rejected') {
      submitting.value = true
      try {
        if (submit) {
          // 修改内容后重提：先保存修改，再 resubmit 生成 version+1
          await updateArchive(editId.value, submitPayload())
          const res = await resubmitArchive(editId.value)
          if (res.data.status === 'pending_first') {
            ElMessage.success(`修改已保存并重新提交（第 ${res.data.version} 版）`)
            router.replace('/my-archives')
          }
        } else {
          await updateArchive(editId.value, submitPayload())
          ElMessage.success('修改已保存，可稍后在记录页重提')
          router.replace('/my-archives')
        }
      } catch (e) {
        ElMessage.error(e.message)
      } finally {
        submitting.value = false
      }
      return
    }
    ElMessage.warning('当前状态不可编辑')
    return
  }

  // ---- 新建模式 ----
  if (!submit) {
    saving.value = true
    try {
      await createArchive({ ...submitPayload(), submit: false })
      clearLocalDraft()
      ElMessage.success('草稿已保存')
      router.replace('/my-archives')
    } catch (e) {
      ElMessage.error(e.message)
    } finally {
      saving.value = false
    }
    return
  }

  submitting.value = true
  try {
    await createArchive({ ...submitPayload(), submit: true })
    clearLocalDraft()
    ElMessage.success('提交成功，等待院系初审')
    router.replace('/my-archives')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

// ---------------- 编辑模式：加载详情 ----------------
async function loadEditDetail() {
  loadingDetail.value = true
  try {
    const res = await getArchive(editId.value)
    const d = res.data
    editStatus.value = d.status
    rejectReason.value = d.reject_reason || ''
    form.title = d.title || ''
    form.category = d.category || ''
    form.content = d.content || ''
    batchRemark.value = d.remark || ''
    attachments.value = (d.attachments || []).map((a) => ({
      id: a.id,
      name: a.name,
      size: a.size,
      mime: a.mime,
      url: `/api/v1/attachments/${a.id}/preview`,
    }))
    if (d.status !== 'draft' && d.status !== 'rejected') {
      ElMessage.warning('仅草稿或已驳回的档案可编辑')
      router.replace('/my-archives')
    }
  } catch (e) {
    ElMessage.error(e.message)
    router.replace('/my-archives')
  } finally {
    loadingDetail.value = false
  }
}

// ---------------- 换设备云端草稿恢复 ----------------
async function tryRestoreCloudDraft() {
  try {
    const res = await getMyDrafts('archive')
    const cloud = (res.data.list || []).find((d) => d.biz_type === 'archive' && d.version > 0 && d.payload)
    if (!cloud) return
    try {
      await ElMessageBox.confirm(
        `检测到您在其他设备保存的草稿「${(cloud.payload.title || '未命名资料').slice(0, 20)}」，是否继续编辑？`,
        '发现云端草稿',
        { confirmButtonText: '继续编辑', cancelButtonText: '放弃草稿', type: 'info' },
      )
      draftVersion.value = cloud.version
      restorePayload(cloud.payload)
      saveLocalDraft()
      draftSavedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
      ElMessage.success('已恢复云端草稿，可继续编辑')
    } catch (action) {
      if (action === 'cancel') {
        await deleteDraftById(cloud.id)
        ElMessage.success('已放弃云端草稿')
      }
    }
  } catch {
    // 云端草稿查询失败不阻塞新建
  }
}

onMounted(async () => {
  if (isEdit.value) {
    loadEditDetail()
    return
  }
  const local = getLocalDraft()
  if (local?.payload) {
    draftVersion.value = local.version || 0
    restorePayload(local.payload)
  } else {
    await tryRestoreCloudDraft()
  }
  syncTimer = setInterval(() => doSync(false), 60 * 1000)
})

onBeforeUnmount(() => {
  clearInterval(syncTimer)
})

const pageTitle = computed(() => (isEdit.value ? '编辑资料' : '提交资料'))
</script>

<template>
  <div class="archive-form-page">
    <div class="page-head">
      <div class="eyebrow">提交资料</div>
      <h1 class="page-title">{{ pageTitle }}</h1>
    </div>

    <div v-loading="loadingDetail" class="form-wrap">
      <template v-if="!loadingDetail">
        <!-- 驳回原因横幅（编辑已驳回档案） -->
        <el-alert
          v-if="isEdit && editStatus === 'rejected'"
          :title="`该档案曾被驳回：${rejectReason || '见记录页详情'}`"
          description="修改内容后重新提交将生成新版本。"
          type="error"
          show-icon
          :closable="false"
          class="mb-16"
        />

        <!-- 基本信息 -->
        <div class="form-card">
          <div class="card-label">基本信息</div>
          <el-form label-position="top">
            <el-form-item label="资料标题" required>
              <el-input v-model="form.title" placeholder="如：全国大学生程序设计竞赛金奖" maxlength="50" show-word-limit clearable />
            </el-form-item>
            <el-form-item label="资料类别" required>
              <el-select v-model="form.category" placeholder="请选择类别" style="width: 100%">
                <el-option v-for="c in ARCHIVE_CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="详细内容" required>
              <el-input
                v-model="form.content"
                type="textarea"
                :rows="5"
                placeholder="请详细描述经历、成果或荣誉情况"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
          </el-form>
        </div>

        <!-- 证明材料 -->
        <div class="form-card">
          <div class="card-label">证明材料</div>
          <BatchUploader
            v-model:files="attachments"
            v-model:remark="batchRemark"
            biz-type="archive"
            :max-count="9"
            @change="onAttachmentsChange"
          />
        </div>

        <!-- 草稿状态（仅新建） -->
        <div v-if="!isEdit && draftSavedAt" class="draft-tip">
          <span v-if="draftDirty">草稿已保存在本机（{{ draftSavedAt }}），稍后自动同步</span>
          <span v-else>草稿已保存（{{ draftSavedAt }}）</span>
        </div>

        <!-- 操作区 -->
        <div class="action-bar">
          <el-button :loading="saving" @click="handleSubmit(false)">
            {{ isEdit ? (editStatus === 'draft' ? '保存草稿' : '保存修改') : '存草稿' }}
          </el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit(true)">
            {{ isEdit ? (editStatus === 'draft' ? '提交审核' : '保存并重新提交') : '提交审核' }}
          </el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.archive-form-page {
  max-width: 860px;
  margin: 0 auto;
}

.page-head {
  margin-bottom: 20px;
}

.eyebrow {
  font-size: 11px;
  letter-spacing: 0.22em;
  color: var(--text-mute);
  margin-bottom: 6px;
}

.page-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 26px;
  font-weight: 700;
  color: var(--text-main);
}

.form-card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: 20px 24px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-sm);
}

.card-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 14px;
}

.draft-tip {
  font-size: 12px;
  color: var(--text-mute);
  padding: 6px 2px 0;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

.mb-16 {
  margin-bottom: 16px;
}
</style>
