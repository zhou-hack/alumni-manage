<script setup>
/**
 * 我的资料页（PC 端校友视角，PRD M02-05）
 * 逻辑对齐 H5 端 RecordsPage：
 * - 我的提交历史与审核状态（驳回显示原因）
 * - 草稿：编辑 / 直接提交；驳回：修改重提 / 直接重提（version+1）
 * - 删除申请（非立即删除，deleting 待审核）
 * - 授权大屏展示 / 撤回授权（撤回自动下架）
 * - 附件预览分流（复用 AttachmentPreviewer：图片原样 / PDF.js / Word/Excel 下载）
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyArchives, resubmitArchive, deleteRequestArchive, updateArchive, ARCHIVE_CATEGORIES } from '@/api/archive'
import { getMyConsents, createConsent, revokeConsent } from '@/api/consents'
import { statusOf, formatTime, formatSize } from '@/utils/format'
import AttachmentPreviewer from '@/components/AttachmentPreviewer.vue'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
/** 已授权大屏：archive_id → consent_id */
const consentMap = ref(new Map())

const categoryText = (v) => ARCHIVE_CATEGORIES.find((c) => c.value === v)?.label || v

// ---------------- 详情抽屉 ----------------
const detailVisible = ref(false)
const detail = ref(null)

async function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

async function loadConsents() {
  try {
    const res = await getMyConsents()
    consentMap.value = new Map((res.data.list || []).filter((c) => c.active).map((c) => [c.archive_id, c.id]))
  } catch {
    // 授权查询失败不阻塞列表
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await getMyArchives({ page: page.value, page_size: pageSize })
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function onPageChange(p) {
  page.value = p
  loadList()
}

// ---------------- 行操作 ----------------
/** 编辑入口（仅 draft / rejected） */
function goEdit(row) {
  router.push({ path: '/archive-form', query: { id: row.id } })
}

/** 草稿直接提交（PATCH submit=true） */
async function handleSubmitDraft(row) {
  try {
    await ElMessageBox.confirm('提交后进入两级审核流程，确定提交该草稿吗？', '提交审核', {
      type: 'warning',
      confirmButtonText: '提交',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    const res = await updateArchive(row.id, { submit: true })
    if (res.data.status === 'pending_first') {
      ElMessage.success('已提交，等待院系初审')
      loadList()
    } else {
      // 后端不支持 PATCH submit（契约缺口）
      ElMessage.warning('保存成功，请进入编辑页提交审核')
      loadList()
    }
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/** 驳回不改内容直接重提 */
async function handleResubmit(row) {
  try {
    await ElMessageBox.confirm('不修改内容直接重新提交审核吗？将生成新版本。', '重新提交', {
      type: 'warning',
      confirmButtonText: '重提',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    const res = await resubmitArchive(row.id)
    ElMessage.success(`已重新提交（第 ${res.data.version} 版）`)
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/** 授权大屏展示（approved 后可授权，管理员上架后大屏可见） */
async function handleConsent(row) {
  try {
    const res = await createConsent(row.id, 'screen')
    consentMap.value.set(row.id, res.data.id)
    consentMap.value = new Map(consentMap.value)
    ElMessage.success('已授权大屏展示，待管理员上架')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/** 撤回授权：档案自动下架 */
async function handleRevoke(row) {
  try {
    await ElMessageBox.confirm(
      '撤回后，该档案将立即从大屏下架；如需展示可重新授权。确定撤回吗？',
      '撤回授权',
      { type: 'warning', confirmButtonText: '撤回', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  const consentId = consentMap.value.get(row.id)
  if (!consentId) return
  try {
    await revokeConsent(consentId)
    consentMap.value.delete(row.id)
    consentMap.value = new Map(consentMap.value)
    ElMessage.success('已撤回授权，档案已下架')
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/** 删除申请（审核通过前不删除） */
async function handleDeleteRequest(row) {
  try {
    await ElMessageBox.confirm(
      '删除申请提交后需管理员审核，审核通过前档案暂不会被删除。确定申请删除该档案吗？',
      '申请删除',
      { type: 'warning', confirmButtonText: '申请删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteRequestArchive(row.id)
    ElMessage.success('删除申请已提交，等待审核')
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(() => {
  loadList()
  loadConsents()
})
</script>

<template>
  <div class="my-archives-page">
    <div class="page-head">
      <div class="eyebrow">我的资料</div>
      <div class="head-row">
        <h1 class="page-title">我的资料</h1>
        <el-button type="primary" @click="router.push('/archive-form')">提交新资料</el-button>
      </div>
    </div>

    <div class="list-card" v-loading="loading">
      <template v-if="list.length">
        <div v-for="row in list" :key="row.id" class="list-row">
          <div class="row-main" @click="openDetail(row)">
            <div class="row-title">
              <span class="title-text">{{ row.title }}</span>
              <el-tag :type="statusOf(row.status).type" size="small" effect="light">
                {{ statusOf(row.status).text }}
              </el-tag>
            </div>
            <div class="row-meta">
              <span>{{ categoryText(row.category) }}</span>
              <span>第 {{ row.version }} 版</span>
              <span>{{ formatTime(row.created_at) }}</span>
              <span>附件 {{ row.attachment_ids?.length || 0 }} 份</span>
              <span v-if="row.published" class="on-screen">已上大屏</span>
            </div>
            <div v-if="row.status === 'rejected' && row.reject_reason" class="row-reject">
              驳回原因：{{ row.reject_reason }}
            </div>
          </div>
          <div class="row-actions">
            <template v-if="row.status === 'draft'">
              <el-button link type="primary" size="small" @click="goEdit(row)">编辑</el-button>
              <el-button link type="primary" size="small" @click="handleSubmitDraft(row)">提交审核</el-button>
            </template>
            <template v-else-if="row.status === 'rejected'">
              <el-button link type="primary" size="small" @click="goEdit(row)">修改内容后重提</el-button>
              <el-button link type="primary" size="small" @click="handleResubmit(row)">直接重提</el-button>
            </template>
            <template v-else-if="row.status === 'approved'">
              <el-button v-if="!consentMap.get(row.id)" link type="primary" size="small" @click="handleConsent(row)">
                授权大屏
              </el-button>
              <el-button v-else link type="warning" size="small" @click="handleRevoke(row)">撤回授权</el-button>
            </template>
            <el-button
              v-if="['approved', 'rejected'].includes(row.status)"
              link
              type="danger"
              size="small"
              @click="handleDeleteRequest(row)"
            >
              申请删除
            </el-button>
          </div>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="还没有提交过资料">
        <el-button type="primary" @click="router.push('/archive-form')">立即提交</el-button>
      </el-empty>

      <div v-if="total > pageSize" class="pager">
        <el-pagination
          layout="total, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="资料详情" size="520px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="类别">{{ categoryText(detail.category) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusOf(detail.status).type" size="small">{{ statusOf(detail.status).text }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="版本">第 {{ detail.version }} 版</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatTime(detail.created_at) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.reject_reason" label="驳回原因" :span="2">
            {{ detail.reject_reason }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.remark" label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">详细内容</h4>
        <p class="detail-content">{{ detail.content || '-' }}</p>

        <h4 class="section-title">附件材料</h4>
        <AttachmentPreviewer :attachments="detail.attachments || []" />
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-head {
  margin-bottom: 20px;
}

.eyebrow {
  font-size: 11px;
  letter-spacing: 0.22em;
  color: var(--text-mute);
  margin-bottom: 6px;
}

.head-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 26px;
  font-weight: 700;
  color: var(--text-main);
}

.list-card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.list-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--line);
  transition: background 0.15s;
}

.list-row:last-child {
  border-bottom: none;
}

.list-row:hover {
  background: var(--surface-2);
}

.row-main {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.row-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-text {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-meta {
  display: flex;
  gap: 14px;
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--text-sub);
}

.on-screen {
  color: var(--green-600, #16a34a);
  font-weight: 600;
}

.row-reject {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: var(--red-50, #fef2f2);
  color: var(--red-700, #b91c1c);
  font-size: 12.5px;
}

.row-actions {
  flex: none;
  display: flex;
  align-items: center;
  gap: 2px;
  padding-top: 4px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 20px;
}

.section-title {
  margin: 18px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
}

.detail-content {
  margin: 0;
  line-height: 1.8;
  color: var(--text-main);
  font-size: 13.5px;
}
</style>
