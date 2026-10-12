/**
 * 草稿模块（API 规范第十二章）· PC 校友侧
 * 同步机制：主存储在本地 localStorage，每 1 分钟定时调 /drafts/sync（本地有变更才发）
 * 冲突策略：版本号乐观锁，冲突时服务端最新草稿覆盖本地
 * 注：storage key 与 H5 端不同（PC 与 H5 各自独立草稿区，避免互相覆盖）
 */
import http from './request'

const STORAGE_KEY = 'alumni_admin_draft_archive'

/** 读本地草稿 */
export function getLocalDraft() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null')
  } catch {
    return null
  }
}

/** 写本地草稿：{ version, payload, dirty, updatedAt } */
export function setLocalDraft(draft) {
  if (draft) localStorage.setItem(STORAGE_KEY, JSON.stringify(draft))
  else localStorage.removeItem(STORAGE_KEY)
}

/** 清除本地草稿（正式提交成功后调用） */
export function clearLocalDraft() {
  localStorage.removeItem(STORAGE_KEY)
}

/** 草稿同步（版本号乐观锁） */
export function syncDraft({ biz_type = 'archive', version, payload }) {
  return http.post('/drafts/sync', { biz_type, version, payload })
}

/** 我的草稿列表（换设备恢复用） */
export function getMyDrafts(biz_type) {
  return http.get('/drafts/my', { params: biz_type ? { biz_type } : undefined })
}

/** 删除云端草稿（按 ID；后端：DELETE /drafts/{id}，仅本人） */
export function deleteDraftById(id) {
  return http.delete(`/drafts/${id}`)
}

/** 同步草稿（拉取服务端最新覆盖本地） */
export function fetchAndApplyDraft(id, storageKey) {
  return http.get('/drafts/my').then((res) => {
    const found = (res.data?.list || []).find((d) => d.id === id)
    if (found && storageKey) {
      localStorage.setItem(
        storageKey,
        JSON.stringify({
          version: found.version,
          payload: found.payload,
          dirty: false,
          updatedAt: found.updated_at,
        }),
      )
    }
    return found
  })
}
