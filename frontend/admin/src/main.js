import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import router from './router'
import { onForceLogout } from '@/api/request'
import 'element-plus/dist/index.css'
import './styles/index.css'

const app = createApp(App)

// 注册全部图标组件
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')

// 令牌失效且刷新失败：统一跳回登录页
onForceLogout(() => {
  router.replace({ name: 'Login' })
})
