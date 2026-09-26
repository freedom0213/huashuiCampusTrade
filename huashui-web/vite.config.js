import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发环境一律代理到网关 6001，不直连 6002/6003/6004。
// 这样前端只认「网关」这一个入口，与生产（Nginx → 网关）保持一致。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:6001',
        changeOrigin: true
      },
      // 上传的图片由 product-service 直接映射在 /uploads/**，
      // 网关这条路由不做 StripPrefix，因此这里也不能改写路径。
      '/uploads': {
        target: 'http://127.0.0.1:6001',
        changeOrigin: true
      }
    }
  }
})
