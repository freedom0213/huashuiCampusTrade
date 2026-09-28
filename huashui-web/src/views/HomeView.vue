<script setup>
import { onActivated, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ProductCard from '@/components/ProductCard.vue'
import PageState from '@/components/PageState.vue'
import { listCategories } from '@/api/category'
import { useProductList } from '@/composables/useProductList'
import { useNotice } from '@/composables/useNotice'
import { useListReturn } from '@/composables/useListReturn'
import { captureCard } from '@/utils/flip'

/* 首页 = 推荐位，只有「热门推荐 + 最新发布」两个固定分区。
   分类胶囊与「查看全部」一律跳 /search，**不在首页原地切换**——
   排序与筛选只实现在 /search 一处，首页原地过滤等于把那套逻辑再写一遍。
   据此，分类胶囊也**不做选中态**（点击即跳走，不需要保留状态）。 */

const router = useRouter()
const SCHOOL = '华北水利水电大学'

const categories = ref([])
const { hasNotice, checkNotice } = useNotice()

/* 热门推荐只出 4 个（设计文档 §4.1：「最多 4 个，右侧查看全部」），
   2 列栅格正好两行；想看更多的走「查看全部 → /search?sort=views」。
   最新发布是首页的第二个分区，首屏看不到，滚动后出现，所以给 20 条 + 触底加载。 */
const hot = useProductList({ sort: 'views', size: 4 })
const latest = useProductList({ sort: 'newest', size: 20 })

/* 从详情页返回时：还原滚动位置 + 不重播卡片入场动画
   （卡片本来就在屏幕上，再"入场"一次会和缩回动画撞在一起）
   🔴 本页被 KeepAlive 缓存：首次加载在 onMounted（只跑一次），
      返回恢复在 onActivated（useListReturn 内部）。 */
const scrollerEl = ref(null)
const { restoring } = useListReturn(scrollerEl)

onMounted(() => {
  loadCategories()
  hot.load(true)
  latest.load(true)
})

/* 每次激活都重算通知红点（退出/切账号后回来不会残留上一个账号的红点） */
onActivated(() => checkNotice())

async function loadCategories() {
  try {
    categories.value = (await listCategories()) || []
  } catch {
    // 分类拿不到不该让首页失败：胶囊行自动退化为只有「全部」
    categories.value = []
  }
}

function open(product, ev) {
  // 记录卡片位置，供详情页播放「卡片放大成详情」的 FLIP 动画
  captureCard(ev?.currentTarget, product.id, scrollerEl.value)
  router.push(`/product/${product.id}`)
}
function goSearch(query = {}) {
  router.push({ path: '/search', query })
}
</script>

<template>
  <div class="page">
    <div ref="scrollerEl" class="page-scroll safe-top" :class="{ restoring }" @scroll="latest.onScroll">
      <!-- 顶部：学校 + 通知入口（两层，不要再加第三层） -->
      <div class="home-top">
        <span class="campus">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
            <path d="M8 1.4c-2.6 0-4.7 2.1-4.7 4.7 0 3.4 4.7 8.5 4.7 8.5s4.7-5.1 4.7-8.5c0-2.6-2.1-4.7-4.7-4.7zm0 6.6a1.9 1.9 0 110-3.8 1.9 1.9 0 010 3.8z" />
          </svg>
          <span>{{ SCHOOL }}</span>
        </span>
        <button class="bell press" aria-label="通知中心" @click="router.push('/notices')">
          <svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
            <path d="M6 8a4 4 0 118 0c0 3.4 1.2 4.4 1.2 4.4H4.8S6 11.4 6 8z" />
            <path d="M8.6 15a1.7 1.7 0 002.8 0" />
          </svg>
          <i v-if="hasNotice" class="dot" />
        </button>
      </div>

      <!-- 搜索：点击进 /search，首页不做即时搜索 -->
      <button class="searchbox press" @click="goSearch()">
        <svg width="15" height="15" viewBox="0 0 17 17" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
          <circle cx="7.4" cy="7.4" r="5.2" />
          <path d="M11.4 11.4L15 15" />
        </svg>
        <span>搜索教材、数码、生活用品</span>
      </button>

      <!-- 分类胶囊：横向滚动，点击即跳搜索页 -->
      <div class="cats">
        <button class="chip press" @click="goSearch()">全部</button>
        <button
          v-for="c in categories"
          :key="c.id"
          class="chip press"
          @click="goSearch({ categoryId: c.id })"
        >
          {{ c.name }}
        </button>
      </div>

      <div class="pad">
        <section>
          <div class="sec-head">
            <h2>热门推荐</h2>
            <a class="press" @click="goSearch({ sort: 'views' })">查看全部 ›</a>
          </div>
          <PageState
            :loading="hot.loading.value"
            :error="hot.error.value"
            :empty="!hot.loading.value && !hot.error.value && !hot.items.value.length"
            empty-text="暂无商品"
            :skeleton-count="4"
            @retry="hot.load(true)"
          />
          <div v-if="hot.items.value.length" class="grid">
            <ProductCard
              v-for="(p, i) in hot.items.value"
              :key="p.id"
              :product="p"
              :index="i"
              @tap="open"
            />
          </div>
        </section>

        <section>
          <div class="sec-head">
            <h2>最新发布</h2>
            <a class="press" @click="goSearch({ sort: 'newest' })">查看全部 ›</a>
          </div>
          <PageState
            :loading="latest.loading.value"
            :error="latest.error.value"
            :empty="!latest.loading.value && !latest.error.value && !latest.items.value.length"
            empty-text="还没有人发布商品"
            empty-hint="成为第一个发布闲置的人吧"
            :skeleton-count="4"
            @retry="latest.load(true)"
          />
          <div v-if="latest.items.value.length" class="grid">
            <ProductCard
              v-for="(p, i) in latest.items.value"
              :key="p.id"
              :product="p"
              :index="i"
              @tap="open"
            />
          </div>
          <p v-if="latest.loadingMore.value" class="more">加载中…</p>
          <p v-else-if="latest.finished.value && latest.items.value.length" class="more">没有更多了</p>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.safe-top {
  padding-top: var(--safe-top);
}

/* 从详情页缩回时，卡片不再播一次入场动画（否则两者会叠加成"抖一下"） */
.page-scroll.restoring :deep(.pcard) {
  animation: none;
}

.home-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 0;
}
.campus {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: var(--fs-3);
  font-weight: 600;
  letter-spacing: -0.2px;
}
.campus svg {
  color: var(--pink);
}
.bell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: var(--text);
}
.bell .dot {
  position: absolute;
  top: 5px;
  right: 5px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--red);
  box-shadow: 0 0 0 1.5px #fff;
}

.searchbox {
  display: flex;
  align-items: center;
  gap: 8px;
  width: calc(100% - 32px);
  height: 40px;
  margin: 14px 16px 0;
  padding: 0 13px;
  border-radius: 12px;
  background: var(--field);
  color: var(--text-3);
  font-size: 13.5px;
  text-align: left;
}

.cats {
  display: flex;
  gap: 8px;
  padding: 14px 16px 2px;
  overflow-x: auto;
  scrollbar-width: none;
}
.cats::-webkit-scrollbar {
  display: none;
}

.pad {
  padding: 0 16px 20px;
}
</style>
