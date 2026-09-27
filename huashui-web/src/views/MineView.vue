<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import * as productApi from '@/api/product'
import * as orderApi from '@/api/order'
import * as favoriteApi from '@/api/favorite'
import { toastOk } from '@/composables/useToast'
import { useNotice, buildNotices, refreshNoticeFrom } from '@/composables/useNotice'
import { PRODUCT_STATUS } from '@/constants/enums'

/* 「我的」聚合页。
   角标口径：后端没有计数接口，用三个轻请求拿 total。
   商品用 size=50 一次拉全量在前端分状态计数（个人商品量级远小于 50，
   同时省掉「在售 / 已售出 / 待审核 / 已驳回」四个请求）。

   通知红点：**不在这里自己算**，而是复用通知中心那份聚合口径
   （`buildNotices` + `refreshNoticeFrom`）—— 本页本来就会拉一次商品列表，
   再补一个订单列表即可，红点与通知中心永远一致。 */

const router = useRouter()
const store = useUserStore()

const SCHOOL = '华北水利水电大学'

const loading = ref(true)
const productStats = ref({ onSale: 0, sold: 0 })
const favCount = ref(0)

const { hasNotice, checkNotice } = useNotice()

const nickname = computed(() => store.profile?.nickname || store.profile?.username || '同学')
const dept = computed(() => store.profile?.dept || '')
const avatar = computed(() => store.profile?.avatar || '')

const MENUS = [
  { key: 'products', label: '我的发布', to: '/user/products', icon: 'box' },
  { key: 'orders', label: '我的订单', to: '/user/orders', icon: 'doc' },
  { key: 'favorites', label: '我的收藏', to: '/user/favorites', icon: 'heart' }
]

const MENU_SECOND = [
  { key: 'notices', label: '通知中心', to: '/notices', icon: 'bell', dot: true },
  { key: 'profile', label: '个人资料', to: '/user/profile', icon: 'user' }
]

onMounted(async () => {
  loading.value = true
  // 三个请求互相独立，并行发；任一失败只影响对应那块，不让整页报错
  const [products, favorites, orders] = await Promise.allSettled([
    productApi.listMyProducts({ page: 1, size: 50 }),
    favoriteApi.listMyFavorites({ page: 1, size: 1 }),
    orderApi.listMyOrders({ role: 'all', page: 1, size: 50 })
  ])

  const productList =
    products.status === 'fulfilled' ? products.value.records || [] : []
  if (products.status === 'fulfilled') {
    productStats.value = {
      onSale: productList.filter((p) => p.status === PRODUCT_STATUS.ON_SALE).length,
      sold: productList.filter((p) => p.status === PRODUCT_STATUS.SOLD).length
    }
  }
  if (favorites.status === 'fulfilled') favCount.value = favorites.value.total || 0

  /* 红点：两份列表都拿到了就地聚合（零额外请求）；
     任一失败才退回 checkNotice() 的轻量重算，避免把「网络失败」显示成「没有通知」 */
  if (products.status === 'fulfilled' && orders.status === 'fulfilled') {
    refreshNoticeFrom(buildNotices(productList, orders.value.records || []))
  } else {
    checkNotice(true)
  }

  loading.value = false
})

function go(menu) {
  router.push(menu.to)
}

function logout() {
  store.logout()
  toastOk('已退出登录')
  router.replace('/')
}
</script>

<template>
  <div class="page">
    <div class="page-scroll safe-top">
      <!-- 顶部归属感区：粉 → 白 渐变 -->
      <div class="minehead">
        <div class="mrow">
          <span class="avatar big">
            <img v-if="avatar" :src="avatar" alt="" />
            <svg v-else width="26" height="26" viewBox="0 0 24 24" fill="currentColor">
              <circle cx="12" cy="8.4" r="4.1" />
              <path d="M12 14.4c-4.4 0-7.6 2.5-7.6 6.1h15.2c0-3.6-3.2-6.1-7.6-6.1z" />
            </svg>
          </span>
          <div class="who">
            <b>{{ nickname }}</b>
            <span>{{ dept ? `${dept} · ${SCHOOL}` : SCHOOL }}</span>
          </div>
        </div>
        <div class="stats">
          <div><b>{{ productStats.onSale }}</b><span>在售</span></div>
          <div><b>{{ productStats.sold }}</b><span>已售出</span></div>
          <div><b>{{ favCount }}</b><span>收藏</span></div>
        </div>
      </div>

      <div class="mlist rise">
        <button v-for="m in MENUS" :key="m.key" class="mitem press" @click="go(m)">
          <span class="mico">
            <svg v-if="m.icon === 'box'" width="16" height="16" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linejoin="round"><path d="M2.8 7.2L10 3.4l7.2 3.8v6.6a1 1 0 01-1 1H3.8a1 1 0 01-1-1V7.2z" /><path d="M2.8 7.2h14.4" /></svg>
            <svg v-else-if="m.icon === 'doc'" width="16" height="16" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linejoin="round"><rect x="4.2" y="2.6" width="11.6" height="14.8" rx="1.6" /><path d="M7.2 7.2h5.6M7.2 10.4h5.6M7.2 13.6h3.4" /></svg>
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M12 20s-7.6-4.6-7.6-9.7A4.4 4.4 0 0112 7.2a4.4 4.4 0 017.6 3.1C19.6 15.4 12 20 12 20z" /></svg>
          </span>
          <b>{{ m.label }}</b>
          <span class="chev">
            <svg width="15" height="15" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M7.8 4.6L13.2 10l-5.4 5.4" /></svg>
          </span>
        </button>
      </div>

      <div class="mlist second rise rise-1">
        <button v-for="m in MENU_SECOND" :key="m.key" class="mitem press" @click="go(m)">
          <span class="mico">
            <svg v-if="m.icon === 'bell'" width="16" height="16" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8a4 4 0 118 0c0 3.4 1.2 4.4 1.2 4.4H4.8S6 11.4 6 8z" /><path d="M8.6 15a1.7 1.7 0 002.8 0" /></svg>
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"><circle cx="12" cy="8" r="3.9" /><path d="M4.6 20.4c.9-3.7 3.8-5.6 7.4-5.6s6.5 1.9 7.4 5.6" /></svg>
          </span>
          <b>{{ m.label }}</b>
          <i v-if="m.dot && hasNotice" class="mdot" />
          <span class="chev">
            <svg width="15" height="15" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M7.8 4.6L13.2 10l-5.4 5.4" /></svg>
          </span>
        </button>

        <button class="mitem danger press" @click="logout">
          <span class="mico">
            <svg width="16" height="16" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M8 3.4H6.2a1.6 1.6 0 00-1.6 1.6v10a1.6 1.6 0 001.6 1.6H8" /><path d="M12.6 13l3.4-3-3.4-3M16 10H8" /></svg>
          </span>
          <b>退出登录</b>
        </button>
      </div>

      <div class="tail">华水闲置 · 校内当面自提</div>
    </div>
  </div>
</template>

<style scoped>
.safe-top {
  padding-top: var(--safe-top);
}

.minehead {
  padding: 18px 16px 20px;
  background: var(--grad-mine);
}
.mrow {
  display: flex;
  align-items: center;
  gap: 14px;
}
.avatar.big {
  width: 58px;
  height: 58px;
  flex: 0 0 58px;
}
.who b {
  display: block;
  font-size: var(--fs-3);
  font-weight: 600;
  letter-spacing: -0.2px;
}
.who span {
  display: block;
  margin-top: 5px;
  font-size: var(--fs-1);
  color: var(--text-2);
}

.stats {
  display: flex;
  margin-top: 20px;
}
.stats > div {
  flex: 1;
  text-align: center;
}
.stats b {
  display: block;
  font-size: 19px;
  font-weight: 600;
  color: var(--text);
  font-variant-numeric: tabular-nums;
}
.stats span {
  display: block;
  margin-top: 3px;
  font-size: var(--fs-1);
  color: var(--text-2);
}

.mlist {
  margin: 12px 16px 0;
  border-radius: var(--r-card);
  background: #fff;
  box-shadow: var(--sh-card);
  overflow: hidden;
}
.mitem {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 15px 16px;
  text-align: left;
  border-bottom: 1px solid var(--line);
}
.mitem:last-child {
  border-bottom: none;
}
.mico {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 9px;
  background: var(--pink-bg);
  color: var(--pink-dp);
}
.mitem b {
  font-size: var(--fs-2);
  font-weight: 500;
  color: var(--text);
}
.chev {
  margin-left: auto;
  color: var(--text-4);
}
.mdot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--red);
}
.mitem.danger .mico {
  background: #fff1f1;
  color: var(--red);
}
.mitem.danger b {
  color: var(--red);
}

.tail {
  padding: 24px 0 30px;
  text-align: center;
  font-size: 10.5px;
  color: var(--text-3);
}
</style>
