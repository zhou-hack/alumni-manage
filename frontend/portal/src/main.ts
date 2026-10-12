// ============================================================================
// 应用入口：Vue 3 + vue-router
// ----------------------------------------------------------------------------
// 桌面端门户入口（移动端 H5 路由已下线，不再做 UA 自动分流）。
// ============================================================================
import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import './index.css';

/* ============================================================================
 * 调试面板：URL 加 ?diag=1 时在右下角弹出设备识别详情
 * ========================================================================== */
if (typeof window !== 'undefined') {
  const url = new URL(window.location.href);
  if (url.searchParams.get('diag') === '1') {
    import('./utils/device').then((m) => {
      const d = m.diagnose();
      const box = document.createElement('div');
      box.style.cssText =
        'position:fixed;right:8px;bottom:8px;max-width:92vw;padding:12px 14px;' +
        'background:rgba(16,42,92,0.95);color:#fff;font:13px/1.5 monospace;' +
        'border-radius:8px;box-shadow:0 4px 16px rgba(0,0,0,0.4);z-index:99999;' +
        'white-space:pre-wrap;word-break:break-all';
      box.textContent = JSON.stringify(d, null, 2);
      document.body.appendChild(box);
    });
  }
}

const app = createApp(App);
app.use(router);
app.mount('#root');