/** 学院批量导入模块（API 规范第十一章）
 *  - 后端支持 xlsx / xls / csv（扩展名 + 内容嗅探双重校验）
 *  - 单次 ≤2000 行 / ≤10MB；坏行不阻断好行
 *  - 模板下载返回 xlsx（数据 sheet + 填写说明 sheet）
 */
import http from './request'

/** 模板下载（xlsx，浏览器自动保存为 xlsx 文件） */
export function getImportTemplate() {
  return http.get('/imports/template', { responseType: 'blob' })
}

/** 上传导入（xlsx/xls/csv ≤10MB、≤2000 行），返回 import_id 后轮询进度 */
export function createImport(file) {
  const form = new FormData()
  form.append('file', file)
  return http.post('/imports', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}

/** 导入任务列表（仅本人上传的任务，按创建时间倒序；用于历史任务面板） */
export function listImports() {
  return http.get('/imports')
}

/** 单个任务进度（status: processing / done；finished 时同时返回 filename/fileSize/createdAt/finishedAt） */
export function getImport(id) {
  return http.get(`/imports/${id}`)
}

/** 错误行报告（行号 + 学号 + 原因） */
export function getImportErrors(id) {
  return http.get(`/imports/${id}/errors`)
}

/** 解析一行错误列表 → CSV 文本（前端拼接，触发浏览器下载；不依赖后端） */
export function errorsToCsv(errors, headers = ['row', 'student_id', 'reason']) {
  const esc = (v) => {
    const s = v == null ? '' : String(v)
    // RFC 4180：含 , " \n 时加双引号包裹，并把内部 " 转 ""
    return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  const rows = [headers.join(',')]
  for (const e of errors || []) rows.push(headers.map((h) => esc(e[h])).join(','))
  // UTF-8 BOM：Excel 打开不乱码
  return '\uFEFF' + rows.join('\r\n')
}

/** 触发浏览器下载（错误报告 → errors_YYYYMMDD_HHmmss.csv） */
export function downloadErrorsCsv(errors) {
  const csv = errorsToCsv(errors)
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  const ts = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const stamp = `${ts.getFullYear()}${pad(ts.getMonth() + 1)}${pad(ts.getDate())}_${pad(ts.getHours())}${pad(ts.getMinutes())}${pad(ts.getSeconds())}`
  a.href = url
  a.download = `import_errors_${stamp}.csv`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}