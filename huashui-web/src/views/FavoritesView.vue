<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ProductCard from '@/components/ProductCard.vue'
import PageState from '@/components/PageState.vue'
import ActionSheet from '@/components/ActionSheet.vue'
import { listMyFavorites, removeFavorite } from '@/api/favorite'
import { PRODUCT_STATUS } from '@/constants/enums'
import { toastOk, toastError, toastFromError } from '@/composables/useToast'
import { captureCard, consumeReturn, restoreScroll } from '@/utils/flip'

/* ==========================================================
   我的收藏 /user/favorites
   接口：GET /api/favorite/mine（按收藏时间倒序，分页 size ≤ 100）、DELETE /api/favorite/{productId}

   🔴 已下架 / 已售出 / 已锁定的收藏**必须留在原地并灰显，不在前端过滤掉**
      （设计稿 figcaption 明确）—— 用户可能想「照着这件找同类」，
      商品悄悄消失会让人以为「我的收藏丢了」。后端也会带出当前 status/statusDesc。

   🔴 产品 id 是雪花 ID，已由后端序列化成**字符串**，比较一律用字符串
      （选中集合、批量取消后的过滤都用 String(id)，不要 parseInt）。

   两个操作入口：
     卡片右上角心形 → 单件取消（在售商品才有；非在售被灰罩盖住，走「管理」批量删）
     右上角「管理」 → 多选态：点卡片改为选中、底部条批量取消（带二次确认）
   ========================================================== */

const router = useRouter()

const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const loadingMore = ref(false)
const finished = ref(false)
const loaded = ref(false)
const error = ref('')

const managing = ref(false)
const selected = ref(new Set())
const confirmOpen = ref(false)
const busy = ref(false)

/* 从详情页返回：还原滚动位置 + 不重播卡片入场动画 */
const scrollerEl = ref(null)
const restoring = ref(false)

const selectedCount = computed(() => selected.value.size)
const allSelected = computed(
  () => items.value.length > 0 && selected.value.size === items.value.length
)

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
    const res = await listMyFavorites({ page: page.value, size: pageSize })
    const records = res.records || []
    total.value = res.total || 0
    items.value = reset ? records : items.value.concat(records)
    finished.value = items.value.length >= total.value || records.length === 0
    loaded.value = true
    if (!finished.value) page.value += 1
  } catch (e) {
    if (reset || !items.value.length) error.value = e.message || '收藏加载失败'
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

onMounted(async () => {
  restoring.value = consumeReturn()
  await load(true)
  await nextTick()
  restoreScroll(scrollerEl.value)
})

/* ── 单件取消收藏 ── */
async function unfav(p) {
  if (busy.value) return
  busy.value = true
  try {
    await removeFavorite(p.id)
    dropItems([String(p.id)])
    toastOk('已取消收藏')
  } catch (e) {
    toastFromError(e, '取消收藏失败')
  } finally {
    busy.value = false
  }
}

/* ── 多选态 ── */
function toggleManage() {
  managing.value = !managing.value
  selected.value.clear()
}
function toggleSelect(p) {
  const id = String(p.id)
  if (selected.value.has(id)) selected.value.delete(id)
  else selected.value.add(id)
}
function toggleSelectAll() {
  if (allSelected.value) selected.value.clear()
  else items.value.forEach((p) => selected.value.add(String(p.id)))
}
function askBatch() {
  if (!selected.value.size || busy.value) return
  confirmOpen.value = true
}

/**
 * 批量取消收藏：逐条调 DELETE（后端没有批量接口），失败的单条保留在列表里。
 * 用 allSettled 而不是 all —— 一件失败不该让其余已成功的也被回滚显示。
 */
async function doBatch() {
  const ids = [...selected.value]
  if (!ids.length || busy.value) return
  busy.value = true
  try {
    const results = await Promise.allSettled(ids.map((id) => removeFavorite(id)))
    const okIds = ids.filter((_, i) => results[i].status === 'fulfilled')
    const failCount = ids.length - okIds.length

    dropItems(okIds)
    selected.value.clear()
    confirmOpen.value = false
    managing.value = false

    if (failCount) {
      toastError('部分商品取消失败', `${failCount} 件未取消成功，请稍后重试`)
    } else {
      toastOk(`已取消收藏 ${okIds.length} 件商品`)
    }
  } finally {
    busy.value = false
  }
}

function dropItems(ids) {
  const set = new Set(ids.map(String))
  items.value = items.value.filter((x) => !set.has(String(x.id)))
  total.value = Math.max(0, total.value - set.size)
}

/* ── 出图 ── */
function open(p, ev) {
  // 多选态下点卡片是「选中」，不是「进详情」
  if (managing.value) return toggleSelect(p)
  // 记录卡片位置，供详情页播放 FLIP 放大动画
  captureCard(ev?.currentTarget, p.id, scrollerEl.value)
  router.push(`/product/${p.id}`)
}

function back() {
  if (managing.value) return toggleManage()
  if (window.history.state?.back) router.back()
  else router.replace('/mine')
}
</script>

<template>
  <div class="page">
    <header class="subnav">
      <button class="iconbtn press" :aria-label="managing ? '退出管理' : '返回'" @click="back">
        <svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12.2 4.6L6.8 10l5.4 5.4" />
        </svg>
      </button>
      <span class="ttl">我的收藏</span>
      <button v-if="items.length" class="act press" @click="toggleManage">
        {{ managing ? '完成' : '管理' }}
      </button>
      <span v-else class="ph" />
    </header>

    <div ref="scrollerEl" class="page-scroll" :class="{ restoring }" @scroll="onScroll">
      <div class="pad">
        <PageState
          :loading="loading"
          :error="error"
          :empty="!loading && !error && !items.length"
          empty-text="还没有收藏任何商品"
          empty-hint="在商品详情页点右上角的心形就能收藏"
          :skeleton-count="4"
          @retry="load(true)"
        />

        <div v-if="items.length" class="grid">
          <ProductCard
            v-for="(p, i) in items"
            :key="p.id"
            :product="p"
            :index="i"
            @tap="open"
          >
            <!-- 非在售：灰罩 + 状态胶囊。留在原地不自动消失（用户可能想照着找同类） -->
            <span v-if="p.status !== PRODUCT_STATUS.ON_SALE" class="dead">
              <em>{{ p.statusDesc || '已下架' }}</em>
            </span>

            <!-- 在售且非多选态：右上角心形，点一下即取消收藏 -->
            <button
              v-else-if="!managing"
              class="favbtn press"
              aria-label="取消收藏"
              @click.stop="unfav(p)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 20s-7.6-4.6-7.6-9.7A4.4 4.4 0 0112 7.2a4.4 4.4 0 017.6 3.1C19.6 15.4 12 20 12 20z" />
              </svg>
            </button>

            <!-- 多选态：右上角选中圈（z-index 高于灰罩，已售出的也能选） -->
            <span
              v-if="managing"
              class="pick"
              :class="{ on: selected.has(String(p.id)) }"
            >
              <svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
                <path d="M3.4 8.4l3 3 6.2-6.6" />
              </svg>
            </span>
          </ProductCard>
        </div>

        <p v-if="loadingMore" class="more">加载中…</p>
        <p v-else-if="finished && items.length" class="more">没有更多了</p>
      </div>
    </div>

    <!-- 多选态的底部条 -->
    <div v-if="managing" class="managebar">
      <button class="all press" @click="toggleSelectAll">
        {{ allSelected ? '取消全选' : '全选' }}
      </button>
      <span class="count">已选 <b>{{ selectedCount }}</b> 项</span>
      <button class="del press" :disabled="!selectedCount || busy" @click="askBatch">
        取消收藏
      </button>
    </div>

    <ActionSheet :visible="confirmOpen" @close="confirmOpen = false">
      <h3>取消收藏这 {{ selectedCount }} 件商品？</h3>
      <!-- 中文文案写成单行：标签内换行会被折成一个空格，中文里会出现多余空隙 -->
      <p>取消后它们会从收藏里移除。想再收藏需要重新找到这些商品。</p>
      <div class="sb2">
        <button class="bg" @click="confirmOpen = false">再想想</button>
        <button class="bs" :disabled="busy" @click="doBatch">
          {{ busy ? '处理中…' : '确认取消收藏' }}
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
.subnav .act {
  font-size: 13.5px;
  color: var(--pink-dp);
  padding: 8px 4px;
}
.subnav .ph {
  width: 34px;
}

.pad {
  padding: 12px 16px 20px;
}

/* 从详情页缩回时不再播一次入场动画（否则与缩回动画叠成"抖一下"） */
.page-scroll.restoring :deep(.pcard) {
  animation: none;
}

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--gap-card);
}

/* ── 卡片右上角：心形 / 灰罩 / 选中圈 ──
   这些元素由 ProductCard 的插槽渲染，是 article.pcard 的直接子元素，
   所以绝对定位要自己算出「缩略图那一块」，不能直接用 inset:0（会盖住标题与价格）。 */
.favbtn {
  position: absolute;
  right: 7px;
  top: 7px;
  z-index: 2;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: var(--pink);
}

/* 缩略图是「卡片全宽的正方形」，所以灰罩用 top/left/right + aspect-ratio 覆盖它 */
.dead {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 2;
  aspect-ratio: 1 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.62);
}
.dead em {
  font-style: normal;
  font-size: 11.5px;
  color: #7c7c80;
  background: rgba(255, 255, 255, 0.95);
  padding: 4px 11px;
  border-radius: var(--r-pill);
}

.pick {
  position: absolute;
  right: 8px;
  top: 8px;
  z-index: 3;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.9);
  border: 1.5px solid #e2e2e7;
  color: transparent;
  box-shadow: 0 1px 5px rgba(28, 28, 30, 0.08);
}
.pick.on {
  background: var(--grad-btn);
  border-color: transparent;
  color: #fff;
}

.more {
  padding: 14px 0 4px;
  text-align: center;
  font-size: var(--fs-1);
  color: var(--text-3);
}

/* ── 多选态底部条 ── */
.managebar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px calc(12px + var(--safe-bottom));
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  box-shadow: var(--sh-bar);
  position: relative;
  z-index: 9;
}
.managebar .all {
  font-size: 13.5px;
  color: var(--text-2);
  padding: 8px 0;
}
.managebar .count {
  flex: 1;
  font-size: 13px;
  color: var(--text-2);
}
.managebar .count b {
  color: var(--pink-dp);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.managebar .del {
  height: 40px;
  padding: 0 18px;
  border-radius: 12px;
  background: var(--grad-btn);
  color: #fff;
  font-size: 13.5px;
  font-weight: 600;
  box-shadow: 0 5px 14px rgba(236, 110, 156, 0.26);
}
.managebar .del:disabled {
  background: var(--field);
  color: var(--text-3);
  box-shadow: none;
  cursor: not-allowed;
}

/* ── 确认层 ── */
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
/* 取消收藏可逆（重新收藏即可），用主色不红色：与「取消订单」同一口径 */
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
