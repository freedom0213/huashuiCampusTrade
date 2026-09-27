<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProductCard from '@/components/ProductCard.vue'
import PageState from '@/components/PageState.vue'
import OptionSheet from '@/components/OptionSheet.vue'
import { listCategories } from '@/api/category'
import { useProductList } from '@/composables/useProductList'
import { CAMPUS_LIST, CONDITION_FILTER_OPTIONS, SORT_OPTIONS } from '@/constants/enums'

/* 商品列表 / 搜索结果 —— 全站唯一的「筛选与排序」落点。
   首页的分类胶囊与「查看全部」都跳到这里，所以这套逻辑只需实现一次。

   ⚠️ 后端不支持价格区间筛选（ProductQueryDTO 无 priceMin/priceMax），
      故此处**不做**价格区间筛选：前端做客户端过滤会让 total 与实际条数对不上。 */

const route = useRoute()
const router = useRouter()

const kw = ref(String(route.query.kw || ''))
const categories = ref([])

const sort = ref(String(route.query.sort || 'newest'))
const campus = ref(route.query.campus ? String(route.query.campus) : null)
const condition = ref(
  route.query.conditionLevel !== undefined && route.query.conditionLevel !== ''
    ? Number(route.query.conditionLevel)
    : null
)
const categoryId = ref(route.query.categoryId ? String(route.query.categoryId) : null)

const CAMPUS_OPTIONS = [{ value: null, label: '不限' }].concat(
  CAMPUS_LIST.map((c) => ({ value: c, label: `${c}校区` }))
)

const list = useProductList(buildParams())

const sheet = ref('')

const title = computed(() => {
  if (kw.value) return kw.value
  const c = categories.value.find((x) => String(x.id) === categoryId.value)
  return c ? c.name : '搜索结果'
})

const sortLabel = computed(
  () => SORT_OPTIONS.find((o) => o.value === sort.value)?.label || '排序'
)
const campusLabel = computed(() => (campus.value ? `${campus.value}校区` : '校区'))
const conditionLabel = computed(
  () => CONDITION_FILTER_OPTIONS.find((o) => o.value === condition.value)?.label || '成色'
)

/** 只带上真正有值的参数，避免 `campus=null` 这类脏串进 URL 与请求 */
function buildParams() {
  const p = { sort: sort.value, size: 20 }
  if (kw.value.trim()) p.kw = kw.value.trim()
  if (categoryId.value) p.categoryId = categoryId.value
  if (campus.value) p.campus = campus.value
  if (condition.value !== null) p.conditionLevel = condition.value
  return p
}

/** 条件变了：同步 URL（replace 避免刷历史）→ 重新请求 */
function apply() {
  const p = buildParams()
  const query = {}
  Object.keys(p).forEach((k) => {
    if (k !== 'size') query[k] = p[k]
  })
  router.replace({ path: '/search', query })
  list.setParams(p)
}

function search() {
  apply()
}

onMounted(async () => {
  list.load(true)
  try {
    categories.value = (await listCategories()) || []
  } catch {
    categories.value = []
  }
})

function open(product) {
  router.push(`/product/${product.id}`)
}
function back() {
  router.back()
}
</script>

<template>
  <div class="page">
    <header class="nav-bar">
      <button class="nav-left press" aria-label="返回" @click="back">
        <svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12.2 4.6L6.8 10l5.4 5.4" />
        </svg>
      </button>
      <span class="ttl">{{ title }}</span>
    </header>

    <div class="searchrow">
      <label class="sbox">
        <svg width="15" height="15" viewBox="0 0 17 17" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
          <circle cx="7.4" cy="7.4" r="5.2" />
          <path d="M11.4 11.4L15 15" />
        </svg>
        <input v-model="kw" placeholder="搜索教材、数码、生活用品" @keyup.enter="search" />
        <button v-if="kw" class="clr press" @click="((kw = ''), search())">×</button>
      </label>
      <button class="sbtn press" @click="search">搜索</button>
    </div>

    <div class="filters">
      <button class="fchip press" :class="{ on: sort !== 'newest' }" @click="sheet = 'sort'">
        {{ sortLabel }}
        <svg width="8" height="8" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M2.8 4.6L6 7.8l3.2-3.2" /></svg>
      </button>
      <button class="fchip press" :class="{ on: !!campus }" @click="sheet = 'campus'">
        {{ campusLabel }}
        <svg width="8" height="8" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M2.8 4.6L6 7.8l3.2-3.2" /></svg>
      </button>
      <button class="fchip press" :class="{ on: condition !== null }" @click="sheet = 'condition'">
        {{ conditionLabel }}
        <svg width="8" height="8" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M2.8 4.6L6 7.8l3.2-3.2" /></svg>
      </button>
    </div>

    <div class="page-scroll" @scroll="list.onScroll">
      <p v-if="list.loaded.value && !list.error.value" class="resultbar">
        {{ list.total.value > 0 ? `找到 ${list.total.value} 件商品` : '没有找到相关商品' }}
      </p>

      <div class="pad">
        <PageState
          :loading="list.loading.value"
          :error="list.error.value"
          :empty="!list.loading.value && !list.error.value && list.loaded.value && !list.items.value.length"
          empty-text="没有找到相关商品"
          empty-hint="试试换个关键词，或放宽校区 / 成色筛选"
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
        <p v-if="list.loadingMore.value" class="more">加载中…</p>
        <p v-else-if="list.finished.value && list.items.value.length" class="more">没有更多了</p>
      </div>
    </div>

    <OptionSheet
      :visible="sheet === 'sort'"
      title="排序方式"
      :options="SORT_OPTIONS"
      :model-value="sort"
      @update:model-value="(v) => ((sort = v), apply())"
      @close="sheet = ''"
    />
    <OptionSheet
      :visible="sheet === 'campus'"
      title="校区"
      :options="CAMPUS_OPTIONS"
      :model-value="campus"
      @update:model-value="(v) => ((campus = v), apply())"
      @close="sheet = ''"
    />
    <OptionSheet
      :visible="sheet === 'condition'"
      title="成色（含更好）"
      :options="CONDITION_FILTER_OPTIONS"
      :model-value="condition"
      @update:model-value="(v) => ((condition = v), apply())"
      @close="sheet = ''"
    />
  </div>
</template>

<style scoped>
.ttl {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.searchrow {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 16px 0;
}
.sbox {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 12px;
  border-radius: 11px;
  background: var(--field);
  color: var(--text-3);
}
.sbox input {
  flex: 1;
  min-width: 0;
  font-size: 13.5px;
  color: var(--text);
}
.sbox input:focus {
  outline: none;
}
.clr {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--text-4);
  color: #fff;
  font-size: 12px;
  line-height: 1;
}
.sbtn {
  font-size: var(--fs-2);
  color: var(--pink-dp);
}

.filters {
  display: flex;
  gap: 8px;
  padding: 13px 16px 0;
  overflow-x: auto;
  scrollbar-width: none;
}
.filters::-webkit-scrollbar {
  display: none;
}
.fchip {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 3px;
  height: 27px;
  padding: 0 10px;
  border-radius: 8px;
  background: var(--field);
  font-size: 11.5px;
  color: #5a5a5e;
}
.fchip.on {
  background: var(--pink-bg);
  color: var(--pink-dp);
  font-weight: 500;
}

.resultbar {
  padding: 15px 16px 0;
  font-size: 11.5px;
  color: var(--text-2);
}
.pad {
  padding: 12px 16px 20px;
}
</style>
