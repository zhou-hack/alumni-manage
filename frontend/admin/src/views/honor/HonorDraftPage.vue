<script setup>
/**
 * 荣誉证书解析草稿页（/honors/draft，[M03 扩展 2026-09-25]）
 * 阶段一：人工填写基本信息 + 成员名单 → 实时人名匹配预览（matched/duplicated/
 *         no_account/ambiguous/unmatched）→ 确认生成荣誉记录（团队生成团队公示行 +
 * 个人行）。阶段二：OCR 自动填充 parsed_json 后复用同一确认流。
 *
 * 业务规则（PRD M03）：
 *   1) 学生资料由导员审核后上交学院；校友资料由校级审核 → 本页提交人仅限 counselor /
 *      college_admin / school_admin / system_admin
 *   2) 荣誉证书识别人名后归档到每位参与人的个人资料库 → confirm 时每 matched/manual
 *      成员写一条个人荣誉
 *   3) 团队荣誉必须拆分个人记录 + 保留一条团队记录 → team_id + is_team_record=1 公示行
 *      挂在 submitter 名下，个人行 is_team_record=0
 *   4) 红头文件以链接形式关联到对应荣誉记录 → redDocUrl 字段
 *   5) 所有荣誉必须带年份、学院、级别三个结构化标签 → awardedAt / collegeName / level
 *      必填
 *   6) 人名识别后与学籍库/校友库匹配，匹配不到标记为待人工确认 → preview 接口返回 5
 *      状态 + confirm 时人工 overrides 强制指定
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Refresh,
  Search,
  Upload,
  View,
  Delete,
  Check,
  Edit,
} from '@element-plus/icons-vue'
import {
  createHonorDraft,
  getHonorDraft,
  updateHonorDraft,
  previewDraftMembers,
  confirmHonorDraft,
  discardHonorDraft,
  HONOR_LEVELS,
} from '@/api/honor'
import { getDict, dictLabel } from '@/api/dict'
import { uploadAttachments, ACCEPT, MAX_FILE_SIZE } from '@/api/attachment'
import { formatTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import RedDocLink from '@/components/RedDocLink.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/* ============================ 字典数据 ============================ */
const categories = ref([])
const colleges = ref([])
async function loadDicts() {
  try {
    ;[categories.value, colleges.value] = await Promise.all([
      getDict('honor_category'),
      getDict('college'),
    ])
  } catch {
    /* 字典加载失败不阻塞 */
  }
}
const LEVEL_OPTIONS = Object.entries(HONOR_LEVELS).map(([value, label]) => ({ label, value }))
const categoryText = (v) => categories.value.find((c) => c.value === v)?.label || v

/* ============================ 草稿创建表单 ============================ */
const formRef = ref(null)
const form = reactive({
  kind: 'solo',
  title: '',
  category: '',
  level: '',
  awardedAt: '',
  collegeName: '',
  redDocUrl: '',
  memberNames: '',
  attachmentId: null,
})

/** 创建进度步骤（驱动顶部 el-steps 高亮）：
 *   0：基本信息（默认）
 *   1：证书已上传 → 高亮第 2 步
 *   2：团队成员已填 → 高亮第 3 步
 *   3：可提交 → 高亮第 4 步
 */
const createStep = computed(() => {
  if (form.attachmentId) return form.kind === 'team' && !form.memberNames.trim() ? 1 : 2
  return 0
})

const formRules = {
  title: [{ required: true, message: '请输入荣誉名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择荣誉类别', trigger: 'change' }],
  level: [{ required: true, message: '请选择级别', trigger: 'change' }],
  awardedAt: [{ required: true, message: '请选择授予日期', trigger: 'change' }],
  collegeName: [{ required: true, message: '请填写学院', trigger: 'blur' }],
  memberNames: [
    {
      validator(_, val, cb) {
        if (form.kind === 'team' && !val?.trim()) return cb(new Error('团队荣誉必须填写成员名单'))
        cb()
      },
      trigger: 'blur',
    },
  ],
}

/* 附件上传 */
const uploading = ref(false)
const uploadedFile = ref(null) // { id, name, size }
async function handleUpload(file) {
  if (file.size > MAX_FILE_SIZE) {
    ElMessage.error('文件超过 10MB')
    return false
  }
  uploading.value = true
  try {
    const res = await uploadAttachments([file.file], 'honor', '荣誉证书原件')
    const arr = res.data?.success || res.success || []
    if (arr[0]) {
      form.attachmentId = arr[0].id
      uploadedFile.value = { id: arr[0].id, name: file.name, size: file.size }
      ElMessage.success('上传成功')
    } else {
      ElMessage.error('上传失败')
    }
  } catch (e) {
    ElMessage.error(e.message || '上传失败')
  } finally {
    uploading.value = false
  }
  return false // 阻止 el-upload 默认行为
}
function clearUploaded() {
  form.attachmentId = null
  uploadedFile.value = null
}

/* ============================ 创建草稿 ============================ */
const creating = ref(false)
async function handleCreate() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  creating.value = true
  try {
    const res = await createHonorDraft({
      attachment_id: form.attachmentId,
      kind: form.kind,
      title: form.title,
      category: form.category,
      level: form.level,
      awarded_at: form.awardedAt || null,
      college_name: form.collegeName,
      red_doc_url: form.redDocUrl || null,
      member_names: form.memberNames || null,
    })
    ElMessage.success('草稿已创建，进入确认页')
    router.replace({ path: '/honors/draft', query: { id: res.data.id } })
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    creating.value = false
  }
}

/* ============================ 草稿详情 + 匹配预览 ============================ */
const draftId = computed(() => Number(route.query.id) || 0)
const draft = ref(null)
const members = ref([]) // PreviewMember[]
const previewing = ref(false)
const draftLoading = ref(false)

async function loadDraft() {
  if (!draftId.value) return
  draftLoading.value = true
  try {
    const res = await getHonorDraft(draftId.value)
    draft.value = res.data
    if (res.data.kind === 'team') {
      members.value = res.data.members || []
    }
  } catch (e) {
    ElMessage.error(e.message)
    draft.value = null
  } finally {
    draftLoading.value = false
  }
}

/* 监听成员名单输入，触发实时预览（防抖 350ms） */
const memberEdit = ref('')
let debounceTimer = null
watch(memberEdit, (val) => {
  if (!draft.value || draft.value.kind !== 'team') return
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => runPreview(val), 350)
})
async function runPreview(names) {
  if (!draft.value || !names?.trim()) {
    members.value = []
    return
  }
  previewing.value = true
  try {
    const res = await previewDraftMembers(draft.value.id, names)
    members.value = res.data.members || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    previewing.value = false
  }
}

/* ============================ 人工修正字段 ============================ */
const editForm = reactive({})
const editVisible = ref(false)
function openEdit() {
  Object.assign(editForm, {
    title: draft.value.title || '',
    category: draft.value.category || '',
    level: draft.value.level || '',
    awardedAt: draft.value.awarded_at || '',
    collegeName: draft.value.college_name || '',
    redDocUrl: draft.value.red_doc_url || '',
    memberNames: draft.value.member_names || '',
  })
  editVisible.value = true
}
async function saveEdit() {
  try {
    await updateHonorDraft(draft.value.id, {
      title: editForm.title,
      category: editForm.category,
      level: editForm.level,
      awarded_at: editForm.awardedAt || null,
      college_name: editForm.collegeName,
      red_doc_url: editForm.redDocUrl || null,
      member_names: editForm.memberNames || null,
    })
    ElMessage.success('已保存')
    editVisible.value = false
    loadDraft()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/* ============================ 人工消歧：未匹配/歧义 → 手动指定 userId ============================ */
const overrides = ref({}) // name -> userId 输入值（暂存）
const overrideOpen = ref({}) // name -> 弹窗开关
const userSearchKeyword = ref('')
const userSearchResults = ref([])

async function searchUsersForOverride(name) {
  userSearchKeyword.value = name
  if (!name?.trim()) {
    userSearchResults.value = []
    return
  }
  try {
    // 复用 alumniAdmin 的搜索接口（按姓名/学号）
    const { searchUsers } = await import('@/api/users')
    const res = await searchUsers({ keyword: name.trim(), page: 1, page_size: 10 })
    userSearchResults.value = res.data?.list || res.data || []
  } catch {
    // 无搜索接口时给出手动输入框
    userSearchResults.value = []
  }
}

function pickOverride(name, row) {
  overrides.value[name] = row.id
  overrideOpen.value[name] = false
  // 本地标记为已指定
  const m = members.value.find((x) => x.name === name)
  if (m) m.status = 'manual'
}

function buildOverridesPayload() {
  // 仅对 manual/未匹配/无账号 默认进行 overrides；其余后端自动匹配
  const list = []
  for (const [name, userId] of Object.entries(overrides.value)) {
    if (userId) list.push({ name, user_id: Number(userId) })
  }
  return list
}

/* ============================ 状态标签 ============================ */
const STATUS_META = {
  matched: { text: '已匹配', type: 'success', icon: '✓' },
  duplicated: { text: '重复记录', type: 'warning', icon: '!' },
  no_account: { text: '账号未激活', type: 'warning', icon: '!' },
  ambiguous: { text: '同名多人', type: 'warning', icon: '?' },
  unmatched: { text: '校友库无此姓名', type: 'danger', icon: '✗' },
  manual: { text: '人工已指定', type: 'success', icon: '✓' },
}
function memberStatusOf(s) {
  return STATUS_META[s] || { text: s || '—', type: 'info', icon: '·' }
}

/* ============================ 确认 / 丢弃 ============================ */
const confirmVisible = ref(false)
const confirming = ref(false)
const confirmResult = ref(null)

async function handleConfirm() {
  if (!draft.value) return
  // 警告：unmatched / ambiguous 成员未消歧
  const unresolved = members.value.filter(
    (m) => (m.status === 'unmatched' || m.status === 'ambiguous') && !overrides.value[m.name],
  )
  if (unresolved.length) {
    try {
        await ElMessageBox.confirm(
          `有 ${unresolved.length} 名成员未匹配/存在歧义：\n${unresolved.map((m) => `· ${m.name}`).join('\n')}\n\n确认仍要提交吗？未匹配成员将作为待人工确认记录留证。`,
          '存在未消歧成员',
          { type: 'warning', confirmButtonText: '仍要提交', cancelButtonText: '取消' },
        )
      } catch {
        return
      }
  }
  confirmVisible.value = true
}

async function doConfirm() {
  confirming.value = true
  try {
    const res = await confirmHonorDraft(draft.value.id, {
      overrides: buildOverridesPayload(),
      split_members: true,
    })
    confirmResult.value = res.data
    ElMessage.success('已确认，草稿已归档为正式荣誉')
    await loadDraft()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    confirming.value = false
    confirmVisible.value = false
  }
}

async function handleDiscard() {
  if (!draft.value) return
  try {
    await ElMessageBox.confirm('确认丢弃此草稿？此操作不可恢复。', '丢弃草稿', {
      type: 'warning',
      confirmButtonText: '丢弃',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await discardHonorDraft(draft.value.id)
    ElMessage.success('已丢弃')
    router.replace('/honors/draft')
    draft.value = null
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/* ============================ 生命周期 ============================ */
onMounted(async () => {
  await loadDicts()
  if (draftId.value) {
    await loadDraft()
    if (draft.value?.kind === 'team') {
      memberEdit.value = draft.value.member_names || ''
    }
  }
})
</script>

<template>
  <div>
    <!-- ===== 未选择草稿：展示创建表单 ===== -->
    <el-card v-if="!draftId" shadow="never" class="create-card">
      <template #header>
        <div class="card-head">
          <div class="card-head-row">
            <span class="ttl">新建荣誉资料</span>
            <span class="sub">填写基本信息 → 上传证书 → 确认生成荣誉记录</span>
          </div>
        </div>
      </template>

      <!-- 顶部步骤条：把流程显式化、降低误操作 -->
      <el-steps :active="createStep" align-center finish-status="success" class="create-steps">
        <el-step title="填写基本信息" description="类型 / 名称 / 类别 / 级别 / 时间 / 学院" />
        <el-step title="上传证书" description="证书原件（10MB 以内，图片/PDF）" />
        <el-step title="团队成员匹配" description="仅团队荣誉需要；自动 + 人工消歧" />
        <el-step title="确认提交" description="系统会校验字段后生成荣誉记录" />
      </el-steps>

      <!-- 操作指引卡片 -->
      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="填写指引"
        description="1) 类型决定是否必填成员名单；2) 学院必填（用于按学院分配荣誉）；3) 级别与授予时间会展示在大屏上；4) 提交后可在「荣誉管理 / 我的荣誉」中查看进度。"
        class="create-hint"
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="100px"
        label-position="right"
        class="create-form"
      >
        <el-form-item label="荣誉类型" prop="kind">
          <el-radio-group v-model="form.kind">
            <el-radio value="solo">个人荣誉</el-radio>
            <el-radio value="team">团队荣誉</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="荣誉名称" prop="title">
          <el-input v-model="form.title" placeholder="例如：全国大学生数学建模竞赛一等奖" maxlength="128" show-word-limit />
        </el-form-item>

        <el-form-item label="荣誉类别" prop="category">
          <el-select v-model="form.category" placeholder="请选择类别" style="width: 240px">
            <el-option v-for="c in categories" :key="c.id" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="级别" prop="level">
          <el-select v-model="form.level" placeholder="请选择级别" style="width: 200px">
            <el-option v-for="l in LEVEL_OPTIONS" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="授予日期" prop="awardedAt">
          <el-date-picker
            v-model="form.awardedAt"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择授予日期"
            style="width: 200px"
          />
        </el-form-item>

        <el-form-item label="学院" prop="collegeName">
          <el-select
            v-model="form.collegeName"
            placeholder="请选择学院"
            filterable
            allow-create
            style="width: 280px"
          >
            <el-option v-for="c in colleges" :key="c.id" :label="c.label" :value="c.label" />
          </el-select>
        </el-form-item>

        <el-form-item label="证书原件">
          <el-upload
            :accept="ACCEPT"
            :show-file-list="false"
            :before-upload="handleUpload"
            class="cert-uploader"
          >
            <el-button :icon="Upload" :loading="uploading">点击上传</el-button>
          </el-upload>
          <div v-if="uploadedFile" class="uploaded-tip">
              <el-icon class="ok"><Check /></el-icon>
              已上传：{{ uploadedFile.name }}（{{ Math.round(uploadedFile.size / 1024) }} KB）
              <el-button link type="danger" :icon="Delete" @click="clearUploaded">移除</el-button>
            </div>
        </el-form-item>

        <el-form-item label="红头文件">
          <el-input v-model="form.redDocUrl" placeholder="如已签发红头文件，请粘贴访问链接" maxlength="512" />
        </el-form-item>

        <el-form-item v-if="form.kind === 'team'" label="成员名单" prop="memberNames">
          <el-input
            v-model="form.memberNames"
            type="textarea"
            :rows="3"
            placeholder="支持中英文逗号/顿号/分号分隔，例如：张三、李四、王五"
          />
          <div class="form-hint">按提交人规则：所有成员归档后，团队公示行挂在您（{{ userStore.user?.nickname || '当前用户' }}）名下。</div>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :icon="Plus"
            :loading="creating"
            size="large"
            @click="handleCreate"
          >创建草稿并预览匹配</el-button>
          <el-button :icon="Refresh" @click="Object.assign(form, { kind:'solo',title:'',category:'',level:'',awardedAt:'',collegeName:'',redDocUrl:'',memberNames:'',attachmentId:null }); uploadedFile = null">重置</el-button>
          <el-button text type="info" @click="router.push('/honors')">返回荣誉列表</el-button>
          <span class="form-hint">点击「创建草稿」后系统会校验必填项，校验失败会标红提示；成功后将跳转到匹配确认页。</span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ===== 草稿详情 / 确认页 ===== -->
    <el-card v-else shadow="never" v-loading="draftLoading">
      <template v-if="draft">
        <template v-if="draft.status !== 'pending'">
          <el-alert
            :title="draft.status === 'confirmed' ? '草稿已确认（已归档为正式荣誉）' : '草稿已丢弃'"
            :type="draft.status === 'confirmed' ? 'success' : 'info'"
            :closable="false"
            show-icon
          />
          <el-divider />
        </template>

        <div class="detail-head">
          <div>
            <h2 class="d-title">{{ draft.title || '(未命名草稿)' }}</h2>
            <div class="d-tags">
              <el-tag size="small">{{ draft.kind === 'team' ? '团队荣誉' : '个人荣誉' }}</el-tag>
              <el-tag size="small" type="info">{{ categoryText(draft.category) || draft.category || '未分类' }}</el-tag>
              <el-tag size="small" type="warning">{{ HONOR_LEVELS[draft.level] || draft.level || '未分级' }}</el-tag>
              <el-tag v-if="draft.awarded_at" size="small" type="success">{{ draft.awarded_at }}</el-tag>
              <el-tag v-if="draft.college_name" size="small" type="info">{{ draft.college_name }}</el-tag>
              <el-tag :type="memberStatusOf(draft.status).type" size="small">
                {{ memberStatusOf(draft.status).text }}
              </el-tag>
            </div>
          </div>
          <div class="d-actions">
            <el-button :icon="Edit" :disabled="draft.status !== 'pending'" @click="openEdit">修正字段</el-button>
            <el-button :icon="Refresh" @click="loadDraft">刷新</el-button>
            <el-button type="danger" :icon="Delete" :disabled="draft.status !== 'pending'" @click="handleDiscard">丢弃草稿</el-button>
          </div>
        </div>

        <el-descriptions :column="2" border class="hud-desc">
          <el-descriptions-item label="提交人">#{{ draft.operator_id }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(draft.created_at) }}</el-descriptions-item>
          <el-descriptions-item v-if="draft.attachment_id" label="证书附件">
            <a :href="`/api/v1/attachments/${draft.attachment_id}/preview`" target="_blank">查看原件</a>
          </el-descriptions-item>
          <el-descriptions-item v-if="draft.red_doc_url" label="红头文件">
            <RedDocLink :url="draft.red_doc_url" />
          </el-descriptions-item>
          <el-descriptions-item v-if="draft.parsed_json" label="OCR 解析" :span="2">
            <pre class="ocr-pre">{{ draft.parsed_json }}</pre>
          </el-descriptions-item>
        </el-descriptions>

        <!-- ===== 团队荣誉：成员匹配预览 ===== -->
        <template v-if="draft.kind === 'team'">
          <el-divider />
          <h3 class="section-title">成员匹配预览</h3>
          <el-input
            v-model="memberEdit"
            type="textarea"
            :rows="2"
            placeholder="成员名单（，/、/;/；/空白分隔）"
            class="member-edit"
            :disabled="draft.status !== 'pending'"
          />
          <div class="member-hint">
            <span v-if="previewing">正在匹配…</span>
            <span v-else>共 {{ members.length }} 人 · 已匹配 {{ members.filter(m => m.status === 'matched' || m.status === 'manual').length }} 人</span>
          </div>

          <el-table :data="members" v-loading="previewing" stripe class="member-table">
            <el-table-column prop="name" label="姓名" width="120" />
            <el-table-column label="学号" width="140">
              <template #default="{ row }">
                <span v-if="row.student_id">{{ row.student_id }}</span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="匹配状态" width="160">
              <template #default="{ row }">
                <el-tag :type="memberStatusOf(row.status).type" size="small">
                  <span class="ic">{{ memberStatusOf(row.status).icon }}</span>
                  {{ memberStatusOf(row.status).text }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="说明" min-width="200" show-overflow-tooltip />
            <el-table-column label="人工指定 userId" width="280">
              <template #default="{ row }">
                <div v-if="overrides[row.name]" class="override-cell">
                  <span class="ok">已指定 → #{{ overrides[row.name] }}</span>
                  <el-button link type="danger" size="small" @click="delete overrides[row.name]">清除</el-button>
                </div>
                <el-button
                  v-else-if="row.status === 'unmatched' || row.status === 'ambiguous' || row.status === 'no_account'"
                  link
                  type="primary"
                  size="small"
                  :icon="Search"
                  @click="overrideOpen[row.name] = true; searchUsersForOverride(row.name)"
                >
                  选择用户
                </el-button>
                <span v-else class="muted">无需处理</span>

                <el-dialog
                  v-model="overrideOpen[row.name]"
                  :title="`人工指定：${row.name}`"
                  width="540px"
                  append-to-body
                >
                  <el-input v-model="userSearchKeyword" placeholder="搜索姓名/学号" clearable />
                  <el-table :data="userSearchResults" max-height="300" style="margin-top: 12px">
                    <el-table-column prop="id" label="ID" width="60" />
                    <el-table-column prop="nickname" label="姓名" width="100" />
                    <el-table-column prop="student_id" label="学号" width="120" />
                    <el-table-column prop="college_name" label="学院" />
                    <el-table-column label="操作" width="80">
                      <template #default="{ row: u }">
                        <el-button link type="primary" size="small" @click="pickOverride(row.name, u)">选</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                  <div v-if="!userSearchResults.length" class="muted">
                    暂无搜索结果；也可直接输入 userId：
                    <el-input-number v-model="overrides[row.name]" :min="1" controls-position="right" style="width: 140px; margin-left: 8px" />
                    <el-button type="primary" size="small" @click="overrideOpen[row.name] = false" style="margin-left: 8px">确定</el-button>
                  </div>
                </el-dialog>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <!-- ===== 确认按钮 ===== -->
        <div v-if="draft.status === 'pending'" class="confirm-bar">
          <el-button type="primary" size="large" :icon="Check" @click="handleConfirm">
            确认 → 生成荣誉记录
          </el-button>
          <span class="confirm-hint">
            团队荣誉将生成 {{ members.length || 1 }} 条个人记录 + 1 条团队公示行（挂在您名下）
          </span>
        </div>

        <!-- ===== 修正字段弹窗 ===== -->
        <el-dialog v-model="editVisible" title="修正草稿字段" width="560px">
          <el-form :model="editForm" label-width="100px">
            <el-form-item label="荣誉名称">
              <el-input v-model="editForm.title" maxlength="128" />
            </el-form-item>
            <el-form-item label="类别">
              <el-select v-model="editForm.category" style="width: 100%">
                <el-option v-for="c in categories" :key="c.id" :label="c.label" :value="c.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="级别">
              <el-select v-model="editForm.level" style="width: 100%">
                <el-option v-for="l in LEVEL_OPTIONS" :key="l.value" :label="l.label" :value="l.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="授予日期">
              <el-date-picker v-model="editForm.awardedAt" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
            <el-form-item label="学院">
              <el-input v-model="editForm.collegeName" />
            </el-form-item>
            <el-form-item label="红头文件">
              <el-input v-model="editForm.redDocUrl" />
            </el-form-item>
            <el-form-item v-if="draft.kind === 'team'" label="成员名单">
              <el-input v-model="editForm.memberNames" type="textarea" :rows="3" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="editVisible = false">取消</el-button>
            <el-button type="primary" @click="saveEdit">保存</el-button>
          </template>
        </el-dialog>

        <!-- ===== 确认结果弹窗 ===== -->
        <el-dialog v-model="confirmVisible" title="确认结果" width="640px">
          <template v-if="confirmResult">
            <el-alert title="草稿已成功归档" type="success" :closable="false" show-icon />
            <p style="margin-top: 12px">
              团队批次：<b>{{ confirmResult.team_id || '(单条)' }}</b> · 生成荣誉 {{ confirmResult.honors?.length || 0 }} 条
            </p>
            <el-table :data="confirmResult.honors || []" max-height="320" style="margin-top: 8px">
              <el-table-column prop="id" label="荣誉 ID" width="100" />
              <el-table-column label="类型" width="120">
                <template #default="{ row }">
                  <el-tag v-if="row.is_team_record" type="warning" size="small">团队公示行 · {{ row.team_role || 'leader' }}</el-tag>
                  <el-tag v-else type="success" size="small">个人行 · {{ row.team_role || 'member' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="user_id" label="获得者 userId" />
            </el-table>
          </template>
          <template v-else-if="confirming">正在提交…</template>
          <template #footer>
            <el-button type="primary" @click="confirmVisible = false; router.push('/honors')">查看荣誉列表</el-button>
          </template>
        </el-dialog>
      </template>
      <el-empty v-else description="草稿不存在或已删除" />
    </el-card>
  </div>
</template>

<style scoped>
.create-card {
  border-radius: var(--radius-lg);
}
.create-steps {
  margin: 0 4px 18px;
}
.create-hint {
  margin-bottom: 20px;
  border-radius: var(--radius-md);
}
.card-head-row {
  display: flex;
  align-items: baseline;
  gap: 14px;
  flex-wrap: wrap;
}
.card-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.card-head .ttl {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}
.card-head .sub {
  font-size: 12px;
  color: var(--text-sub);
}
.create-form {
  max-width: 720px;
}
.form-hint {
  font-size: 11.5px;
  color: var(--text-sub);
  margin-top: 4px;
}
.uploaded-tip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--brand-700);
  margin-left: 8px;
}
.uploaded-tip .ok {
  color: var(--success-500);
}

.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 12px;
}
.d-title {
  margin: 0 0 6px;
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
}
.d-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.d-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.hud-desc {
  border-radius: var(--radius-md);
}
.section-title {
  margin: 4px 0 10px;
  font-size: 14px;
  font-weight: 700;
}
.member-edit {
  max-width: 720px;
}
.member-hint {
  margin: 6px 0 12px;
  font-size: 12px;
  color: var(--text-sub);
}
.member-table {
  border-radius: var(--radius-md);
}
.muted {
  color: var(--text-sub);
  font-size: 12px;
}
.ic {
  margin-right: 3px;
  font-weight: 700;
}
.override-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}
.override-cell .ok {
  color: var(--success-600);
  font-weight: 600;
}
.ocr-pre {
  margin: 0;
  font-size: 12px;
  color: var(--text-sub);
  white-space: pre-wrap;
  word-break: break-all;
}
.confirm-bar {
  margin-top: 24px;
  display: flex;
  align-items: center;
  gap: 14px;
}
.confirm-hint {
  font-size: 12px;
  color: var(--text-sub);
}
</style>