<script setup>
/**
 * Lucide 图标（经 better-icons CLI 取自 lucide 集，内联 currentColor 描边，随文字颜色）
 * 用法：<LucideIcon name="user" :size="16" />
 */
const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 16 },
  strokeWidth: { type: [Number, String], default: 2 }
})

const ICONS = {
  'user': '<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle fill="currentColor" cx="12" cy="7" r="4"/></g>',
  'pen-line': '<path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 21h8m.174-14.188a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"/>',
  'list': '<path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 5h.01M3 12h.01M3 19h.01M8 5h13M8 12h13M8 19h13"/>',
  'star': '<path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.12 2.12 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.12 2.12 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.12 2.12 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.12 2.12 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.12 2.12 0 0 0 1.597-1.16z"/>',
  'bar-chart-3': '<path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 3v18h18m-3-4V9m-5 8V5M8 17v-3"/>',
  'log-out': '<path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m16 17l5-5l-5-5m5 5H9m0 9H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>',
  'castle': '<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="M10 5V3m4 2V3m1 18v-3a3 3 0 0 0-6 0v3m9-18v8m0-6H6m16 6H2"/><path fill="currentColor" d="M22 9v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9m4-6v8"/></g>',
  'megaphone': '<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="M11 6a13 13 0 0 0 8.4-2.8A1 1 0 0 1 21 4v12a1 1 0 0 1-1.6.8A13 13 0 0 0 11 14H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z"/><path fill="currentColor" d="M6 14a12 12 0 0 0 2.4 7.2a2 2 0 0 0 3.2-2.4A8 8 0 0 1 10 14M8 6v8"/></g>',
  'trophy': '<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="M10 14.66V17a1 1 0 0 1-1 1a2 2 0 0 0-2 2v2m7-7.34V17a1 1 0 0 0 1 1a2 2 0 0 1 2 2v2m.916-12H19.5A2.5 2.5 0 0 0 22 7.5V5a1 1 0 0 0-1-1h-3M4 22h16"/><path fill="currentColor" d="M6 9a6 6 0 0 0 12 0V3a1 1 0 0 0-1-1H7a1 1 0 0 0-1 1z"/><path fill="currentColor" d="M6.084 10H4.5A2.5 2.5 0 0 1 2 7.5V5a1 1 0 0 1 1-1h3"/></g>',
  'users': '<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M16 3.128a4 4 0 0 1 0 7.744M22 21v-2a4 4 0 0 0-3-3.87"/><circle fill="currentColor" cx="9" cy="7" r="4"/></g>',
  'images': '<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="m22 11l-1.296-1.296a2.4 2.4 0 0 0-3.408 0L11 16"/><path fill="currentColor" d="M4 8a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2"/><circle cx="13" cy="7" r="1" fill="currentColor"/><rect fill="currentColor" width="14" height="14" x="8" y="2" rx="2"/></g>',
  'contact': '<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="M16 2v2M7 21v-2a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v2M8 2v2"/><circle fill="currentColor" cx="12" cy="10" r="3"/><rect fill="currentColor" width="18" height="18" x="3" y="3" rx="2"/></g>',
  'message-circle': '<path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092a10 10 0 1 0-4.777-4.719"/>',
  'camera': '<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z"/><circle fill="currentColor" cx="12" cy="13" r="3"/></g>',
  'paperclip': '<path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m16 6l-8.414 8.586a2 2 0 0 0 2.829 2.829l8.414-8.586a4 4 0 1 0-5.657-5.657l-8.379 8.551a6 6 0 1 0 8.485 8.485l8.379-8.551"/>',
  'file-text': '<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z"/><path fill="currentColor" d="M14 2v5a1 1 0 0 0 1 1h5M10 9H8m8 4H8m8 4H8"/></g>',
  'lock-keyhole': '<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><circle fill="currentColor" cx="12" cy="16" r="1"/><rect fill="currentColor" width="18" height="12" x="3" y="10" rx="2"/><path fill="currentColor" d="M7 10V7a5 5 0 0 1 10 0v3"/></g>',
  'loader-circle': '<path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 12a9 9 0 1 1-6.219-8.56"/>',
  'circle-check-big': '<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="M21.801 10A10 10 0 1 1 17 3.335"/><path fill="currentColor" d="m9 11l3 3L22 4"/></g>',
  'folder': '<path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"/>',
  'medal': '<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="M7.21 15L2.66 7.14a2 2 0 0 1 .13-2.2L4.4 2.8A2 2 0 0 1 6 2h12a2 2 0 0 1 1.6.8l1.6 2.14a2 2 0 0 1 .14 2.2L16.79 15M11 12L5.12 2.2M13 12l5.88-9.8M8 7h8"/><circle fill="currentColor" cx="12" cy="17" r="5"/><path fill="currentColor" d="M12 18v-2h-.5"/></g>',
  'trash-2': '<path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 11v6m4-6v6m5-11v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>',
  'search': '<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="m21 21l-4.34-4.34"/><circle fill="currentColor" cx="11" cy="11" r="8"/></g>',
  'check': '<path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 6L9 17l-5-5"/>',
  'award': '<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="m15.477 12.89l1.515 8.526a.5.5 0 0 1-.81.47l-3.58-2.687a1 1 0 0 0-1.197 0l-3.586 2.686a.5.5 0 0 1-.81-.469l1.514-8.526"/><circle fill="currentColor" cx="12" cy="8" r="6"/></g>',
}

const html = ICONS[props.name] || ''
</script>

<template>
  <svg
    xmlns="http://www.w3.org/2000/svg"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    style="flex-shrink: 0"
    v-html="html"
  />
</template>
