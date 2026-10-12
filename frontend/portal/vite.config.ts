import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [vue()],
    server: {
      // 监听全部网卡（IPv4 + IPv6），避免 localhost 仅解析到 ::1 时浏览器连不上
      host: true,
      port: 5175,
      open: false,
      // 联调时将 /api 转发到后端服务（默认本机 8080，可用 VITE_PROXY_TARGET 覆盖）
      proxy: {
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true
        }
      },
      // 忽略 md 文档，避免文件被占用时 watcher 抛 EBUSY 崩溃
      watch: { ignored: ['**/*.md'] }
    },
    build: {
      // ============================================================================
      // 关键分包策略（解决"点击后空白需要手动刷新"问题）
      // ----------------------------------------------------------------------------
      // 背景：vue-router 的动态 import() 把每个页面切成独立 chunk。dev 下浏览器
      //       首次访问某 chunk 时 vite 要现编译（300ms~1s 慢），用户看到的是空白。
      // 方案：把 vendor（vue/vue-router/echarts/pdfjs）+ 共享组件（mobile/* profile/*）
      //       拆到独立 chunk 并通过 preload 预热；浏览器访问 /m 时能命中缓存的 vendor，
      //       后续跳任何页面都不再重新解析依赖。
      // ============================================================================
      rollupOptions: {
        output: {
          manualChunks: {
            // 第三方核心依赖（与业务无关，单独缓存）
            'vendor-vue': ['vue', 'vue-router'],
            // ECharts（Dashboard 用，体积大独立）
            'vendor-echarts': ['echarts'],
            // PDF 预览（详情页用，独立）
            'vendor-pdf': ['pdfjs-dist'],
            // 个人中心共用组件（/profile* 共用）
            'shared-profile': [
              './src/components/profile/ProfileShell.vue',
              './src/components/profile/PageHeader.vue',
              './src/components/profile/BackToProfile.vue',
              './src/components/profile/ConfirmDialog.vue',
              './src/components/profile/ToastBar.vue',
              './src/components/profile/StatusTag.vue',
              './src/components/profile/shared.ts'
            ]
          }
        }
      },
      // chunk 大小警告阈值（H5 上主要业务页都不会超）
      chunkSizeWarningLimit: 800
    },
    optimizeDeps: {
      // ============================================================================
      // 依赖预构建：vue-router 必须预构建（HMR 优化），避免每次访问都重新编译
      // ============================================================================
      include: [
        'vue',
        'vue-router',
        'echarts',
        'pdfjs-dist'
      ]
    }
  };
});