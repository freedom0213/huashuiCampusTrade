import axios from 'axios'

/* ==========================================================
   统一请求层
   约定（见 docs/03-前端接口核对清单.md）：
   1. baseURL = /api，开发环境由 Vite 代理到网关 6001
   2. 请求头 Authorization: Bearer <token>
   3. 后端统一响应体 Result：{ code, message, data }，字段名是 message（不是 msg）
   4. code === 200 视为成功，拦截器直接返回 data；其余一律 reject(Error)，
      Error.message 就是后端给用户看的中文文案，调用方直接展示即可
   5. ⚠️ 网关 allow-credentials=false，所以 withCredentials 必须为 false
      （token 走请求头，不依赖 Cookie）
   6. ⚠️ 所有 Long 已被后端序列化成字符串，前端一律按 string 处理，不要 parseInt
   ========================================================== */

const TOKEN_KEY = 'huashui_token'
const USER_KEY = 'huashui_user'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export function getStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export function setStoredUser(user) {
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
  else localStorage.removeItem(USER_KEY)
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

/* 401 的唯一出口：由 main.js 注入「清状态 + 跳登录」，避免在这里 import router 造成循环依赖 */
let unauthorizedHandler = null
export function onUnauthorized(handler) {
  unauthorizedHandler = handler
}
function fireUnauthorized() {
  clearAuth()
  if (unauthorizedHandler) unauthorizedHandler()
}

const service = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: false
})

service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) config.headers.Authorization = `Bearer ${token}`
    return config
  },
  (error) => Promise.reject(error)
)

service.interceptors.response.use(
  (response) => {
    const body = response.data

    // 非统一响应体（理论上不会出现）直接返回，避免误伤
    if (!body || typeof body !== 'object' || !('code' in body)) return body

    if (body.code === 200) return body.data

    if (body.code === 401) fireUnauthorized()

    const error = new Error(body.message || '请求失败')
    error.code = body.code
    return Promise.reject(error)
  },
  (error) => {
    const status = error.response?.status
    const body = error.response?.data

    /* 文案优先级：
       ① 响应体里的 message（后端 Result，或 vite 代理错误处理给出的结构化原因，
          如「后端服务未启动或不可达」——比笼统的「系统繁忙」有用得多）
       ② 按 HTTP 状态码给默认文案 */
    let message = ''
    if (body && typeof body === 'object' && body.message) message = body.message

    if (status === 401) {
      fireUnauthorized()
      message = message || '登录已过期，请重新登录'
    } else if (status === 403) {
      message = message || '没有操作权限'
    } else if (status === 404) {
      message = message || '请求的资源不存在'
    } else if (status === 405) {
      message = message || '请求方法不支持'
    } else if (status >= 500) {
      message = message || (status === 503 ? '服务暂不可用，请稍后重试' : '系统繁忙，请稍后重试')
    } else if (error.code === 'ECONNABORTED') {
      message = '请求超时，请检查网络'
    } else if (!status) {
      // 请求根本没发出去 / 没收到响应（断网、DNS 失败等）
      message = '无法连接服务器，请检查网络'
    }

    const e = new Error(message)
    e.code = status
    return Promise.reject(e)
  }
)

export default service
