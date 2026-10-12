<script setup>
/**
 * 荣誉大屏（/honor-screen · 仅 school_admin / system_admin）
 *
 * 设计意图：去掉"大屏配置"独立管理页。本页只展示：
 *   1) 荣誉类型分布   2) 校友荣誉长廊   3) 校友风采   4) 学院分布
 * 「让谁上 / 谁下」统一在 /honors 荣誉管理页操作（行内 + 批量 + 日志）。
 */
import { useRouter } from 'vue-router'
import LucideIcon from '@/components/LucideIcon.vue'

const router = useRouter()

/** 前台预览：新标签页打开门户荣誉大屏 */
function openPortalScreen() {
  const portalBase = import.meta.env.VITE_PORTAL_BASE || 'http://localhost:5175'
  window.open(`${portalBase}/dashboard`, '_blank', 'noopener')
}

/** 跳到荣誉管理做上下架 */
function goManageHonors() {
  router.push('/honors')
}
</script>

<template>
  <div class="screen-layout">
    <div class="head">
      <div class="head-text">
        <div class="eyebrow">荣誉大屏</div>
        <h1 class="page-title">荣誉大屏总览</h1>
        <p class="page-sub">
          荣誉类型分布、校友荣誉长廊、校友风采、学院分布四个维度实时呈现给前台大屏。
          上架 / 下架请前往「荣誉管理」操作。
        </p>
      </div>
      <div class="head-actions">
        <el-button type="primary" plain @click="goManageHonors">
          <span style="display:inline-flex;align-items:center;gap:6px;">
            <LucideIcon name="Medal" :size="14" />
            去荣誉管理（上下架）
          </span>
        </el-button>
        <button
          type="button"
          class="preview-btn"
          title="在新标签页打开门户荣誉大屏（按当前 active 配置实时渲染）"
          @click="openPortalScreen"
        >
          <LucideIcon name="View" :size="16" />
          <span>前台预览</span>
        </button>
      </div>
    </div>

    <router-view />
  </div>
</template>

<style scoped>
.screen-layout {
  padding: 0;
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
}

.head-text .eyebrow {
  font-family: var(--font-mono, monospace);
  font-size: 11px;
  letter-spacing: 1.2px;
  color: var(--text-sub);
  text-transform: uppercase;
}

.head-text .page-title {
  font-size: 26px;
  font-weight: 700;
  margin: 6px 0 6px;
  color: var(--text-main);
}

.head-text .page-sub {
  font-size: 13px;
  color: var(--text-sub);
  margin: 0;
  max-width: 720px;
  line-height: 1.6;
}

.head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.preview-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 16px;
  height: 36px;
  align-self: stretch;
  border-radius: var(--radius-md, 6px);
  border: 1px solid var(--brand-500, #2563eb);
  background: rgba(37, 99, 235, 0.06);
  color: var(--brand-500, #2563eb);
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: 0.5px;
  cursor: pointer;
  transition: background 0.18s, color 0.18s, transform 0.15s;
}
.preview-btn:hover {
  background: var(--brand-500, #2563eb);
  color: #fff;
  transform: translateY(-1px);
}
</style>
