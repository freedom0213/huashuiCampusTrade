import { onUnmounted, ref } from 'vue'

/* ==========================================================
   倒计时心跳（订单「待付款」用）

   🔴 口径：剩余时间 = 后端给的 `remainSeconds` − **本地已流逝秒数**，
      而不是「每秒把 remainSeconds 减 1」。

   为什么必须这样：手机锁屏 / 切到别的标签页 / 切到别的 App 时，
   浏览器会把 setInterval 节流到每分钟几次甚至完全暂停。
   自己减 1 的写法会**越走越慢** —— 用户看到还剩 25 分钟，实际已经过了 3 分钟，
   然后订单被后端扫描任务取消，用户一脸懵（「不是还有 25 分钟吗」）。
   用「时间基线相减」则天然与真实时钟对齐：被节流只会让**刷新变稀疏**，
   一旦恢复前台立刻追上真实值，永远算不错。

   ⚠️ 必须在组件 setup() 里调用（onUnmounted 依赖组件实例）。
   ========================================================== */

export function useCountdown() {
  /** 自 start() 起本地已流逝的整秒数 */
  const elapsed = ref(0)

  let base = 0
  let timer = null

  function start() {
    stop()
    base = Date.now()
    elapsed.value = 0
    timer = setInterval(() => {
      elapsed.value = Math.floor((Date.now() - base) / 1000)
    }, 1000)
  }

  function stop() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  /**
   * 某条订单此刻的剩余秒数（已归零则为 0）。
   * `remainSeconds` 由后端返回，只在「待付款」时有值，其余为 0。
   */
  function left(item) {
    const s = Number(item?.remainSeconds)
    if (!Number.isFinite(s) || s <= 0) return 0
    return Math.max(0, Math.floor(s) - elapsed.value)
  }

  onUnmounted(stop)

  return { elapsed, start, stop, left }
}
