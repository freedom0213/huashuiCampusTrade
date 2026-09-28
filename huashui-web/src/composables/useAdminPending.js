import { ref } from 'vue'
import { getAuditStats } from '@/api/admin'

/* ==========================================================
   管理端「待审核」徽章 —— 模块级共享状态 + 轮询

   需求（2026-09-28 用户提）：用户发布商品后，管理端要能看到提醒。
   实现选择「侧边栏数字徽章 + 定时轮询」：
   · 数据源就是已有的 GET /product/admin/audit/stats，零后端改动
   · 模块级 ref（与 useNotice 同风格）→ AdminLayout 显示、AuditView 操作后刷新，天然同步
   · 管理端是 PC 长开页，30s 轮询足够；页面卸载即停（stop）

   ⚠️ 轮询失败一律静默：它只是提醒，不该影响后台任何操作。
   ⚠️ 未登录 / 非管理员时 stats 会 403，pending 保持 0（不显示徽章），符合预期。
   ========================================================== */

const pending = ref(0)
let timer = 0

/** 拉一次待审核数（静默失败） */
export async function refreshAdminPending() {
  try {
    const s = await getAuditStats()
    pending.value = Number(s?.pending) || 0
  } catch {
    /* 静默：提醒功能失败不影响后台操作 */
  }
}

/** 进入管理端时启动轮询 */
export function startAdminPendingPolling(intervalMs = 30000) {
  refreshAdminPending()
  stopAdminPendingPolling()
  timer = window.setInterval(refreshAdminPending, intervalMs)
}

/** 离开管理端时停止（不清零，下次进来会立刻刷新） */
export function stopAdminPendingPolling() {
  if (timer) {
    window.clearInterval(timer)
    timer = 0
  }
}

export function useAdminPending() {
  return { pending }
}
