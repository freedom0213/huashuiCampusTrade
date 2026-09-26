import { ref } from 'vue'

/* ==========================================================
   全局反馈提示（Toast）
   规则（见 docs/02-前端设计V1.md §3.5.1）：
   · 只有两种形态：ok ✓ 绿色（正常流程的操作成功，**含「取消订单」**）、
     err ！ 橙色（仅异常，**必须写出失败原因**）
   · 刻意不设 ✗ —— 正常业务动作用红叉会被读成系统故障
   ========================================================== */

const state = ref({
  visible: false,
  type: 'ok',
  message: '',
  sub: ''
})

let timer = null

/** 成功：说出「哪个动作成功了」 */
export function toastOk(message, sub = '') {
  return show('ok', message, sub)
}

/** 异常：第一个参数是失败标题，第二个参数**必须**是失败原因 */
export function toastError(message, sub = '') {
  return show('err', message, sub)
}

/**
 * 从任意 Error 展示失败原因：后端 message 已是用户可读中文，直接展示
 * @param {Error} error 请求层 reject 出来的 Error（带 code）
 * @param {string} title 失败标题，如「下单失败」
 */
export function toastFromError(error, title = '操作失败') {
  const reason = error?.message || '网络异常，请稍后重试'
  return show('err', title, reason)
}

export function hideToast() {
  state.value.visible = false
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
}

function show(type, message, sub) {
  state.value = {
    visible: false,
    type,
    message: message || (type === 'ok' ? '操作成功' : '操作失败'),
    sub: sub || ''
  }
  if (timer) clearTimeout(timer)

  // 先关再开，保证连续触发时动画能重新播放
  requestAnimationFrame(() => {
    state.value.visible = true
    // 带补充说明时多看一会儿
    timer = setTimeout(() => {
      state.value.visible = false
      timer = null
    }, sub ? 2000 : 1600)
  })
}

export function useToast() {
  return { state, toastOk, toastError, toastFromError, hideToast }
}
