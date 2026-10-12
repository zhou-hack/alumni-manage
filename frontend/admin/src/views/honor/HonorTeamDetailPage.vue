<script setup>
/**
 * 团队荣誉详情（/honors/team/:teamId · counselor/college_admin/school_admin/system_admin）
 * - GET /honors/team/{teamId}：团队成员留证 + 同批荣誉摘要
 *
 * 使用入口：HonorListPage / HonorDraftPage 「按团队 ID」跳转（后续接入）
 */
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Medal } from '@element-plus/icons-vue'
import { getHonorTeam } from '@/api/honor'
import { formatTime } from '@/utils/format'
import LucideIcon from '@/components/LucideIcon.vue'

const route = useRoute()
const loading = ref(false)
const data = ref(null)

async function load() {
  const teamId = route.params.teamId
  if (!teamId) {
    ElMessage.warning('缺少 team_id')
    return
  }
  loading.value = true
  try {
    const res = await getHonorTeam(teamId)
    data.value = res.data || res
  } catch (e) {
    ElMessage.error(e.message || '团队荣誉加载失败')
  } finally {
    loading.value = false
  }
}

function statusOf(s) {
  if (s === 'matched') return { text: '已匹配', tone: 'success' }
  if (s === 'unmatched') return { text: '未匹配', tone: 'warning' }
  if (s === 'ambiguous') return { text: '重名', tone: 'warning' }
  if (s === 'no_account') return { text: '无账号', tone: 'info' }
  if (s === 'duplicated') return { text: '重复', tone: 'danger' }
  if (s === 'manual') return { text: '人工指定', tone: 'primary' }
  return { text: s || '—', tone: '' }
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <div class="eyebrow">团队荣誉</div>
        <h1 class="page-title">团队荣誉详情</h1>
        <p class="page-sub">查看成员留证（matched/duplicated/no_account/ambiguous/unmatched）与同批荣誉摘要。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div v-if="data" class="block">
      <header><h3>团队信息</h3></header>
      <div class="kv-grid">
        <div class="kv"><span class="k">团队 ID</span><span class="v">{{ data.team_id || data.id }}</span></div>
        <div class="kv"><span class="k">荣誉标题</span><span class="v">{{ data.title }}</span></div>
        <div class="kv"><span class="k">级别</span><span class="v">{{ data.level }}</span></div>
        <div class="kv"><span class="k">学院</span><span class="v">{{ data.college_name || data.college }}</span></div>
        <div class="kv"><span class="k">授予日期</span><span class="v">{{ data.awarded_at }}</span></div>
        <div class="kv"><span class="k">创建时间</span><span class="v">{{ formatTime(data.created_at) }}</span></div>
      </div>
    </div>

    <div v-if="data" class="block">
      <header><h3>成员留证</h3></header>
      <div class="list-card" v-loading="loading">
        <div class="th head">
          <div>姓名</div>
          <div class="col-hide">学院</div>
          <div class="col-hide">届别</div>
          <div>匹配状态</div>
        </div>
        <template v-if="(data.members || []).length">
          <div v-for="(m, idx) in data.members" :key="idx" class="th">
            <div class="col-main">
              <div class="cell-title">{{ m.name }}</div>
              <div class="cell-meta">{{ m.student_id || '—' }}</div>
            </div>
            <div class="cell col-hide">{{ m.college || '—' }}</div>
            <div class="cell col-hide">{{ m.grad_year ? m.grad_year + ' 届' : '—' }}</div>
            <div>
              <el-tag :type="statusOf(m.match_status).tone" effect="light" size="small">
                {{ statusOf(m.match_status).text }}
              </el-tag>
            </div>
          </div>
        </template>
        <div v-else class="empty">
          <LucideIcon name="users" :size="26" />
          <h4>暂无成员记录</h4>
        </div>
      </div>
    </div>

    <div v-if="data && data.related_honors" class="block">
      <header><h3>同批荣誉摘要</h3></header>
      <ul class="rel-list">
        <li v-for="(h, idx) in data.related_honors" :key="idx">
          <el-icon><Medal /></el-icon>
          <span>{{ h.title }} · {{ h.level }} · {{ formatTime(h.awarded_at) }}</span>
        </li>
      </ul>
    </div>

    <div v-if="!data && !loading" class="placeholder">
      <LucideIcon name="users" :size="42" />
      <p>请通过团队荣誉提交/导入入口进入本页面</p>
    </div>
  </div>
</template>

<style scoped>
@import '../shared/list-shared.css';
.eyebrow {
  font-family: var(--font-mono, monospace);
  font-size: 11px;
  letter-spacing: 1.2px;
  color: var(--text-sub);
  text-transform: uppercase;
}
.page-title { font-size: 22px; font-weight: 700; margin: 4px 0 6px; color: var(--text-main); }
.page-sub { font-size: 13px; color: var(--text-sub); margin: 0; max-width: 720px; line-height: 1.6; }

.block {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 14px;
}
.block header h3 { font-size: 14px; font-weight: 700; margin: 0 0 12px; color: var(--text-main); }

.kv-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px 18px;
}
.kv { display: flex; flex-direction: column; gap: 4px; }
.kv .k { font-size: 11px; color: var(--text-sub); }
.kv .v { font-size: 13.5px; color: var(--text-main); font-weight: 500; }

.th {
  display: grid;
  grid-template-columns: 1.4fr 1.2fr 0.8fr 1fr;
  align-items: center;
  gap: 16px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--line);
}
.th.head {
  background: var(--surface-2);
  font-size: 12px;
  font-weight: 700;
  color: var(--text-sub);
}
.th:last-child { border-bottom: none; }

.rel-list { list-style: none; margin: 0; padding: 0; }
.rel-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  font-size: 13px;
  color: var(--text-main);
  border-bottom: 1px dashed var(--line);
}
.rel-list li:last-child { border-bottom: none; }
.rel-list li .el-icon { color: var(--brand-700); }

.placeholder {
  text-align: center;
  padding: 60px 0;
  color: var(--text-sub);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

@media (max-width: 860px) {
  .kv-grid { grid-template-columns: 1fr; }
  .th { grid-template-columns: 1fr 1fr; }
  .th .col-hide { display: none; }
}
</style>
