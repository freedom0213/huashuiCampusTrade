<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageState from '@/components/PageState.vue'
import ActionSheet from '@/components/ActionSheet.vue'
import { listMyOrders, getOrderDetail, payOrder, completeOrder, cancelOrder } from '@/api/order'
import { getUserBrief } from '@/api/user'
import { ORDER_STATUS, ORDER_STATUS_LABEL, ORDER_STATUS_STYLE } from '@/constants/enums'
import { formatPrice, formatCountdown } from '@/utils/format'
import { toastOk, toastFromError } from '@/composables/useToast'
import { useCountdown } from '@/composables/useCountdown'

/* ==========================================================
   我的订单 /user/orders
   接口：GET /api/order/mine?role=buyer|seller（+ 单条 pay / complete / cancel）

   两个视角（同一账号既是买家也是卖家，`t_orders` 有 buyer_id 与 seller_id）：
     我买到的 role=buyer  → 展示**卖家**信息（要去赴约的是买家）
     我卖出的 role=seller → 展示**买家**信息（要联系买家的是卖家）

   🔴 OrderVO **不做跨服务聚合**（后端注释明确）：只有 buyerId / sellerId，没有对端昵称。
      所以对端昵称要前端并行调 `GET /api/user/detail/{id}` 补 —— 与「我的发布」
      补 rejectReason 是同一类做法。按 id 去重后请求，且结果缓存在 peers 里，
      翻页 / 切视角都不重复请求。

   🔴 状态文案用本页映射（ORDER_STATUS_LABEL），不用后端 statusDesc：
      后端 status=1 的文案是「已付款」，而产品口径要「已付款，待收货」——
      少半句用户就不知道后面还有「确认收货」这个动作。
   ========================================================== */

const route = useRoute()
const router = useRouter()

const ROLES = [
  { key: 'buyer', label: '我买到的' },
  { key: 'seller', label: '我卖出的' }
]

const role = ref(route.query.role === 'seller' ? 'seller' : 'buyer')

const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const loadingMore = ref(false)
const finished = ref(false)
const loaded = ref(false)
const error = ref('')

/** 对端昵称缓存：{ [userId]: {nickname, username} | null }。null = 拉过但失败，不再重试 */
const peers = ref({})

const busy = ref(false)
const cancelTarget = ref(null)

const countdown = useCountdown()

/* ── 对端是谁：后端返回的 role 是「当前用户」在这笔订单里的角色 ── */
function isSellerOrder(o) {
  return o?.role === 'SELLER'
}
function peerIdOf(o) {
  const id = isSellerOrder(o) ? o?.buyerId : o?.sellerId
  return id ? String(id) : ''
}
function peerRoleLabel(o) {
  return isSellerOrder(o) ? '买家' : '卖家'
}
function peerName(o) {
  const id = peerIdOf(o)
  const p = peers.value[id]
  if (p === undefined) return '加载中…'
  if (p === null) return peerRoleLabel(o)
  return p.nickname || p.username || peerRoleLabel(o)
}

/* ── 拉列表 ── */
async function load(reset) {
  if (reset) {
    page.value = 1
    items.value = []
    finished.value = false
    error.value = ''
    loaded.value = false
  }
  if (finished.value) return

  if (reset) loading.value = true
  else loadingMore.value = true

  try {
    const res = await listMyOrders({ role: role.value, page: page.value, size: pageSize })
    const records = res.records || []
    total.value = res.total || 0
    items.value = reset ? records : items.value.concat(records)
    finished.value = items.value.length >= total.value || records.length === 0
    loaded.value = true
    if (!finished.value) page.value += 1

    // 有数据才开始走秒（空列表没必要每秒重渲染）
    if (items.value.length) countdown.start()
    fillPeers(records)
  } catch (e) {
    if (reset || !items.value.length) error.value = e.message || '订单加载失败'
    else finished.value = true
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

/** 补齐对端昵称：按 id 去重，只拉没有缓存过的 */
async function fillPeers(records) {
  const ids = [...new Set(records.map(peerIdOf).filter(Boolean))].filter(
    (id) => peers.value[id] === undefined
  )
  if (!ids.length) return
  await Promise.all(
    ids.map(async (id) => {
      try {
        const brief = await getUserBrief(id)
        peers.value = { ...peers.value, [id]: brief || null }
      } catch {
        // 记 null：取不到昵称只影响这一行文字，不能因此反复重试
        peers.value = { ...peers.value, [id]: null }
      }
    })
  )
}

function onScroll(e) {
  const el = e.target
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 240) loadMore()
}
function loadMore() {
  if (loading.value || loadingMore.value || finished.value || !loaded.value) return
  load(false)
}

function switchRole(key) {
  if (role.value === key) return
  role.value = key
  // replace：视角切换不该往历史里堆记录，否则返回键要按很多次才出得去
  router.replace({ path: '/user/orders', query: key === 'seller' ? { role: 'seller' } : {} })
  load(true)
}

onMounted(() => load(true))

/* ── 倒计时归零：这条订单应该已被后端扫描任务取消，取一次最新状态 ──
   只取「刚刚归零」的那几条，取到就置 __tried 标记，避免每秒重复请求。
   若扫描任务还没跑到（status 仍是待付款），把入口收起来并标成「已超时」——
   不能让用户继续点「我已完成线下付款」，那一步后端也会被状态校验挡回来。 */
watch(countdown.elapsed, () => {
  const zeros = items.value.filter(
    (o) => o.status === ORDER_STATUS.WAITING_PAY && !o.__triedRefresh && countdown.left(o) === 0
  )
  zeros.forEach(async (o) => {
    o.__triedRefresh = true
    try {
      const fresh = await getOrderDetail(o.orderNo)
      Object.assign(o, fresh)
      if (fresh.status === ORDER_STATUS.CANCELLED) {
        toastOk('订单已超时取消', '商品已重新上架')
      } else {
        o.__expired = true
      }
    } catch {
      o.__expired = true
    }
  })
})

/* ── 展示映射 ── */
function statusText(o) {
  if (o.status === ORDER_STATUS.WAITING_PAY) {
    if (o.__expired) return '已超时'
    return `${ORDER_STATUS_LABEL[0]} ${formatCountdown(countdown.left(o))}`
  }
  return ORDER_STATUS_LABEL[o.status] || o.statusDesc || ''
}

function statusStyle(o) {
  if (o.status === ORDER_STATUS.WAITING_PAY && o.__expired) return 'dead'
  return ORDER_STATUS_STYLE[o.status] || ''
}

/** 金额：优先订单总额（本项目单品单量，两者相等，取总额更语义正确） */
function amountOf(o) {
  const v = o?.totalAmount ?? o?.productPrice
  return formatPrice(v)
}

/* ── 操作按钮：随「状态 + 角色」变化（设计文档 §5.2） ──
   卖家在「待付款 / 已付款」上没有按钮 —— 设计口径就是「等待买家付款 / 等待买家确认」，
   这不是漏做，而是这两个状态确实没有卖家可执行的动作（后端允许卖家代确认，
   但那会让「谁确认的」变得含糊，产品口径定为买家确认）。 */
function actions(o) {
  const st = o.status
  if (st === ORDER_STATUS.WAITING_PAY) {
    if (isSellerOrder(o)) return []
    if (o.__expired) return [{ label: '查看详情', act: 'open' }]
    return [
      { label: '取消订单', act: 'cancel' },
      { label: '我已完成线下付款', act: 'pay', primary: true }
    ]
  }
  if (st === ORDER_STATUS.PAID) {
    if (isSellerOrder(o)) return []
    return [{ label: '确认收货', act: 'complete', primary: true }]
  }
  return [{ label: '查看详情', act: 'open' }]
}

/** 卖家在等待期显示的说明（代替按钮位） */
function waitingText(o) {
  if (isSellerOrder(o) && o.status === ORDER_STATUS.WAITING_PAY) return '等待买家付款'
  if (isSellerOrder(o) && o.status === ORDER_STATUS.PAID) return '等待买家确认'
  return ''
}

const emptyText = computed(() =>
  role.value === 'buyer' ? '你还没有买到的商品' : '还没有人买你的东西'
)
const emptyHint = computed(() =>
  role.value === 'buyer' ? '看到喜欢的就联系卖家约个时间见面吧' : '发布商品后，有人下单就会出现在这里'
)

async function refreshOne(o) {
  try {
    const fresh = await getOrderDetail(o.orderNo)
    Object.assign(o, fresh)
    o.__triedRefresh = true
  } catch {
    /* 刷新失败保持当前展示即可 */
  }
}

async function run(act, o) {
  if (act === 'open') return open(o)
  if (act === 'cancel') {
    cancelTarget.value = o
    return
  }
  if (busy.value) return

  const texts = {
    pay: ['付款已确认', '请与卖家当面确认商品后点击确认收货', '确认付款失败'],
    complete: ['交易完成', '商品已标记为已售出', '确认收货失败']
  }
  busy.value = true
  try {
    if (act === 'pay') await payOrder(o.orderNo)
    else await completeOrder(o.orderNo)
    o.status = act === 'pay' ? ORDER_STATUS.PAID : ORDER_STATUS.COMPLETED
    o.remainSeconds = 0
    toastOk(texts[act][0], texts[act][1])
  } catch (e) {
    toastFromError(e, texts[act][2])
    // 状态非法通常意味着服务端已经变了（对方操作过 / 超时被取消），静默对齐一次
    refreshOne(o)
  } finally {
    busy.value = false
  }
}

async function doCancel() {
  const o = cancelTarget.value
  if (!o || busy.value) return
  busy.value = true
  try {
    await cancelOrder(o.orderNo)
    o.status = ORDER_STATUS.CANCELLED
    o.cancelReason = isSellerOrder(o) ? '卖家主动取消' : '买家主动取消'
    o.remainSeconds = 0
    cancelTarget.value = null
    toastOk('订单已取消', '商品已重新上架')
  } catch (e) {
    cancelTarget.value = null
    toastFromError(e, '取消失败')
    refreshOne(o)
  } finally {
    busy.value = false
  }
}

function open(o) {
  router.push(`/order/${o.orderNo}`)
}
function back() {
  if (window.history.state?.back) router.back()
  else router.replace('/mine')
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
      <span class="ttl">我的订单</span>
      <span class="ph" />
    </header>

    <div class="segs">
      <span
        v-for="r in ROLES"
        :key="r.key"
        class="sg"
        :class="{ on: role === r.key }"
        @click="switchRole(r.key)"
      >
        {{ r.label }}
      </span>
    </div>

    <div class="page-scroll" @scroll="onScroll">
      <div class="pad">
        <PageState
          :loading="loading"
          :error="error"
          :empty="!loading && !error && !items.length"
          :empty-text="emptyText"
          :empty-hint="emptyHint"
          :skeleton-count="3"
          @retry="load(true)"
        />

        <template v-if="items.length">
          <article
            v-for="(o, i) in items"
            :key="o.orderNo"
            class="ocard rise"
            :style="{ animationDelay: `${((i % 6) + 1) * 0.05}s` }"
            @click="open(o)"
          >
            <div class="obody">
              <div class="th" :class="`t${(i % 4) + 1}`">
                <img v-if="o.productCover" :src="o.productCover" alt="" />
                <svg v-else width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round">
                  <rect x="3.2" y="4.6" width="17.6" height="14.8" rx="2.4" />
                  <circle cx="8.8" cy="9.8" r="1.6" />
                  <path d="M4.2 16.6l4.6-4.2 3.4 3 3-2.6 4.6 3.8" />
                </svg>
              </div>
              <div class="bd">
                <h4>{{ o.productTitle }}</h4>
                <div class="sub">
                  {{ peerRoleLabel(o) }} {{ peerName(o) }}
                  <span v-if="o.tradePlace"> · {{ o.tradePlace }}</span>
                </div>
              </div>
              <div class="amt">¥{{ amountOf(o) }}</div>
            </div>

            <div class="oacts">
              <span class="stag" :class="statusStyle(o)">{{ statusText(o) }}</span>
              <span class="sp" />
              <span v-if="waitingText(o)" class="waiting">{{ waitingText(o) }}</span>
              <button
                v-for="a in actions(o)"
                :key="a.act"
                class="minibtn press"
                :class="{ primary: a.primary }"
                @click.stop="run(a.act, o)"
              >
                {{ a.label }}
              </button>
            </div>
          </article>

          <p v-if="loadingMore" class="more">加载中…</p>
          <p v-else-if="finished" class="more">没有更多了</p>
        </template>
      </div>
    </div>

    <ActionSheet :visible="!!cancelTarget" @close="cancelTarget = null">
      <h3>取消这笔订单？</h3>
      <!-- 中文文案写成单行：HTML 会把标签内的换行折成一个空格，中文里会出现多余空隙 -->
      <p>取消后商品会<b>重新上架</b>，其他同学可以继续下单。若已和对方约好时间，建议先聊聊再决定。</p>
      <div class="sb2">
        <button class="bg" @click="cancelTarget = null">再想想</button>
        <button class="bs" :disabled="busy" @click="doCancel">
          {{ busy ? '取消中…' : '确认取消' }}
        </button>
      </div>
    </ActionSheet>
  </div>
</template>

<style scoped>
/* ── 顶栏（标题绝对居中）── */
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

/* ── 视角分段器（两个）──
   设计稿是 `padding: 6px 60px 0`：只有两段，两侧留白让它们居中收拢。
   🔴 仍用 flex:1 而不是设计稿的 display:table —— 见「我的发布」里的实测记录：
   table-cell 按内容宽度分配，段数一多就会被挤出屏幕。 */
.segs {
  flex: 0 0 auto;
  display: flex;
  padding: 6px 60px 0;
  border-bottom: 1px solid var(--line);
}
.segs .sg {
  flex: 1;
  position: relative;
  text-align: center;
  padding: 12px 0 11px;
  font-size: 12.5px;
  color: var(--text-2);
  cursor: pointer;
  white-space: nowrap;
}
.segs .sg.on {
  color: var(--pink);
  font-weight: 600;
}
.segs .sg.on::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -1px;
  transform: translateX(-50%);
  width: 20px;
  height: 2.5px;
  border-radius: 2px;
  background: var(--pink);
}

.pad {
  padding: 12px 12px 20px;
}

/* ── 订单卡 ── */
.ocard {
  padding: 14px;
  margin-bottom: 9px;
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--sh-card);
  cursor: pointer;
}

.obody {
  display: flex;
  align-items: flex-start;
  gap: 11px;
}
.th {
  width: 64px;
  height: 64px;
  flex: 0 0 64px;
  border-radius: 12px;
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
.th.t1 { background: linear-gradient(150deg, #fff3f7, #fbe2ec); }
.th.t2 { background: linear-gradient(150deg, #f5f7fc, #e7ecf5); }
.th.t3 { background: linear-gradient(150deg, #f1f9f4, #e0f1e8); }
.th.t4 { background: linear-gradient(150deg, #fdf8f2, #f3eade); }

.bd {
  flex: 1;
  min-width: 0;
}
.bd h4 {
  font-size: 13.5px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.bd .sub {
  margin-top: 5px;
  font-size: 12px;
  color: #6e6e73;
  line-height: 1.5;
}
.amt {
  flex: 0 0 auto;
  font-size: 15px;
  font-weight: 600;
  color: var(--pink);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.2px;
}

.oacts {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 11px;
}
.oacts .sp {
  flex: 1;
  min-width: 0;
}
.waiting {
  font-size: 11.5px;
  color: var(--text-3);
}

/* 订单卡里的迷你按钮（与设计稿 .minibtn 同尺寸） */
.minibtn {
  height: 28px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: #fff;
  font-size: 12px;
  color: #3a3a3c;
  white-space: nowrap;
}
.minibtn.primary {
  border-color: transparent;
  background: var(--pink-bg);
  color: var(--pink-dp);
  font-weight: 500;
}

.more {
  padding: 12px 0 4px;
  text-align: center;
  font-size: var(--fs-1);
  color: var(--text-3);
}

/* ── 取消确认层 ── */
h3 {
  font-size: 16.5px;
  font-weight: 600;
  letter-spacing: -0.2px;
}
p {
  margin-top: 11px;
  font-size: 12.5px;
  line-height: 1.75;
  color: #6e6e73;
}
p b {
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
/* 🔴 「取消订单」用主色而不是红色：
   它是我们提供的**正常业务动作**，不是异常。红色会被读成系统故障
   （与「反馈只有 ✓ 与 ！，没有 ✗」是同一条口径）。 */
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
