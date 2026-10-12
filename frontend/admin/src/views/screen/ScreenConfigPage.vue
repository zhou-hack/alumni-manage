<script setup>
/**
 * 大屏配置管理（/honor-screen/config · 仅 school_admin / system_admin）
 * - GET /admin/screen/config —— 配置列表（多份配置 + 当前生效标识）
 * - PUT /admin/screen/config/{id} —— 修改配置（layout/theme/sort/maxItems/refreshSec/showFields）
 * - POST /admin/screen/config —— 新建配置（status=draft）
 * - DELETE /admin/screen/config/{id} —— 删除配置（非 active）
 * - GET /admin/screen/config/active —— 当前生效（用于"前置预览"卡片）
 *
 * UI：左侧配置列表 + 右侧编辑面板；保存后下次大屏拉取时自动生效。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Refresh, View } from '@element-plus/icons-vue'
import {
  getActiveScreenConfig,
  listScreenConfigs,
  createScreenConfig,
  updateScreenConfig,
  deleteScreenConfig,
} from '@/api/screen'
import { formatTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import LucideIcon from '@/components/LucideIcon.vue'

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)

const active = ref(null)
const list = ref([])
const selectedId = ref(null)
const selected = computed(() => list.value.find((c) => c.id === selectedId.value) || null)

const LAYOUT_OPTIONS = [
  { value: 'grid-3', label: '三列布局' },
  { value: 'grid-4', label: '四列布局' },
  { value: 'waterfall', label: '瀑布流' },
]
const THEME_OPTIONS = [
  { value: 'tech', label: '科技蓝' },
  { value: 'gold', label: '荣誉金' },
  { value: 'dark', label: '深空黑' },
  { value: 'warm', label: '暖阳橙' },
]
const SORT_OPTIONS = [
  { value: 'awarded_at_desc', label: '最新优先' },
  { value: 'level_asc', label: '级别优先' },
  { value: 'pinned_first', label: '置顶优先' },
]
const REFRESH_OPTIONS = [
  { value: 0, label: '不自动刷新' },
  { value: 10, label: '10 秒' },
  { value: 30, label: '30 秒' },
  { value: 60, label: '1 分钟' },
]
const SHOW_FIELDS = [
  { key: 'title', label: '荣誉标题' },
  { key: 'level', label: '级别' },
  { key: 'category', label: '类别' },
  { key: 'granted_by', label: '授予方' },
  { key: 'awarded_at', label: '授予日期' },
  { key: 'owner', label: '获奖人' },
]

/** 编辑表单：避免污染 selected */
const form = reactive({
  name: '',
  layout: 'grid-3',
  theme_color: 'tech',
  refresh_sec: 30,
  sort_by: 'awarded_at_desc',
  max_items: 12,
  show_fields: { title: true, level: true, category: true, granted_by: false, awarded_at: true, owner: true },
  status: 'draft',
})

function syncFormFromSelected() {
  if (!selected.value) return
  const c = selected.value
  form.name = c.name || ''
  form.layout = c.layout || 'grid-3'
  form.theme_color = c.theme_color || 'tech'
  form.refresh_sec = c.refresh_sec ?? 30
  form.sort_by = c.sort_by || 'awarded_at_desc'
  form.max_items = c.max_items ?? 12
  form.show_fields = {
    title: c.show_fields?.title ?? true,
    level: c.show_fields?.level ?? true,
    category: c.show_fields?.category ?? true,
    granted_by: c.show_fields?.granted_by ?? false,
    awarded_at: c.show_fields?.awarded_at ?? true,
    owner: c.show_fields?.owner ?? true,
  }
  form.status = c.status || 'draft'
}

async function loadActive() {
  try {
    const res = await getActiveScreenConfig()
    active.value = res.data || null
  } catch { /* 静默 */ }
}

async function loadList() {
  loading.value = true
  try {
    const res = await listScreenConfigs({ page: 1, page_size: 50 })
    list.value = res.data?.list || []
    if (!selectedId.value && list.value.length) {
      selectedId.value = list.value[0].id
      syncFormFromSelected()
    } else if (selectedId.value) {
      syncFormFromSelected()
    }
  } catch (e) {
    ElMessage.error(e.message || '配置列表加载失败')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!selected.value) return
  saving.value = true
  try {
    await updateScreenConfig(selected.value.id, {
      name: form.name.trim() || selected.value.name,
      layout: form.layout,
      theme_color: form.theme_color,
      refresh_sec: Number(form.refresh_sec) || 0,
      sort_by: form.sort_by,
      max_items: Number(form.max_items) || 12,
      show_fields: { ...form.show_fields },
      // 关键：保留原 status，避免数据库 NOT NULL 约束导致 500
      status: selected.value.status || 'active',
    })
    ElMessage.success('已保存')
    await Promise.all([loadList(), loadActive()])
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleActivate() {
  if (!selected.value) return
  // "发布"等同于 PUT（保留 active 标记）—— 后端在 update 时将 status=active 视为激活
  saving.value = true
  try {
    // 关键：必须带上当前所有必填字段（name/layout/themeColor/sortBy），否则 DTO @NotBlank 校验会拒
    await updateScreenConfig(selected.value.id, {
      name: form.name.trim() || selected.value.name,
      layout: form.layout,
      theme_color: form.theme_color,
      refresh_sec: Number(form.refresh_sec) || 0,
      sort_by: form.sort_by,
      max_items: Number(form.max_items) || 12,
      show_fields: { ...form.show_fields },
      status: 'active',
    })
    ElMessage.success('已设为当前生效配置')
    await Promise.all([loadList(), loadActive()])
  } catch (e) {
    ElMessage.error(e.message || '激活失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  if (row.status === 'active') {
    ElMessage.warning('生效中的配置不能删除，请先激活其他配置')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除配置【${row.name}】？此操作不可恢复。`, '删除配置', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
    })
  } catch { return }
  try {
    await deleteScreenConfig(row.id)
    ElMessage.success('已删除')
    if (selectedId.value === row.id) selectedId.value = null
    await loadList()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

function openPortalScreen() {
  const portalBase = import.meta.env.VITE_PORTAL_BASE || 'http://localhost:5175'
  window.open(`${portalBase}/dashboard`, '_blank', 'noopener')
}

function statusMeta(s) {
  if (s === 'active') return { text: '生效中', tone: 'success' }
  if (s === 'draft') return { text: '草稿', tone: 'info' }
  if (s === 'archived') return { text: '归档', tone: 'warning' }
  return { text: s || '—', tone: '' }
}

onMounted(async () => {
  await Promise.all([loadActive(), loadList()])
})
</script>

<template>
  <div class="config-page">
    <!-- 头部概览 -->
    <div class="overview">
      <div class="ov-block">
        <div class="ov-lab">当前生效</div>
        <div class="ov-val">
          <LucideIcon name="Monitor" :size="18" />
          <span v-if="active">{{ active.name }}</span>
          <span v-else class="ov-empty">未配置生效项</span>
        </div>
        <div class="ov-sub" v-if="active">最近更新：{{ formatTime(active.updated_at) }}</div>
      </div>
      <el-button type="primary" :icon="View" @click="openPortalScreen">前台预览</el-button>
    </div>

    <div class="grid">
      <!-- 左侧：配置列表 -->
      <div class="list-card">
        <div class="list-head">
          <h3>配置列表</h3>
          <el-button :icon="Plus" type="primary" size="small" @click="handleCreate" :loading="saving">新建</el-button>
        </div>
        <div class="list-body" v-loading="loading">
          <div
            v-for="c in list"
            :key="c.id"
            class="list-row"
            :class="{ active: c.id === selectedId, current: c.status === 'active' }"
            @click="selectedId = c.id; syncFormFromSelected()"
          >
            <div class="row-main">
              <div class="row-name">
                {{ c.name }}
                <el-tag v-if="c.status === 'active'" type="success" size="small" effect="dark">生效</el-tag>
              </div>
              <div class="row-meta">
                {{ LAYOUT_OPTIONS.find((o) => o.value === c.layout)?.label || c.layout }}
                · {{ THEME_OPTIONS.find((o) => o.value === c.theme_color)?.label || c.theme_color }}
              </div>
            </div>
            <el-button
              v-if="c.status !== 'active'"
              :icon="Delete"
              size="small"
              link
              type="danger"
              @click.stop="handleDelete(c)"
            />
          </div>
          <div v-if="!list.length && !loading" class="empty-tip">暂无配置，请新建</div>
        </div>
      </div>

      <!-- 右侧：编辑面板 -->
      <div class="edit-card">
        <div v-if="!selected" class="placeholder">
          <LucideIcon name="Settings" :size="42" />
          <p>请选择左侧配置进行编辑，或点击"新建"创建一份</p>
        </div>
        <template v-else>
          <div class="edit-head">
            <div>
              <h3>{{ selected.name }}</h3>
              <el-tag :type="statusMeta(selected.status).tone" size="small" effect="light">
                {{ statusMeta(selected.status).text }}
              </el-tag>
            </div>
            <div class="head-actions">
              <el-button
                v-if="selected.status !== 'active'"
                type="primary"
                :loading="saving"
                @click="handleActivate"
              >设为生效</el-button>
              <el-button type="primary" :icon="Edit" :loading="saving" @click="handleSave">保存修改</el-button>
            </div>
          </div>

          <div class="form">
            <div class="form-row">
              <label>配置名称</label>
              <el-input v-model="form.name" placeholder="如：大屏 v2 试运行" />
            </div>

            <div class="form-row two">
              <div>
                <label>布局</label>
                <el-select v-model="form.layout" style="width: 100%">
                  <el-option v-for="o in LAYOUT_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </div>
              <div>
                <label>主题色</label>
                <el-select v-model="form.theme_color" style="width: 100%">
                  <el-option v-for="o in THEME_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </div>
            </div>

            <div class="form-row two">
              <div>
                <label>排序方式</label>
                <el-select v-model="form.sort_by" style="width: 100%">
                  <el-option v-for="o in SORT_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </div>
              <div>
                <label>自动刷新</label>
                <el-select v-model="form.refresh_sec" style="width: 100%">
                  <el-option v-for="o in REFRESH_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </div>
            </div>

            <div class="form-row">
              <label>榜单最大条数</label>
              <el-input-number v-model="form.max_items" :min="1" :max="50" />
              <span class="hint">建议 8 ~ 20，过大会拖慢大屏渲染</span>
            </div>

            <div class="form-row">
              <label>展示字段</label>
              <div class="check-group">
                <el-checkbox
                  v-for="f in SHOW_FIELDS"
                  :key="f.key"
                  v-model="form.show_fields[f.key]"
                >{{ f.label }}</el-checkbox>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.config-page { padding: 0; }

.overview {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}
.ov-block {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 16px 20px;
  flex: 1;
}
.ov-lab { font-size: 12px; color: var(--text-sub); }
.ov-val {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 700;
  color: var(--brand-700);
  margin-top: 8px;
}
.ov-empty { color: var(--text-sub); font-weight: 500; font-size: 14px; }
.ov-sub { font-size: 11px; color: var(--text-sub); margin-top: 6px; }

.grid {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
}

.list-card, .edit-card {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: 12px;
}
.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--line);
}
.list-head h3 { font-size: 14px; font-weight: 700; margin: 0; color: var(--text-main); }
.list-body {
  max-height: 540px;
  overflow-y: auto;
}
.list-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
  transition: background 0.12s;
}
.list-row:hover { background: var(--surface-2); }
.list-row.active {
  background: var(--brand-50);
  border-left: 3px solid var(--brand-500);
  padding-left: 15px;
}
.list-row.current { background: rgba(16, 185, 129, 0.05); }
.row-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-main);
  display: flex;
  align-items: center;
  gap: 6px;
}
.row-meta {
  font-size: 11px;
  color: var(--text-sub);
  margin-top: 2px;
}
.empty-tip {
  padding: 32px 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-sub);
}

.edit-card { padding: 18px 22px; min-height: 320px; }
.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 320px;
  color: var(--text-sub);
  font-size: 13px;
  gap: 12px;
}

.edit-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}
.edit-head h3 { font-size: 16px; font-weight: 700; margin: 0 8px 0 0; display: inline; }
.head-actions { display: flex; gap: 8px; }

.form { display: flex; flex-direction: column; gap: 14px; }
.form-row { display: flex; flex-direction: column; gap: 6px; }
.form-row.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.form-row label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-sub);
  letter-spacing: 0.4px;
}
.hint {
  font-size: 11px;
  color: var(--text-sub);
  margin-left: 8px;
}

.check-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  padding: 10px 12px;
  background: var(--surface-2);
  border-radius: 8px;
}

@media (max-width: 960px) {
  .grid { grid-template-columns: 1fr; }
  .list-body { max-height: 280px; }
}
</style>
