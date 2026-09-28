import request from './request'

/* ==========================================================
   管理端 —— 对应 /api/product/admin/**
   契约：docs/13-商品审核与管理端接口设计.md（唯一契约）
   扩展：仓库根「管理端-后端接口扩展需求.md」（2026-09-28 用户拍板 A 方案）

   🔴 所有接口都需要管理员身份（t_user.role = 2）：
      服务层 UserContext.requireAdmin() 兜底，非管理员 HTTP 200 + code:403。
   🔴 所有 id 是字符串（跨端约定：Long 序列化为 string，不要 parseInt）。
   🔴 stats / status=1|4|all / keyword / sellerName / createdAt 属于**扩展契约**，
      后端就位前调用会失败 —— 调用方必须容错（统计失败静默降级，不阻塞列表）。
   ========================================================== */

/**
 * 审核列表
 * @param {object} params
 *   status: 0 待审核(默认) / 1 在售 / 4 已下架 / 5 已驳回 / all 全部（1/4/all 为扩展）
 *   current, size（≤50）
 *   keyword: 可选，商品标题 / 卖家模糊匹配（扩展）
 */
export const getAuditList = (params) => request.get('/product/admin/audit/list', { params })

/** 审核统计（扩展）：{ pending, onSale, rejected, offShelf } —— 四个状态的商品数 */
export const getAuditStats = () => request.get('/product/admin/audit/stats')

/** 审核通过：待审核(0) → 在售(1)，并补记 publish_time；20006=不在待审核状态 */
export const approveProduct = (id) => request.put(`/product/admin/${id}/approve`)

/** 审核驳回：待审核(0) → 已驳回(5)；reason 必填 ≤255 字（空 → 400） */
export const rejectProduct = (id, reason) => request.put(`/product/admin/${id}/reject`, { reason })

/** 强制下架：在售(1) → 已下架(4)；已锁定(2)/已售出(3) 拒绝（20006，在途交易保护） */
export const forceOffProduct = (id) => request.put(`/product/admin/${id}/force-off`)
