<script setup>
/**
 * 首页轮播管理（/hero，school_admin / system_admin）
 * 门户首页大图轮播位维护：图片上传（canvas 压缩 ≤500KB）+ 标题/副标题/顺序/上下架
 * published=门户展示 / withdrawn=隐藏；删除不可恢复（二次确认）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  getHeroAdminList,
  createHeroSlide,
  updateHeroSlide,
  deleteHeroSlide,
} from '@/api/hero'
import { formatTime } from '@/utils/format'
import LucideIcon from '@/components/LucideIcon.vue'

const loading = ref(false)
const list = ref([])

const STATUS_META = {
  published: { text: '展示中', type: 'success' },
  withdrawn: { text: '已隐藏', type: 'info' },
}
const statusOf = (s) => STATUS_META[s] || { text: s || '—', type: 'info' }

const sorted = computed(() => [...list.value].sort((a, b) => (a.display_order ?? 0) - (b.display_order ?? 0) || a.id - b.id))

/** 按展示位置分组：首屏轮播图 / 美丽校园（上下两个分区） */
const SECTIONS = [
  { key: 'hero', title: '首屏轮播图', desc: '门户首页顶部大图轮播（满屏）' },
  { key: 'campus', title: '美丽校园', desc: '首页「美丽校园」校园风光模块轮播' },
]
const grouped = computed(() =>
  SECTIONS.map((sec) => ({
    ...sec,
    items: sorted.value.filter((s) => (s.placement || 'hero') === sec.key),
  }))
)

async function loadList() {
  loading.value = true
  try {
    const res = await getHeroAdminList()
    list.value = res.data.list || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

/* ===== 照片直传（canvas 压缩为 base64 data URL，≤5MB；首屏大图给更高配额） ===== */
const photoInput = ref(null)
const photoBusy = ref(false)

function pickPhoto() {
  photoInput.value?.click()
}

async function onPhotoPicked(e) {
  const f = e.target.files && e.target.files[0]
  e.target.value = ''
  if (!f) return
  if (!/^image\//.test(f.type)) {
    ElMessage.warning('请选择图片文件')
    return
  }
  photoBusy.value = true
  try {
    form.image = await compressPhoto(f)
    ElMessage.success('图片已就绪')
  } catch (err) {
    ElMessage.error(err.message || '图片处理失败')
  } finally {
    photoBusy.value = false
  }
}

/** canvas 压缩：最长边 ≤1600（首屏大图高清优先），jpeg 0.82；超 500KB 逐级降质量 */
function compressPhoto(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const img = new Image()
      img.onload = () => {
        const scale = Math.min(1, 1600 / Math.max(img.width, img.height))
        const w = Math.max(1, Math.round(img.width * scale))
        const h = Math.max(1, Math.round(img.height * scale))
        const canvas = document.createElement('canvas')
        canvas.width = w
        canvas.height = h
        canvas.getContext('2d').drawImage(img, 0, 0, w, h)
        let q = 0.82
        let dataUrl = canvas.toDataURL('image/jpeg', q)
        while (dataUrl.length > 500 * 1024 * 1.37 && q > 0.4) {
          q -= 0.1
          dataUrl = canvas.toDataURL('image/jpeg', q)
        }
        resolve(dataUrl)
      }
      img.onerror = () => reject(new Error('图片解析失败'))
      img.src = reader.result
    }
    reader.onerror = () => reject(new Error('图片读取失败'))
    reader.readAsDataURL(file)
  })
}

/* ===== 新增 / 编辑弹窗 ===== */
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const form = reactive({})

function blankForm() {
  Object.assign(form, {
    title: '',
    subtitle: '',
    image: '',
    display_order: 0,
    status: 'published',
    placement: 'hero',
  })
}

function openCreate(placement) {
  editingId.value = null
  blankForm()
  if (placement) form.placement = placement
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    title: row.title || '',
    subtitle: row.subtitle || '',
    image: row.image || '',
    display_order: row.display_order ?? 0,
    status: row.status || 'published',
    placement: row.placement || 'hero',
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写主标题')
    return
  }
  if (!form.image) {
    ElMessage.warning('请上传轮播图片')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateHeroSlide(editingId.value, { ...form })
      ElMessage.success('已保存')
    } else {
      await createHeroSlide({ ...form })
      ElMessage.success('已新增')
    }
    dialogVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  const next = row.status === 'published' ? 'withdrawn' : 'published'
  try {
    await updateHeroSlide(row.id, { status: next })
    ElMessage.success(next === 'published' ? '已上架，门户立即展示' : '已隐藏，门户不再展示')
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.title}」？删除后门户不再展示该张，且不可恢复。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteHeroSlide(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(loadList)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="eyebrow">门户 · 首页轮播</div>
        <h1 class="page-title">首页轮播</h1>
        <p class="page-sub">门户图片素材统一维护：展示位置可选「首屏轮播」或「美丽校园」，支持上传图片（自动压缩）、编辑标题副标题、调整顺序与上下架，保存后门户立即生效。</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增轮播</el-button>
      </div>
    </div>

    <div class="list-card" v-loading="loading">
      <template v-if="sorted.length">
        <!-- 按展示位置上下分区：首屏轮播图 / 美丽校园 -->
        <section v-for="sec in grouped" :key="sec.key" class="place-sec">
          <div class="place-head">
            <div>
              <h3 class="place-title">{{ sec.title }}</h3>
              <p class="place-desc">{{ sec.desc }} · 共 {{ sec.items.length }} 张</p>
            </div>
            <el-button size="small" :icon="Plus" @click="openCreate(sec.key)">新增到{{ sec.title }}</el-button>
          </div>
          <div v-if="sec.items.length">
            <div v-for="s in sec.items" :key="s.id" class="slide-row">
              <div class="thumb">
                <img v-if="s.image" :src="s.image" :alt="s.title" />
                <span v-else class="thumb-empty">无图</span>
              </div>
              <div class="slide-main">
                <div class="slide-line">
                  <span class="slide-title">{{ s.title }}</span>
                  <el-tag :type="statusOf(s.status).type" effect="light" size="small">{{ statusOf(s.status).text }}</el-tag>
                  <span class="order-chip">#{{ s.display_order ?? 0 }}</span>
                </div>
                <p class="slide-sub">{{ s.subtitle || '（无副标题）' }}</p>
                <p class="slide-meta">ID #{{ s.id }} · 更新于 {{ formatTime(s.updated_at) }}</p>
              </div>
              <div class="acts">
                <button class="act-link" @click="openEdit(s)">编辑</button>
                <button class="act-link" @click="toggleStatus(s)">{{ s.status === 'published' ? '隐藏' : '上架' }}</button>
                <button class="act-link act-reject" @click="handleDelete(s)">删除</button>
              </div>
            </div>
          </div>
          <div v-else class="place-empty">该位置暂无素材，点击右上角「新增到{{ sec.title }}」添加。</div>
        </section>
      </template>

      <div v-else-if="!loading" class="empty">
        <div class="ico"><LucideIcon name="images" :size="30" /></div>
        <h4>暂无轮播配置</h4>
        <p>点击右上角「新增轮播」创建素材，展示位置可选「首屏轮播」或「美丽校园」。</p>
      </div>
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? `编辑轮播 #${editingId}` : '新增轮播'"
      width="680px"
      destroy-on-close
    >
      <el-form label-width="92px" label-position="left">
        <el-form-item label="轮播图片" required>
          <div class="photo-uploader">
            <div v-if="form.image" class="photo-preview">
              <img :src="form.image" alt="轮播图预览" />
              <div class="photo-mask">
                <button type="button" class="photo-btn" @click="pickPhoto">更换</button>
                <button type="button" class="photo-btn danger" @click="form.image = ''">移除</button>
              </div>
            </div>
            <button v-else type="button" class="photo-empty" :disabled="photoBusy" @click="pickPhoto">
              <span class="ic"><LucideIcon name="images" :size="24" /></span>
              <span>{{ photoBusy ? '处理中…' : '点击上传图片' }}</span>
              <span class="tip">建议横图 · 自动压缩至 500KB 内</span>
            </button>
            <input ref="photoInput" type="file" accept="image/*" hidden @change="onPhotoPicked" />
          </div>
        </el-form-item>
        <el-form-item label="主标题" required>
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="如 城堡长灯 · 静候归人" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="form.subtitle" maxlength="200" show-word-limit placeholder="如 无论走多远，这里永远为你亮灯" />
        </el-form-item>
        <el-form-item label="展示位置" required>
          <el-radio-group v-model="form.placement">
            <el-radio value="hero">首屏轮播（门户顶部大图）</el-radio>
            <el-radio value="campus">美丽校园（首页校园风光模块）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="展示顺序">
          <el-input-number v-model="form.display_order" :min="0" controls-position="right" />
          <span class="order-tip">数字越小越靠前</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="published">上架展示</el-radio>
            <el-radio value="withdrawn">隐藏</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
@import '../shared/list-shared.css';

.head-actions {
  display: flex;
  gap: 10px;
}

/* ===== 展示位置分区（首屏轮播图 / 美丽校园，上下排列） ===== */
.place-sec + .place-sec {
  border-top: 8px solid var(--surface-2);
}

.place-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px 12px;
}

.place-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
}

.place-desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-mute);
}

.place-empty {
  margin: 0 20px 16px;
  padding: 18px;
  border: 1px dashed var(--line);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--text-mute);
  text-align: center;
}

.slide-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--line);
  transition: background 0.12s;
}

.slide-row:hover {
  background: var(--surface-2);
}

.slide-row:last-child {
  border-bottom: none;
}

.thumb {
  width: 148px;
  height: 84px;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1px solid var(--line);
  background: var(--surface-3);
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumb-empty {
  font-size: 12px;
  color: var(--text-mute);
}

.slide-main {
  flex: 1;
  min-width: 0;
}

.slide-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.slide-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}

.order-chip {
  font-size: 11px;
  color: var(--text-mute);
  background: var(--surface-3);
  border-radius: var(--radius-sm);
  padding: 2px 8px;
}

.slide-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-sub);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.slide-meta {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-mute);
}

.acts {
  display: flex;
  gap: 12px;
  flex-shrink: 0;
}

.order-tip {
  margin-left: 10px;
  font-size: 12px;
  color: var(--text-mute);
}

/* 轮播图上传控件（首屏大图，16:9 预览） */
.photo-uploader {
  width: 100%;
}
.photo-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 320px;
  height: 180px;
  border: 1.5px dashed #c0c8d6;
  border-radius: var(--radius-md);
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}
.photo-empty:hover:not(:disabled) {
  border-color: var(--brand-500, #2f6bff);
  background: #f0f5ff;
  color: var(--brand-500, #2f6bff);
}
.photo-empty:disabled {
  opacity: 0.6;
  cursor: wait;
}
.photo-empty .ic {
  font-size: 26px;
}
.photo-empty .tip {
  font-size: 11px;
  color: #94a3b8;
}
.photo-preview {
  position: relative;
  width: 320px;
  height: 180px;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1px solid #e2e8f0;
}
.photo-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.photo-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(15, 23, 42, 0.45);
  opacity: 0;
  transition: opacity 0.2s;
}
.photo-preview:hover .photo-mask {
  opacity: 1;
}
.photo-btn {
  padding: 5px 12px;
  border: none;
  border-radius: var(--radius-sm);
  background: #fff;
  color: #1e293b;
  font-size: 12px;
  cursor: pointer;
}
.photo-btn.danger {
  color: #dc2626;
}
</style>
