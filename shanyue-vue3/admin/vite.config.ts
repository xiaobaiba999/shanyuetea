import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    // vue/vue-router/pinia API 自动按需导入 + Element Plus API（ElMessage 等）自动导入
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
      dts: 'src/auto-imports.d.ts',
    }),
    // Element Plus 组件按需自动引入（模板里直接写 el-xxx 即可）
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  css: {
    // 使用内联空 PostCSS 配置，避免 Vite 向上层目录搜索到无关的 postcss.config 导致构建失败
    postcss: { plugins: [] },
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      // ▼▼▼ 真实联调说明（重要）▼▼▼
      // 前端所有请求统一走 /api/** 前缀（不带 /admin 段），本代理与生产 nginx 反代同款映射：
      //   /api/employee/login  →  http://localhost:8080/admin/employee/login
      // 即把 /api 前缀重写为 /admin 后转发给苍穹外卖模式后端（Spring Boot，监听 8080）。
      // 使用：mock 默认已关闭（v1.5.2 起 src/utils/env.ts 缺省 false），dev server 起来即直接联调；
      // 若你在 .env 里设过 VITE_USE_MOCK=true 记得删掉。
      // 若你的 nginx 也已按此规则配置（location /api/ { proxy_pass http://localhost:8080/admin/; }），
      // 生产构建（VITE_USE_MOCK=false）无需任何额外改造。
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '/admin'),
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 1600,
  },
})
