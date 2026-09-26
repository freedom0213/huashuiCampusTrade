import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { onUnauthorized } from './api/request'
import { useUserStore } from './stores/user'
import './styles/base.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

/* 401 的唯一出口：清登录态 + 跳登录，并带上回跳地址。
   放在这里而不是 request.js 里，是为了避免「请求层 → router → store」的循环依赖。 */
onUnauthorized(() => {
  const user = useUserStore(pinia)
  user.logout()
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    router.replace({ path: '/login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
