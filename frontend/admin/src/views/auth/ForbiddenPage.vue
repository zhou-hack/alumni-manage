<script setup>
/** 403 无权限页：越权路由兜底展示 */
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

function goHome() {
  router.replace(userStore.defaultHome)
}
</script>

<template>
  <div class="forbidden">
    <el-result icon="warning" title="403 · 无访问权限" sub-title="当前角色无权访问该页面，如需开通请联系校级管理员">
      <template #extra>
        <el-button type="primary" @click="goHome">返回我的首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.forbidden {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg);
}
</style>
