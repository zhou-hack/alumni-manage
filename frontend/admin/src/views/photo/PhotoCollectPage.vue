<script setup>
/**
 * 校友照片收集页（/admin/photos · 导员 / 校级 / 系统管理员）
 *
 * - 列表：缩略图 + 标题 + 上传人 + 标签 + 状态 + 操作
 * - 审核：approve / reject（驳回需填原因）
 * - 推送：仅校级 / 系统管理员；支持 all_users / college / role / single_user
 * - 推送日志：操作人 + 目标 + 收件人数 + 备注
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, Promotion } from '@element-plus/icons-vue'
import { listAdminPhotos, reviewPhoto, pushPhoto, listPhotoPushLogs } from '@/api/photo'
import { useUserStore } from '@/stores/user'
import { formatTime } from '@/utils/format'

const userStore = useUserStore()
const isPushRole = computed(() =>
  ['school_admin', 'system_admin'].includes(userStore.user?.role)
)

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filter = reactive({ status: '' })

const STATUS = {
  pending: { text: '待审核', type: 'warning' },
  approved: { text: '已通过', type: 'success' },
  rejected: { text: '已驳回', type: 'danger' },
  pushed: { text: '已推送', type: 'info' },
}

async function loadList() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value }
    if (filter.status) params.status = filter.status
    const res = await listAdminPhotos(params)
    rows.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function onReview(row, action) {
  let reason = ''
  if (action === 'reject') {
    try {
      const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回照片', {
        inputValidator: (v) => (v && v.trim() ? true : '原因不能为空'),
      })
      reason = value.trim()
    } catch {
      return
    }
  }
  try {
    await reviewPhoto(row.id, { action, reason: reason || null })
    ElMessage.success(action === 'approve' ? '已通过' : '已驳回')
    await loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/* ==================== 推送 ==================== */
const pushOpen = ref(false)
const pushTarget = ref(null)
const pushForm = reactive({
  target_type: 'all_users',
  target_id: '',
  note: '',
})

function openPush(row) {
  pushTarget.value = row
  pushForm.target_type = 'all_users'
  pushForm.target_id = ''
  pushForm.note = ''
  pushOpen.value = true
}
async function submitPush() {
  if (!pushTarget.value) return
  try {
    const res = await pushPhoto(pushTarget.value.id, {
      target_type: pushForm.target_type,
      target_id: pushForm.target_id || null,
      note: pushForm.note || null,
    })
    ElMessage.success(`已推送 ${res.data?.recipients ?? 0} 人`)
    pushOpen.value = false
    await loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/* ==================== 推送日志 ==================== */
const logsOpen = ref(false)
const logsLoading = ref(false)
const logsRows = ref([])

async function openLogs() {
  logsOpen.value = true
  logsLoading.value = true
  try {
    const res = await listPhotoPushLogs({ page: 1, page_size: 50 })
    logsRows.value = res.data.list || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    logsLoading.value = false
  }
}

onMounted(loadList)
</script>

<template>
  <div class="photo-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">校友照片收集</h2>
        <p class="page-sub">导员审核用户提交的照片，通过后可按标签 / 学院 / 角色 / 单人定向推送</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Promotion" @click="openLogs">推送日志</el-button>
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 140px" @change="loadList">
        <el-option label="全部" value="" />
        <el-option label="待审核" value="pending" />
        <el-option label="已通过" value="approved" />
        <el-option label="已驳回" value="rejected" />
        <el-option label="已推送" value="pushed" />
      </el-select>
    </div>

    <el-table :data="rows" v-loading="loading" stripe class="photo-table">
      <el-table-column label="缩略图" width="100">
        <template #default="{ row }">
          <div class="thumb">
            <img v-if="row.url" :src="row.url" :alt="row.title" />
            <span v-else class="placeholder">无图</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="标题" min-width="160">
        <template #default="{ row }">
          <p class="t-title">{{ row.title || '未命名' }}</p>
          <p v-if="row.caption" class="t-caption">{{ row.caption }}</p>
        </template>
      </el-table-column>
      <el-table-column label="上传人" width="100">
        <template #default="{ row }">
          <span class="mono">#{{ row.user_id }}</span>
        </template>
      </el-table-column>
      <el-table-column label="标签" min-width="180">
        <template #default="{ row }">
          <el-tag
            v-for="t in (row.tags || [])"
            :key="t"
            size="small"
            effect="plain"
            class="tag-chip"
          >
            {{ t }}
          </el-tag>
          <span v-if="!(row.tags || []).length" class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="STATUS[row.status]?.type" size="small">
            {{ STATUS[row.status]?.text || row.status }}
          </el-tag>
          <p v-if="row.status === 'rejected' && row.reject_reason" class="reject-reason">
            {{ row.reject_reason }}
          </p>
        </template>
      </el-table-column>
      <el-table-column label="推送" width="120">
        <template #default="{ row }">
          <span v-if="row.pushed_count != null && row.pushed_count > 0" class="mono">
            {{ row.pushed_count }} 人
          </span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="150">
        <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'pending'">
            <el-button link type="primary" @click="onReview(row, 'approve')">通过</el-button>
            <el-button link type="danger" @click="onReview(row, 'reject')">驳回</el-button>
          </template>
          <template v-else-if="isPushRole && row.status === 'approved'">
            <el-button link type="primary" @click="openPush(row)">推送</el-button>
          </template>
          <template v-else-if="isPushRole && row.status === 'pushed'">
            <el-button link type="primary" @click="openPush(row)">再推一次</el-button>
          </template>
          <template v-else>
            <span class="muted">—</span>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        :total="total"
        :page-size="pageSize"
        layout="total, prev, pager, next, sizes"
        :page-sizes="[10, 20, 50]"
        @current-change="loadList"
        @size-change="(v) => { pageSize = v; loadList() }"
      />
    </div>

    <!-- 推送弹窗 -->
    <el-dialog v-model="pushOpen" title="推送照片" width="480px">
      <template v-if="pushTarget">
        <p class="push-target">目标照片：<b>{{ pushTarget.title }}</b></p>
        <el-form label-position="top">
          <el-form-item label="推送目标">
            <el-select v-model="pushForm.target_type" style="width: 100%">
              <el-option label="全部 active 用户" value="all_users" />
              <el-option label="按学院" value="college" />
              <el-option label="按角色" value="role" />
              <el-option label="单人" value="single_user" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="pushForm.target_type !== 'all_users'" label="目标 ID / 值">
            <el-input
              v-model="pushForm.target_id"
              :placeholder="
                pushForm.target_type === 'college'
                  ? '学院名称（如：计算机学院）'
                  : pushForm.target_type === 'role'
                    ? '角色（如：alumni / student）'
                    : '用户 ID'
              "
            />
          </el-form-item>
          <el-form-item label="备注（可选）">
            <el-input v-model="pushForm.note" type="textarea" :rows="2" maxlength="120" show-word-limit />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="pushOpen = false">取消</el-button>
        <el-button type="primary" @click="submitPush">推送</el-button>
      </template>
    </el-dialog>

    <!-- 推送日志抽屉 -->
    <el-drawer v-model="logsOpen" direction="rtl" size="640px" title="照片推送日志">
      <el-table :data="logsRows" v-loading="logsLoading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="照片" width="80">
          <template #default="{ row }">
            <span class="mono">#{{ row.photo_id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="目标" width="200">
          <template #default="{ row }">
            <span class="mono">{{ row.target_type }}<span v-if="row.target_id">#{{ row.target_id }}</span></span>
          </template>
        </el-table-column>
        <el-table-column label="收件人" width="80" prop="recipients" />
        <el-table-column label="操作人" width="80">
          <template #default="{ row }">
            <span class="mono">#{{ row.operator_id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="120">
          <template #default="{ row }">
            <span>{{ row.note || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="140">
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<style scoped>
.photo-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}
.page-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-sub);
}
.head-actions {
  display: flex;
  gap: 8px;
}
.filter-bar {
  display: flex;
  gap: 12px;
}
.photo-table {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  overflow: hidden;
}
.thumb {
  width: 64px;
  height: 64px;
  border-radius: 6px;
  overflow: hidden;
  background: #f3f4f6;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #94a3b8;
  font-size: 12px;
}
.t-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}
.t-caption {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-sub);
}
.tag-chip {
  margin-right: 4px;
  margin-bottom: 2px;
}
.reject-reason {
  margin: 4px 0 0;
  font-size: 11px;
  color: #ef4444;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
.muted {
  color: var(--text-sub);
  font-size: 12px;
}
.pager {
  display: flex;
  justify-content: flex-end;
}
.push-target {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--text-sub);
}
</style>