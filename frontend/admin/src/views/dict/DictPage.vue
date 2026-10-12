<script setup>
/**
 * 字典管理页（Day 3 F1，API 规范第三章）
 * 学院 / 专业 / 届别 / 荣誉类别 四类字典的增删改查
 * 被业务数据引用的字典项禁止删除（409 RESOURCE_REFERENCED）
 */
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { getDict, addDictItem, updateDictItem, deleteDictItem, invalidateDict } from '@/api/dict'

const DICT_TYPES = [
  { type: 'college', label: '学院' },
  { type: 'major', label: '专业' },
  { type: 'grad_year', label: '届别' },
  { type: 'honor_category', label: '荣誉类别' },
]

const activeType = ref('college')
const activeLabel = computed(() => DICT_TYPES.find((t) => t.type === activeType.value)?.label || '')
const loading = ref(false)
const list = ref([])

// ---------------- 编辑弹窗 ----------------
const dialogVisible = ref(false)
const editingId = ref(null) // null=新增
const form = reactive({ label: '', value: '', sort: null })
const formTitle = computed(() => (editingId.value ? `编辑${activeLabel.value}` : `新增${activeLabel.value}`))

async function loadList() {
  loading.value = true
  try {
    // 按 ID 升序（与后端 sort 字段无关，统一以 id 为准）
    const rows = await getDict(activeType.value, true)
    list.value = (rows || []).slice().sort((a, b) => a.id - b.id)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function switchType(type) {
  activeType.value = type
  loadList()
}

function openAdd() {
  editingId.value = null
  form.label = ''
  form.value = ''
  form.sort = null
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.label = row.label
  form.value = row.value
  form.sort = row.sort
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.label.trim() || !form.value.trim()) {
    ElMessage.warning('名称与编码为必填项')
    return
  }
  try {
    if (editingId.value) {
      await updateDictItem(activeType.value, editingId.value, {
        label: form.label.trim(),
        value: form.value.trim(),
        sort: form.sort,
      })
      ElMessage.success('修改成功')
    } else {
      await addDictItem(activeType.value, {
        label: form.label.trim(),
        value: form.value.trim(),
        sort: form.sort,
      })
      ElMessage.success('新增成功')
    }
    invalidateDict(activeType.value)
    dialogVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除${activeLabel.value}「${row.label}」吗？若已被业务数据引用将无法删除。`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteDictItem(activeType.value, row.id)
    invalidateDict(activeType.value)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

loadList()
</script>

<template>
  <div class="dict-page">
    <div class="dict-head">
      <el-radio-group v-model="activeType" @change="switchType">
        <el-radio-button v-for="t in DICT_TYPES" :key="t.type" :value="t.type">
          {{ t.label }}
        </el-radio-button>
      </el-radio-group>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增{{ activeLabel }}</el-button>
    </div>

    <el-table :data="list" v-loading="loading" class="dict-table">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="label" label="名称" min-width="180" />
      <el-table-column prop="value" label="编码" min-width="140" />
      <el-table-column prop="sort" label="排序" width="100" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="formTitle" width="420px" append-to-body>
      <el-form label-width="70px">
        <el-form-item label="名称" required>
          <el-input v-model="form.label" placeholder="如：计算机学院" maxlength="30" />
        </el-form-item>
        <el-form-item label="编码" required>
          <el-input v-model="form.value" placeholder="如：CS（英文标识，保存后不可重复）" maxlength="30" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" controls-position="right" placeholder="数字越小越靠前" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dict-page {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: 18px;
}

.dict-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
