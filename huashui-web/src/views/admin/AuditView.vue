<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  getAuditList,
  getAuditStats,
  approveProduct,
  rejectProduct,
  forceOffProduct
} from '@/api/admin'
import { formatPrice } from '@/utils/format'
import { toastFromError, toastOk } from '@/composables/useToast'
import { refreshAdminPending } from '@/composables/useAdminPending'
import RejectDialog from './components/RejectDialog.vue'
import ForceOffDialog from './components/ForceOffDialog.vue'

/* ==========================================================
   管理端 · 商品审核 /admin/audit
   契约：docs/13-商品审核与管理端接口设计.md + 扩展（仓库根需求清单）。

   口径：
   · 状态 = 后端 t_product.status tinyint：0 待审核 / 1 在售 / 2 已锁定 /
     3 已售出 / 4 已下架 / 5 已驳回。列表接口只会返回所查状态，
     2/3 不应出现，映射里兜底渲染灰徽章、无操作。
   · 统计卡「已通过」= 在售(1) 数量；「已下架」= 4。stats 接口是扩展契约，
     后端未就位时静默降级为 '–'，**不阻塞列表**。
   · 提交时间：扩展字段 createdAt（审核通过前 publishTime 为空），
     兜底链 createdAt → publishTime → '–'。
   · 操作映射：待审核 → 通过/驳回；在售 → 强制下架；其余无操作。
     通过是低风险正向动作，按设计稿**不设确认弹窗**（驳回/强下才确认）。
   ========================================================== */

const PAGE_SIZE = 10

const TABS = [
  { key: 'all', label: '全部', status: 'all', statKey: 'total' },
  { key: 'pending', label: '待审核', status: 0, statKey: 'pending' },
  { key: 'onSale', label: '已通过', status: 1, statKey: 'onSale' },
  { key: 'rejected', label: '已驳回', status: 5, statKey: 'rejected' },
  { key: 'offShelf', label: '已下架', status: 4, statKey: 'offShelf' }
]

const BADGE = {
  0: { text: '待审核', cls: 'b-pending' },
  1: { text: '已上架', cls: 'b-onsale' },
  2: { text: '已锁定', cls: 'b-dead' },
  3: { text: '已售出', cls: 'b-dead' },
  4: { text: '已下架', cls: 'b-off' },
  5: { text: '已驳回', cls: 'b-rejected' }
}

/* —— 统计 —— */
const stats = ref(null)
const statsFailed = ref(false)
const statCards = computed(() => [
  { label: '待审核', value: statVal('pending'), cls: 'n-pending' },
  { label: '已通过', value: statVal('onSale'), cls: 'n-onsale' },
  { label: '已驳回', value: statVal('rejected'), cls: 'n-rejected' },
  { label: '已下架', value: statVal('offShelf'), cls: 'n-off' }
])
function statVal(key) {
  if (!stats.value) return '–'
  return stats.value[key] ?? '–'
}
const tabCount = computed(() => {
  const t = TABS.find((x) => x.key === tab.value)
  if (!t || !stats.value) return null
  if (t.statKey === 'total') {
    const s = stats.value
    if ([s.pending, s.onSale, s.rejected, s.offShelf].some((v) => v == null)) return null
    return s.pending + s.onSale + s.rejected + s.offShelf
  }
  return stats.value[t.statKey] ?? null
})

/* —— 列表 —— */
const tab = ref('all')
const keyword = ref('')
const items = ref([])
const total = ref(0)
const current = ref(1)
const loading = ref(false)
const error = ref('')
const loaded = ref(false)
const listTicker = ref(0) // 每次成功加载 +1，驱动行进入动画重放

const pages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const pageList = computed(() => Array.from({ length: Math.min(pages.value, 50) }, (_, i) => i + 1))

async function loadStats() {
  try {
    stats.value = await getAuditStats()
    statsFailed.value = false
  } catch {
    /* 扩展接口未就位 / 偶发失败：静默降级为 '–'，不打扰列表主流程 */
    stats.value = null
    statsFailed.value = true
  }
}

/* 🔴 请求序号守卫：刷新 / 切 Tab / 搜索可能并发出发多个 load，
   慢的旧响应如果后到会把新状态覆盖掉（实测：刷新后立刻切 Tab，
   旧 Tab 的响应把新 Tab 的列表顶掉）。只接受**最新一次**请求的响应。 */
let loadSeq = 0

async function load({ reset = false } = {}) {
  const seq = ++loadSeq
  if (reset) current.value = 1
  loading.value = true
  error.value = ''
  try {
    const params = { status: TABS.find((x) => x.key === tab.value).status, current: current.value, size: PAGE_SIZE }
    const kw = keyword.value.trim()
    if (kw) params.keyword = kw
    const res = await getAuditList(params)
    if (seq !== loadSeq) return // 已有更新的请求在途，丢弃本次旧响应
    items.value = res.records || []
    total.value = res.total || 0
    loaded.value = true
    listTicker.value += 1
  } catch (e) {
    if (seq !== loadSeq) return
    if (reset || !loaded.value) {
      error.value = e.message || '加载失败'
      items.value = []
      total.value = 0
    } else {
      toastFromError(e, '加载失败')
    }
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function switchTab(key) {
  if (tab.value === key) return
  tab.value = key
  load({ reset: true })
}

/* 搜索：300ms 防抖 */
let kwTimer = null
function onKeywordInput() {
  clearTimeout(kwTimer)
  kwTimer = setTimeout(() => load({ reset: true }), 300)
}

function goPage(p) {
  if (p < 1 || p > pages.value || p === current.value || loading.value) return
  current.value = p
  load()
}

async function refresh() {
  await Promise.all([load({ reset: false }), loadStats()])
  // 操作后同步侧边栏「待审核」徽章（不必等 30s 轮询）
  refreshAdminPending()
}

/* —— 操作 —— */
async function onApprove(p) {
  try {
    await approveProduct(p.id)
    toastOk('已通过审核', `「${p.title}」已上架`)
    refresh()
  } catch (e) {
    toastFromError(e, '审核通过失败')
  }
}

const rejectTarget = ref(null)
const rejectVisible = ref(false)
function openReject(p) {
  rejectTarget.value = p
  rejectVisible.value = true
}
/* done/fail 由弹窗注入：成功才关窗，失败就地提示 */
function onRejectConfirm(reason, { done, fail }) {
  rejectProduct(rejectTarget.value.id, reason)
    .then(() => {
      toastOk('已驳回', '理由已展示给卖家')
      done()
      refresh()
    })
    .catch(fail)
}

const forceTarget = ref(null)
const forceVisible = ref(false)
function openForce(p) {
  forceTarget.value = p
  forceVisible.value = true
}
function onForceConfirm({ done, fail }) {
  forceOffProduct(forceTarget.value.id)
    .then(() => {
      toastOk('已下架', `「${forceTarget.value.title}」已对买家不可见`)
      done()
      refresh()
    })
    .catch(fail)
}

/* 提交时间：MM-DD HH:mm（后端扩展字段 createdAt，兜底 publishTime） */
function submitTime(p) {
  const t = p.createdAt || p.publishTime
  if (!t) return '–'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return '–'
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function sellerName(p) {
  return p.sellerName || (p.sellerId != null ? `#${p.sellerId}` : '–')
}

onMounted(() => {
  load({ reset: true })
  loadStats()
})
</script>

<template>
  <div class="audit-page">
    <!-- 标题行 -->
    <div class="page-head">
      <div>
        <h2 class="page-title">商品审核</h2>
        <p class="page-sub">处理待审核商品，维护平台内容质量</p>
      </div>
      <button class="refresh-btn" :class="{ spinning: loading }" :disabled="loading" @click="refresh">
        <svg viewBox="0 0 16 16" fill="none">
          <path
            d="M13.5 8a5.5 5.5 0 1 1-1.6-3.9M13.5 1.8v2.7h-2.7"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        刷新
      </button>
    </div>

    <!-- 统计卡 -->
    <div class="stat-row">
      <div v-for="card in statCards" :key="card.label" class="stat-card">
        <p class="stat-label">{{ card.label }}</p>
        <p class="stat-num" :class="card.cls">{{ card.value }}</p>
      </div>
    </div>

    <!-- Tab + 搜索 -->
    <div class="filter-row">
      <div class="tabs">
        <button
          v-for="t in TABS"
          :key="t.key"
          class="tab"
          :class="{ active: tab === t.key }"
          @click="switchTab(t.key)"
        >
          {{ t.label }}
          <span v-if="t.key === tab && tabCount != null" class="tab-num">{{ tabCount }}</span>
        </button>
      </div>
      <div class="search-box">
        <svg viewBox="0 0 16 16" fill="none">
          <circle cx="7" cy="7" r="4.5" stroke="currentColor" stroke-width="1.4" />
          <path d="m10.5 10.5 3 3" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
        </svg>
        <input v-model="keyword" type="text" placeholder="搜索商品标题 / 卖家" @input="onKeywordInput" />
      </div>
    </div>

    <!-- 列表卡片 -->
    <div class="table-card">
      <div class="thead">
        <span>商品信息</span>
        <span>卖家</span>
        <span>提交时间</span>
        <span>状态</span>
        <span class="th-op">操作</span>
      </div>

      <div v-if="error" class="list-state">
        <p class="state-err">{{ error }}</p>
        <button class="retry-btn" @click="load({ reset: true })">重试</button>
      </div>

      <div v-else-if="loading && !loaded" class="list-state"><p class="state-tip">加载中…</p></div>

      <div v-else-if="!items.length" class="list-state">
        <p class="state-tip">{{ keyword ? '没有匹配的商品' : '这个状态下暂时没有商品' }}</p>
      </div>

      <template v-else>
        <div
          v-for="p in items"
          :key="`${listTicker}-${p.id}`"
          class="trow"
          :class="{ dim: p.status === 4 || p.status === 5 }"
        >
          <div class="cell-goods">
            <!-- 🔴 字段名必须是 coverUrl（后端 ProductListVO），不是 cover ——
                 管理列表直接用后端 records 未做映射，写错不会报错、只表现为缩略图全部破图 -->
            <img class="g-thumb" :src="p.coverUrl" alt="" loading="lazy" />
            <div class="g-info">
              <p class="g-title">{{ p.title }}</p>
              <p class="g-price">¥{{ formatPrice(p.price) }}</p>
            </div>
          </div>
          <span class="cell-seller" :title="sellerName(p)">{{ sellerName(p) }}</span>
          <span class="cell-time">{{ submitTime(p) }}</span>
          <span class="cell-status">
            <span class="badge" :class="BADGE[p.status]?.cls || 'b-dead'">{{ BADGE[p.status]?.text || '未知' }}</span>
          </span>
          <span class="cell-op">
            <template v-if="p.status === 0">
              <button class="op op-ok" :disabled="loading" @click="onApprove(p)">通过</button>
              <button class="op op-reject" :disabled="loading" @click="openReject(p)">驳回</button>
            </template>
            <button v-else-if="p.status === 1" class="op op-force" :disabled="loading" @click="openForce(p)">
              强制下架
            </button>
          </span>
        </div>

        <!-- 分页 -->
        <div class="pager">
          <span class="pager-total">共 {{ total }} 条</span>
          <div class="pager-btns">
            <button class="pg" :disabled="current <= 1" @click="goPage(current - 1)">上一页</button>
            <button
              v-for="n in pageList"
              :key="n"
              class="pg pg-num"
              :class="{ cur: n === current }"
              @click="goPage(n)"
            >
              {{ n }}
            </button>
            <button class="pg" :disabled="current >= pages" @click="goPage(current + 1)">下一页</button>
          </div>
        </div>
      </template>
    </div>

    <RejectDialog v-model="rejectVisible" :product="rejectTarget" @confirm="onRejectConfirm" />
    <ForceOffDialog v-model="forceVisible" :product="forceTarget" @confirm="onForceConfirm" />
  </div>
</template>

<style scoped>
/* 统一变量：Tailwind 对应色（设计稿采样 #4f45e4 ≈ indigo-600） */
.audit-page {
  --indigo: #4f45e4;
  --ink: #1c1c1e;
  --ink-2: #6b7280;
  --ink-3: #9ca3af;
  --line: #f3f4f6;
  --green: #22c55e;
  --green-deep: #16a34a;
  --red: #ef4444;
  --amber: #f59e0b;
  text-align: left;
  max-width: 1180px;
  margin: 0 auto;
}

/* —— 标题行 —— */
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ink);
  margin: 0;
}
.page-sub {
  font-size: 13px;
  color: var(--ink-3);
  margin: 4px 0 0;
}
.refresh-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e5e7eb;
  color: var(--ink-2);
  font-size: 13px;
  font-weight: 500;
}
.refresh-btn:hover:not(:disabled) {
  color: var(--ink);
  border-color: #d1d5db;
}
.refresh-btn:disabled {
  opacity: 0.7;
  cursor: default;
}
.refresh-btn svg {
  width: 14px;
  height: 14px;
}
.refresh-btn.spinning svg {
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* —— 统计卡 —— */
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-top: 20px;
}
.stat-card {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 1px 3px rgba(23, 23, 26, 0.03);
}
.stat-label {
  font-size: 13px;
  color: var(--ink-2);
  margin: 0;
}
.stat-num {
  font-size: 28px;
  font-weight: 700;
  margin: 6px 0 0;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.n-pending {
  color: var(--amber);
}
.n-onsale {
  color: var(--green-deep);
}
.n-rejected {
  color: var(--red);
}
.n-off {
  color: #374151;
}

/* —— Tab + 搜索 —— */
.filter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 22px;
}
.tabs {
  display: flex;
  align-items: center;
  gap: 4px;
}
.tab {
  padding: 7px 12px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--ink-2);
  transition: color 0.15s, background 0.15s;
}
.tab:hover {
  color: var(--ink);
}
.tab.active {
  color: var(--indigo);
  background: #fff;
  box-shadow: inset 0 0 0 1px #edeaff, 0 1px 3px rgba(23, 23, 26, 0.04);
  font-weight: 600;
}
.tab-num {
  font-size: 12px;
  margin-left: 2px;
  font-variant-numeric: tabular-nums;
}
.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 240px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 7px 12px;
  color: var(--ink-3);
}
.search-box:focus-within {
  border-color: var(--indigo);
  box-shadow: 0 0 0 3px rgba(79, 69, 228, 0.1);
}
.search-box svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}
.search-box input {
  border: none;
  outline: none;
  background: none;
  font-size: 13px;
  color: var(--ink);
  width: 100%;
}
.search-box input::placeholder {
  color: var(--ink-3);
}

/* —— 表格卡片 —— */
.table-card {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  margin-top: 14px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(23, 23, 26, 0.03);
}
.thead,
.trow {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 132px 118px 100px 170px;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
}
.thead {
  height: 42px;
  background: #f9fafb;
  font-size: 12px;
  color: var(--ink-2);
}
.th-op {
  text-align: right;
  padding-right: 64px;
}
.trow {
  min-height: 66px;
  border-top: 1px solid var(--line);
  animation: row-in 0.28s var(--ease, ease) both;
}
@keyframes row-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.trow.dim .g-title,
.trow.dim .g-price,
.trow.dim .cell-seller,
.trow.dim .cell-time {
  opacity: 0.55;
}

.cell-goods {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.g-thumb {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  object-fit: cover;
  background: var(--field, #f6f6f8);
  flex-shrink: 0;
}
.g-info {
  min-width: 0;
}
.g-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.g-price {
  font-size: 12px;
  color: var(--pink);
  font-weight: 600;
  margin: 3px 0 0;
}
.cell-seller,
.cell-time {
  font-size: 13px;
  color: var(--ink-2);
  font-variant-numeric: tabular-nums;
  /* 🔴 卖家列现在是 19 位雪花 id（sellerName 后端未就位），不加截断会溢出压到
     相邻的「提交时间」列（实测两者数字重叠）。列宽固定时必须配 ellipsis，
     完整 id 通过 title 提供（hover 可见）。 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.badge {
  display: inline-block;
  font-size: 12px;
  font-weight: 500;
  padding: 3px 9px;
  border-radius: 6px;
}
.b-pending {
  background: #fef3c7;
  color: #d97706;
}
.b-onsale {
  background: #dcfce7;
  color: var(--green-deep);
}
.b-rejected {
  background: #fee2e2;
  color: var(--red);
}
.b-off {
  background: #f3f4f6;
  color: var(--ink-2);
}
.b-dead {
  background: #f3f4f6;
  color: var(--ink-2);
}

.cell-op {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.op {
  padding: 5px 13px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  transition: background 0.15s, opacity 0.15s;
}
.op:disabled {
  opacity: 0.6;
  cursor: default;
}
.op-ok {
  background: var(--green);
  color: #fff;
}
.op-ok:hover:not(:disabled) {
  background: var(--green-deep);
}
.op-reject,
.op-force {
  background: #fee2e2;
  color: var(--red);
}
.op-reject:hover:not(:disabled),
.op-force:hover:not(:disabled) {
  background: #fecaca;
}

/* —— 状态/空态 —— */
.list-state {
  padding: 56px 0;
  text-align: center;
}
.state-tip {
  font-size: 13px;
  color: var(--ink-3);
  margin: 0;
}
.state-err {
  font-size: 13px;
  color: var(--red);
  margin: 0 0 10px;
}
.retry-btn {
  padding: 6px 16px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #fff;
  font-size: 12px;
  color: var(--ink-2);
}
.retry-btn:hover {
  color: var(--ink);
}

/* —— 分页 —— */
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-top: 1px solid var(--line);
}
.pager-total {
  font-size: 12px;
  color: var(--ink-3);
  font-variant-numeric: tabular-nums;
}
.pager-btns {
  display: flex;
  gap: 6px;
}
.pg {
  padding: 6px 12px;
  border-radius: 7px;
  border: 1px solid #e5e7eb;
  background: #fff;
  font-size: 12px;
  color: var(--ink-2);
  transition: background 0.15s, color 0.15s;
}
.pg:hover:not(:disabled):not(.cur) {
  color: var(--ink);
  background: #f9fafb;
}
.pg:disabled {
  opacity: 0.45;
  cursor: default;
}
.pg-num {
  min-width: 32px;
  font-variant-numeric: tabular-nums;
}
.pg.cur {
  background: var(--indigo);
  border-color: var(--indigo);
  color: #fff;
  font-weight: 600;
}
</style>
