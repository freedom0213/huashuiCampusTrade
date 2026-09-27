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
   7. 🔴 **失败有两条通道，必须都处理**：
      ① **业务码** —— HTTP 200 + `Result{code != 200}`，在成功回调里按 `body.code` 判；
      ② **HTTP 状态码** —— 网关/框架直接拒绝，在错误回调里按 `status` 翻译。
         🔴 据后端口径（2026-09-27），**本项目会出现非 2xx 的只有两种**：
         `429`（网关限流）与 `503`（网关下游不可用），**且 body 仍是统一 Result**，
         所以 ① 的 message 优先级最高；409/423 之类的业务冲突**不会出现**
         （全走 HTTP 200 + 业务码，如 30002 被抢、30006 商品服务不可用）。
      ⚠️ 判「是否处于错误分支」不要只看 ①：只覆盖 ① 的话，
      ② 类失败会**静默地拿到一个空原因**（`new Error('')`），
      toast 只剩标题 —— 用户看不出该怎么办。
      ⇒ 错误回调末尾**必须有一句兜底文案**，把"漏枚举某个状态码"从静默变成可容忍。
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
       ② 按 HTTP 状态码给默认文案
       ③ 🔴 兜底一句话（见函数末尾）——保证 `！` 永远有原因 */
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
    } else if (status === 429) {
      /* 🔴 限流（阶段 11 Sentinel）：这是**跨端约定**，务必保留。
         限流被拒时后端返回的是 **HTTP 429**（body 仍是 Result{code:429,message}）——
         也就是说它不走上面「成功回调里按 body.code 判断」那条路，只能在这一层翻译。
         若这里不处理：429 既不满足 401/403/404/405，也不满足 `status >= 500`，
         `message` 会保持空串 → toast 只剩「登录失败」而没有原因，
         用户不知道该重试还是该放弃 —— 直接违反「`！` 必须写出失败原因」的口径。
         文案口径（2026-09-27 后端统一）：**「操作过于频繁，请稍后再试」**。
         正常情况下 body.message 就是这句（优先用 body）；下面这句兜底只在
         body 不是 Result（如代理/框架拦下、拿不到包体）时生效，措辞保持一致。 */
      message = message || '操作过于频繁，请稍后再试'
    } else if (status >= 500) {
      message = message || (status === 503 ? '服务暂不可用，请稍后重试' : '系统繁忙，请稍后重试')
    } else if (error.code === 'ECONNABORTED') {
      message = '请求超时，请检查网络'
    } else if (!status) {
      // 请求根本没发出去 / 没收到响应（断网、DNS 失败等）
      message = '无法连接服务器，请检查网络'
    }

    /* 🔴 兜底：以上都没命中（400 / 409 / 418 … 任何没枚举过的状态码）时也必须给出一句话。
       否则 `new Error('')` → toast 的 `！` 只有标题、没有原因，
       而「带原因的失败提示」是我们对用户的承诺（见 docs/02-前端设计V1.md §3.5.1）。
       加这一条不只是为了 429：它把「漏枚举某个状态码」这件事从**静默**变成**可容忍**。 */
    if (!message) message = '请求失败，请稍后重试'

    const e = new Error(message)
    e.code = status
    return Promise.reject(e)
  }
)

export default service
