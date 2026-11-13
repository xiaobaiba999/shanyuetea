import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

// v-loading 指令按需注册
import { ElLoading } from 'element-plus'
// ElMessage / ElMessageBox 在工具层显式引入，这里补齐对应样式
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/loading/style/css'

import './styles/element-overrides.css'
import './styles/global.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElLoading)

app.mount('#app')
