<script setup>
/**
 * 修改密码页（PRD M01-03：初始密码首次登录强制改密）
 * - 强度校验：≥8 位含字母+数字（API 规范 1.7）
 * - 密码一律 SHA-256 散列后上传，前端不传明文
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/api/auth'
import { sha256Hex } from '@/utils/crypto'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const form = reactive({ old: '', next: '', confirm: '' })

const STRONG = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/

async function submit() {
  if (!form.old) return ElMessage.warning('请输入原密码')
  if (!STRONG.test(form.next)) return ElMessage.warning('新密码需 ≥8 位且同时包含字母和数字')
  if (form.next === form.old) return ElMessage.warning('新密码不能与原密码相同')
  if (form.next !== form.confirm) return ElMessage.warning('两次输入的新密码不一致')
  loading.value = true
  try {
    await changePassword(await sha256Hex(form.old), await sha256Hex(form.next))
    // 清除强制改密标志，进入角色默认首页
    userStore.setLoginUser({ ...userStore.user, must_change_password: false })
    ElMessage.success('密码修改成功')
    router.replace(userStore.defaultHome)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="cp-page">
    <el-card class="cp-card">
      <div class="cp-head">
        <el-icon class="cp-icon"><Lock /></el-icon>
        <div>
          <div class="cp-title">首次登录请修改初始密码</div>
          <div class="cp-sub">为保障账号安全，系统分配的初始密码必须修改后方可使用</div>
        </div>
      </div>
      <el-form label-position="top" class="cp-form" @submit.prevent="submit">
        <el-form-item label="原密码">
          <el-input v-model="form.old" type="password" show-password placeholder="请输入初始密码" autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="form.next" type="password" show-password placeholder="≥8 位，需包含字母和数字" autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="form.confirm" type="password" show-password placeholder="再次输入新密码" autocomplete="new-password" @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" size="large" class="cp-btn" :loading="loading" @click="submit">确认修改</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.cp-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg);
}

.cp-card {
  width: 460px;
}

.cp-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.cp-icon {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-xl);
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cp-title {
  font-size: 16px;
  font-weight: 600;
}

.cp-sub {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 4px;
}

.cp-btn {
  width: 100%;
  margin-top: 6px;
}
</style>
