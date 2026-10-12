/** 附件模块（API 规范第六章）· 校友侧上传 */
import http from './request'

/** 支持的类型：图片 / PDF / Word / Excel（PRD 3.3：MVP 仅这四类） */
export const ACCEPT = '.jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx'
export const MAX_FILE_SIZE = 10 * 1024 * 1024 // 单文件 ≤ 10MB

/**
 * 批量上传附件
 * @param {File[]} files 文件列表
 * @param {string} bizType 业务归属 archive / honor / activate 等
 * @param {string} remark 统一备注（批量上传时多文件共享）
 * @param {(percent:number)=>void} onProgress 上传进度回调（0-100）
 * @returns {Promise<{success: [], failed: []}>}
 */
export function uploadAttachments(files, bizType = 'archive', remark = '', onProgress = null) {
  const form = new FormData()
  for (const file of files) form.append('file', file)
  form.append('biz_type', bizType)
  if (remark) form.append('remark', remark)
  return http.post('/attachments', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
    onUploadProgress: onProgress
      ? (e) => onProgress(e.total ? Math.round((e.loaded / e.total) * 100) : 0)
      : undefined,
  })
}

/** 预览地址（图片/PDF 直出流） */
export function previewUrl(id) {
  const base = import.meta.env.VITE_API_BASE || '/api/v1'
  return `${base}/attachments/${id}/preview`
}

/** 删除附件（管理端，从档案/荣誉引用中移除并删除物理文件，留审计日志） */
export function deleteAttachment(id) {
  return http.delete(`/attachments/${id}`)
}
