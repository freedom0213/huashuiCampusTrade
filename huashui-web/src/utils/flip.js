/* ==========================================================
   共享元素转场（FLIP）—— 「商品卡片 ⇄ 商品详情」的进出场动画

   为什么手写 FLIP 而不用 View Transitions API（见 docs/02-前端设计V1.md §3.5）：
   后者的转场图层挂在 `document` 上，**不会被应用外壳的 `overflow:hidden` 裁剪**，
   放大时会「飞出屏幕」。手写 FLIP 只在两个真实元素之间做 transform，天然被裁剪。

   原理（FLIP = First / Last / Invert / Play）：
     First  记录起始元素的位置与尺寸（列表页的卡片缩略图）
     Last   记录结束元素的位置与尺寸（详情页的大图）
     Invert 把结束元素「反算」回起始位置：translate(dx,dy) + scale(sx,sy)，transform-origin 用 0 0
     Play   下一帧清掉 transform，让浏览器补间到真实位置

   ⚠️ scale 会同时缩放 border-radius，所以起始圆角要写成 `15 / sx` ——
      这样「视觉圆角」才与未被缩放的卡片一致，否则动画第一帧的圆角会明显偏小。

   🔴 本模块只做「尽力而为」的动画：元素找不到、尺寸为 0、记录过期，一律静默跳过，
      绝不抛异常、绝不阻塞路由跳转。动画是锦上添花，页面可用性优先。
   ========================================================== */

/** 卡片缩略图的圆角，与 ProductCard 的 .pcard border-radius 保持一致 */
const CARD_RADIUS = 15

/* 一次进入的现场：列表页点击卡片时记录，详情页用它做放大动画，
   返回时再用同一个 rect 把大图缩回去（所以不能在详情页挂载后就清空）。 */
let enterFrom = null
/** 是否由「详情页返回按钮」发起返回 —— 只有它才播缩回动画 */
let returnPending = false

/**
 * 列表页点击卡片时调用
 * @param {object} p
 * @param {string} p.productId
 * @param {DOMRect} p.rect      卡片缩略图的视口矩形
 * @param {number} [p.scrollTop] 列表滚动位置，返回时用来还原（否则缩回的位置会错位）
 */
export function setEnterFrom({ productId, rect, scrollTop = 0 }) {
  if (!productId || !rect || !rect.width || !rect.height) {
    enterFrom = null
    return
  }
  enterFrom = {
    productId: String(productId),
    rect: {
      left: rect.left,
      top: rect.top,
      width: rect.width,
      height: rect.height
    },
    scrollTop
  }
}

/** 详情页取出本次进入的现场；productId 不匹配（如直接粘贴链接打开）则视为没有 */
export function getEnterFrom(productId) {
  if (!enterFrom || enterFrom.productId !== String(productId)) return null
  return enterFrom
}

/**
 * 列表页点击卡片时调用：从卡片根元素找到缩略图并记录现场。
 * 三个列表页（首页 / 搜索 / 卖家主页）共用这一份，避免各写一遍。
 * @param {HTMLElement} cardEl 卡片根元素（article.pcard）
 * @param {string} productId
 * @param {HTMLElement} [scrollerEl] 列表滚动容器，用于记录 scrollTop
 * @returns {boolean} 是否成功建立现场；false 时只是不播动画，不影响跳转
 */
export function captureCard(cardEl, productId, scrollerEl) {
  const thumb = cardEl?.querySelector?.('.thumb')
  if (!thumb) return false
  setEnterFrom({
    productId,
    rect: thumb.getBoundingClientRect(),
    scrollTop: scrollerEl?.scrollTop || 0
  })
  return true
}

/** 详情页返回按钮：标记「这次返回要播缩回动画」 */
export function markReturn() {
  returnPending = true
}

/** 列表页消费这个标记（用完即清，避免普通导航被误播动画） */
export function consumeReturn() {
  const v = returnPending
  returnPending = false
  return v
}

/** 详情页卸载时判断：是否正处于「返回列表播缩回动画」的流程中 */
export function isReturnPending() {
  return returnPending
}

/**
 * 播放「卡片 → 详情大图」的放大动画
 * @param {HTMLElement} el 详情页的大图容器
 * @param {object} from setEnterFrom 记录的对象
 */
export function playEnter(el, from) {
  if (!el || !from) return
  const first = from.rect
  const last = el.getBoundingClientRect()
  if (!last.width || !last.height) return

  const dx = first.left - last.left
  const dy = first.top - last.top
  const sx = first.width / last.width
  const sy = first.height / last.height

  el.style.transition = 'none'
  el.style.transformOrigin = '0 0'
  el.style.transform = `translate(${dx}px, ${dy}px) scale(${sx}, ${sy})`
  el.style.borderRadius = `${CARD_RADIUS / sx}px`

  requestAnimationFrame(() => {
    el.style.transition =
      'transform 460ms cubic-bezier(.22,1,.36,1), border-radius 460ms cubic-bezier(.22,1,.36,1)'
    el.style.transform = 'none'
    el.style.borderRadius = '0'
    // 动画结束后清掉行内样式，避免影响后续轮播的布局测量
    setTimeout(() => {
      el.style.transition = ''
      el.style.transformOrigin = ''
      el.style.borderRadius = ''
    }, 520)
  })
}

/**
 * 播放「详情大图 → 卡片」的缩回动画
 * @param {HTMLElement} el 详情页的大图容器
 * @param {object} from setEnterFrom 记录的对象
 * @returns {Promise<void>} 动画播放完毕的 Promise（调用方据此决定何时 router.back()）
 */
export function playReturn(el, from) {
  return new Promise((resolve) => {
    if (!el || !from) return resolve()
    const last = el.getBoundingClientRect()
    const first = from.rect
    if (!last.width || !last.height) return resolve()

    const dx = first.left - last.left
    const dy = first.top - last.top
    const sx = first.width / last.width
    const sy = first.height / last.height

    // 返回曲线与进入不同：进入用「快出慢入」，返回用对称的 ease-in-out（见设计文档 §3.5）
    el.style.transition = 'none'
    el.style.transformOrigin = '0 0'
    el.style.transform = 'none'
    el.style.borderRadius = '0'

    requestAnimationFrame(() => {
      el.style.transition =
        'transform 420ms cubic-bezier(.4,0,.6,1), border-radius 420ms cubic-bezier(.4,0,.6,1)'
      el.style.transform = `translate(${dx}px, ${dy}px) scale(${sx}, ${sy})`
      el.style.borderRadius = `${CARD_RADIUS / sx}px`
      setTimeout(resolve, 420)
    })
  })
}

/**
 * 恢复列表页的滚动位置。
 * 🔴 必须在「数据渲染完成后」再调用（nextTick 之后），否则列表还没高度、scrollTop 会被吞掉。
 * 这不只是体验优化：返回时大图缩回的目标位置按「进入时的滚动位置」计算，
 * 不还原滚动位置的话，缩回点会与真实卡片错位。
 * @param {HTMLElement} scrollerEl 列表的滚动容器
 */
export function restoreScroll(scrollerEl) {
  if (!scrollerEl || !enterFrom || !enterFrom.scrollTop) return
  scrollerEl.scrollTop = enterFrom.scrollTop
}

/** 详情页卸载时清场，避免残留记录影响下一次普通导航 */
export function clearEnterFrom() {
  enterFrom = null
  returnPending = false
}

/* 「刚刚离开商品详情页」标记：由详情页卸载时无条件设置，被列表页消费。
   与 returnPending 的区别 —— 后者只有「点详情页返回按钮」才设置，
   而用户完全可能用浏览器返回键 / 手势返回，那条路径拿不到 returnPending。
   非缓存列表页（卖家主页 / 收藏）靠它判断「这次挂载是返回还是首次进入」，
   是「返回就不该重播卡片入场动画」的唯一可靠线索。 */
let leftDetail = false

/** 详情页卸载时调用（无条件） */
export function markLeftDetail() {
  leftDetail = true
}

/** 列表页挂载时消费（用完即清） */
export function consumeLeftDetail() {
  const v = leftDetail
  leftDetail = false
  return v
}
