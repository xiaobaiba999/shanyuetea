import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from 'unplugin-vue-components/resolvers'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    // Vant 按需引入：模板里直接写 <van-xxx>，组件与样式自动按需加载
    Components({
      resolvers: [VantResolver()],
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
    // C 端 H5 使用 5174，与管理后台（5173）可同时开启
    port: 5174,
    host: true,
    // 联调模式（VITE_USE_MOCK=false 时）：/api 代理到本地 Spring Boot，并去掉 /api 前缀
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
