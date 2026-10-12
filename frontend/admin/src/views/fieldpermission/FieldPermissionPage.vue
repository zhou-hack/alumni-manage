<script setup>
/**
 * 字段级权限管理（/field-permissions · 仅 system_admin 可写，school_admin 可读）
 * - 顶部角色切换 Tab（alumni/student/counselor/college_admin/school_admin/system_admin）
 * - 中间表格：字段 × 可见性三档单选（visible / masked / hidden）
 * - 底部保存按钮：PUT /admin/field-permissions/{role}
 */
import { computed, reactive, ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Check, Lock } from '@element-plus/icons-vue'
import {
  getFieldPermissions,
  updateFieldPermissions,
  ROLES,
  VISIBILITY,
  FIELD_KEYS,
} from '@/api/fieldPermission'
import { useUserStore } from '@/stores/user'
import LucideIcon from '@/components/LucideIcon.vue'

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const activeRole = ref('alumni')
const matrix = reactive({ roles: {} })
/** 编辑中的字段暂存（保存时合并到 matrix.roles[role]） */
const draft = reactive({})

const isSystemAdmin = computed(() => userStore.user?.role === 'system_admin')

async function loadMatrix() {
  loading.value = true
  try {
    const res = await getFieldPermissions()
    const roles = res.data?.roles || res.roles || {}
    // 后端默认只返回已配置的项；前端 FIELD_KEYS 全集兜底（缺省值 = visible）
    for (const role of ROLES) {
      const src = roles[role.value] || {}
      draft[role.value] = {}
      for (const f of FIELD_KEYS) {
        draft[role.value][f.key] = src[f.key] || 'visible'
      }
    }
    Object.assign(matrix.roles, roles)
  } catch (e) {
    ElMessage.error(e.message || '字段权限加载失败')
  } finally {
    loading.value = false
  }
}

const currentFields = computed(() => draft[activeRole.value] || {})

function setVisibility(role, key, val) {
  draft[role][key] = val
}

function applyPreset(role, preset) {
  // 全可见 / 全脱敏 / 全隐藏 一键填充
  const next = {}
  for (const f of FIELD_KEYS) next[f.key] = preset
  draft[role] = next
}

async function handleSave() {
  if (!isSystemAdmin.value) {
    ElMessage.warning('仅系统管理员可修改字段权限')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将覆盖【${activeRole.value}】角色的所有字段可见性，确认保存？`,
      '保存字段权限',
      { type: 'warning', confirmButtonText: '保存', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  saving.value = true
  try {
    await updateFieldPermissions(activeRole.value, draft[activeRole.value])
    matrix.roles[activeRole.value] = { ...draft[activeRole.value] }
    ElMessage.success('已保存')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function toneOf(v) {
  return VISIBILITY.find((x) => x.value === v)?.tone || ''
}

onMounted(loadMatrix)
watch(activeRole, () => {
  // 切换角色时如未初始化过则兜底
  if (!draft[activeRole.value]) {
    const src = matrix.roles[activeRole.value] || {}
    draft[activeRole.value] = {}
    for (const f of FIELD_KEYS) draft[activeRole.value][f.key] = src[f.key] || 'visible'
  }
})
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <div class="eyebrow">字段权限</div>
        <h1 class="page-title">字段权限矩阵</h1>
        <p class="page-sub">
          按角色配置校友基础字段（手机号 / 身份证 / 行业等）的可见性三档——可见 / 脱敏 / 隐藏。
          仅系统管理员可写入；其他管理员只读。
        </p>
      </div>
      <el-button :icon="Refresh" @click="loadMatrix">刷新</el-button>
    </div>

    <div class="role-tabs">
      <button
        v-for="r in ROLES"
        :key="r.value"
        class="role-tab"
        :class="{ active: activeRole === r.value }"
        @click="activeRole = r.value"
      >
        {{ r.label }}
        <span class="role-en">({{ r.value }})</span>
      </button>
    </div>

    <div class="presets">
      <span class="preset-lab">快捷：</span>
      <button class="preset-btn" @click="applyPreset(activeRole, 'visible')">全可见</button>
      <button class="preset-btn" @click="applyPreset(activeRole, 'masked')">全脱敏</button>
      <button class="preset-btn" @click="applyPreset(activeRole, 'hidden')">全隐藏</button>
    </div>

    <div class="matrix-card">
      <div class="matrix-row head">
        <div>字段</div>
        <div>当前值</div>
        <div class="col-actions">设为</div>
      </div>
      <div
        v-for="f in FIELD_KEYS"
        :key="f.key"
        class="matrix-row"
      >
        <div class="col-field">
          <div class="cell-title">{{ f.label }}</div>
          <div class="cell-meta">{{ f.key }}</div>
        </div>
        <div>
          <el-tag :type="toneOf(currentFields[f.key])" effect="light" size="small">
            {{ VISIBILITY.find((v) => v.value === currentFields[f.key])?.label || '可见' }}
          </el-tag>
        </div>
        <div class="col-actions">
          <el-radio-group
            :model-value="currentFields[f.key]"
            :disabled="!isSystemAdmin"
            @change="(v) => setVisibility(activeRole, f.key, v)"
          >
            <el-radio-button v-for="v in VISIBILITY" :key="v.value" :value="v.value">
              {{ v.label }}
            </el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </div>

    <div class="foot-bar" v-if="isSystemAdmin">
      <el-button type="primary" :icon="Check" :loading="saving" @click="handleSave">保存</el-button>
      <span class="foot-hint">
        <el-icon><Lock /></el-icon>
        保存后将即时生效于登录态读权限
      </span>
    </div>
    <div class="foot-bar" v-else>
      <el-alert type="info" :closable="false" show-icon>
        您是只读角色（当前 <b>{{ userStore.user?.role }}</b>）。字段权限仅系统管理员可写入。
      </el-alert>
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
.page-title { font-size: 24px; font-weight: 700; margin: 4px 0 6px; color: var(--text-main); }
.page-sub { font-size: 13px; color: var(--text-sub); margin: 0; max-width: 720px; line-height: 1.6; }

.role-tabs {
  display: flex;
  gap: 8px;
  margin: 18px 0 14px;
  flex-wrap: wrap;
}
.role-tab {
  padding: 8px 16px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--surface-1);
  color: var(--text-sub);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.role-tab:hover { color: var(--text-main); border-color: var(--brand-500); }
.role-tab.active {
  background: linear-gradient(135deg, var(--brand-500), var(--brand-700));
  color: #fff;
  border-color: transparent;
  font-weight: 600;
}
.role-en {
  margin-left: 6px;
  font-family: var(--font-mono, monospace);
  font-size: 11px;
  opacity: 0.7;
}

.presets {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 12px;
  color: var(--text-sub);
}
.preset-lab { font-weight: 600; }
.preset-btn {
  padding: 4px 12px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--surface-1);
  color: var(--text-main);
  font-size: 12px;
  cursor: pointer;
}
.preset-btn:hover { border-color: var(--brand-500); color: var(--brand-700); }

.matrix-card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 18px;
}
.matrix-row {
  display: grid;
  grid-template-columns: 1.4fr 0.8fr 2.2fr;
  align-items: center;
  gap: 16px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--line);
}
.matrix-row:last-child { border-bottom: none; }
.matrix-row.head {
  background: var(--surface-2);
  font-size: 12px;
  font-weight: 700;
  color: var(--text-sub);
  letter-spacing: 0.5px;
  text-transform: uppercase;
}
.col-field .cell-title { font-weight: 600; color: var(--text-main); font-size: 13px; }
.col-field .cell-meta { font-family: var(--font-mono, monospace); font-size: 11px; color: var(--text-sub); margin-top: 2px; }

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
  color: var(--text-sub);
}

@media (max-width: 860px) {
  .matrix-row {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .matrix-row.head { display: none; }
}
</style>
