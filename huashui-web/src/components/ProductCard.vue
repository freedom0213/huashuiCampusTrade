<script setup>
import { computed } from 'vue'
import { formatPrice } from '@/utils/format'
import { CONDITION_DESC } from '@/constants/enums'

/* 商品卡 —— 卡片只放三层：图片 · 标题 · 价格行。
   地点与卖家昵称都不进卡片（2026-09-26 用户反馈：卡片数据太多、图片才是主体）。
   真实封面来自 coverUrl（/uploads/**）；没有封面时按序号轮换低饱和渐变，不用 emoji 凑数。

   出图方式由父级决定（emit tap 而不是自己跳路由）——
   块 3-B 的「卡片 ⇄ 详情」FLIP 转场需要父级拿到卡片 DOM 引用做首末位置计算。 */

const props = defineProps({
  product: { type: Object, required: true },
  /** 仅用于入场动画的错落延迟 */
  index: { type: Number, default: 0 },
  /** 收藏页用：卡片右上角的心形/状态遮罩由父级插槽提供 */
  showState: { type: Boolean, default: false }
})

defineEmits(['tap'])

const tone = computed(() => `t${(props.index % 4) + 1}`)
const delay = computed(() => `${((props.index % 6) + 1) * 0.05}s`)
const cover = computed(() => props.product.coverUrl || '')
const condition = computed(
  () => props.product.conditionDesc || CONDITION_DESC[props.product.conditionLevel] || ''
)
const price = computed(() => formatPrice(props.product.price))
const origin = computed(() =>
  props.product.originalPrice && Number(props.product.originalPrice) > Number(props.product.price)
    ? formatPrice(props.product.originalPrice)
    : ''
)
</script>

<template>
  <article class="pcard press" :style="{ animationDelay: delay }" @click="$emit('tap', product)">
    <div class="thumb" :class="tone">
      <img v-if="cover" :src="cover" :alt="product.title" loading="lazy" />
      <svg v-else class="ph" width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round">
        <rect x="3.2" y="4.6" width="17.6" height="14.8" rx="2.4" />
        <circle cx="8.8" cy="9.8" r="1.6" />
        <path d="M4.2 16.6l4.6-4.2 3.4 3 3-2.6 4.6 3.8" />
      </svg>
    </div>

    <div class="info">
      <h3>{{ product.title }}</h3>
      <div class="prow">
        <span class="price"><span class="rmb">¥</span>{{ price }}</span>
        <span v-if="origin" class="origin">¥{{ origin }}</span>
        <span v-if="condition" class="cond">{{ condition }}</span>
      </div>
    </div>

    <slot />
  </article>
</template>

<style scoped>
.pcard {
  position: relative;
  border-radius: 15px;
  background: #fff;
  box-shadow: var(--sh-card);
  overflow: hidden;
  cursor: pointer;
  animation: rise 0.55s var(--ease) backwards;
}

.thumb {
  position: relative;
  aspect-ratio: 1 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  color: #c9c9ce;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
/* 无封面时的低饱和渐变占位，按序号轮换保证栅格有层次 */
.t1 { background: linear-gradient(150deg, #fff3f7, #fbe2ec); }
.t2 { background: linear-gradient(150deg, #f5f7fc, #e7ecf5); }
.t3 { background: linear-gradient(150deg, #f1f9f4, #e0f1e8); }
.t4 { background: linear-gradient(150deg, #fdf8f2, #f3eade); }

.info {
  padding: 9px 10px 12px;
}
h3 {
  font-size: 12.5px;
  font-weight: 500;
  line-height: 1.34;
  color: #6e6e73;
  letter-spacing: -0.05px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.prow {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin-top: 6px;
}
.price {
  font-size: 15px;
  color: var(--pink);
}
.cond {
  margin-left: auto;
  font-size: 10px;
  color: var(--text-3);
}
</style>
