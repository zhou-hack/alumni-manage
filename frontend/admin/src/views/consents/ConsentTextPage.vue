<script setup>
/**
 * 授权文本管理（M16 · P1）
 * - 当前生效文本只读展示
 * - 受后端限制：版本管理端点尚未提供，本页只展示当前生效版本
 * - "我的授权记录" 也一并展示（任意登录用户可读）
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getConsentText, getMyConsents } from '@/api/consents'
import { formatTime } from '@/utils/format'

const loading = ref(false)
const text = ref(null) // { version, body, updated_at, ... }
const myConsents = ref([])
const consentsLoading = ref(false)

async function loadText() {
  loading.value = true
  try {
    const res = await getConsentText()
    text.value = res.data
  } catch (e) {
    ElMessage.error(e.message || '获取当前生效文本失败')
    text.value = null
  } finally {
    loading.value = false
  }
}

async function loadMyConsents() {
  consentsLoading.value = true
  try {
    const res = await getMyConsents()
    myConsents.value = res.data?.list || []
  } catch (e) {
    myConsents.value = []
  } finally {
    consentsLoading.value = false
  }
}

const SCOPE_TEXT = {
  screen: '大屏风采展示',
  directory: '校友通讯录',
}

onMounted(() => {
  loadText()
  loadMyConsents()
})
</script>

<template>
  <div class="consent-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">授权协议管理</h2>
        <p class="page-sub">
          校友档案用于大屏风采展示、校友通讯录等场景时，须经校友本人授权。
          当前生效的协议文本如下所示。
        </p>
      </div>
      <el-button :icon="Refresh" @click="loadText">刷新</el-button>
    </div>

    <div class="info-bar">
      <el-icon class="info-icon"><span>!</span></el-icon>
      <div class="info-text">
        当前页面只展示<strong>当前生效</strong>的协议文本。版本切换 / 历史版本浏览需后端提供管理端点后再开放编辑入口。
      </div>
    </div>

    <!-- 当前生效文本 -->
    <el-card class="text-card" v-loading="loading" shadow="never">
      <template #header>
        <div class="text-head">
          <span class="title">当前生效协议</span>
          <span v-if="text?.version" class="version">v{{ text.version }}</span>
          <span v-if="text?.updated_at" class="updated">更新于 {{ formatTime(text.updated_at) }}</span>
        </div>
      </template>
      <pre v-if="text?.body" class="text-body">{{ text.body }}</pre>
      <el-empty v-else description="暂无授权文本" />
    </el-card>

    <!-- 我的授权记录 -->
    <el-card class="my-card" v-loading="consentsLoading" shadow="never">
      <template #header>
        <div class="text-head">
          <span class="title">我的授权记录</span>
          <span class="count">共 {{ myConsents.length }} 条</span>
        </div>
      </template>
      <el-table :data="myConsents" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="范围" min-width="120">
          <template #default="{ row }">{{ SCOPE_TEXT[row.scope] || row.scope || '-' }}</template>
        </el-table-column>
        <el-table-column label="档案" min-width="160">
          <template #default="{ row }">
            {{ row.archive_title || row.archiveTitle || row.archive_id || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.revoked_at" type="info" effect="light">已撤回</el-tag>
            <el-tag v-else type="success" effect="light">已授权</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="授权时间" width="170">
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column v-if="myConsents.some((c) => c.revoked_at)" label="撤回时间" width="170">
          <template #default="{ row }">{{ formatTime(row.revoked_at) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!consentsLoading && !myConsents.length" description="暂无授权记录" />
    </el-card>
  </div>
</template>

<style scoped>
.consent-page {
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

.info-bar {
  display: flex;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 10px;
  background: rgba(47, 107, 255, 0.06);
  border: 1px dashed rgba(47, 107, 255, 0.3);
  font-size: 13px;
  color: var(--text-main);
}
.info-icon {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 12px;
  flex: none;
}
.info-text {
  line-height: 1.7;
}

.text-card,
.my-card {
  border-radius: var(--radius-xl);
  border: 1px solid var(--line);
}

.text-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.title {
  font-weight: 600;
}
.version {
  padding: 2px 10px;
  border-radius: var(--radius-full);
  background: rgba(5, 150, 105, 0.12);
  color: #059669;
  font-size: 12px;
  font-weight: 600;
}
.updated,
.count {
  font-size: 12px;
  color: var(--text-sub);
}

.text-body {
  margin: 0;
  padding: 16px;
  background: rgba(15, 23, 42, 0.03);
  border-radius: 8px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 480px;
  overflow: auto;
}
</style>