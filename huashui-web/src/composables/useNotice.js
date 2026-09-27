import { ref } from 'vue'
import * as productApi from '@/api/product'
import * as orderApi from '@/api/order'
import { getToken } from '@/api/request'

/**
 * 通知红点的共享状态
 *
 * 红点口径（纯前端聚合，不建表）：我的发布含「待审核(0)/已驳回(5)」或我的订单含「待付款(0)」。
 *
 * 为什么做成模块级状态：
 * - 首页顶栏铃铛与「我的」页的「通知中心」都要显示同一个红点，两处必须一致；
 * - 「我的」页本来就会拉一次 `listMyProducts({size:50})`，从中算红点是**零成本**的，
 *   所以它用 `setHasNotice()` 直接写入；首页没有这份数据，才走 `checkNotice()` 发 3 个轻请求（size=1）。
 * - 结果在整个页面会话内缓存，避免在首页与我的之间来回切换时重复请求。
 */

const hasNotice = ref(false)
const checked = ref(false)
let checking = false

export function setHasNotice(value) {
  hasNotice.value = !!value
  checked.value = true
}

export async function checkNotice(force = false) {
  if (!getToken()) {
    hasNotice.value = false
    checked.value = true
    return false
  }
  if (checked.value && !force) return hasNotice.value
  if (checking) return hasNotice.value

  checking = true
  try {
    const [pending, rejected, waitingPay] = await Promise.allSettled([
      productApi.listMyProducts({ status: 0, page: 1, size: 1 }),
      productApi.listMyProducts({ status: 5, page: 1, size: 1 }),
      orderApi.listMyOrders({ role: 'buyer', status: 0, page: 1, size: 1 })
    ])
    const gt = (r) => (r.status === 'fulfilled' ? (r.value.total || 0) > 0 : false)
    hasNotice.value = gt(pending) || gt(rejected) || gt(waitingPay)
    checked.value = true
  } finally {
    checking = false
  }
  return hasNotice.value
}

/** 退出登录时清掉，避免下个账号看到上一个账号的红点 */
export function resetNotice() {
  hasNotice.value = false
  checked.value = false
}

export function useNotice() {
  return { hasNotice, checked, checkNotice, setHasNotice, resetNotice }
}
