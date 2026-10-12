<script setup>
/**
 * 红头文件链接组件
 * - 校级 / 院级 / 导员：直接打开（后端权限已校验，前端透传附件 ID）
 * - 校友 / 学生：弹窗提示需要审批，点击"申请查看"才打开外链
 * 注：red_doc_url 是外部 / 内部 URL，前端不直接判断权限，由后端服务端鉴权。
 *    本组件仅在用户角色不满足归档门槛时给出友好引导。
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Link } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  /** 红头文件 URL（外部或内部） */
  url: { type: String, default: '' },
  /** 附件 ID（如果红头文件同时上传到了 attachments，则走 /preview 权限） */
  attachmentId: { type: [Number, String], default: null },
  /** 显示文字，默认"打开红头文件" */
  label: { type: String, default: '打开红头文件' },
})

const userStore = useUserStore()
const requesting = ref(false)

// 有权限直接看的角色（与后端 SecurityConfig 路由策略对齐）
const PRIVILEGED_ROLES = new Set(['counselor', 'college_admin', 'school_admin', 'system_admin'])
const canDirectOpen = () => PRIVILEGED_ROLES.has(userStore.user?.role)

/** 直接打开（管理员/校级/院系/导员） */
function directOpen() {
  if (!props.url && !props.attachmentId) return
  if (props.attachmentId) {
    window.open(`/api/v1/attachments/${props.attachmentId}/preview`, '_blank')
  } else {
    window.open(props.url, '_blank')
  }
}

/** 校友 / 学生：弹窗申请 */
async function requestAndOpen() {
  try {
    await ElMessageBox.confirm(
      '红头文件受后端访问权限控制，校外用户首次查看需向校级管理员提交申请。是否继续打开？',
      '查看红头文件',
      { type: 'info', confirmButtonText: '继续打开', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  requesting.value = true
  try {
    // 这里外延是真实由用户主动打开：服务端审计会记录点击
    directOpen()
    ElMessage.success('已为您打开红头文件')
  } finally {
    requesting.value = false
  }
}

function handleClick(e) {
  e.preventDefault()
  if (canDirectOpen()) {
    directOpen()
  } else {
    requestAndOpen()
  }
}
</script>

<template>
  <a v-if="url || attachmentId" href="#" class="red-doc-link" @click="handleClick">
    <el-icon class="link-icon"><Link /></el-icon>
    <span class="link-text">{{ label }}</span>
  </a>
  <span v-else class="muted">无</span>
</template>

<style scoped>
.red-doc-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--brand);
  text-decoration: none;
  font-weight: 500;
}
.red-doc-link:hover {
  text-decoration: underline;
}
.link-icon {
  font-size: 14px;
}
.muted {
  color: var(--text-sub);
}
</style>