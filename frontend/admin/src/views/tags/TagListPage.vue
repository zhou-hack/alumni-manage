<script setup>
/**
 * 标签管理（M13 · 仅 school_admin 可写）
 * - 列表 + 按 group / 关键词筛选
 * - 新建 / 改名 / 调排序 / 删除
 * - 绑定抽屉：选业务类型 + 业务 ID + 多选标签
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Link } from '@element-plus/icons-vue'
import {
  getTags,
  createTag,
  updateTag,
  deleteTag,
  bindTags,
  TAG_GROUP_OPTIONS,
} from '@/api/tags'
import { formatTime } from '@/utils/format'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const filter = reactive({ group: '', keyword: '' })

async function loadList() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value }
    if (filter.group) params.group = filter.group
    if (filter.keyword.trim()) params.keyword = filter.keyword.trim()
    const res = await getTags(params)
    list.value = (res.data.list || []).slice()
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  loadList()
}
function resetFilter() {
  filter.group = ''
  filter.keyword = ''
  onSearch()
}

const groupLabel = (g) => TAG_GROUP_OPTIONS.find((x) => x.value === g)?.label || g || '-'

/* ---------- 新建 / 编辑 ---------- */
const editVisible = ref(false)
const editSaving = ref(false)
const isEditing = ref(false)
const editForm = reactive({ id: null, name: '', group: 'custom', sort: 100 })

function resetEditForm() {
  editForm.id = null
  editForm.name = ''
  editForm.group = 'custom'
  editForm.sort = 100
}
function openCreate() {
  isEditing.value = false
  resetEditForm()
  editVisible.value = true
}
async function openEdit(row) {
  isEditing.value = true
  resetEditForm()
  editForm.id = row.id
  editForm.name = row.name
  editForm.group = row.group || 'custom'
  editForm.sort = row.sort ?? 100
  editVisible.value = true
}
async function submitEdit() {
  if (!editForm.name.trim()) {
    ElMessage.warning('请填写标签名')
    return
  }
  editSaving.value = true
  try {
    if (isEditing.value) {
      await updateTag(editForm.id, { name: editForm.name.trim(), sort: editForm.sort })
      ElMessage.success('已保存')
    } else {
      await createTag({
        name: editForm.name.trim(),
        group: editForm.group,
        sort: editForm.sort,
      })
      ElMessage.success('已创建')
    }
    editVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    editSaving.value = false
  }
}

/* ---------- 删除 ---------- */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除标签「${row.name}」？若已被业务引用，将提示错误并不允许删除。`,
      '删除标签',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteTag(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/* ---------- 绑定抽屉 ---------- */
const bindVisible = ref(false)
const bindSaving = ref(false)
const bindForm = reactive({ bizType: 'archive', bizId: null, selectedTagIds: [] })

function openBind(row) {
  bindForm.bizType = 'archive'
  bindForm.bizId = null
  bindForm.selectedTagIds = []
  // 预选当前标签：单标签绑定场景
  bindForm.selectedTagIds = [row.id]
  bindVisible.value = true
}
async function submitBind() {
  if (!bindForm.bizId) {
    ElMessage.warning('请填写业务 ID')
    return
  }
  bindSaving.value = true
  try {
    await bindTags(bindForm.bizType, bindForm.bizId, bindForm.selectedTagIds)
    ElMessage.success('绑定成功')
    bindVisible.value = false
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    bindSaving.value = false
  }
}

const hasFilter = computed(() => filter.group || filter.keyword.trim())

onMounted(loadList)
</script>

<template>
  <div class="tag-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">标签管理</h2>
        <p class="page-sub">
          按"行业 / 兴趣 / 届别 / 技能"等多维度管理标签，可绑定到档案、荣誉、校友等业务；仅校管理员可写
        </p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建标签</el-button>
    </div>

    <div class="filter-bar">
      <el-select v-model="filter.group" placeholder="分组" clearable style="width: 140px" @change="onSearch">
        <el-option v-for="g in TAG_GROUP_OPTIONS" :key="g.value" :label="g.label" :value="g.value" />
      </el-select>
      <el-input
        v-model="filter.keyword"
        placeholder="搜索标签名"
        clearable
        style="width: 200px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-button @click="onSearch">查询</el-button>
      <el-button v-if="hasFilter" text @click="resetFilter">重置</el-button>
    </div>

    <el-table :data="list" v-loading="loading" class="tag-table" stripe>
      <el-table-column prop="id" label="ID" width="64" />
      <el-table-column label="标签名" min-width="160">
        <template #default="{ row }">
          <el-tag effect="plain">{{ row.name }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分组" width="120">
        <template #default="{ row }">{{ groupLabel(row.group) }}</template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" sortable />
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <div class="ops-row">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" :icon="Link" @click="openBind(row)">绑定</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        :total="total"
        :page-size="pageSize"
        layout="total, prev, pager, next"
        @current-change="loadList"
      />
    </div>

    <!-- 新建 / 编辑 -->
    <el-dialog
      v-model="editVisible"
      :title="isEditing ? '编辑标签' : '新建标签'"
      width="460px"
      destroy-on-close
    >
      <el-form label-width="82px" label-position="left">
        <el-form-item label="标签名" required>
          <el-input v-model="editForm.name" maxlength="32" placeholder="≤32 字符，同分组内不可重名" />
        </el-form-item>
        <el-form-item label="分组" required>
          <el-select v-model="editForm.group" :disabled="isEditing" style="width: 100%">
            <el-option v-for="g in TAG_GROUP_OPTIONS" :key="g.value" :label="g.label" :value="g.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editForm.sort" :min="0" :max="9999" controls-position="right" style="width: 140px" />
          <span class="field-tip">数字越小越靠前</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="submitEdit">
          {{ isEditing ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 绑定抽屉 -->
    <el-dialog v-model="bindVisible" title="标签绑定" width="500px" destroy-on-close>
      <el-form label-width="92px" label-position="left">
        <el-form-item label="业务类型">
          <el-select v-model="bindForm.bizType" style="width: 100%">
            <el-option label="档案" value="archive" />
            <el-option label="荣誉" value="honor" />
            <el-option label="校友" value="alumni" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务 ID" required>
          <el-input-number
            v-model="bindForm.bizId"
            :min="1"
            :controls="false"
            placeholder="档案 / 荣誉 / 校友 ID"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="要绑定的标签">
          <el-select v-model="bindForm.selectedTagIds" multiple filterable style="width: 100%" placeholder="最多 20 个">
            <el-option v-for="t in list" :key="t.id" :label="`${t.name}（${groupLabel(t.group)}）`" :value="t.id" />
          </el-select>
          <div class="field-tip">此接口为覆盖式，提交后该业务原先的标签将被替换</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button type="primary" :loading="bindSaving" @click="submitBind">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tag-page {
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
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}
.tag-table {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  overflow: hidden;
}
.ops-row {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  white-space: nowrap;
}
.ops-row .el-button + .el-button {
  margin-left: 6px;
}
.pager {
  display: flex;
  justify-content: flex-end;
}
.field-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-sub);
}
</style>