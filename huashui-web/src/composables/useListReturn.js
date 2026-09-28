import { nextTick, onActivated, onDeactivated, ref } from 'vue'
import { clearEnterFrom, consumeReturn, restoreScroll } from '@/utils/flip'

/* ==========================================================
   列表页 × KeepAlive 的「返回恢复」逻辑（HomeView / SearchView 共用）

   背景：列表页被 KeepAlive 缓存后，从详情返回时组件实例与数据**原样复活**，
   不再重新挂载、不再重新请求 —— 这是消掉「返回动画结束后卡一下才出列表」的关键。
   KeepAlive 下 onMounted 只跑一次，返回相关的恢复逻辑必须挂在 onActivated。

   做三件事：
   1. FLIP 返回（详情页缩回动画后回来）：还原进入时的滚动位置，消费掉返回标记
   2. 普通 activate（如 首页→我的→返回首页）：还原离开时的滚动位置（keep-alive
      恢复时 DOM 重建，scrollTop 会归零，不还就跳回顶部）
   3. 统一在消费后 clearEnterFrom()：FLIP 记录的生命周期严格限定在
      「点击卡片 → 详情 → 返回该列表」一条链内，防止残留导致误播动画
   ========================================================== */

export function useListReturn(scrollerEl) {
  /** true 时列表页给入场动画加 animation:none（模板绑定 :class="{ restoring }"） */
  const restoring = ref(false)

  let lastTop = 0
  /** 首次激活（= 首次进入本页）允许播卡片入场动画；之后一律抑制 */
  let activatedOnce = false

  onDeactivated(() => {
    lastTop = scrollerEl.value?.scrollTop || 0
  })

  onActivated(async () => {
    /* 🔴 抑制条件与「用户怎么返回」解耦。
       曾经的写法是「只在 FLIP 返回标记存在时抑制」，而那个标记只由详情页的
       返回按钮设置 —— 用户用浏览器返回键 / 手势返回时标记不存在，
       卡片上场动画就会完整重播（表现：所有卡片又弹一遍，像刷新了页面）。
       叠加 KeepAlive 后更明显：DOM 被存起来再插回文档树时，
       浏览器会从头重播 CSS 动画（与 display:none 再显示同源）。
       → 正确语义：入场动画只属于「首次进入本页」，此后任何重新激活都不该重播。 */
    if (activatedOnce) {
      /* 🔴 抑制一旦生效就**永久保持**，绝不"过一会儿移除"。
         踩过的坑：曾用 `setTimeout(() => restoring = false, 900)` 复位，
         结果是 —— 移除 class 会让 `.pcard` 的 animation 从 `none` 变回 `rise`，
         浏览器视其为一次新动画、从头播放，用户看到「返回首页静等约 2 秒后
         所有卡片又弹一遍」。CSS 动画属性来回切换本身就会重启动画，
         所以抑制只能是单向的（本组件生命周期内不再具备入场动画）。 */
      restoring.value = true

      if (consumeReturn()) {
        // FLIP 缩回动画结束后的返回：按进入时记录的位置还原
        await nextTick()
        restoreScroll(scrollerEl.value)
        clearEnterFrom()
      } else if (lastTop) {
        // 普通返回（浏览器返回键 / 从别的页面切回）：DOM 重建后 scrollTop 归零，还原离开时的位置
        await nextTick()
        if (scrollerEl.value) scrollerEl.value.scrollTop = lastTop
      }
    }
    activatedOnce = true
  })

  return { restoring }
}
