<script setup>
/**
 * 组织架构同步页（/admin/org-sync · 仅 school_admin / system_admin）
 *
 * 功能：
 *   - 顶部三张「同步卡片」：在读学生（active）/ 往届校友（old）/ 教师（teacher）
 *     点击「立即同步」按钮，按合并模式拉新数据；同步走 SPI（当前为校友库 Mock）
 *   - 下方同步日志表：总/新增/更新/跳过/失败计数 + 失败明细（点击展开）
 *
 * 备注：mock 数据由后端 AlumniBaseDataSource 提供；生产可切换为教务/HR 系统
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Connection, CircleCheck, Warning } from '@element-plus/icons-vue'
import { syncOrg, listOrgSyncLogs } from '@/api/orgsync'
import { formatTime } from '@/utils/format'

const TYPES = [
  {
    value: 'active',
    label: '在读学生',
    desc: '从校友基础表中筛选 grad_year > 今年，自动生成 student 角色账号',
    icon: 'school',
  },
  {
    value: 'old',
    label: '往届校友',
    desc: '从校友基础表中筛选 grad_year ≤ 今年，自动生成 alumni 角色账号',
    icon: 'user',
  },
  {
    value: 'teacher',
    label: '教师档案',
    desc: '从校友基础表中角色为 teacher 的记录（若无数据将整体跳过）',
    icon: 'avatar',
  },
]

const busy = ref({ active: false, old: false, teacher: false })
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const detail = reactive({ open: false, row: null })

async function loadLogs() {
  loading.value = true
  try {
    const res = await listOrgSyncLogs({ page: page.value, page_size: pageSize.value })
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function handleSync(type) {
  try {
    await ElMessageBox.confirm(
      `确认按【${TYPES.find((t) => t.value === type).label}】拉取最新数据？同步过程将按学号比对新增/更新/跳过。`,
      '组织架构同步',
      { type: 'info', confirmButtonText: '立即同步' }
    )
  } catch {
    return
  }
  busy.value[type] = true
  try {
    const res = await syncOrg(type, 'merge')
    const d = res.data || {}
    ElMessage.success(
      `同步完成：总 ${d.total ?? 0} / 新增 ${d.inserted ?? 0} / 更新 ${d.updated ?? 0} / 跳过 ${d.skipped ?? 0} / 失败 ${d.failed ?? 0}`
    )
    await loadLogs()
  } catch (e) {
    ElMessage.error(e.message || '同步失败')
  } finally {
    busy.value[type] = false
  }
}

function statusMeta(s) {
  if (s === 'success') return { text: '成功', type: 'success' }
  if (s === 'failed') return { text: '失败', type: 'danger' }
  if (s === 'partial') return { text: '部分成功', type: 'warning' }
  if (s === 'running') return { text: '运行中', type: 'info' }
  return { text: s || '-', type: '' }
}

function openDetail(row) {
  detail.row = row
  detail.open = true
}

onMounted(loadLogs)
</script>

<template>
  <div class="orgsync-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">组织架构同步</h2>
        <p class="page-sub">按组织架构接入学生 / 校友 / 教师数据；点击「立即同步」拉新数据；同步日志可追溯。</p>
      </div>
      <el-button :icon="Refresh" @click="loadLogs">刷新日志</el-button>
    </div>

    <!-- 同步卡片 -->
    <div class="sync-grid">
      <article v-for="t in TYPES" :key="t.value" class="sync-card">
        <div class="card-head">
          <el-icon class="card-icon"><Connection /></el-icon>
          <h3>{{ t.label }}</h3>
        </div>
        <p class="card-desc">{{ t.desc }}</p>
        <div class="card-actions">
          <el-button
            type="primary"
            :loading="busy[t.value]"
            @click="handleSync(t.value)"
          >
            立即同步
          </el-button>
        </div>
      </article>
    </div>

    <!-- 同步日志 -->
    <div class="logs-block">
      <h3 class="block-title">同步日志</h3>
      <el-table :data="list" v-loading="loading" stripe class="logs-table">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="来源" width="120">
          <template #default="{ row }">
            <span class="mono">{{ row.source || 'alumni_base' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.sync_type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模式" width="100">
          <template #default="{ row }">{{ row.mode }}</template>
        </el-table-column>
        <el-table-column label="总数" width="80" prop="total" />
        <el-table-column label="新增" width="80">
          <template #default="{ row }">
            <span class="cnt-inserted">+{{ row.inserted }}</span>
          </template>
        </el-table-column>
        <el-table-column label="更新" width="80">
          <template #default="{ row }">
            <span class="cnt-updated">{{ row.updated }}</span>
          </template>
        </el-table-column>
        <el-table-column label="跳过" width="80" prop="skipped" />
        <el-table-column label="失败" width="80">
          <template #default="{ row }">
            <span :class="{ 'cnt-failed': row.failed > 0 }">{{ row.failed }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type" size="small">
              {{ statusMeta(row.status).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作人" width="100">
          <template #default="{ row }">
            <span class="mono">#{{ row.operator_id ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="完成时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.finished_at || row.started_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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
          @current-change="loadLogs"
          @size-change="(v) => { pageSize = v; loadLogs() }"
        />
      </div>
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detail.open" direction="rtl" size="520px" title="同步日志详情">
      <template v-if="detail.row">
        <div class="detail-block">
          <div class="detail-row"><span class="k">记录 ID</span><span class="v">#{{ detail.row.id }}</span></div>
          <div class="detail-row"><span class="k">来源</span><span class="v mono">{{ detail.row.source }}</span></div>
          <div class="detail-row"><span class="k">类型</span><span class="v">{{ detail.row.sync_type }}</span></div>
          <div class="detail-row"><span class="k">模式</span><span class="v">{{ detail.row.mode }}</span></div>
          <div class="detail-row"><span class="k">状态</span><el-tag :type="statusMeta(detail.row.status).type" size="small">{{ statusMeta(detail.row.status).text }}</el-tag></div>
          <div class="detail-row"><span class="k">操作人</span><span class="v mono">#{{ detail.row.operator_id ?? '-' }}</span></div>
          <div class="detail-row"><span class="k">开始</span><span class="v">{{ formatTime(detail.row.started_at) }}</span></div>
          <div class="detail-row"><span class="k">结束</span><span class="v">{{ formatTime(detail.row.finished_at) }}</span></div>
          <div class="detail-row"><span class="k">总数</span><span class="v">{{ detail.row.total }}</span></div>
          <div class="detail-row"><span class="k">新增</span><span class="v cnt-inserted">+{{ detail.row.inserted }}</span></div>
          <div class="detail-row"><span class="k">更新</span><span class="v cnt-updated">{{ detail.row.updated }}</span></div>
          <div class="detail-row"><span class="k">跳过</span><span class="v">{{ detail.row.skipped }}</span></div>
          <div class="detail-row"><span class="k">失败</span><span :class="['v', { 'cnt-failed': detail.row.failed > 0 }]">{{ detail.row.failed }}</span></div>
          <div v-if="detail.row.remark" class="detail-row col">
            <span class="k">备注</span>
            <pre class="json">{{ detail.row.remark }}</pre>
          </div>
          <div v-if="detail.row.failures && detail.row.failures.length" class="detail-row col">
            <span class="k">失败明细</span>
            <el-table :data="detail.row.failures" size="small" class="failures-table">
              <el-table-column prop="student_id" label="学号" width="120" />
              <el-table-column prop="reason" label="原因" />
            </el-table>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.orgsync-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
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
.sync-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 14px;
}
.sync-card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: box-shadow 0.18s ease;
}
.sync-card:hover {
  box-shadow: var(--shadow-md);
}
.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-head h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
}
.card-icon {
  color: var(--brand);
  font-size: 20px;
}
.card-desc {
  margin: 0;
  font-size: 12px;
  color: var(--text-sub);
  line-height: 1.6;
}
.card-actions {
  margin-top: auto;
  display: flex;
  justify-content: flex-end;
}
.logs-block {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: 16px 18px 18px;
}
.block-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 700;
}
.logs-table {
  border-radius: var(--radius-md);
  overflow: hidden;
}
.cnt-inserted {
  color: #10b981;
  font-weight: 600;
}
.cnt-updated {
  color: #2563eb;
  font-weight: 600;
}
.cnt-failed {
  color: #ef4444;
  font-weight: 600;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

/* 抽屉 */
.detail-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.detail-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.detail-row.col {
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
}
.k {
  width: 70px;
  flex: none;
  font-size: 12px;
  color: var(--text-sub);
}
.v {
  font-size: 13px;
  word-break: break-word;
}
.json {
  margin: 0;
  padding: 10px;
  background: rgba(15, 23, 42, 0.04);
  border-radius: 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
.failures-table {
  border-radius: 6px;
}
</style>