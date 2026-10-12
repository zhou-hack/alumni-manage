<script setup>
/**
 * 优秀校友管理（/outstanding，school_admin / system_admin）
 * 运营精选位 CRUD + 上下架：draft 草稿 → published 发布（门户展厅展示）→ withdrawn 下架
 * 时间线 / 代表荣誉为结构化数组，表单内按行编辑（年份 + 事件 / 荣誉 + 级别，≤20 项）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  getOutstandingList,
  createOutstanding,
  updateOutstanding,
  deleteOutstanding,
} from '@/api/alumniAdmin'
import { getUsers } from '@/api/users'
import { getDict, dictLabel } from '@/api/dict'
import { formatTime } from '@/utils/format'
import LucideIcon from '@/components/LucideIcon.vue'

const loading = ref(false)
const list = ref([])
const statusFilter = ref('')
const colleges = ref([])

const STATUS_META = {
  draft: { text: '草稿', type: 'info' },
  published: { text: '已发布', type: 'success' },
  withdrawn: { text: '已下架', type: 'warning' },
}
const statusOf = (s) => STATUS_META[s] || { text: s || '—', type: 'info' }

const filtered = computed(() =>
  statusFilter.value ? list.value.filter((r) => r.status === statusFilter.value) : list.value,
)

async function loadList() {
  loading.value = true
  try {
    const res = await getOutstandingList({ page: 1, page_size: 100 })
    // 默认按 ID 降序（最新的在前）
    list.value = (res.data.list || []).slice().sort((a, b) => a.id - b.id)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

/* ===== 照片直传（canvas 压缩为 base64 data URL，≤100KB） ===== */
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
    form.photo_url = await compressPhoto(f)
    ElMessage.success('照片已就绪')
  } catch (err) {
    ElMessage.error(err.message || '照片处理失败')
  } finally {
    photoBusy.value = false
  }
}

/** canvas 压缩：最长边 ≤720，jpeg 0.85；仍超 100KB 逐级降质量（与 RichEditor 同口径） */
function compressPhoto(file) {
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

/* ===== 校友选择（远程搜索，免填 user_id） ===== */
const userOptions = ref([])
const userSearching = ref(false)

async function searchUsers(keyword) {
  const kw = String(keyword || '').trim()
  if (!kw) {
    userOptions.value = []
    return
  }
  userSearching.value = true
  try {
    const res = await getUsers({ role: 'alumni', keyword: kw, page: 1, page_size: 10 })
    userOptions.value = res.data.list || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    userSearching.value = false
  }
}

/** 选中校友后带出学号/姓名/学院/届别，可再改 */
function onUserPicked(id) {
  const u = userOptions.value.find((x) => x.id === id)
  if (!u) return
  form.user_id = u.id
  form.student_id = u.student_id || ''
  form.display_name = u.real_name || u.nickname || ''
  if (u.grad_year) form.grad_year = u.grad_year
  const hit = colleges.value.find((c) => c.label === u.college)
  if (hit) form.college_id = hit.id
}

/* ===== 时间线 / 荣誉：按行编辑（空行保存时自动剔除） ===== */
const AWARD_LEVELS = ['国家级', '省级', '市级', '校级']

function addTimelineRow() {
  form.timeline.push({ year: '', event: '' })
}

function addAwardRow() {
  form.awards.push({ title: '', year: '', level: '', category: '' })
}

/** 结构化行 → 后端数组；含空字段的行视为未填剔除，半填的行报错定位 */
function collectRows(rows, label, requiredKeys, maxLen) {
  const out = []
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i]
    const cleaned = {}
    for (const [k, v] of Object.entries(row)) {
      const t = String(v ?? '').trim()
      if (t) cleaned[k] = t
    }
    if (!Object.keys(cleaned).length) continue
    const missing = requiredKeys.filter((k) => !cleaned[k])
    if (missing.length) {
      throw new Error(`${label}第 ${i + 1} 行缺少：${missing.join(' / ')}`)
    }
    out.push(cleaned)
  }
  if (out.length > maxLen) throw new Error(`${label}最多 ${maxLen} 项`)
  return out
}

function blankForm() {
  Object.assign(form, {
    user_id: '',
    student_id: '',
    display_name: '',
    photo_url: '',
    message: '',
    college_id: '',
    grad_year: '',
    current_title: '',
    current_city: '',
    display_order: 0,
    featured: false,
    timeline: [],
    awards: [],
  })
}

function openCreate() {
  editingId.value = null
  blankForm()
  userOptions.value = []
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    user_id: row.user_id,
    student_id: row.student_id,
    display_name: row.display_name,
    photo_url: row.photo_url || '',
    message: row.message || '',
    college_id: row.college_id ?? '',
    grad_year: row.grad_year ?? '',
    current_title: row.current_title || '',
    current_city: row.current_city || '',
    display_order: row.display_order ?? 0,
    featured: Boolean(row.featured),
    timeline: (row.timeline || []).map((t) => ({ year: t.year || '', event: t.event || '' })),
    awards: (row.awards || []).map((a) => ({
      title: a.title || '',
      year: a.year || '',
      level: a.level || '',
      category: a.category || '',
    })),
  })
  // 预置当前选中项，保证编辑态下拉框能回显
  userOptions.value = [
    { id: row.user_id, real_name: row.display_name, student_id: row.student_id },
  ]
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.user_id) {
    ElMessage.warning('请先搜索并选择校友')
    return
  }
  if (!form.student_id || !form.display_name) {
    ElMessage.warning('请填写 学号 / 展示姓名')
    return
  }
  let timeline, awards
  try {
    timeline = collectRows(form.timeline, '时间线', ['year', 'event'], 20)
    awards = collectRows(form.awards, '代表荣誉', ['title', 'year', 'level'], 20)
  } catch (e) {
    ElMessage.error(e.message)
    return
  }
  const payload = {
    user_id: Number(form.user_id),
    student_id: form.student_id,
    display_name: form.display_name,
    photo_url: form.photo_url || null,
    message: form.message || null,
    college_id: form.college_id ? Number(form.college_id) : null,
    grad_year: form.grad_year ? Number(form.grad_year) : null,
    current_title: form.current_title || null,
    current_city: form.current_city || null,
    display_order: Number(form.display_order) || 0,
    featured: Boolean(form.featured),
    timeline,
    awards,
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateOutstanding(editingId.value, payload)
      ElMessage.success('已保存')
    } else {
      await createOutstanding(payload)
      ElMessage.success('已创建（草稿），发布后将在门户展厅展示')
    }
    dialogVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

/* ===== 上下架 / 删除 ===== */
async function setStatus(row, status, tip) {
  try {
    await updateOutstanding(row.id, { status })
    ElMessage.success(tip)
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认从优秀校友中移除「${row.display_name}」？该操作不可恢复。`,
      '移除精选位',
      { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteOutstanding(row.id)
    ElMessage.success('已移除')
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  loadList()
  try {
    colleges.value = await getDict('college')
  } catch {
    /* 字典失败不阻断列表 */
  }
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="eyebrow">优秀校友</div>
        <h1 class="page-title">优秀校友管理</h1>
        <p class="page-sub">维护门户首页「优秀校友」展厅：published 状态对访客可见；排序值越小越靠前。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增精选校友</el-button>
    </div>

    <div class="toolbar">
      <div class="chips">
        <button class="chip" :class="{ active: statusFilter === '' }" @click="statusFilter = ''">
          全部 {{ list.length }}
        </button>
        <button
          v-for="(meta, key) in STATUS_META"
          :key="key"
          class="chip"
          :class="{ active: statusFilter === key }"
          @click="statusFilter = statusFilter === key ? '' : key"
        >
          {{ meta.text }} {{ list.filter((r) => r.status === key).length }}
        </button>
      </div>
      <div class="toolbar-spacer"></div>
      <el-button :icon="Refresh" @click="loadList">刷新</el-button>
    </div>

    <div class="list-card" v-loading="loading">
      <div class="list-row head">
        <div>校友</div>
        <div class="col-hide">学院 · 届别</div>
        <div class="col-hide">当前身份</div>
        <div>排序 / 推荐</div>
        <div>状态</div>
        <div class="col-actions">操作</div>
      </div>

      <template v-if="filtered.length">
        <div v-for="row in filtered" :key="row.id" class="list-row">
          <div class="col-main">
            <div class="cell-title">{{ row.display_name }}</div>
            <div class="cell-meta">{{ row.student_id }} · #{{ row.id }}</div>
          </div>
          <div class="cell col-hide">
            {{ row.college || dictLabel('college', row.college_id) || '—' }}
            <div class="cell-meta">{{ row.grad_year ? row.grad_year + '届' : '—' }}</div>
          </div>
          <div class="cell col-hide cell-ellipsis">
            {{ [row.current_title, row.current_city].filter(Boolean).join(' / ') || '—' }}
          </div>
          <div class="cell">
            <span class="mono">{{ row.display_order }}</span>
            <el-tag v-if="row.featured" size="small" type="warning" effect="plain" class="ml-8">推荐</el-tag>
          </div>
          <div>
            <el-tag :type="statusOf(row.status).type" effect="light">{{ statusOf(row.status).text }}</el-tag>
          </div>
          <div class="row-actions" @click.stop>
            <button class="act-link" @click="openEdit(row)">编辑</button>
            <button
              v-if="row.status !== 'published'"
              class="act-link act-approve"
              @click="setStatus(row, 'published', '已发布，门户展厅将展示')"
            >
              发布
            </button>
            <button
              v-if="row.status === 'published'"
              class="act-link act-warn"
              @click="setStatus(row, 'withdrawn', '已下架，门户展厅不再展示')"
            >
              下架
            </button>
            <button class="act-link act-reject" @click="handleDelete(row)">移除</button>
          </div>
        </div>
      </template>

      <div v-else-if="!loading" class="empty">
        <div class="ico"><LucideIcon name="trophy" :size="30" /></div>
        <h4>暂无精选校友</h4>
        <p>点击右上角「新增精选校友」创建第一位展厅成员（默认草稿，发布后可见）。</p>
      </div>
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? `编辑精选校友 #${editingId}` : '新增精选校友'"
      width="640px"
      destroy-on-close
    >
      <el-form label-width="92px" label-position="left">
        <div class="form-grid">
          <el-form-item label="选择校友" required>
            <el-select
              v-model="form.user_id"
              filterable
              remote
              clearable
              :remote-method="searchUsers"
              :loading="userSearching"
              placeholder="输入姓名 / 学号搜索"
              style="width: 100%"
              @change="onUserPicked"
            >
              <el-option
                v-for="u in userOptions"
                :key="u.id"
                :label="`${u.real_name || u.nickname || '—'}（${u.student_id}）`"
                :value="u.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="学号" required>
            <el-input v-model="form.student_id" placeholder="选中校友后自动带出" />
          </el-form-item>
          <el-form-item label="展示姓名" required>
            <el-input v-model="form.display_name" maxlength="50" />
          </el-form-item>
          <el-form-item label="照片">
            <div class="photo-uploader">
              <div v-if="form.photo_url" class="photo-preview">
                <img :src="form.photo_url" alt="照片预览" />
                <div class="photo-mask">
                  <button type="button" class="photo-btn" @click="pickPhoto">更换</button>
                  <button type="button" class="photo-btn danger" @click="form.photo_url = ''">移除</button>
                </div>
              </div>
              <button v-else type="button" class="photo-empty" :disabled="photoBusy" @click="pickPhoto">
                <span class="ic"><LucideIcon name="camera" :size="24" /></span>
                <span>{{ photoBusy ? '处理中…' : '点击上传照片' }}</span>
                <span class="tip">自动压缩至 100KB 内</span>
              </button>
              <input ref="photoInput" type="file" accept="image/*" hidden @change="onPhotoPicked" />
              <el-input
                v-model="form.photo_url"
                size="small"
                placeholder="或直接填写图片地址（可选）"
                class="photo-url-input"
              />
            </div>
          </el-form-item>
          <el-form-item label="学院">
            <el-select v-model="form.college_id" clearable filterable placeholder="选择学院" style="width: 100%">
              <el-option v-for="c in colleges" :key="c.id" :label="c.label" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="毕业年份">
            <el-input-number v-model="form.grad_year" :min="2000" :max="2100" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="当前职位">
            <el-input v-model="form.current_title" maxlength="100" placeholder="如 前端开发工程师" />
          </el-form-item>
          <el-form-item label="当前城市">
            <el-input v-model="form.current_city" maxlength="50" placeholder="如 福州" />
          </el-form-item>
          <el-form-item label="展示顺序">
            <el-input-number v-model="form.display_order" :min="0" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="首页推荐">
            <el-switch v-model="form.featured" />
          </el-form-item>
        </div>
        <el-form-item label="一句话寄语">
          <el-input v-model="form.message" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="时间线">
          <div class="rows-editor">
            <div v-for="(row, i) in form.timeline" :key="i" class="edit-row">
              <el-input v-model="row.year" class="cell-year" maxlength="10" placeholder="年份 如 2016" />
              <el-input v-model="row.event" class="cell-grow" maxlength="100" placeholder="经历，如 入学游戏艺术设计专业" />
              <button type="button" class="row-del" title="删除此行" @click="form.timeline.splice(i, 1)">
                <LucideIcon name="x" :size="14" />
              </button>
            </div>
            <button type="button" class="row-add" :disabled="form.timeline.length >= 20" @click="addTimelineRow">
              + 添加一段经历
            </button>
          </div>
        </el-form-item>
        <el-form-item label="代表荣誉">
          <div class="rows-editor">
            <div v-for="(row, i) in form.awards" :key="i" class="edit-row">
              <el-input v-model="row.title" class="cell-grow" maxlength="60" placeholder="荣誉名称" />
              <el-input v-model="row.year" class="cell-year" maxlength="10" placeholder="年份" />
              <el-select v-model="row.level" class="cell-level" filterable allow-create placeholder="级别">
                <el-option v-for="lv in AWARD_LEVELS" :key="lv" :label="lv" :value="lv" />
              </el-select>
              <el-input v-model="row.category" class="cell-category" maxlength="20" placeholder="分类(可选)" />
              <button type="button" class="row-del" title="删除此行" @click="form.awards.splice(i, 1)">
                <LucideIcon name="x" :size="14" />
              </button>
            </div>
            <button type="button" class="row-add" :disabled="form.awards.length >= 20" @click="addAwardRow">
              + 添加一项荣誉
            </button>
          </div>
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

/* 照片直传控件 */
.photo-uploader {
  width: 100%;
}
.photo-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 148px;
  height: 148px;
  border: 1.5px dashed #c0c8d6;
  border-radius: var(--radius-xl);
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
  width: 148px;
  height: 148px;
  border-radius: var(--radius-xl);
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
  border-radius: 0;
  background: #fff;
  color: #1e293b;
  font-size: 12px;
  cursor: pointer;
}
.photo-btn.danger {
  color: #dc2626;
}
.photo-url-input {
  margin-top: 8px;
}

/* 时间线 / 荣誉 按行编辑器 */
.rows-editor {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.edit-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.cell-year {
  width: 110px;
  flex-shrink: 0;
}
.cell-grow {
  flex: 1;
  min-width: 0;
}
.cell-level {
  width: 110px;
  flex-shrink: 0;
}
.cell-category {
  width: 110px;
  flex-shrink: 0;
}
.row-del {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.row-del:hover {
  background: #fef2f2;
  color: #dc2626;
}
.row-add {
  align-self: flex-start;
  padding: 6px 12px;
  border: 1.5px dashed #c0c8d6;
  border-radius: 8px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}
.row-add:hover:not(:disabled) {
  border-color: var(--brand-500, #2f6bff);
  color: var(--brand-500, #2f6bff);
  background: #f0f5ff;
}
.row-add:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
