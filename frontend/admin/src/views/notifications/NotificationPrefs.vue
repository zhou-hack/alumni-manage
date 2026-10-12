<script setup>
/**
 * 通知订阅设置（/notifications/prefs · 所有登录角色可访问）
 * - SMS / Email 双渠道配置
 * - 后端 API：
 *   GET  /api/v1/notification-subscriptions/my
 *   PUT  /api/v1/notification-subscriptions   body: { sms: {enabled,scenes}, email: {enabled,address,scenes} }
 * - 后端场景取值（SubscriptionService.SCENES）：review_result / announcement
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Check } from '@element-plus/icons-vue'
import {
  getSubscriptions,
  saveSubscriptions,
  SUBSCRIPTION_SCENES,
} from '@/api/notifications'

const loading = ref(false)
const saving = ref(false)
/** 邮箱基础校验（与后端 SubscriptionService.normalizeAddress 同步） */
const EMAIL_RE = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/

const form = reactive({
  sms: { enabled: false, scenes: [] },
  email: { enabled: false, address: '', scenes: [] },
})

function emptyForm() {
  form.sms = { enabled: false, scenes: [] }
  form.email = { enabled: false, address: '', scenes: [] }
}

async function loadPrefs() {
  loading.value = true
  emptyForm()
  try {
    const res = await getSubscriptions()
    const d = res.data || {}
    form.sms.enabled = Boolean(d.sms?.enabled)
    form.sms.scenes = Array.isArray(d.sms?.scenes) ? [...d.sms.scenes] : []
    form.email.enabled = Boolean(d.email?.enabled)
    form.email.address = d.email?.address || ''
    form.email.scenes = Array.isArray(d.email?.scenes) ? [...d.email.scenes] : []
  } catch {
    /* 默认全关 */
  } finally {
    loading.value = false
  }
}

const dirty = ref(false)
function markDirty() {
  dirty.value = true
}

async function handleSave() {
  // 客户端校验：开启邮箱订阅时必填合法邮箱
  if (form.email.enabled) {
    if (!form.email.address || !EMAIL_RE.test(form.email.address)) {
      ElMessage.warning('开启邮箱订阅时需填写合法邮箱地址')
      return
    }
  }
  saving.value = true
  try {
    await saveSubscriptions({
      sms: { enabled: form.sms.enabled, scenes: form.sms.scenes },
      email: {
        enabled: form.email.enabled,
        address: form.email.address.trim(),
        scenes: form.email.scenes,
      },
    })
    dirty.value = false
    ElMessage.success('订阅已保存')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleReset() {
  await loadPrefs()
  dirty.value = false
}

onMounted(loadPrefs)
</script>

<template>
  <div class="prefs-page" v-loading="loading">
    <div class="page-head">
      <div>
        <div class="eyebrow">通知订阅</div>
        <h1 class="page-title">通知订阅设置</h1>
        <p class="page-sub">
          开启短信 / 邮箱订阅后，系统将按你勾选的场景推送通知；未开启的渠道不会推送。
          默认全关，最小打扰原则。
        </p>
      </div>
      <el-button :icon="Refresh" @click="handleReset">刷新</el-button>
    </div>

    <div class="channels">
      <!-- SMS -->
      <section class="card">
        <header class="card-head">
          <div>
            <h3 class="card-title">短信订阅</h3>
            <p class="card-sub">手机号沿用账号绑定号；未开启时不发送短信</p>
          </div>
          <el-switch
            v-model="form.sms.enabled"
            inline-prompt
            active-text="开"
            inactive-text="关"
            @change="markDirty"
          />
        </header>
        <div class="card-body" :class="{ disabled: !form.sms.enabled }">
          <div class="scenes-label">通知场景</div>
          <el-checkbox-group v-model="form.sms.scenes" :disabled="!form.sms.enabled" @change="markDirty">
            <el-checkbox v-for="s in SUBSCRIPTION_SCENES" :key="s.key" :value="s.key" border class="scene-cb">
              {{ s.label }}
            </el-checkbox>
          </el-checkbox-group>
        </div>
      </section>

      <!-- Email -->
      <section class="card">
        <header class="card-head">
          <div>
            <h3 class="card-title">邮箱订阅</h3>
            <p class="card-sub">开启需填写合法邮箱；同一地址可在多渠道切换</p>
          </div>
          <el-switch
            v-model="form.email.enabled"
            inline-prompt
            active-text="开"
            inactive-text="关"
            @change="markDirty"
          />
        </header>
        <div class="card-body" :class="{ disabled: !form.email.enabled }">
          <el-form-item label="邮箱地址" class="email-form-item">
            <el-input
              v-model="form.email.address"
              placeholder="your@example.com"
              :disabled="!form.email.enabled"
              clearable
              @input="markDirty"
            />
          </el-form-item>
          <div class="scenes-label">通知场景</div>
          <el-checkbox-group v-model="form.email.scenes" :disabled="!form.email.enabled" @change="markDirty">
            <el-checkbox v-for="s in SUBSCRIPTION_SCENES" :key="s.key" :value="s.key" border class="scene-cb">
              {{ s.label }}
            </el-checkbox>
          </el-checkbox-group>
        </div>
      </section>
    </div>

    <footer class="foot-bar">
      <el-button type="primary" :icon="Check" :loading="saving" :disabled="!dirty" @click="handleSave">
        保存设置
      </el-button>
      <span v-if="dirty" class="foot-hint">
        <el-icon><Check /></el-icon>
        有未保存的修改
      </span>
      <span v-else class="foot-hint muted">
        当前已是最新设置
      </span>
    </footer>
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
.page-title { font-size: 24px; font-weight: 700; margin: 4px 0 6px; color: var(--text-main); }
.page-sub { font-size: 13px; color: var(--text-sub); margin: 0; max-width: 720px; line-height: 1.6; }

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
}

.channels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 18px;
}

.card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  margin: 0;
  color: var(--text-main);
}
.card-sub {
  font-size: 12px;
  color: var(--text-sub);
  margin: 4px 0 0;
}

.card-body {
  border-top: 1px solid var(--line);
  padding-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: opacity 0.18s ease;
}
.card-body.disabled {
  opacity: 0.45;
  pointer-events: none;
}

.scenes-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-sub);
  letter-spacing: 0.4px;
}

.scene-cb {
  margin-right: 8px;
  margin-bottom: 6px;
}

.email-form-item {
  margin: 0;
}

.foot-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: 12px;
}
.foot-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--brand-700);
}
.foot-hint.muted {
  color: var(--text-sub);
}

@media (max-width: 860px) {
  .channels {
    grid-template-columns: 1fr;
  }
}
</style>
