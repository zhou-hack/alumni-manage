<script setup>
/**
 * 校友大屏展示字段编辑弹窗（V18）
 *
 * 用法：
 *   <AlumniProfileEditDialog v-model="visible" :mode="'admin'" :alumni-id="row.id"
 *     @saved="onSaved" />
 *   <AlumniProfileEditDialog v-model="visible" :mode="'byHonor'" :honor-id="row.id"
 *     @saved="onSaved" />
 *   <AlumniProfileEditDialog v-model="visible" :mode="'self'" @saved="onSaved" />
 *
 * mode:
 *   - 'admin'    ：校级/系统管理员写任意校友（调 PATCH /admin/alumni/{id}/profile）
 *   - 'byHonor'  ：从荣誉行反查校友（先 GET /honors/{id}/alumni-base-id，再走 admin 写）
 *   - 'self'     ：校友本人写自己（调 PATCH /me/profile，不传 alumniId）
 *
 * Props:
 *   v-model  boolean   弹窗开关
 *   mode     'admin' | 'byHonor' | 'self'
 *   alumniId number     仅 mode='admin' 需要
 *   honorId  number     仅 mode='byHonor' 需要
 *
 * Emits:
 *   saved    (newProfile)  保存成功后回调
 */
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, Refresh, Check } from '@element-plus/icons-vue'
import LucideIcon from '@/components/LucideIcon.vue'
import {
  getAdminProfile,
  updateAdminProfile,
  getMyProfile,
  updateMyProfile,
  getAlumniBaseIdByHonor,
} from '@/api/alumniProfile'
import { uploadAttachments } from '@/api/attachment'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  mode: { type: String, default: 'self' }, // 'admin' | 'byHonor' | 'self'
  alumniId: { type: Number, default: null },
  honorId: { type: Number, default: null },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const previewUrl = ref('')
const form = ref(emptyForm())

// mode='byHonor' 解析出的目标校友库 ID（异步解析）
const resolvedAlumniId = ref(null)

function emptyForm() {
  return {
    photoUrl: '',
    industry: '',
    intro: '',
    quote: '',
    motto: '',
  }
}

const titleText = computed(() => {
  if (props.mode === 'admin') return `编辑校友档案 #${props.alumniId}`
  if (props.mode === 'byHonor') return `编辑校友风采（荣誉 #${props.honorId}）`
  return '编辑我的档案'
})

async function load() {
  loading.value = true
  try {
    let targetId = null
    if (props.mode === 'admin' && props.alumniId) {
      targetId = props.alumniId
    } else if (props.mode === 'byHonor' && props.honorId) {
      const r = await getAlumniBaseIdByHonor(props.honorId)
      targetId = r.data?.alumni_base_id || r.alumni_base_id || null
      resolvedAlumniId.value = targetId
      if (!targetId) {
        ElMessage.error('未找到该荣誉对应的校友库记录')
        return
      }
    } else {
      // self：先 GET /me/profile 取当前账号校友库 ID
      const r = await getMyProfile()
      const d = r.data || {}
      targetId = d.id
      form.value = {
        photoUrl: d.photoUrl || '',
        industry: d.industry || '',
        intro: d.intro || '',
        quote: d.quote || '',
        motto: d.motto || '',
      }
      previewUrl.value = d.photoUrl || ''
      return
    }
    const res = await getAdminProfile(targetId)
    const d = res.data || {}
    form.value = {
      photoUrl: d.photoUrl || '',
      industry: d.industry || '',
      intro: d.intro || '',
      quote: d.quote || '',
      motto: d.motto || '',
    }
    previewUrl.value = d.photoUrl || ''
  } catch (e) {
    ElMessage.error(e.message || '资料加载失败')
  } finally {
    loading.value = false
  }
}

async function submitForm() {
  saving.value = true
  try {
    const payload = { ...form.value }
    let res
    if (props.mode === 'admin' && props.alumniId) {
      res = await updateAdminProfile(props.alumniId, payload)
    } else if (props.mode === 'byHonor' && resolvedAlumniId.value) {
      res = await updateAdminProfile(resolvedAlumniId.value, payload)
    } else {
      res = await updateMyProfile(payload)
    }
    ElMessage.success('已保存')
    emit('saved', res.data)
    visible.value = false
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onPickPhoto(file) {
  if (!file) return
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('照片超过 10MB')
    return
  }
  uploading.value = true
  try {
    const res = await uploadAttachments([file], 'alumni_photo', '校友风采照片')
    const arr = res.data?.success || res.success || []
    if (arr[0]?.url) {
      form.value.photoUrl = arr[0].url
      previewUrl.value = arr[0].url
      ElMessage.success('已上传，请点击保存')
    } else {
      ElMessage.error('上传失败')
    }
  } catch (e) {
    ElMessage.error(e.message || '上传失败')
  } finally {
    uploading.value = false
  }
  return false // 阻止 el-upload 默认上传
}

function clearPhoto() {
  form.value.photoUrl = ''
  previewUrl.value = ''
}

watch(visible, (v) => {
  if (v) load()
})
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="titleText"
    width="560px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div v-loading="loading">
      <!-- 照片区 -->
      <section class="photo-block">
        <div class="photo-frame">
          <img v-if="previewUrl" :src="previewUrl" alt="校友照片" />
          <div v-else class="photo-placeholder">
            <LucideIcon name="UserFilled" :size="36" />
            <span>暂无照片</span>
          </div>
        </div>
        <div class="photo-actions">
          <el-upload
            :show-file-list="false"
            :before-upload="onPickPhoto"
            accept="image/*"
          >
            <el-button :icon="Upload" :loading="uploading">上传照片</el-button>
          </el-upload>
          <el-button v-if="form.photoUrl" :icon="Refresh" @click="clearPhoto">清除</el-button>
          <div class="photo-hint">支持 JPG/PNG/WebP，≤10MB</div>
        </div>
      </section>

      <el-form label-width="80px" label-position="left" class="profile-form">
        <el-form-item label="所在行业">
          <el-input v-model="form.industry" maxlength="64" show-word-limit placeholder="例如：互联网 / 教育 / 金融 ……" />
        </el-form-item>
        <el-form-item label="个人简介">
          <el-input
            v-model="form.intro"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="一段话介绍自己的成长经历 / 当前在做的事 / 期待与校友的合作……"
          />
        </el-form-item>
        <el-form-item label="人生信条">
          <el-input v-model="form.quote" maxlength="200" show-word-limit placeholder="一句最想分享的话" />
        </el-form-item>
        <el-form-item label="座右铭">
          <el-input v-model="form.motto" maxlength="200" show-word-limit placeholder="印在心里、推自己前行的那句话" />
        </el-form-item>
      </el-form>

      <div class="footer-hint">
        <LucideIcon name="InfoFilled" :size="14" />
        <span>
          保存后将立刻出现在荣誉大屏「校友风采 / 长廊」中。
          <span v-if="userStore.role === 'alumni' || userStore.role === 'student'">你只能修改自己的资料。</span>
        </span>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :icon="Check" :loading="saving" @click="submitForm">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.photo-block {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 12px;
  margin-bottom: 16px;
  background: var(--surface-2);
  border: 1px dashed var(--line);
  border-radius: var(--radius-lg);
}
.photo-frame {
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand-500), var(--brand-700));
  overflow: hidden;
  display: grid;
  place-items: center;
  color: #fff;
  flex-shrink: 0;
}
.photo-frame img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.photo-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  opacity: 0.85;
}
.photo-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}
.photo-hint {
  font-size: 12px;
  color: var(--text-sub);
}
.profile-form {
  margin-top: 4px;
}
.footer-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
  font-size: 12px;
  color: var(--text-sub);
}
</style>
