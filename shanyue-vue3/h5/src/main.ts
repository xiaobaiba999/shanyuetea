import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/index.css'

// Vant 按需样式由 unplugin-vue-components(VantResolver) 处理模板组件；
// 函数式调用的组件需手动引入样式
import 'vant/es/toast/style'
import 'vant/es/dialog/style'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
