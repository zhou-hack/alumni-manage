<script setup>
/**
 * 审核流程可视化（清单：ReviewFlowChart 状态机）
 * 按业务类型展示状态机：
 * - archive / honor：提交 → 院系初审 → 校级复核 → 通过（驳回 → 修改后重提回初审）
 * - archive_delete：申请 → 校级审核 → 删除生效（驳回 → 恢复原状态）
 * - activate_manual：申请 → 校级审核 → 完成激活
 */
import { computed } from 'vue'

const props = defineProps({
  bizType: { type: String, required: true },
  /** 当前审核环节：first / second */
  stage: { type: String, default: '' },
  /** 审核单状态：pending / approved / rejected */
  status: { type: String, default: 'pending' },
})

const FLOW_STEPS = {
  twoLevel: ['提交资料', '院系初审', '校级复核', '审核通过'],
  delete: ['提交删除申请', '校级审核', '删除生效'],
  manual: ['提交核验申请', '校级审核', '完成激活'],
}

const steps = computed(() => {
  if (props.bizType === 'archive_delete') return FLOW_STEPS.delete
  if (props.bizType === 'activate_manual') return FLOW_STEPS.manual
  return FLOW_STEPS.twoLevel
})

/** 驳回发生在哪个环节（用于流程图上标红） */
const rejectedIdx = computed(() => {
  if (props.status !== 'rejected') return -1
  if (props.bizType === 'archive_delete' || props.bizType === 'activate_manual') return 1
  return props.stage === 'first' ? 1 : 2
})

/** 当前进行到的节点 */
const currentIdx = computed(() => {
  if (props.status === 'approved') return steps.value.length - 1
  if (props.status === 'rejected') return rejectedIdx.value
  if (props.status === 'pending') {
    if (props.bizType === 'archive_delete' || props.bizType === 'activate_manual') return 1
    return props.stage === 'first' ? 1 : 2
  }
  return 0
})

/** 节点展示态：done / active / rejected / todo */
function stateOf(i) {
  if (i === rejectedIdx.value) return 'rejected'
  if (props.status === 'approved') return 'done'
  if (i < currentIdx.value) return 'done'
  if (i === currentIdx.value) return 'active'
  return 'todo'
}

const rejectHint = computed(() => {
  if (props.status !== 'rejected') return ''
  if (props.bizType === 'archive_delete') return '删除申请被驳回，档案恢复原状态'
  if (props.bizType === 'activate_manual') return '核验申请被驳回，请校友补充材料后重新提交'
  return props.stage === 'first' ? '院系初审驳回，校友修改后可重新提交（生成新版本）' : '校级复核驳回，校友修改后可重新提交（生成新版本）'
})
</script>

<template>
  <div class="flow-chart">
    <div class="flow-line">
      <template v-for="(label, i) in steps" :key="label">
        <div class="flow-node" :class="stateOf(i)">
          <span class="node-dot">
            <template v-if="stateOf(i) === 'done'">✓</template>
            <template v-else-if="stateOf(i) === 'rejected'">✗</template>
            <template v-else>{{ i + 1 }}</template>
          </span>
          <span class="node-label">{{ label }}</span>
          <span v-if="stateOf(i) === 'active'" class="node-tag">进行中</span>
          <span v-else-if="stateOf(i) === 'rejected'" class="node-tag node-tag--rej">已驳回</span>
        </div>
        <div v-if="i < steps.length - 1" class="flow-arrow" :class="{ lit: i < currentIdx }">→</div>
      </template>
    </div>
    <div v-if="rejectHint" class="flow-hint">{{ rejectHint }}</div>
  </div>
</template>

<style scoped>
.flow-chart {
  padding: 4px 0;
}

.flow-line {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.flow-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  position: relative;
  flex-shrink: 0;
  min-width: 72px;
}

.node-dot {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  border: 2px solid var(--line);
  background: var(--card);
  color: var(--text-sub);
  transition: all 0.25s;
}

.node-label {
  font-size: 12px;
  color: var(--text-sub);
  white-space: nowrap;
}

.node-tag {
  position: absolute;
  top: -8px;
  right: -14px;
  font-size: 10px;
  line-height: 1;
  padding: 3px 7px;
  border-radius: var(--radius-full);
  background: var(--brand, #102a5c);
  color: #fff;
  white-space: nowrap;
}
.node-tag--rej {
  background: #ef4444;
}

.flow-node.done .node-dot {
  border-color: #10b981;
  background: #10b981;
  color: #fff;
}
.flow-node.done .node-label {
  color: var(--text-main);
}

.flow-node.active .node-dot {
  border-color: var(--brand, #102a5c);
  color: var(--brand, #102a5c);
  box-shadow: 0 0 0 4px rgba(79, 70, 229, 0.15);
  animation: pulse 1.6s ease-in-out infinite;
}
.flow-node.active .node-label {
  color: var(--brand, #102a5c);
  font-weight: 600;
}

.flow-node.rejected .node-dot {
  border-color: #ef4444;
  background: #ef4444;
  color: #fff;
}
.flow-node.rejected .node-label {
  color: #ef4444;
  font-weight: 600;
}

.flow-arrow {
  color: var(--line);
  font-size: 16px;
  margin-top: 7px;
  transition: color 0.25s;
}
.flow-arrow.lit {
  color: #10b981;
}

.flow-hint {
  margin-top: 10px;
  font-size: 12px;
  color: #ef4444;
  background: rgba(239, 68, 68, 0.08);
  border-radius: 0;
  padding: 8px 12px;
}

@keyframes pulse {
  0%,
  100% {
    box-shadow: 0 0 0 4px rgba(79, 70, 229, 0.15);
  }
  50% {
    box-shadow: 0 0 0 7px rgba(79, 70, 229, 0.08);
  }
}
</style>
