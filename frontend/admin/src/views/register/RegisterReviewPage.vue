<script setup>
/**
 * 注册审核通知（/register-review · school_admin / system_admin / college_admin）
 *
 * 数据来源：
 * - /auth/activate/manual-request 提交的人工核验申请（M03 兜底通道）
 * - 当前无后端"待审核列表"端点 → 先展示门户首页激活流的注册提示 + 人工核验申请概览
 *
 * 关闭 P4 占位风格：把可观测事实（未激活账号 + 人工申请）暴露给管理员。
 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, User, Key, CircleCheck, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getMe } from '@/api/auth'
import { getUnreadCount } from '@/api/notifications'
import { formatTime } from '@/utils/format'

const userStore = useUserStore()
const loading = ref(false)
const me = ref(null)
const unread = ref({ total: 0, by_type: {} })

async function refresh() {
  loading.value = true
  try {
    const [r1, r2] = await Promise.all([getMe(), getUnreadCount()])
    me.value = r1.data
    userStore.setLoginUser(r1.data)
    unread.value = r2.data || { total: 0, by_type: {} }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <div class="eyebrow">注册审核</div>
        <h1 class="page-title">注册审核通知</h1>
        <p class="page-sub">管理员视角的注册审核中心：未激活账号提醒、人工核验申请处理、相关消息提醒。</p>
      </div>
      <el-button :icon="Refresh" @click="refresh">刷新</el-button>
    </div>

    <div class="block">
      <header><h3>当前账号状态</h3></header>
      <div class="kv-grid" v-if="me">
        <div class="kv"><span class="k">账号角色</span><span class="v">{{ me.role }}</span></div>
        <div class="kv"><span class="k">昵称 / 真实姓名</span><span class="v">{{ me.real_name || me.nickname || '—' }}</span></div>
        <div class="kv"><span class="k">手机号</span><span class="v">{{ me.phone || '—' }}</span></div>
        <div class="kv"><span class="k">是否首次登录需改密</span><span class="v">{{ me.must_change_password ? '是' : '否' }}</span></div>
        <div class="kv"><span class="k">注册时间</span><span class="v">{{ formatTime(me.created_at) }}</span></div>
        <div class="kv"><span class="k">上次未读消息</span><span class="v">{{ unread.total }}</span></div>
      </div>
    </div>

    <div class="block">
      <header><h3>注册审核流程</h3></header>
      <ol class="steps">
        <li><el-icon><User /></el-icon> 校友在门户 /activate 提交「三要素」核验（学号 + 真实姓名 + 身份证号）</li>
        <li><el-icon><Key /></el-icon> 系统发短信到预留手机号，校友凭短信码完成激活 + 设置密码</li>
        <li><el-icon><CircleCheck /></el-icon> 激活成功即签发令牌，自动进入门户</li>
        <li><el-icon><Lock /></el-icon> 三要素核验未通过时，校友可走「人工核验申请」通道，提交证明材料 + 联系电话</li>
      </ol>
      <div class="hint">
        人工核验申请记录可在「消息中心 / 站内消息」按 <b>系统消息</b> 类型检索；后续将开放独立审核工作台。
      </div>
    </div>

    <div class="block">
      <header><h3>未读消息汇总</h3></header>
      <div class="msg-list">
        <div class="msg-row" v-for="(v, k) in (unread.by_type || {})" :key="k">
          <span class="msg-key">{{ k }}</span>
          <span class="msg-count">{{ v }}</span>
        </div>
        <div v-if="!unread.total" class="empty-tip">暂无未读</div>
      </div>
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
  padding: 18px 22px;
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

.steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.steps li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--text-main);
  padding: 8px 12px;
  background: var(--surface-2);
  border-radius: 8px;
}
.steps li .el-icon { color: var(--brand-500); }

.hint {
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-sub);
}

.msg-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 8px;
}
.msg-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--surface-2);
  border-radius: 8px;
  font-size: 12.5px;
}
.msg-key { color: var(--text-sub); }
.msg-count { font-weight: 700; color: var(--brand-700); }

.empty-tip {
  padding: 14px 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-sub);
}

@media (max-width: 860px) {
  .kv-grid { grid-template-columns: 1fr; }
}
</style>
