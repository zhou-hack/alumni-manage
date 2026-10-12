<script setup>
/**
 * 审核详情弹窗（M04：四类 biz_type 分流展示，居中弹窗）
 * - archive：档案审核（标题/类别/内容/附件）
 * - honor：荣誉审核（名称/类别/级别/授予单位/日期/证书原件）
 * - archive_delete：删除审核（展示待删除档案信息）
 * - activate_manual：激活人工核验（单级审核，展示核验申请信息）
 * 通过 / 驳回操作内置；操作后触发 actioned 通知列表刷新
 */
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getReview, approveReview, rejectReview, getReviewLogs } from '@/api/review'
import { BIZ_TYPE_TEXT, STAGE_TEXT, reviewStatusOf, statusOf, formatTime, CATEGORY_TEXT } from '@/utils/format'
import { HONOR_LEVELS } from '@/api/honor'
import AttachmentPreviewer from '@/components/AttachmentPreviewer.vue'
import ReviewFlowChart from '@/components/ReviewFlowChart.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  reviewId: { type: Number, default: null },
})
const emit = defineEmits(['update:modelValue', 'actioned'])

const loading = ref(false)
const detail = ref(null)
const logList = ref([])
const acting = ref(false)

async function load() {
  if (!props.reviewId) return
  loading.value = true
  detail.value = null
  logList.value = []
  try {
    const res = await getReview(props.reviewId)
    detail.value = res.data
    const logRes = await getReviewLogs(props.reviewId)
    logList.value = logRes.data.list || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) load()
  },
)

function close() {
  emit('update:modelValue', false)
}

async function handleApprove() {
  try {
    await ElMessageBox.confirm('确认通过该审核吗？', '审核通过', {
      type: 'warning',
      confirmButtonText: '通过',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  acting.value = true
  try {
    await approveReview(props.reviewId)
    ElMessage.success(detail.value?.stage === 'first' ? '初审通过，已转入校级复核' : '复核通过，已入库')
    emit('actioned')
    close()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    acting.value = false
  }
}

async function handleReject() {
  let reason = ''
  try {
    const res = await ElMessageBox.prompt('请填写驳回原因（必填，将展示给提交人）', '审核驳回', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '如：证书照片不清晰，请重新上传',
      inputValidator: (v) => (v && v.trim() ? true : '驳回原因不能为空'),
    })
    reason = res.value
  } catch {
    return
  }
  acting.value = true
  try {
    await rejectReview(props.reviewId, reason)
    ElMessage.success('已驳回')
    emit('actioned')
    close()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    acting.value = false
  }
}

const categoryText = (v) => CATEGORY_TEXT[v] || v
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="审核详情"
    width="min(760px, 94vw)"
    top="7vh"
    class="review-dialog"
    :close-on-click-modal="false"
    @close="close"
  >
    <div v-loading="loading" class="dr-body">
      <template v-if="detail">
        <!-- 审核基本信息 -->
        <div class="block">
          <h4>审核信息</h4>
          <div class="block-top">
            <span class="badge" :class="`badge--${reviewStatusOf(detail.status).tone}`">
              <span class="dot"></span>{{ reviewStatusOf(detail.status).text }}
            </span>
            <span class="stage-tag">{{ STAGE_TEXT[detail.stage] || detail.stage }}</span>
          </div>
          <dl class="kv">
            <dt>审核ID</dt><dd>#{{ detail.id }}</dd>
            <dt>业务类型</dt><dd>{{ BIZ_TYPE_TEXT[detail.biz_type] || detail.biz_type }}</dd>
            <dt>提交时间</dt><dd>{{ formatTime(detail.created_at) }}</dd>
          </dl>
        </div>

        <!-- 审核流程可视化（状态机） -->
        <div class="block">
          <h4>审核流程</h4>
          <ReviewFlowChart :biz-type="detail.biz_type" :stage="detail.stage" :status="detail.status" />
        </div>

        <!-- ① 档案审核 / 删除审核：展示档案详情 -->
        <template v-if="(detail.biz_type === 'archive' || detail.biz_type === 'archive_delete') && detail.archive">
          <el-alert
            v-if="detail.biz_type === 'archive_delete'"
            title="校友已提交删除申请，审核通过后该档案将从库中删除"
            type="warning"
            :closable="false"
            class="dr-alert"
          />
          <div class="block">
            <h4>{{ detail.biz_type === 'archive_delete' ? '待删除档案' : '档案内容' }}</h4>
            <dl class="kv">
              <dt>标题</dt><dd>{{ detail.archive.title }}</dd>
              <dt>类别</dt><dd>{{ categoryText(detail.archive.category) }}</dd>
              <dt>提交人</dt><dd>{{ detail.archive.owner_name }}（{{ detail.archive.student_id }}）</dd>
              <dt>届别</dt><dd>{{ detail.archive.grad_year }} 届</dd>
              <dt>档案状态</dt>
              <dd>
                <el-tag :type="statusOf(detail.archive.status).type" size="small">
                  {{ statusOf(detail.archive.status).text }}
                </el-tag>
                <span class="text-sub ml-4">第 {{ detail.archive.version }} 版</span>
              </dd>
              <dt>附件</dt>
              <dd>{{ detail.archive.attachment_ids?.length ? `${detail.archive.attachment_ids.length} 份` : '无附件' }}</dd>
            </dl>
          </div>
          <div class="block">
            <h4>附件材料</h4>
            <AttachmentPreviewer :attachments="detail.archive.attachments || []" />
          </div>
          <div class="block">
            <h4>详细内容</h4>
            <p class="detail-content">{{ detail.archive.content || '-' }}</p>
            <template v-if="detail.archive.remark">
              <div class="block-sep"></div>
              <h4>备注</h4>
              <p class="detail-content">{{ detail.archive.remark }}</p>
            </template>
          </div>
        </template>

        <!-- ② 荣誉审核：展示荣誉详情 -->
        <template v-else-if="detail.biz_type === 'honor' && detail.honor">
          <div class="block">
            <h4>荣誉内容</h4>
            <dl class="kv">
              <dt>荣誉名称</dt><dd>{{ detail.honor.title }}</dd>
              <dt>级别</dt><dd>{{ HONOR_LEVELS[detail.honor.level] || detail.honor.level }}</dd>
              <dt>授予单位</dt><dd>{{ detail.honor.granted_by || '-' }}</dd>
              <dt>授予日期</dt><dd>{{ detail.honor.awarded_at }}</dd>
              <dt>获得者</dt><dd>{{ detail.honor.owner_name }}（{{ detail.honor.student_id }}）</dd>
              <dt>证书原件</dt>
              <dd>{{ detail.honor.attachment_ids?.length ? `${detail.honor.attachment_ids.length} 份附件` : '无原件（文字记录）' }}</dd>
              <dt>关联档案</dt>
              <dd>{{ detail.honor.archive_id ? `档案 #${detail.honor.archive_id}` : '未关联' }}</dd>
            </dl>
          </div>
          <div class="block">
            <h4>证书原件</h4>
            <AttachmentPreviewer :attachments="detail.honor.attachments || []" />
          </div>
        </template>

        <!-- ③ 激活人工核验：展示核验申请（单级审核） -->
        <template v-else-if="detail.biz_type === 'activate_manual' && detail.manual_request">
          <el-alert
            title="学籍申报为单级审核（校级/系统管理员）：通过即开通激活资格，驳回需填写原因"
            type="info"
            :closable="false"
            class="dr-alert"
          />
          <div class="block">
            <h4>学籍申报</h4>
            <dl class="kv">
              <dt>申报编号</dt><dd>#{{ detail.manual_request.request_id_no ?? detail.manual_request.id ?? detail.id }}</dd>
              <dt>姓名</dt><dd>{{ detail.manual_request.real_name }}</dd>
              <dt>学号</dt><dd>{{ detail.manual_request.student_id }}</dd>
              <dt>学院</dt><dd>{{ detail.manual_request.college || '-' }}</dd>
              <dt>专业</dt><dd>{{ detail.manual_request.major || '-' }}</dd>
              <dt>毕业年份</dt><dd>{{ detail.manual_request.grad_year || '-' }}</dd>
              <dt>联系电话</dt><dd>{{ detail.manual_request.contact_phone || '-' }}</dd>
              <dt>申报说明</dt><dd>{{ detail.manual_request.note || detail.manual_request.remark || '-' }}</dd>
              <dt v-if="detail.manual_request.advisor_note">AI 审核建议</dt>
              <dd v-if="detail.manual_request.advisor_note">
                <el-tag size="small" type="info">参考</el-tag>
                <span class="ml-2">{{ detail.manual_request.advisor_note }}</span>
              </dd>
            </dl>
          </div>
          <div v-if="detail.manual_request.attachments?.length" class="block">
            <h4>证明材料（{{ detail.manual_request.attachments.length }} 份）</h4>
            <AttachmentPreviewer :attachments="detail.manual_request.attachments" />
          </div>
        </template>

        <!-- 审核日志 -->
        <div class="block">
          <h4>审核日志</h4>
          <el-empty v-if="!logList.length" description="暂无审核记录" :image-size="60" />
          <div v-else class="tl">
            <div
              v-for="(log, i) in logList"
              :key="i"
              class="tl-item"
              :class="{ done: log.action.includes('通过'), reject: log.action.includes('驳回') }"
            >
              <div class="tl-t">{{ log.reviewer }} · {{ log.action }}</div>
              <div class="tl-m">{{ formatTime(log.at) }}</div>
              <div v-if="log.reason" class="tl-m">原因：{{ log.reason }}</div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 操作区（吸底） -->
    <template v-if="detail?.status === 'pending'" #footer>
      <div class="dr-foot">
        <el-button type="danger" plain :loading="acting" @click="handleReject">驳回</el-button>
        <el-button type="primary" :loading="acting" @click="handleApprove">通过</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.dr-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.dr-alert {
  border-radius: var(--radius-lg);
}

/* ===== block 卡片（对齐设计稿 .block） ===== */
.block {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  padding: 18px 20px;
}

.block h4 {
  margin: 0 0 10px;
  font-size: 12.5px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: var(--text-sub);
}

.block-sep {
  height: 1px;
  background: var(--line);
  margin: 14px 0;
}

.block-top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.stage-tag {
  font-size: 12px;
  font-weight: 600;
  color: var(--brand-700);
  background: var(--brand-50);
  border-radius: var(--radius-full);
  padding: 4px 12px;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 24px;
  padding: 0 10px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 600;
}

.badge .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.badge--info { background: var(--blue-50); color: var(--blue-700); }
.badge--info .dot { background: var(--blue-500); }
.badge--success { background: var(--green-50); color: var(--green-700); }
.badge--success .dot { background: var(--green-500); }
.badge--warning { background: var(--amber-50); color: var(--amber-700); }
.badge--warning .dot { background: var(--amber-500); }
.badge--danger { background: var(--red-50); color: var(--red-700); }
.badge--danger .dot { background: var(--red-500); }

/* ===== kv 网格（对齐设计稿 .kv） ===== */
.kv {
  display: grid;
  grid-template-columns: 96px 1fr 96px 1fr;
  gap: 8px 16px;
  font-size: 13px;
  margin: 0;
}

.kv dt {
  color: var(--text-sub);
}

.kv dd {
  margin: 0;
  color: var(--text-main);
  font-weight: 500;
}

.detail-content {
  margin: 0;
  line-height: 1.8;
  color: var(--text-main);
  font-size: 13px;
}

/* ===== 时间轴（对齐设计稿 .tl） ===== */
.tl {
  padding-left: 20px;
}

.tl-item {
  position: relative;
  padding-bottom: 18px;
}

.tl-item:last-child {
  padding-bottom: 0;
}

.tl-item::before {
  content: '';
  position: absolute;
  left: -20px;
  top: 3px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--surface-1);
  border: 3px solid var(--brand-400);
}

.tl-item.done::before {
  background: var(--brand-600);
  border-color: var(--brand-600);
}

.tl-item.reject::before {
  background: var(--red-500);
  border-color: var(--red-500);
}

.tl-t {
  font-size: 13px;
  font-weight: 600;
}

.tl-m {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 2px;
}

/* ===== 吸底操作区（对齐设计稿 .drawer-foot） ===== */
.dr-foot {
  display: flex;
  gap: 12px;
}

.dr-foot .el-button {
  flex: 1;
  margin-left: 0;
}
</style>

<style>
/* 弹窗整体对齐设计稿（非 scoped：覆盖 el-dialog 容器，居中展示） */
.review-dialog {
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: var(--shadow-lg);
  padding: 0;
}

.review-dialog .el-dialog__header {
  margin: 0;
  padding: 18px 24px;
  border-bottom: 1px solid var(--line);
  background: var(--surface-1);
}

.review-dialog .el-dialog__title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 600;
  color: var(--text-main);
}

.review-dialog .el-dialog__body {
  background: var(--bg);
  padding: 20px 24px;
  max-height: 64vh;
  overflow-y: auto;
}

.review-dialog .el-dialog__footer {
  padding: 14px 24px;
  border-top: 1px solid var(--line);
  background: var(--surface-1);
  box-shadow: 0 -4px 16px oklch(25% 0.03 258 / 0.05);
}
</style>
