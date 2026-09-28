<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ActionSheet from '@/components/ActionSheet.vue'
import { getProductDetail } from '@/api/product'
import { getUserBrief, getContact } from '@/api/user'
import { getSoldCount, createOrder } from '@/api/order'
import { addFavorite, removeFavorite } from '@/api/favorite'
import { PRODUCT_STATUS, PRODUCT_STATUS_DESC, PRODUCT_STATUS_STYLE, CONDITION_DESC } from '@/constants/enums'
import { formatPrice, formatCount } from '@/utils/format'
import { toastOk, toastError, toastFromError } from '@/composables/useToast'
import { useUserStore } from '@/stores/user'
import { clearEnterFrom, getEnterFrom, isReturnPending, markLeftDetail, markReturn, playEnter, playReturn } from '@/utils/flip'

/* ==========================================================
   商品详情 /product/:id —— 全站信息密度最高的页面，也是唯一的交易入口。

   三个请求并行组装（后端 ProductDetailVO 只有 sellerId，不含卖家昵称/头像）：
     ① GET /api/product/detail/{id}      商品本体（owned / favorited 只有带 token 时才准）
     ② GET /api/user/detail/{sellerId}   卖家昵称 / 院系（游客可访问）
     ③ GET /api/order/sold-count/{sellerId} 历史成交笔数（游客可访问，是二手交易的信任信号）
   ① 失败 → 整页错误态；②③ 失败 → 只少一块信息，不让整页挂掉。

   🔴 业务口径（docs/02-前端设计V1.md §4.3.1）：「先联系 → 再下单」。
      点「立即下单」先弹确认层，因为线下当面交易必须先约好时间地点，
      否则商品锁 30 分钟后自动取消，双方都白折腾。
   ========================================================== */

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const SCHOOL = '华北水利水电大学'
const id = computed(() => String(route.params.id || ''))

const detail = ref(null)
const seller = ref(null)
const soldCount = ref(null)

const loading = ref(true)
const error = ref('')

/* 收藏 */
const favOn = ref(false)
const favBusy = ref(false)
const favBeat = ref(false)

/* 浮层：'' | 'order' | 'contact' */
const sheet = ref('')
const creating = ref(false)

/* 联系方式 */
const phone = ref('')
const phoneError = ref('')
const phoneLoading = ref(false)

/* 轮播 */
const curIdx = ref(0)

/* 转场 */
const galleryEl = ref(null)
const leaving = ref(false)

const owned = computed(() => detail.value?.owned === true)
const status = computed(() => detail.value?.status)
const isLogin = computed(() => userStore.isLogin)
const sellerId = computed(() => String(detail.value?.sellerId || ''))

const images = computed(() => {
  const list = detail.value?.imageUrls
  return Array.isArray(list) ? list.filter(Boolean) : []
})

const title = computed(() => detail.value?.title || '')
const price = computed(() => formatPrice(detail.value?.price))
const origin = computed(() =>
  Number(detail.value?.originalPrice) > Number(detail.value?.price)
    ? formatPrice(detail.value?.originalPrice)
    : ''
)
const condition = computed(
  () => detail.value?.conditionDesc || CONDITION_DESC[detail.value?.conditionLevel] || ''
)
const categoryName = computed(() => detail.value?.categoryName || '')
const tradePlace = computed(() => detail.value?.tradePlace || '')
const description = computed(() => detail.value?.description || '')
const statusDesc = computed(
  () => detail.value?.statusDesc || PRODUCT_STATUS_DESC[status.value] || ''
)
const statusStyle = computed(() => PRODUCT_STATUS_STYLE[status.value] || '')

/** 大图上的状态角标：只在「非在售」时出现 */
const badge = computed(() => {
  const s = status.value
  if (s === undefined || s === PRODUCT_STATUS.ON_SALE) return ''
  return statusDesc.value
})

const sellerName = computed(
  () => seller.value?.nickname || seller.value?.username || detail.value?.sellerName || '卖家'
)
const sellerDept = computed(() => seller.value?.dept || '')
const sellerAvatar = computed(() => seller.value?.avatar || '')
const sellerMeta = computed(() => {
  const parts = []
  if (sellerDept.value) parts.push(sellerDept.value)
  if (soldCount.value !== null) parts.push(`成交 ${soldCount.value} 笔`)
  return parts.join(' · ')
})

/* ── 操作条主按钮：文案与禁用态完全由商品状态决定（§5.1） ── */
const mainAction = computed(() => {
  if (owned.value) return { text: '这是你发布的商品', disabled: true }
  switch (status.value) {
    case PRODUCT_STATUS.PENDING_AUDIT:
      return { text: '审核中', disabled: true }
    case PRODUCT_STATUS.ON_SALE:
      return { text: '立即下单', disabled: false }
    case PRODUCT_STATUS.LOCKED:
      return { text: '已被抢购', disabled: true }
    case PRODUCT_STATUS.SOLD:
      return { text: '已售出', disabled: true }
    case PRODUCT_STATUS.OFF_SHELF:
      return { text: '已下架', disabled: true }
    case PRODUCT_STATUS.REJECTED:
      return { text: '已驳回', disabled: true }
    default:
      return { text: '暂不可下单', disabled: true }
  }
})

/** 操作条上方的说明行：能下单时讲规则，不能下单时讲原因 */
const actionHint = computed(() => {
  if (owned.value) return '你发布的商品不能购买或收藏'
  switch (status.value) {
    case PRODUCT_STATUS.ON_SALE:
      return '下单后商品将为你保留 30 分钟，超时未确认付款会自动取消并重新上架'
    case PRODUCT_STATUS.PENDING_AUDIT:
      return '审核通过后会自动上架，届时其他同学即可下单'
    case PRODUCT_STATUS.LOCKED:
      return '其他买家正在下单，30 分钟内未付款会自动释放'
    case PRODUCT_STATUS.SOLD:
      return '这件商品已经卖出，看看别的吧'
    case PRODUCT_STATUS.OFF_SHELF:
      return '卖家已下架这件商品'
    case PRODUCT_STATUS.REJECTED:
      return '这件商品未通过审核'
    default:
      return ''
  }
})

/* ── 拉数据 ── */
async function fetchAll() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await getProductDetail(id.value)
    favOn.value = detail.value?.favorited === true
  } catch (e) {
    error.value = e.message || '商品加载失败'
    loading.value = false
    return
  }
  loading.value = false

  // 卖家资料与成交数互不依赖，并行拿；任一失败只影响对应那块信息
  const sid = sellerId.value
  if (!sid) return
  const [brief, sold] = await Promise.allSettled([getUserBrief(sid), getSoldCount(sid)])
  if (brief.status === 'fulfilled') seller.value = brief.value
  if (sold.status === 'fulfilled') soldCount.value = sold.value
}

/** 静默刷新（下单失败后同步最新状态用，不闪骨架） */
async function refreshQuietly() {
  try {
    const d = await getProductDetail(id.value)
    detail.value = d
    favOn.value = d?.favorited === true
  } catch {
    /* 静默刷新失败不影响当前展示 */
  }
}

onMounted(async () => {
  /* 🔴 播放时机：**不等数据**。
     大图容器现在与加载骨架共用同一个 DOM（始终存在、尺寸由 CSS 的 1:1 决定），
     所以可以立刻开播 —— 慢网络（手机 / 隧道）下等 fetchAll 完成再播的话，
     用户看到的是「点了一下、等一两秒、直接出现详情页」，动画等于没有。 */
  const from = getEnterFrom(id.value)
  if (from && galleryEl.value) playEnter(galleryEl.value, from)

  await fetchAll()
})

/* 🔴 卸载清场：只有「返回列表播缩回动画」这一条路径允许带走 enterFrom
   （首页要在 restoreScroll 里用它的 scrollTop，消费完由列表页清除）。
   其余任何离开方式（去卖家页 / 去下单 / 去登录）都必须清掉，
   否则记录残留 → 下次同 id 的详情页（如从卖家页 / 消息中心进入）会误播放大动画。
   这就是「FLIP 只属于 首页⇄详情 一条链」的范围收窄。

   另：无条件打「刚离开详情页」标记 —— 列表页据此抑制卡片入场动画重播
   （浏览器返回键路径拿不到 returnPending，只能靠它）。 */
onBeforeUnmount(() => {
  markLeftDetail()
  if (!isReturnPending()) clearEnterFrom()
})

/* ── 返回：有转场现场就先缩回原卡片，再真正返回 ── */
/* 🔴 重入锁：缩回动画要 420ms，其间用户（尤其手机连点 / touch 双触发）再点返回，
   会第二次走到 router.back() → **一次退两层**，直接跳过首页落到更早的页面
   （实测表现为「点返回却跳到了『我的』」）。 */
let backing = false
async function back() {
  if (backing) return
  backing = true

  const from = getEnterFrom(id.value)
  if (from && galleryEl.value && !loading.value) {
    markReturn()
    leaving.value = true
    await playReturn(galleryEl.value, from)
  }
  const st = router.options.history.state
  if (st && st.back) router.back()
  else router.replace('/')
}

/* ── 收藏 ── */
async function toggleFav() {
  if (favBusy.value) return
  if (!isLogin.value) return toLogin()
  if (owned.value) return toastError('收藏失败', '不能收藏自己发布的商品')

  favBusy.value = true
  const want = !favOn.value
  try {
    if (want) await addFavorite(id.value)
    else await removeFavorite(id.value)
    favOn.value = want
    // 心跳只在「收藏」成功时播，取消收藏保持安静
    if (want) {
      favBeat.value = true
      setTimeout(() => (favBeat.value = false), 650)
      toastOk('已收藏', '卖家降价或下架时会出现在你的收藏里')
    } else {
      toastOk('已取消收藏')
    }
  } catch (e) {
    toastFromError(e, want ? '收藏失败' : '取消收藏失败')
  } finally {
    favBusy.value = false
  }
}

function toLogin() {
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}

/* ── 联系卖家 ── */
function openContact() {
  if (!isLogin.value) return toLogin()
  if (owned.value) return
  sheet.value = 'contact'
  if (phone.value || phoneLoading.value) return
  loadPhone()
}

async function loadPhone() {
  phoneLoading.value = true
  phoneError.value = ''
  try {
    phone.value = (await getContact(sellerId.value)) || ''
    if (!phone.value) phoneError.value = '卖家未填写联系方式'
  } catch (e) {
    phoneError.value = e.message || '联系方式加载失败'
  } finally {
    phoneLoading.value = false
  }
}

/** 13800136688 → 138 0013 6688（与设计稿一致，方便核对朗读） */
const phoneText = computed(() => {
  const p = String(phone.value || '').replace(/\s/g, '')
  return p.length === 11 ? `${p.slice(0, 3)} ${p.slice(3, 7)} ${p.slice(7)}` : p
})

async function copyPhone() {
  if (!phone.value) return
  try {
    if (!navigator.clipboard?.writeText) throw new Error('unsupported')
    await navigator.clipboard.writeText(phone.value)
    toastOk('已复制手机号', '手机号同微信')
  } catch {
    // 回退路径：非 https 环境（如局域网 IP 访问）clipboard API 不可用
    const ta = document.createElement('textarea')
    ta.value = phone.value
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
    if (ok) toastOk('已复制手机号', '手机号同微信')
    else toastError('复制失败', '请长按手机号手动复制')
  }
}

/* ── 下单 ── */
function askOrder() {
  if (mainAction.value.disabled) return
  if (!isLogin.value) return toLogin()
  sheet.value = 'order'
}

async function confirmOrder() {
  if (creating.value) return
  creating.value = true
  try {
    const order = await createOrder(id.value)
    sheet.value = ''
    toastOk('订单创建成功', '商品已为你锁定 30 分钟')
    const no = order?.orderNo
    // replace：下单后不该再回到「可下单」的详情页旧状态
    router.replace(no ? `/order/${no}` : '/user/orders')
  } catch (e) {
    // 失败时先收起浮层再提示，否则用户看不到按钮状态已经变化
    sheet.value = ''
    toastFromError(e, '下单失败')
    refreshQuietly()
  } finally {
    creating.value = false
  }
}

/* ── 轮播 ── */
function onRailScroll(e) {
  const w = e.target.clientWidth || 1
  const i = Math.round(e.target.scrollLeft / w)
  const max = Math.max(0, images.value.length - 1)
  const next = Math.min(Math.max(i, 0), max)
  if (next !== curIdx.value) curIdx.value = next
}

function goSeller() {
  if (sellerId.value) router.push(`/seller/${sellerId.value}`)
}
</script>

<template>
  <div class="page" :class="{ leaving }">
    <!-- 浮动返回键：浮在大图上（沉浸式），不占布局高度 -->
    <div class="navfloat">
      <button class="nbtn press" aria-label="返回" @click="back">
        <svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12.2 4.6L6.8 10l5.4 5.4" />
        </svg>
      </button>
    </div>

    <div class="page-scroll">
      <!-- 🔴 大图区容器：加载骨架与真实大图**共用同一个 DOM 节点**（ref 始终有值），
           这样「卡片放大成详情」的 FLIP 动画可以在数据到达前就开播 ——
           此前容器写在 v-else 里，动画必须等 fetchAll 完成，慢网络（手机 / cpolar 隧道）
           下要等 1~2 秒，用户只看到「直接跳转、没有动画」。 -->
      <div v-if="!error" ref="galleryEl" class="gallery">
        <div v-if="loading" class="sk-gallery" />
        <template v-else>
            <div v-if="images.length" class="rail" @scroll="onRailScroll">
              <div v-for="(u, i) in images" :key="i" class="slide" :class="`t${(i % 4) + 1}`">
                <img :src="u" :alt="title" @error="(e) => (e.target.style.display = 'none')" />
              </div>
            </div>
            <!-- 无图：低饱和渐变 + 线性图标占位（与卡片占位同一套语言，不用 emoji） -->
            <div v-else class="slide t2 empty">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round">
                <rect x="3.2" y="4.6" width="17.6" height="14.8" rx="2.4" />
                <circle cx="8.8" cy="9.8" r="1.6" />
                <path d="M4.2 16.6l4.6-4.2 3.4 3 3-2.6 4.6 3.8" />
              </svg>
            </div>

            <span v-if="badge" class="badge" :class="statusStyle">{{ badge }}</span>

            <div v-if="images.length > 1" class="dots">
              <i v-for="(u, i) in images" :key="i" :class="{ on: i === curIdx }" />
            </div>
        </template>
      </div>

      <!-- ── 加载中：骨架形状与真实布局一致，避免加载完成时的位置跳变 ── -->
      <template v-if="loading">
        <div class="detail">
          <div class="sk-line w46" />
          <div class="sk-line w86 tall" />
          <div class="sk-line w34" />
          <div class="sk-card" />
          <div class="sk-line w92" />
          <div class="sk-line w78" />
        </div>
      </template>

      <!-- ── 失败 ── -->
      <div v-else-if="error" class="state">
        <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
          <circle cx="12" cy="12" r="8.6" />
          <path d="M12 7.8v4.6M12 16.1h.02" />
        </svg>
        <p class="msg">{{ error }}</p>
        <button class="retry press" @click="fetchAll">重试</button>
      </div>

      <!-- ── 正常 ── -->
      <template v-else>
        <div class="detail">
          <div class="prow-big fade" style="animation-delay: 0.11s">
            <span class="price-big"><small>¥</small>{{ price }}</span>
            <span v-if="origin" class="origin-big">¥{{ origin }}</span>
            <span class="pmeta">
              {{ formatCount(detail.viewCount) }} 浏览 · {{ formatCount(detail.favoriteCount) }} 收藏
            </span>
          </div>

          <h1 class="dtitle fade" style="animation-delay: 0.152s">{{ title }}</h1>

          <div class="metaline fade" style="animation-delay: 0.194s">
            <span v-if="condition">{{ condition }}</span>
            <span v-if="condition && categoryName"> · </span>
            <span v-if="categoryName">{{ categoryName }}</span>
          </div>

          <div v-if="tradePlace" class="placecard fade" style="animation-delay: 0.236s">
            <span class="pin">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
                <path d="M8 1.4c-2.6 0-4.7 2.1-4.7 4.7 0 3.4 4.7 8.5 4.7 8.5s4.7-5.1 4.7-8.5c0-2.6-2.1-4.7-4.7-4.7zm0 6.6a1.9 1.9 0 110-3.8 1.9 1.9 0 010 3.8z" />
              </svg>
            </span>
            <div>
              <b>{{ tradePlace }}</b>
              <span>校内当面自提 · 平台不提供配送</span>
            </div>
          </div>

          <p v-if="description" class="desc fade" style="animation-delay: 0.278s">{{ description }}</p>
          <p v-else class="desc desc-empty fade" style="animation-delay: 0.278s">卖家没有填写描述</p>

          <p v-if="actionHint" class="orderhint fade" style="animation-delay: 0.32s">{{ actionHint }}</p>

          <div v-if="sellerId" class="seller press fade" style="animation-delay: 0.362s" @click="goSeller">
            <span class="av">
              <img v-if="sellerAvatar" :src="sellerAvatar" alt="" />
              <svg v-else width="19" height="19" viewBox="0 0 24 24" fill="currentColor">
                <circle cx="12" cy="8.4" r="4.1" />
                <path d="M12 14.4c-4.4 0-7.6 2.5-7.6 6.1h15.2c0-3.6-3.2-6.1-7.6-6.1z" />
              </svg>
            </span>
            <div class="nm">
              <b>{{ sellerName }}</b>
              <span>{{ sellerMeta || SCHOOL }}</span>
            </div>
            <span class="chev">
              <svg width="16" height="16" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M7.8 4.6L13.2 10l-5.4 5.4" />
              </svg>
            </span>
          </div>
        </div>
      </template>
    </div>

    <div v-if="!loading && !error" class="actionbar">
      <button
        class="fav press"
        :class="{ on: favOn, beat: favBeat, off: owned }"
        :aria-pressed="favOn"
        @click="toggleFav"
      >
        <svg v-if="favOn" width="20" height="20" viewBox="0 0 24 24" fill="currentColor" class="heart">
          <path d="M12 20.4s-7.9-4.8-7.9-10.1A4.7 4.7 0 0112 7a4.7 4.7 0 017.9 3.3c0 5.3-7.9 10.1-7.9 10.1z" />
        </svg>
        <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round">
          <path d="M12 20s-7.6-4.6-7.6-9.7A4.4 4.4 0 0112 7.2a4.4 4.4 0 017.6 3.1C19.6 15.4 12 20 12 20z" />
        </svg>
        <span>{{ favOn ? '已收藏' : '收藏' }}</span>
      </button>

      <button class="act2 press" :class="{ off: owned }" @click="openContact">
        <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round">
          <path d="M6.6 3.9h3l1.6 3.9-1.9 1.2a11.2 11.2 0 005.8 5.8l1.2-1.9 3.9 1.6v3a2 2 0 01-2.2 2A16.6 16.6 0 014.6 6.1a2 2 0 012-2.2z" />
        </svg>
        <span>联系卖家</span>
      </button>

      <button class="btn-main" :disabled="mainAction.disabled" @click="askOrder">
        {{ mainAction.text }}
      </button>
    </div>

    <!-- ── 浮层：下单确认层 / 联系卖家层（同一个面板内切换，不重新上滑） ── -->
    <ActionSheet :visible="!!sheet" @close="sheet = ''">
      <template v-if="sheet === 'order'">
        <h3>已和卖家确认好见面时间了吗？</h3>
        <p>
          本项目为<b>校内当面交易</b>。下单后商品会立刻为你锁定 <b>30 分钟</b>，超时未确认付款会自动取消。
        </p>
        <p class="tip">卖家可能正在上课或不在校，建议先联系确认时间地点，再下单。</p>
        <div class="sb2">
          <button class="bg" @click="((sheet = ''), openContact())">先联系卖家</button>
          <button class="bs" :disabled="creating" @click="confirmOrder">
            {{ creating ? '提交中…' : '确认下单' }}
          </button>
        </div>
      </template>

      <template v-else-if="sheet === 'contact'">
        <h3>联系卖家</h3>
        <div class="phone-row">
          <span v-if="phoneLoading" class="ph ph-sk">加载中…</span>
          <span v-else-if="phone" class="ph">{{ phoneText }}</span>
          <span v-else class="ph ph-err">{{ phoneError || '暂无联系方式' }}</span>
          <button v-if="phone" class="cp press" @click="copyPhone">复制</button>
          <button v-else-if="phoneError" class="cp press" @click="loadPhone">重试</button>
        </div>
        <p class="tip">手机号同微信 · 仅登录后可见，游客与爬虫看不到</p>
        <div class="sb2">
          <button class="bg" @click="sheet = ''">关闭</button>
          <button class="bs" :disabled="mainAction.disabled" @click="askOrder">去下单</button>
        </div>
      </template>
    </ActionSheet>
  </div>
</template>

<style scoped>
/* ================= 浮动返回键 ================= */
.navfloat {
  position: absolute;
  top: calc(var(--safe-top) + 10px);
  left: 0;
  right: 0;
  z-index: 15;
  display: flex;
  align-items: center;
  padding: 0 16px;
  pointer-events: none;
}
.nbtn {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.74);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  color: var(--text);
  box-shadow: 0 1px 6px rgba(28, 28, 30, 0.1);
  pointer-events: auto;
}

/* ================= 大图轮播 =================
   沉浸式：图片顶到屏顶，状态栏与返回键浮在图上。
   transform-origin 由 FLIP 接管（见 utils/flip.js），这里只保证尺寸确定。 */
.gallery {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  background: #f4f6fa;
  will-change: transform;
}
.rail {
  display: flex;
  height: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  scroll-snap-type: x mandatory;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.rail::-webkit-scrollbar {
  display: none;
}
.slide {
  flex: 0 0 100%;
  height: 100%;
  scroll-snap-align: start;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  color: #c9c9ce;
}
.slide img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.slide.t1 { background: linear-gradient(150deg, #fff3f7, #fbe2ec); }
.slide.t2 { background: linear-gradient(150deg, #f5f7fc, #e4eaf4); }
.slide.t3 { background: linear-gradient(150deg, #f1f9f4, #e0f1e8); }
.slide.t4 { background: linear-gradient(150deg, #fdf8f2, #f3eade); }

.badge {
  position: absolute;
  top: calc(var(--safe-top) + 14px);
  right: 16px;
  height: 24px;
  padding: 0 10px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  font-size: 11.5px;
  font-weight: 500;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: var(--text-2);
  box-shadow: 0 2px 8px rgba(28, 28, 30, 0.08);
}
.badge.warn { color: var(--orange); }
.badge.err { color: var(--red); }
.badge.ok { color: var(--green); }
.badge.dead { color: var(--text-2); }

.dots {
  position: absolute;
  bottom: 14px;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  gap: 6px;
}
.dots i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(28, 28, 30, 0.2);
  transition: all 0.3s var(--ease);
}
.dots i.on {
  width: 18px;
  border-radius: 3px;
  background: var(--pink);
}

/* ================= 内容区 ================= */
.detail {
  padding: 14px 16px 24px;
}

.prow-big {
  display: flex;
  align-items: baseline;
  gap: 7px;
}
.price-big {
  font-size: var(--fs-display);
  font-weight: 600;
  color: var(--pink);
  letter-spacing: -0.7px;
  font-variant-numeric: tabular-nums;
}
.price-big small {
  font-size: 16px;
  font-weight: 600;
  margin-right: 1px;
}
.origin-big {
  font-size: 12.5px;
  color: var(--text-4);
  text-decoration: line-through;
}
.pmeta {
  margin-left: auto;
  align-self: flex-end;
  padding-bottom: 5px;
  font-size: 11.5px;
  color: var(--text-3);
}

.dtitle {
  margin-top: 10px;
  font-size: 16.5px;
  font-weight: 600;
  line-height: 1.42;
  letter-spacing: -0.2px;
}
.metaline {
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--text-2);
}

/* 地点卡：白底细边，刻意不用粉底 —— 粉色整屏不超过 4 处 */
.placecard {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-top: 16px;
  padding: 13px 14px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid var(--line);
  box-shadow: 0 1px 5px rgba(28, 28, 30, 0.035);
}
.pin {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--pink-bg);
  color: var(--pink);
}
.placecard b {
  display: block;
  font-size: 14px;
  font-weight: 600;
}
.placecard span {
  display: block;
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--text-2);
}

.desc {
  margin-top: 20px;
  font-size: 13.5px;
  line-height: 1.75;
  color: #48484a;
  white-space: pre-wrap;
  word-break: break-word;
}
.desc-empty {
  color: var(--text-3);
}

.orderhint {
  margin-top: 18px;
  font-size: 11.5px;
  line-height: 1.65;
  text-align: center;
  color: #a0a0a5;
}

.seller {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
  cursor: pointer;
}
.av {
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--grad-avatar);
  color: #fff;
  overflow: hidden;
}
.av img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.seller .nm {
  flex: 1;
  min-width: 0;
}
.seller b {
  display: block;
  font-size: 14.5px;
  font-weight: 600;
}
.seller span {
  display: block;
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--text-2);
}
.chev {
  color: #d1d1d6;
}

/* ================= 底部操作条 ================= */
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
.fav,
.act2 {
  width: 60px;
  flex: 0 0 60px;
  height: 48px;
  border-radius: var(--r-btn);
  background: var(--field);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  font-size: 10px;
  color: #3a3a3c;
}
.act2 {
  width: 72px;
  flex: 0 0 72px;
}
.fav.on {
  color: var(--pink);
  background: var(--pink-bg);
}
.fav.off,
.act2.off {
  opacity: 0.4;
  cursor: not-allowed;
}
.actionbar .btn-main {
  flex: 1;
  min-width: 0;
  height: 48px;
  font-size: 15.5px;
  letter-spacing: 0.3px;
  padding: 0 8px;
}

/* 爱心心跳：收藏成功时播一次 */
.fav.beat .heart {
  animation: beat 0.62s var(--ease-pop);
}
@keyframes beat {
  0% { transform: scale(0.72); }
  35% { transform: scale(1.26); }
  60% { transform: scale(0.94); }
  80% { transform: scale(1.08); }
  100% { transform: scale(1); }
}

/* ================= 浮层内部 ================= */
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
p.tip {
  font-size: 11.5px;
  color: #a0a0a5;
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

.phone-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding: 15px 16px;
  border-radius: 14px;
  background: var(--pink-mist);
  border: 1px solid #fbe2eb;
}
.phone-row .ph {
  font-size: 19px;
  font-weight: 600;
  color: var(--pink-dp);
  letter-spacing: 0.4px;
  font-variant-numeric: tabular-nums;
}
.phone-row .ph-sk,
.phone-row .ph-err {
  font-size: 13px;
  font-weight: 400;
  letter-spacing: 0;
  color: var(--text-2);
}
.phone-row .cp {
  margin-left: auto;
  height: 32px;
  padding: 0 14px;
  border-radius: 9px;
  background: #fff;
  color: var(--pink-dp);
  font-size: 12.5px;
  font-weight: 500;
  box-shadow: 0 1px 4px rgba(28, 28, 30, 0.08);
}

/* ================= 三态 ================= */
.sk-gallery {
  /* 现在它是 .gallery 的子元素（父容器已定 1:1），撑满即可 */
  width: 100%;
  height: 100%;
  background: linear-gradient(100deg, #f4f4f6 30%, #ececf0 50%, #f4f4f6 70%);
  background-size: 220% 100%;
  animation: shimmer 1.25s linear infinite;
}
.sk-line {
  height: 12px;
  margin-top: 14px;
  border-radius: 6px;
  background: linear-gradient(100deg, #f4f4f6 30%, #ececf0 50%, #f4f4f6 70%);
  background-size: 220% 100%;
  animation: shimmer 1.25s linear infinite;
}
.sk-line.tall { height: 20px; }
.sk-line.w34 { width: 34%; }
.sk-line.w46 { width: 46%; }
.sk-line.w78 { width: 78%; }
.sk-line.w86 { width: 86%; }
.sk-line.w92 { width: 92%; }
.sk-card {
  height: 76px;
  margin-top: 18px;
  border-radius: 14px;
  background: linear-gradient(100deg, #f4f4f6 30%, #ececf0 50%, #f4f4f6 70%);
  background-size: 220% 100%;
  animation: shimmer 1.25s linear infinite;
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

/* ================= 动效 ================= */
/* 内容逐条淡入上移 8px：首条 110ms，其后每条 +42ms（见设计文档 §3.5） */
.fade {
  animation: fadeUp 0.5s var(--ease) backwards;
}
@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
/* 返回时内容先淡出，只留大图做缩回动画 */
.page.leaving .detail,
.page.leaving .actionbar {
  opacity: 0;
  transition: opacity 0.34s linear;
}
</style>
