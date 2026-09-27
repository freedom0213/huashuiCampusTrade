<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageState from '@/components/PageState.vue'
import ActionSheet from '@/components/ActionSheet.vue'
import { listMyProducts, offShelf, onShelf, deleteProduct } from '@/api/product'
import { formatPrice } from '@/utils/format'
import { toastFromError, toastOk } from '@/composables/useToast'
import { CONDITION_DESC } from '@/constants/enums'

/* ==========================================================
   我的发布 /user/products
   接口：GET /api/product/mine（可带 status）、PUT off-shelf / on-shelf、DELETE

   🔴 分组为何在前端做（而不是每次带 status 请求）：
      后端 `ProductQueryDTO.status` 是**单值**，而产品要的分组是
      「在售 = 在售 + 已被下单」「已结束 = 已售出 + 已下架 + 已驳回」，
      一个分组跨多个状态 → 带 status 请求做不到。
      所以一次拉回自己的商品（首次 size 取上限 50），再在前端分组与计数。
      自己的商品量级很小，这样最简单且计数准确；
      若超过 50 条则触底继续加载，此时计数会随加载更新（量级极小，可接受）。

   🔴 状态文案用**本页自己的映射**，不直接用后端 statusDesc：
      设计稿把 status=2 写成「已被下单」（比「已锁定」更贴近用户理解），
      这是产品口径，故在此覆盖。 */
const router = useRouter()

const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 50 // 后端上限就是 50
const loading = ref(false)
const loadingMore = ref(false)
const finished = ref(false)
const error = ref('')
const loaded = ref(false)

const group = ref('all')
const confirmTarget = ref(null)

const STATUS_LABEL = {
  0: '审核中',
  1: '在售',
  2: '已被下单',
  3: '已售出',
  4: '已下架',
  5: '已驳回'
}
const STATUS_STYLE = { 0: 'warn', 1: 'ok', 2: 'warn', 3: 'dead', 4: 'dead', 5: 'err' }

/* 分组：覆盖 0–5 全部状态，不重不漏 */
const GROUPS = [
  { key: 'all', label: '全部', test: () => true },
  { key: 'onSale', label: '在售', test: (p) => p.status === 1 || p.status === 2 },
  { key: 'audit', label: '审核中', test: (p) => p.status === 0 },
  { key: 'ended', label: '已结束', test: (p) => [3, 4, 5].includes(p.status) }
]

const counts = computed(() => {
  const c = {}
  GROUPS.forEach((g) => (c[g.key] = items.value.filter(g.test).length))
  return c
})

const list = computed(() => {
  const g = GROUPS.find((x) => x.key === group.value)
  return g ? items.value.filter(g.test) : items.value
})

const emptyText = computed(
  () => (group.value === 'all' ? '你还没有发布过商品' : '这个分组下暂时没有商品')
)

async function load(reset) {
  if (reset) {
    page.value = 1
    items.value = []
    finished.value = false
    error.value = ''
  }
  if (finished.value) return

  if (reset || !loaded.value) loading.value = true
  else loadingMore.value = true

  try {
    // 刻意不传 status：分组在前端做（见文件头说明）
    const res = await listMyProducts({ page: page.value, size: pageSize })
    const records = res.records || []
    total.value = res.total || 0
    items.value = reset ? records : items.value.concat(records)
    finished.value = items.value.length >= total.value || records.length === 0
    loaded.value = true
    if (!finished.value) page.value += 1
  } catch (e) {
    if (reset || !items.value.length) error.value = e.message || '加载失败'
    else finished.value = true
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function onScroll(e) {
  const el = e.target
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 240) loadMore()
}
function loadMore() {
  if (loading.value || loadingMore.value || finished.value || !loaded.value) return
  load(false)
}

/* 🔴 `rejectReason` 只有 `GET /api/product/mine` 会返回（后端 2026-09-27 `ad961bc` 补的字段）。
   公共商品列表与**收藏列表一律返回 null**，这是有意的：驳回原因是卖家的私事 ——
   收藏列表会保留已下架/已售出的商品，收藏过它的买家也会看到，
   无条件返回等于把「卖家为什么被驳回」告诉买家。
   → 所以**这一行只在「我的发布」渲染**，其它场景按 null 处理（本页就是唯一的出口）。
   此前那段「对每张已驳回卡片再查一次详情取原因」的绕行代码已随该字段上线删除。 */

onMounted(() => load(true))

/* ── 操作按钮：随状态变化 ── */
function actions(p) {
  switch (p.status) {
    case 0: // 审核中
      return [{ label: '编辑', act: 'edit' }]
    case 1: // 在售
      return [
        { label: '编辑', act: 'edit' },
        { label: '下架', act: 'off' }
      ]
    case 2: // 已被下单
    case 3: // 已售出
      return [{ label: '查看订单', act: 'orders' }]
    case 4: // 已下架
      return [
        { label: '编辑', act: 'edit' },
        { label: '重新上架', act: 'on', primary: true },
        { label: '删除', act: 'del', danger: true }
      ]
    case 5: // 已驳回
      return [
        { label: '修改重提', act: 'edit', primary: true },
        { label: '删除', act: 'del', danger: true }
      ]
    default:
      return []
  }
}

const busy = ref(false)

async function run(act, p) {
  if (act === 'edit') {
    router.push(`/publish/${p.id}`)
    return
  }
  if (act === 'orders') {
    // 卖家视角：这些是自己发布的商品，订单里的角色一定是卖家
    router.push({ path: '/user/orders', query: { role: 'seller' } })
    return
  }
  if (act === 'del') {
    confirmTarget.value = p
    return
  }
  if (busy.value) return

  busy.value = true
  try {
    if (act === 'off') {
      await offShelf(p.id)
      p.status = 4
      toastOk('商品已下架')
    } else if (act === 'on') {
      await onShelf(p.id)
      // 重新上架后要再走审核（后端口径），先按待审核展示，下次进页面以服务端为准
      p.status = 0
      toastOk('已重新提交审核', '管理员通过后将自动上架')
    }
  } catch (e) {
    toastFromError(e, act === 'off' ? '下架失败' : '上架失败')
  } finally {
    busy.value = false
  }
}

async function doDelete() {
  const p = confirmTarget.value
  if (!p || busy.value) return
  busy.value = true
  try {
    await deleteProduct(p.id)
    items.value = items.value.filter((x) => x.id !== p.id)
    total.value = Math.max(0, total.value - 1)
    confirmTarget.value = null
    toastOk('商品已删除')
  } catch (e) {
    toastFromError(e, '删除失败')
  } finally {
    busy.value = false
  }
}

function open(p) {
  router.push(`/product/${p.id}`)
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
      <span class="ttl">我的发布</span>
      <button class="act press" @click="router.push('/publish')">＋ 发布</button>
    </header>

    <div class="segs">
      <span
        v-for="g in GROUPS"
        :key="g.key"
        class="sg"
        :class="{ on: group === g.key }"
        @click="group = g.key"
      >
        {{ g.label }} {{ counts[g.key] }}
      </span>
    </div>

    <div class="page-scroll" @scroll="onScroll">
      <div class="pad">
        <PageState
          :loading="loading"
          :error="error"
          :empty="!loading && !error && !list.length"
          :empty-text="emptyText"
          empty-hint="发布后需要管理员审核通过才会上架"
          :skeleton-count="4"
          @retry="load(true)"
        />

        <template v-if="list.length">
          <article
            v-for="(p, i) in list"
            :key="p.id"
            class="nitem rise"
            :style="{ animationDelay: `${((i % 6) + 1) * 0.05}s` }"
          >
            <button class="nico press" :class="`t${(i % 4) + 1}`" @click="open(p)">
              <img v-if="p.coverUrl" :src="p.coverUrl" alt="" />
              <svg v-else width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round">
                <rect x="3.2" y="4.6" width="17.6" height="14.8" rx="2.4" />
                <circle cx="8.8" cy="9.8" r="1.6" />
                <path d="M4.2 16.6l4.6-4.2 3.4 3 3-2.6 4.6 3.8" />
              </svg>
            </button>

            <div class="txt">
              <b class="press" @click="open(p)">{{ p.title }}</b>
              <p>
                ¥{{ formatPrice(p.price) }} ·
                {{ p.conditionDesc || CONDITION_DESC[p.conditionLevel] || '' }}
                <span v-if="p.tradePlace"> · {{ p.tradePlace }}</span>
              </p>
              <!-- rejectReason 只在 /api/product/mine 有值（卖家私事，公共/收藏列表为 null） -->
              <p v-if="p.status === 5 && p.rejectReason" class="reject">
                驳回原因：{{ p.rejectReason }}
              </p>
              <div class="actions">
                <!-- 🔴 一律用本页的 STATUS_LABEL，**不要**写成 `p.statusDesc || …`：
                     后端对 status=2 返回的是「已锁定」，而设计稿口径是「已被下单」；
                     且本地改状态（下架/上架）后 statusDesc 不会跟着变，标签会停在旧文案。
                     这条实测踩过：下架后按钮已是「重新上架/删除」，标签却还显示「在售」。 -->
                <span class="stag" :class="STATUS_STYLE[p.status]">
                  {{ STATUS_LABEL[p.status] || p.statusDesc || '' }}
                </span>
                <button
                  v-for="a in actions(p)"
                  :key="a.act"
                  class="minibtn press"
                  :class="{ primary: a.primary, danger: a.danger }"
                  @click="run(a.act, p)"
                >
                  {{ a.label }}
                </button>
              </div>
            </div>
          </article>

          <p v-if="loadingMore" class="more">加载中…</p>
          <p v-else-if="finished" class="more">没有更多了</p>
        </template>
      </div>
    </div>

    <ActionSheet :visible="!!confirmTarget" @close="confirmTarget = null">
      <h3>删除这件商品？</h3>
      <p>「{{ confirmTarget?.title }}」删除后无法恢复，如需重新出售要再次发布并等待审核。</p>
      <div class="sb2">
        <button class="bg" @click="confirmTarget = null">取消</button>
        <button class="del" :disabled="busy" @click="doDelete">确认删除</button>
      </div>
    </ActionSheet>
  </div>
</template>

<style scoped>
/* ── 顶栏（标题绝对居中，左右各一个按钮）── */
.subnav {
  position: relative;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: calc(50px + var(--safe-top));
  padding: calc(var(--safe-top) + 0px) 12px 0;
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
.subnav .act {
  font-size: 13.5px;
  color: var(--pink-dp);
  padding: 8px 4px;
}

/* ── 分段器 ──
   🔴 必须用 flex:1 平分，**不要用设计稿里的 `display:table` + `table-cell`**：
   table 的单元格按**内容宽度**分配，4 段（全部 6 / 在售 1 / 审核中 1 / 已结束 4）
   实测每格拿到 155px、总宽 620px > 容器 351px → 第 4 段「已结束」被挤出屏幕外裁掉。
   flex:1 与内容无关，严格平分。 */
.segs {
  flex: 0 0 auto;
  display: flex;
  padding: 0 12px;
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

/* ── 列表项 ── */
.nitem {
  display: flex;
  gap: 11px;
  padding: 14px;
  margin-bottom: 9px;
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--sh-card);
}
.nico {
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
.nico img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.nico.t1 { background: linear-gradient(150deg, #fff3f7, #fbe2ec); }
.nico.t2 { background: linear-gradient(150deg, #f5f7fc, #e7ecf5); }
.nico.t3 { background: linear-gradient(150deg, #f1f9f4, #e0f1e8); }
.nico.t4 { background: linear-gradient(150deg, #fdf8f2, #f3eade); }

.nitem .txt {
  flex: 1;
  min-width: 0;
}
.nitem b {
  display: block;
  font-size: 13.5px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--text);
}
.nitem p {
  margin-top: 4px;
  font-size: 12px;
  color: #6e6e73;
  line-height: 1.55;
}
.nitem .reject {
  color: var(--red);
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 9px;
}
.actions .stag {
  flex: 0 0 auto;
}
.minibtn {
  height: 28px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: #fff;
  font-size: 12px;
  color: #3a3a3c;
}
.minibtn.primary {
  border-color: transparent;
  background: var(--pink-bg);
  color: var(--pink-dp);
  font-weight: 500;
}
.minibtn.danger {
  color: var(--red);
}

.more {
  padding: 12px 0 4px;
  text-align: center;
  font-size: var(--fs-1);
  color: var(--text-3);
}

/* ── 删除确认层 ── */
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
}
.sb2 .bg {
  background: var(--field);
  color: #3a3a3c;
}
.sb2 .del {
  background: var(--red);
  color: #fff;
  font-weight: 600;
}
.sb2 .del:disabled {
  opacity: 0.5;
}
</style>
