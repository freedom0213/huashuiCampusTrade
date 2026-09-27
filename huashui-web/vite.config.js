import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 代理错误处理：后端没启动时，vite 默认回 500 + 一段 HTML，
 * 浏览器端只能笼统地显示「系统繁忙」，让人误以为平台出了故障。
 * 这里换成结构化的 503 响应，让前端 toast 能说出真实原因。
 */
function onProxyError(target) {
  return (proxy) => {
    proxy.on('error', (err, _req, res) => {
      if (res && typeof res.writeHead === 'function' && !res.headersSent) {
        res.writeHead(503, { 'Content-Type': 'application/json; charset=utf-8' })
        res.end(
          JSON.stringify({
            code: 503,
            message: `后端服务未启动或不可达（${target}）。请先启动基础设施容器与 4 个 Java 服务`
          })
        )
      } else {
        console.warn(`[proxy error] ${target}: ${err.code || err.message}`)
      }
    })
  }
}

/* 开发环境一律代理到网关 6001，不直连 6002/6003/6004。
   这样前端只认「网关」这一个入口，与生产（Nginx → 网关）保持一致。

   🔴 `HUASHUI_API_TARGET` 可以让代理指向别的端口，**专为「后端未启动时用临时桩服务验证」准备**：
   桩服务放在 6001 会**挡住后端会话的真实网关**（他们起不来网关、只能换端口）。
   所以用桩验证时请：
     ① 把桩起在别的端口（如 6009），**不要占 6001**
     ② `HUASHUI_API_TARGET=http://127.0.0.1:6009 npm run dev`
   默认值仍是 6001，日常开发不受影响。 */
const API_TARGET = process.env.HUASHUI_API_TARGET || 'http://127.0.0.1:6001'
const API_TARGET_LABEL = API_TARGET.replace(/^https?:\/\//, '')

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
        target: API_TARGET,
        changeOrigin: true,
        configure: onProxyError(`后端 ${API_TARGET_LABEL}`)
      },
      // 上传的图片由 product-service 直接映射在 /uploads/**，
      // 网关这条路由不做 StripPrefix，因此这里也不能改写路径。
      '/uploads': {
        target: API_TARGET,
        changeOrigin: true,
        configure: onProxyError(`后端 ${API_TARGET_LABEL}`)
      }
    }
  }
})

