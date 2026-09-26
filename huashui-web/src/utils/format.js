/* 展示层格式化工具 */

/** 价格：¥2350 / ¥18.5（整数不补小数位，非整数保留 2 位） */
export function formatPrice(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '0'
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

/** 金额拆成「符号 + 数字」，便于把 ¥ 做小一号 */
export function splitPrice(value) {
  return { symbol: '¥', amount: formatPrice(value) }
}

/** 「龙子湖 · 第二食堂」→ 短地点（卡片上用），取地标部分 */
export function shortPlace(tradePlace) {
  if (!tradePlace) return ''
  const parts = String(tradePlace).split('·')
  return parts.length > 1 ? parts[parts.length - 1].trim() : tradePlace
}

/** 相对时间：刚刚 / 5 分钟前 / 3 小时前 / 昨天 / 09-20 */
export function fromNow(time) {
  if (!time) return ''
  const t = parseTime(time)
  if (!t) return ''
  const diff = Date.now() - t
  if (diff < 60 * 1000) return '刚刚'
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / 3600000)} 小时前`
  if (diff < 48 * 60 * 60 * 1000) return '昨天'
  const d = new Date(t)
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 完整时间：2026-09-26 14:30:52 */
export function formatTime(time, withSeconds = false) {
  const t = parseTime(time)
  if (!t) return ''
  const d = new Date(t)
  const base = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(
    d.getHours()
  )}:${pad(d.getMinutes())}`
  return withSeconds ? `${base}:${pad(d.getSeconds())}` : base
}

/**
 * 倒计时：秒数 → mm:ss（超过 1 小时显示 hh:mm:ss）
 * remainSeconds 是数字类型（后端刻意用 Integer，避免被 Long→String 规则波及）
 */
export function formatCountdown(seconds) {
  const s = Math.max(0, Math.floor(Number(seconds) || 0))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(sec)}` : `${pad(m)}:${pad(sec)}`
}

/** 大数字友好显示：1799 → 1.8k */
export function formatCount(n) {
  const num = Number(n) || 0
  return num >= 1000 ? `${(num / 1000).toFixed(1)}k` : String(num)
}

/** 图片兜底：加载失败时回落为渐变占位块（由 CSS 背景承担，这里只清空 src） */
export function onImgError(e) {
  e.target.style.visibility = 'hidden'
}

function pad(n) {
  return String(n).padStart(2, '0')
}

function parseTime(time) {
  if (typeof time === 'number') return time
  // 后端格式 "2026-09-26T14:30:52"：iOS Safari 对空格分隔不支持，这里统一替换
  const t = Date.parse(String(time).replace(' ', 'T'))
  return Number.isNaN(t) ? 0 : t
}
