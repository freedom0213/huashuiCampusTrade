<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProductCard from '@/components/ProductCard.vue'
import PageState from '@/components/PageState.vue'
import { getUserBrief } from '@/api/user'
import { getSoldCount } from '@/api/order'
import { useProductList } from '@/composables/useProductList'
import { captureCard, consumeReturn, restoreScroll } from '@/utils/flip'

/* 卖家主页 —— 二手交易的信任建立在它上面：
   「已成交 N 笔」是核心信任信号，数据来自 order-service 的 sold-count（口径：已付款 + 交易完成，已取消不计）。
   三个接口全部对游客开放（白名单），未登录也能看，只在交易动作上才要求登录。 */

const route = useRoute()
const router = useRouter()

const SCHOOL = '华北水利水电大学'
const sellerId = computed(() => String(route.params.id || ''))

const seller = ref(null)
const soldCount = ref(null)
const profileError = ref('')

const list = useProductList({ sellerId: sellerId.value, size: 50 })

/* 滚动容器 + 「从详情页返回」的还原标记（见 utils/flip.js） */
const scrollerEl = ref(null)
const restoring = ref(false)

const nickname = computed(() => seller.value?.nickname || seller.value?.username || '卖家')
const dept = computed(() => seller.value?.dept || '')
const avatar = computed(() => seller.value?.avatar || '')

onMounted(async () => {
  restoring.value = consumeReturn()
  await list.load(true)
  await nextTick()
  restoreScroll(scrollerEl.value)
  // 资料与成交数互不依赖，并行拿；任一失败只影响对应的那块信息，不让整页失败
  const [brief, sold] = await Promise.allSettled([
    getUserBrief(sellerId.value),
    getSoldCount(sellerId.value)
  ])
  if (brief.status === 'fulfilled') seller.value = brief.value
  else profileError.value = brief.reason?.message || '卖家信息加载失败'
  if (sold.status === 'fulfilled') soldCount.value = sold.value
})

function open(product, ev) {
  // 记录卡片位置，供详情页播放「卡片放大成详情」的 FLIP 动画
  captureCard(ev?.currentTarget, product.id, scrollerEl.value)
  router.push(`/product/${product.id}`)
}
function back() {
  router.back()
}
</script>

<template>
  <div class="page">
    <div class="sellerhead">
      <div class="navrow">
        <button class="iconbtn press" aria-label="返回" @click="back">
          <svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12.2 4.6L6.8 10l5.4 5.4" />
          </svg>
        </button>
      </div>

      <div class="sellerrow">
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

      <div class="pillrow">
        <span class="pill">
          <svg width="12" height="12" viewBox="0 0 16 16" fill="#DB5A8A">
            <path d="M8 1.6a6.4 6.4 0 100 12.8A6.4 6.4 0 008 1.6zm3 4.9l-3.7 3.7a.8.8 0 01-1.1 0L4.9 8.9a.8.8 0 111.1-1.1l.8.8 3.1-3.1a.8.8 0 111.1 1.1z" />
          </svg>
          已成交 {{ soldCount === null ? '—' : soldCount }} 笔
        </span>
        <span class="pill">在售 {{ list.total.value }} 件</span>
      </div>
    </div>

    <div ref="scrollerEl" class="page-scroll" :class="{ restoring }" @scroll="list.onScroll">
      <div class="pad">
        <p v-if="profileError" class="warn">{{ profileError }}</p>

        <div class="sec-head">
          <h2>TA 的在售</h2>
          <a>{{ list.total.value }} 件</a>
        </div>

        <PageState
          :loading="list.loading.value"
          :error="list.error.value"
          :empty="!list.loading.value && !list.error.value && !list.items.value.length"
          empty-text="TA 暂时没有在售商品"
          :skeleton-count="4"
          @retry="list.load(true)"
        />
        <div v-if="list.items.value.length" class="grid">
          <ProductCard
            v-for="(p, i) in list.items.value"
            :key="p.id"
            :product="p"
            :index="i"
            @tap="open"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sellerhead {
  flex: 0 0 auto;
  padding: calc(var(--safe-top) + 4px) 16px 20px;
  background: var(--grad-mine);
}
.navrow {
  display: flex;
  align-items: center;
}
.iconbtn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  margin-left: -8px;
  color: var(--text);
}

.sellerrow {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-top: 16px;
}
.avatar.big {
  width: 58px;
  height: 58px;
  flex: 0 0 58px;
}
.who b {
  display: block;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.2px;
}
.who span {
  display: block;
  margin-top: 4px;
  font-size: 11.5px;
  color: #9a7a88;
}

.pillrow {
  display: flex;
  gap: 8px;
  margin-top: 15px;
  flex-wrap: wrap;
}
.pill {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 11px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  font-size: 11.5px;
  color: #8a5a70;
}

.pad {
  padding: 0 16px 20px;
}
.warn {
  padding: 12px 0 0;
  font-size: var(--fs-1);
  color: var(--orange);
}

/* 从详情页缩回时，卡片不再播一次入场动画（否则两者会叠加成"抖一下"） */
.page-scroll.restoring :deep(.pcard) {
  animation: none;
}
</style>
