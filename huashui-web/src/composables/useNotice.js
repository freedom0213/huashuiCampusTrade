import { ref } from 'vue'
import * as productApi from '@/api/product'
import * as orderApi from '@/api/order'
import { getToken } from '@/api/request'
import { PRODUCT_STATUS, ORDER_STATUS } from '@/constants/enums'
import { formatPrice } from '@/utils/format'

/* ==========================================================
   通知中心 + 通知红点（**聚合视图，不建表**，见 docs/02-前端设计V1.md §2.4）

   两条来源，两次请求：
     ① GET /api/product/mine?size=50         → 审核状态（待审核 / 审核通过 / 审核未通过）
     ② GET /api/order/mine?role=all&size=50  → 订单状态（新订单 / 待付款 / 交易完成）

   🔴 红点口径（2026-09-27 统一，**与块 2 的旧口径不同**）：
      旧口径是「业务状态」——我的发布含待审核/已驳回，或我的订单含待付款。它有个副作用：
      红点会为一个「通知中心里根本不存在的事件」点亮（如商品在等审核），
      用户点进去看不到对应内容。
      现改为 **「有未读通知」**：与列表共用同一份 `buildNotices`，语义也和设计稿一致
      （设计稿只在列表项上画未读小圆点），且让右上角「全部已读」真正生效。

   🔴 已读机制（2026-09-28 改，**两级**）：
      ① **单条已读**：点开某条通知 → 把它的 `key` 记进 localStorage 的已读集合
         （`huashui_notice_read_ids`）。这样「点开一条、只有这一条变已读」成立。
      ② **全部已读**：把**时间水位线**（`huashui_notice_read_at`）推到此刻 ——
         一次覆盖当前所有条目，同时清空已读集合（已无用）。
      判定：`时间晚于水位线 && 不在已读集合里` → 未读。
      ⚠️ 为什么不能只用水位线（旧实现）：水位线是「全局单调阈值」，
      点开任意一条都只能把水位线推到该条时间，会让比它早的所有通知一起变已读，
      或者（若推到现在）等于全部已读 —— 无法表达「只读这一条」（实测用户报的正是这个）。

   ⚠️ 聚合视图的固有代价（设计已接受）：通知时间只能是业务字段时间
      （商品用 publishTime、订单用 createTime/finishTime），
      所以「审核通过」的时间实际上是**提交时间**而非审核通过时间 —— 后端没有审核时间字段。
   ========================================================== */

const READ_AT_KEY = 'huashui_notice_read_at'
const READ_IDS_KEY = 'huashui_notice_read_ids'

/** 已读 key 集合的内存缓存（避免在 filter 里对每条反复 JSON.parse） */
let readIdsCache = null

function loadReadIds() {
  if (readIdsCache) return readIdsCache
  try {
    readIdsCache = new Set(JSON.parse(localStorage.getItem(READ_IDS_KEY) || '[]'))
  } catch {
    readIdsCache = new Set()
  }
  return readIdsCache
}

function persistReadIds() {
  let arr = Array.from(readIdsCache)
  // 上限 300：通知 key 随商品/订单持续增长，只留最近的，防 localStorage 无限膨胀
  if (arr.length > 300) {
    readIdsCache = new Set(arr.slice(-300))
    arr = Array.from(readIdsCache)
  }
  localStorage.setItem(READ_IDS_KEY, JSON.stringify(arr))
}

const hasNotice = ref(false)
const checked = ref(false)
const unreadCount = ref(0)
let checking = false

function ts(time) {
  if (!time) return 0
  if (typeof time === 'number') return time
  // 后端格式 "2026-09-26 14:30:52"：iOS Safari 对空格分隔不支持，统一换成 T
  const t = Date.parse(String(time).replace(' ', 'T'))
  return Number.isNaN(t) ? 0 : t
}

function getReadAt() {
  return Number(localStorage.getItem(READ_AT_KEY) || 0)
}

/**
 * 把两份列表拼成通知流（纯函数，便于单测与复用）。
 * 每条：{ key, icon, tone, title, body, time, to }
 *   icon  check | doc | bang | clock
 *   tone  ok | pink | err | wait | dead   （只影响图标底色/颜色）
 */
export function buildNotices(products = [], orders = []) {
  const list = []

  products.forEach((p) => {
    const base = { key: `p-${p.id}-${p.status}`, time: p.publishTime }
    if (p.status === PRODUCT_STATUS.PENDING_AUDIT) {
      list.push({
        ...base, icon: 'clock', tone: 'dead', to: '/user/products',
        title: '商品已提交，等待审核',
        body: `「${p.title}」已提交审核，通过后会自动上架。`
      })
    } else if (p.status === PRODUCT_STATUS.ON_SALE) {
      list.push({
        ...base, icon: 'check', tone: 'ok', to: `/product/${p.id}`,
        title: '商品审核通过',
        body: `「${p.title}」已通过审核，现已上架。`
      })
    } else if (p.status === PRODUCT_STATUS.REJECTED) {
      list.push({
        ...base, icon: 'bang', tone: 'err', to: '/user/products',
        title: '商品审核未通过',
        body: `「${p.title}」未通过审核：${p.rejectReason || '请修改后重新提交'}`
      })
    }
    // 已锁定(2) / 已售出(3) / 已下架(4) 由订单类通知覆盖，这里不重复出条目
  })

  orders.forEach((o) => {
    if (o.status === ORDER_STATUS.WAITING_PAY) {
      const base = { key: `o-${o.orderNo}-0`, time: o.createTime, to: `/order/${o.orderNo}` }
      if (o.role === 'SELLER') {
        list.push({
          ...base, icon: 'doc', tone: 'pink',
          title: '有人想买你的商品',
          body: `「${o.productTitle}」收到新订单，请在 30 分钟内与买家完成当面交易。`
        })
      } else {
        list.push({
          ...base, icon: 'clock', tone: 'wait',
          title: '你的订单等待付款',
          body: `「${o.productTitle}」已为你锁定，请在 30 分钟内与卖家当面完成交易并确认付款。`
        })
      }
    } else if (o.status === ORDER_STATUS.COMPLETED) {
      list.push({
        key: `o-${o.orderNo}-2`,
        time: o.finishTime || o.createTime,
        to: `/order/${o.orderNo}`,
        icon: 'check', tone: 'dead',
        title: '交易已完成',
        body: `「${o.productTitle}」的交易已完成，成交价 ¥${formatPrice(o.totalAmount ?? o.productPrice)}。`
      })
    }
  })

  return list.sort((a, b) => ts(b.time) - ts(a.time))
}

/** 通知是否未读：没被单独点开过，且晚于「全部已读」的水位线 */
export function isUnread(n) {
  const t = ts(n.time)
  // ⚠️ 时间字段解析失败（后端没给 / 格式异常）不能当成「已读」：
  //    水位线比较在 t=0 时恒为假，会让这类通知永远点不亮红点（静默漏提醒）。
  //    这时只按「是否被单独点开过」判定。
  if (!t) return !loadReadIds().has(n.key)
  return t > getReadAt() && !loadReadIds().has(n.key)
}

/**
 * 单条已读：点开某条通知时调用。
 * 只把它自己记进已读集合，**不动时间水位线** —— 这样「读一条」就只是那一条变已读。
 */
export function markRead(key) {
  if (!key) return
  const ids = loadReadIds()
  if (ids.has(key)) return
  ids.add(key)
  persistReadIds()
  // 红点（铃铛）要跟着变：让下一次 checkNotice 真正重算，而不是继续用缓存
  checked.value = false
}

/** 拉两份列表 → 通知流。通知中心与红点共用这一份口径。 */
export async function loadNotices() {
  const [products, orders] = await Promise.all([
    productApi.listMyProducts({ page: 1, size: 50 }),
    orderApi.listMyOrders({ role: 'all', page: 1, size: 50 })
  ])
  return buildNotices(products?.records || [], orders?.records || [])
}

/** 用一份已有的通知流刷新红点（避免重复请求；MineView 已有商品列表时可用） */
export function refreshNoticeFrom(notices) {
  const unread = notices.filter(isUnread).length
  unreadCount.value = unread
  hasNotice.value = unread > 0
  checked.value = true
}

/** 拉取并刷新红点（首页铃铛用） */
export async function checkNotice(force = false) {
  if (!getToken()) {
    hasNotice.value = false
    unreadCount.value = 0
    checked.value = true
    return false
  }
  if (checked.value && !force) return hasNotice.value
  if (checking) return hasNotice.value

  checking = true
  try {
    refreshNoticeFrom(await loadNotices())
  } catch {
    /* 拿不到就保持当前红点，不要把「网络失败」误报成「没有通知」 */
  } finally {
    checking = false
  }
  return hasNotice.value
}

/** 全部已读：水位线推到此刻（一次覆盖所有现存条目），并清空单条已读集合 */
export function markAllRead() {
  localStorage.setItem(READ_AT_KEY, String(Date.now()))
  readIdsCache = new Set()
  localStorage.removeItem(READ_IDS_KEY)
  hasNotice.value = false
  unreadCount.value = 0
  checked.value = true
}

/** 退出登录时清掉，避免下个账号看到上一个账号的红点 */
export function resetNotice() {
  hasNotice.value = false
  unreadCount.value = 0
  checked.value = false
  /* 🔴 水位线与已读集合都是**账号私有**的，必须一起清：
     否则换账号登录后，时间早于上个账号水位线的通知会被误判成「已读」，
     红点漏亮（不报错，只是少提醒）。 */
  localStorage.removeItem(READ_AT_KEY)
  localStorage.removeItem(READ_IDS_KEY)
  readIdsCache = null
}

export function useNotice() {
  return { hasNotice, unreadCount, checked, checkNotice, refreshNoticeFrom, resetNotice }
}
