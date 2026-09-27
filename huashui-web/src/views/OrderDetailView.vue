<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ActionSheet from '@/components/ActionSheet.vue'
import { getOrderDetail, payOrder, completeOrder, cancelOrder } from '@/api/order'
import { getUserBrief, getContact } from '@/api/user'
import { ORDER_STATUS, ORDER_STATUS_LABEL } from '@/constants/enums'
import { formatPrice, formatTime, formatCountdown } from '@/utils/format'
import { toastOk, toastError, toastFromError } from '@/composables/useToast'
import { useCountdown } from '@/composables/useCountdown'

/* ==========================================================
   订单详情 /order/:orderNo —— 全站交互最重的页面（设计文档 §4.7）

   数据组装（后端 OrderVO 不做跨服务聚合，只有 buyerId / sellerId）：
     ① GET /api/order/detail/{orderNo}   订单本体（含商品快照、交易地点快照）
     ② GET /api/user/detail/{peerId}     对端昵称（游客可访问）
     ③ GET /api/user/{peerId}/contact    对端联系方式（需登录，手机号全站唯一出口）
   ① 失败 → 整页错误态；②③ 失败 → 只少一段信息，不让整页挂掉。
   对端是谁取决于**当前用户在这笔订单里的角色**：我买到的 → 卖家；我卖出的 → 买家。

   🔴 商品信息一律用**订单快照**（productTitle / productCover / productPrice / tradePlace），
      不再去查商品：商品可能已被改价、下架甚至逻辑删除，而订单里必须是「当时谈定的东西」。
   🔴 倒计时归零后订单会被 XXL-JOB 取消，所以要重新拉取；扫描任务有周期，
      拉一次可能还是「待付款」→ 此时收起付款入口并标注「已超时」，再隔一段时间重试。
   ========================================================== */

const route = useRoute()
const router = useRouter()

const orderNo = computed(() => String(route.params.orderNo || ''))

const order = ref(null)
const peer = ref(null)
const phone = ref('')
const phoneError = ref('')

const loading = ref(true)
const error = ref('')

const sheet = ref('') // '' | 'cancel'
const busy = ref(false)

const countdown = useCountdown()

const isSeller = computed(() => order.value?.role === 'SELLER')
const peerId = computed(() => {
  const o = order.value
  if (!o) return ''
  const id = isSeller.value ? o.buyerId : o.sellerId
  return id ? String(id) : ''
})
const peerRoleLabel = computed(() => (isSeller.value ? '买家' : '卖家'))
const peerName = computed(
  () => peer.value?.nickname || peer.value?.username || peerRoleLabel.value
)

/* ── 倒计时 ──
   secsLeft：非「待付款」一律 -1，让「已归零」只对真正的待付款订单成立
   （否则 order 还是 null 时会算出 0，把超时轮询给提前触发） */
const secsLeft = computed(() => {
  if (order.value?.status !== ORDER_STATUS.WAITING_PAY) return -1
  return countdown.left(order.value)
})
const expired = computed(
  () => order.value?.status === ORDER_STATUS.WAITING_PAY && secsLeft.value === 0
)
const remainText = computed(() => formatCountdown(Math.max(0, secsLeft.value)))

const statusTitle = computed(() => {
  if (!order.value) return ''
  if (expired.value) return '已超时'
  return ORDER_STATUS_LABEL[order.value.status] || order.value.statusDesc || ''
})

/** 135****8888 → 138 0013 6688（与设计稿一致，便于逐位朗读核对） */
const phoneText = computed(() => {
  const p = String(phone.value || '').replace(/\s/g, '')
  return p.length === 11 ? `${p.slice(0, 3)} ${p.slice(3, 7)} ${p.slice(7)}` : p
})

/* ── 操作条：随「状态 + 角色」变化（设计文档 §5.2） ──
   终态（交易完成 / 已取消）没有任何可执行动作 → 整条操作条隐藏，
   而不是留一条空的白条占位。 */
const barActions = computed(() => {
  const o = order.value
  if (!o) return []
  if (o.status === ORDER_STATUS.WAITING_PAY) {
    if (expired.value) return [{ text: '订单已超时', disabled: true, main: true }]
    return isSeller.value
      ? [{ text: '等待买家付款', disabled: true, main: true }]
      : [
          { text: '取消订单', act: 'cancel' },
          { text: '我已完成线下付款', act: 'pay', main: true }
        ]
  }
  if (o.status === ORDER_STATUS.PAID) {
    return isSeller.value
      ? [{ text: '等待买家确认', disabled: true, main: true }]
      : [{ text: '确认收货', act: 'complete', main: true }]
  }
  return []
})

/* ── 拉数据 ── */
async function fetchAll() {
  loading.value = true
  error.value = ''
  try {
    order.value = await getOrderDetail(orderNo.value)
  } catch (e) {
    error.value = e.message || '订单加载失败'
    loading.value = false
    return
  }
  loading.value = false
  if (order.value?.status === ORDER_STATUS.WAITING_PAY) countdown.start()

  const pid = peerId.value
  if (!pid) return
  // 昵称与联系方式互不依赖，并行拿；任一失败只影响对应的那一行
  const [brief, contact] = await Promise.allSettled([getUserBrief(pid), getContact(pid)])
  if (brief.status === 'fulfilled') peer.value = brief.value
  if (contact.status === 'fulfilled') {
    phone.value = contact.value || ''
    if (!phone.value) phoneError.value = '对方未填写联系方式'
  } else {
    phoneError.value = contact.reason?.message || '联系方式加载失败'
  }
}

async function refreshQuietly() {
  try {
    const fresh = await getOrderDetail(orderNo.value)
    order.value = fresh
    if (fresh.status !== ORDER_STATUS.WAITING_PAY) countdown.stop()
  } catch {
    /* 静默刷新失败不影响当前展示 */
  }
}

onMounted(fetchAll)

/* ── 归零 → 轮询等后端取消 ──
   扫描任务有周期，归零瞬间拉一次很可能还是「待付款」；
   每 15s 重试、最多 6 次，够覆盖常见的扫描间隔。恢复前台后依然准确，
   因为倒计时用的是时间基线（见 composables/useCountdown.js）。 */
let expireTimer = null
let expireTries = 0

async function pollAfterExpire() {
  try {
    const fresh = await getOrderDetail(orderNo.value)
    order.value = fresh
    countdown.stop()
    if (fresh.status !== ORDER_STATUS.WAITING_PAY) {
      toastOk('订单已超时取消', '商品已重新上架')
      return
    }
  } catch {
    /* 保持当前展示，继续重试 */
  }
  expireTries += 1
  if (expireTries < 6) expireTimer = setTimeout(pollAfterExpire, 15000)
}

watch(secsLeft, (v, old) => {
  if (v === 0 && old !== 0) pollAfterExpire()
})

onUnmounted(() => {
  if (expireTimer) clearTimeout(expireTimer)
})

/* ── 操作 ── */
async function run(a) {
  if (busy.value || a.disabled) return
  if (a.act === 'cancel') {
    sheet.value = 'cancel'
    return
  }

  const texts = {
    pay: ['付款已确认', '请与卖家当面确认商品无误后点击确认收货', '确认付款失败'],
    complete: ['交易完成', '商品已标记为已售出', '确认收货失败']
  }
  const t = texts[a.act]
  busy.value = true
  try {
    if (a.act === 'pay') await payOrder(orderNo.value)
    else if (a.act === 'complete') await completeOrder(orderNo.value)
    await refreshQuietly()
    toastOk(t[0], t[1])
  } catch (e) {
    toastFromError(e, t[2])
    // 状态非法通常意味着服务端已经变了（对方操作过 / 超时被取消）
    refreshQuietly()
  } finally {
    busy.value = false
  }
}

async function doCancel() {
  if (busy.value) return
  busy.value = true
  try {
    await cancelOrder(orderNo.value)
    sheet.value = ''
    await refreshQuietly()
    toastOk('订单已取消', '商品已重新上架')
  } catch (e) {
    sheet.value = ''
    toastFromError(e, '取消失败')
    refreshQuietly()
  } finally {
    busy.value = false
  }
}

/* ── 复制 ── */
async function copy(text, okMsg) {
  if (!text) return
  try {
    if (!navigator.clipboard?.writeText) throw new Error('unsupported')
    await navigator.clipboard.writeText(text)
    toastOk(okMsg)
  } catch {
    // 回退：非 https（如局域网 IP 访问）时 clipboard API 不可用
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    let ok = false
    try {
      ok = document.execCommand('copy')
    } catch {
      ok = false
    }
    document.body.removeChild(ta)
    if (ok) toastOk(okMsg)
    else toastError('复制失败', '请长按手动复制')
  }
}

function copyPhone() {
  copy(phone.value, '已复制手机号')
}
function copyOrderNo() {
  copy(orderNo.value, '已复制订单号')
}

function goProduct() {
  if (order.value?.productId) router.push(`/product/${order.value.productId}`)
}
function back() {
  if (window.history.state?.back) router.back()
  else router.replace('/user/orders')
}
</script>

<template>
  <div class="page">
    <header class="subnav">
      <button class="iconbtn press" aria-label="返回" @click="back">
        <svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12.2 4.6L6.8 10l5.4 5.4" />
        </svg>
      </button>
      <span class="ttl">订单详情</span>
      <span class="ph" />
    </header>

    <!-- ── 加载中：骨架形状与真实布局一致 ── -->
    <div v-if="loading" class="page-scroll">
      <div class="sk-state" />
      <div class="sk-card" />
      <div class="sk-card tall" />
    </div>

    <!-- ── 失败 ── -->
    <div v-else-if="error" class="page-scroll">
      <div class="state">
        <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
          <circle cx="12" cy="12" r="8.6" />
          <path d="M12 7.8v4.6M12 16.1h.02" />
        </svg>
        <p class="msg">{{ error }}</p>
        <button class="retry press" @click="fetchAll">重试</button>
      </div>
    </div>

    <!-- ── 正常 ── -->
    <template v-else>
      <div class="ostate">
        <h2>{{ statusTitle }}</h2>

        <p v-if="order.status === ORDER_STATUS.WAITING_PAY && !expired">
          请在 <b class="tabnum">{{ remainText }}</b>
          内{{ isSeller ? '等待买家确认付款' : '与卖家当面完成交易并确认付款' }}<br />
          超时订单会自动取消，商品重新上架
        </p>
        <p v-else-if="expired">
          已超过付款时限，系统正在自动取消订单<br />
          商品会重新上架，如还想购买请回到商品页重新下单
        </p>
        <p v-else-if="order.status === ORDER_STATUS.PAID">
          {{ isSeller ? '买家已确认完成线下付款，请配合完成交割' : '你已确认完成线下付款，请与卖家当面确认商品' }}<br />
          {{ isSeller ? '交割完成后由买家点击「确认收货」' : '确认无误后点击下方「确认收货」' }}
        </p>
        <p v-else-if="order.status === ORDER_STATUS.COMPLETED">
          交易已完成，商品已标记为已售出<br />
          感谢使用华水闲置
        </p>
        <p v-else>
          {{ order.cancelReason || '订单已取消' }}<br />
          商品已重新上架，其他同学可以继续下单
        </p>
      </div>

      <div class="page-scroll">
        <!-- ① 商品快照：只用订单里的快照字段，不回查商品 -->
        <div class="card2">
          <div class="ttl">商品信息</div>
          <div class="obody press" @click="goProduct">
            <div class="th t2">
              <img v-if="order.productCover" :src="order.productCover" alt="" />
              <svg v-else width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round">
                <rect x="3.2" y="4.6" width="17.6" height="14.8" rx="2.4" />
                <circle cx="8.8" cy="9.8" r="1.6" />
                <path d="M4.2 16.6l4.6-4.2 3.4 3 3-2.6 4.6 3.8" />
              </svg>
            </div>
            <div class="bd">
              <h4>{{ order.productTitle }}</h4>
              <div class="sub">下单时已锁定成交价</div>
            </div>
            <div class="amt"><small>¥</small><b>{{ formatPrice(order.productPrice) }}</b></div>
          </div>
        </div>

        <!-- ② 交易信息 -->
        <div class="card2">
          <div class="ttl">交易信息</div>
          <div class="kv">
            <span class="k">交易地点</span>
            <span class="v">{{ order.tradePlace || '未填写' }}</span>
          </div>
          <div class="kv">
            <span class="k">{{ peerRoleLabel }}</span>
            <span class="v">
              {{ peerName }}
              <template v-if="phone">
                · <span class="tabnum">{{ phoneText }}</span>
                <span class="copy" @click="copyPhone">复制</span>
              </template>
              <template v-else-if="phoneError"> · {{ phoneError }}</template>
            </span>
          </div>
          <div class="kv">
            <span class="k">下单时间</span>
            <span class="v tabnum">{{ formatTime(order.createTime, true) }}</span>
          </div>
          <div v-if="order.payTime" class="kv">
            <span class="k">付款时间</span>
            <span class="v tabnum">{{ formatTime(order.payTime, true) }}</span>
          </div>
          <div v-if="order.finishTime" class="kv">
            <span class="k">完成时间</span>
            <span class="v tabnum">{{ formatTime(order.finishTime, true) }}</span>
          </div>
          <div v-if="order.cancelTime" class="kv">
            <span class="k">取消时间</span>
            <span class="v tabnum">{{ formatTime(order.cancelTime, true) }}</span>
          </div>
          <div v-if="order.cancelReason" class="kv">
            <span class="k">取消原因</span>
            <span class="v">{{ order.cancelReason }}</span>
          </div>
          <div class="kv">
            <span class="k">订单号</span>
            <span class="v tabnum">
              {{ order.orderNo }}
              <span class="copy" @click="copyOrderNo">复制</span>
            </span>
          </div>
        </div>

        <!-- ③ 交易方式：固定说明。全站不出现「支付」，避免暗示平台经手资金 -->
        <div class="card2 last">
          <div class="ttl">交易方式</div>
          <p>仅支持校内当面自提。平台不介入资金流 —— 请当面确认商品无误后，再点击下方按钮确认付款。</p>
          <p>华水闲置只做信息撮合，不经手任何款项；如有纠纷由双方线下协商解决。</p>
        </div>
      </div>

      <div v-if="barActions.length" class="actionbar">
        <template v-for="a in barActions" :key="a.text">
          <button v-if="!a.main" class="btn-side press" @click="run(a)">{{ a.text }}</button>
          <button v-else class="btn-main" :disabled="busy || a.disabled" @click="run(a)">
            {{ busy && a.act ? '处理中…' : a.text }}
          </button>
        </template>
      </div>
    </template>

    <ActionSheet :visible="sheet === 'cancel'" @close="sheet = ''">
      <h3>取消这笔订单？</h3>
      <!-- 中文文案写成单行：HTML 会把标签内的换行折成一个空格，中文里会出现多余空隙 -->
      <p>取消后商品会<b>重新上架</b>，其他同学可以继续下单。若已和对方约好时间，建议先聊聊再决定。</p>
      <div class="sb2">
        <button class="bg" @click="sheet = ''">再想想</button>
        <button class="bs" :disabled="busy" @click="doCancel">
          {{ busy ? '取消中…' : '确认取消' }}
        </button>
      </div>
    </ActionSheet>
  </div>
</template>

<style scoped>
/* ── 顶栏 ── */
.subnav {
  position: relative;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: calc(50px + var(--safe-top));
  padding: var(--safe-top) 12px 0;
}
.subnav .ttl {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  font-size: 16px;
  font-weight: 600;
}
.subnav .iconbtn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  margin-left: -6px;
  color: var(--text);
}
.subnav .ph {
  width: 34px;
}

/* ── 状态区（粉 → 白渐变，与「我的」页同一套语言）── */
.ostate {
  flex: 0 0 auto;
  padding: 20px 16px 24px;
  background: var(--grad-mine);
}
.ostate h2 {
  font-size: 21px;
  font-weight: 600;
  letter-spacing: -0.4px;
}
.ostate p {
  margin-top: 8px;
  font-size: 12.5px;
  line-height: 1.62;
  color: #9a7a88;
}
.ostate p b {
  color: var(--pink-dp);
  font-weight: 600;
}

.tabnum {
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'tnum';
}

/* ── 卡片 ── */
.card2 {
  margin: 14px 16px 0;
  padding: 14px;
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--sh-card);
}
.card2.last {
  margin-bottom: 18px;
}
.card2 .ttl {
  margin-bottom: 10px;
  font-size: 11.5px;
  color: var(--text-2);
}
.card2 p {
  font-size: 12px;
  line-height: 1.72;
  color: #6e6e73;
}
.card2 p + p {
  margin-top: 8px;
}

/* ── 商品快照行 ── */
.obody {
  display: flex;
  align-items: center;
  gap: 11px;
  cursor: pointer;
}
.th {
  width: 70px;
  height: 70px;
  flex: 0 0 70px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  color: #c9c9ce;
}
.th img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.th.t2 { background: linear-gradient(150deg, #f5f7fc, #e7ecf5); }
.bd {
  flex: 1;
  min-width: 0;
}
.bd h4 {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
  color: #48484a;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.bd .sub {
  margin-top: 5px;
  font-size: 11px;
  color: var(--text-2);
}
.amt {
  flex: 0 0 auto;
  margin-left: auto;
  text-align: right;
  color: var(--pink);
  white-space: nowrap;
}
.amt small {
  font-size: 11px;
  font-weight: 600;
}
.amt b {
  font-size: 15px;
  font-weight: 600;
}

/* ── 键值行 ── */
.kv {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 7px 0;
  font-size: 12.5px;
}
.kv .k {
  flex: 0 0 auto;
  color: var(--text-2);
}
.kv .v {
  color: #3a3a3c;
  text-align: right;
  word-break: break-all;
}
.copy {
  margin-left: 6px;
  font-size: 11px;
  color: var(--pink-dp);
  cursor: pointer;
  white-space: nowrap;
}

/* ── 操作条 ── */
.actionbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 14px 16px calc(14px + var(--safe-bottom));
  background: rgba(255, 255, 255, 0.84);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  box-shadow: var(--sh-bar);
  position: relative;
  z-index: 9;
}
.btn-side {
  flex: 0 0 96px;
  width: 96px;
  height: 48px;
  border-radius: var(--r-btn);
  background: var(--field);
  color: #3a3a3c;
  font-size: 13.5px;
}
.actionbar .btn-main {
  flex: 1;
  min-width: 0;
  height: 48px;
  font-size: 15px;
  letter-spacing: 0.2px;
  padding: 0 8px;
}

/* ── 三态 ── */
.sk-state {
  height: 108px;
  background: linear-gradient(100deg, #f8eef4 30%, #f3e6ed 50%, #f8eef4 70%);
  background-size: 220% 100%;
  animation: shimmer 1.25s linear infinite;
}
.sk-card {
  height: 116px;
  margin: 14px 16px 0;
  border-radius: 14px;
  background: linear-gradient(100deg, #f4f4f6 30%, #ececf0 50%, #f4f4f6 70%);
  background-size: 220% 100%;
  animation: shimmer 1.25s linear infinite;
}
.sk-card.tall {
  height: 188px;
}
@keyframes shimmer {
  from { background-position: 140% 0; }
  to { background-position: -40% 0; }
}

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 120px 30px 40px;
  color: var(--text-4);
}
.state .msg {
  font-size: var(--fs-2);
  color: var(--text-2);
  text-align: center;
}
.retry {
  margin-top: 4px;
  height: 34px;
  padding: 0 20px;
  border-radius: 10px;
  background: var(--field);
  color: var(--pink-dp);
  font-size: var(--fs-2);
}

/* ── 取消确认层 ── */
h3 {
  font-size: 16.5px;
  font-weight: 600;
  letter-spacing: -0.2px;
}
h3 + p {
  margin-top: 11px;
  font-size: 12.5px;
  line-height: 1.75;
  color: #6e6e73;
}
h3 + p b {
  color: var(--pink-dp);
  font-weight: 600;
}
.sb2 {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}
.sb2 button {
  flex: 1;
  height: 46px;
  border-radius: 13px;
  font-size: 14.5px;
  font-weight: 500;
  transition: transform 0.3s var(--ease-pop);
}
.sb2 button:active {
  transform: scale(0.96);
}
.sb2 .bg {
  background: var(--field);
  color: #3a3a3c;
}
/* 取消订单用主色，不用红色：正常业务动作，不是异常（见设计文档 §3.5.1） */
.sb2 .bs {
  background: var(--grad-btn);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 6px 16px rgba(236, 110, 156, 0.28);
}
.sb2 .bs:disabled {
  background: var(--field);
  color: var(--text-3);
  box-shadow: none;
  cursor: not-allowed;
}
</style>
