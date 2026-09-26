<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toastError, toastOk } from '@/composables/useToast'
import { CAMPUS_LIST, CONDITION_OPTIONS, ORDER_STATUS_DESC, PRODUCT_STATUS_DESC } from '@/constants/enums'

/* 块 1 的占位页：骨架 + 设计令牌的可视化验证。
   每个块开工时，用真实页面替换对应路由的 component 即可。 */

const route = useRoute()
const router = useRouter()

const title = computed(() => route.meta.title || '华水闲置')
const block = computed(() => route.meta.block)
const apis = computed(() => route.meta.apis || [])
const back = () => (window.history.length > 1 ? router.back() : router.push('/'))

const SWATCHES = [
  { name: '主色', var: '--pink', hex: '#EC6E9C' },
  { name: '浅粉', var: '--pink-lt', hex: '#F5A2C2' },
  { name: '深粉', var: '--pink-dp', hex: '#DB5A8A' },
  { name: '粉底', var: '--pink-bg', hex: '#FDEDF4' },
  { name: '正文', var: '--text', hex: '#1C1C1E' },
  { name: '次要', var: '--text-2', hex: '#8E8E93' },
  { name: '三级', var: '--text-3', hex: '#B8B8BD' },
  { name: '成功', var: '--green', hex: '#34C759' },
  { name: '待处理', var: '--orange', hex: '#FF9500' },
  { name: '驳回', var: '--red', hex: '#FF3B30' }
]

const FONT_STEPS = [
  { token: '--fs-display', size: '28px', use: '详情页价格' },
  { token: '--fs-4', size: '20px', use: '页面主标题' },
  { token: '--fs-3', size: '15px', use: '区块标题 / 价格 / 按钮' },
  { token: '--fs-2', size: '13px', use: '正文 / 卡片标题' },
  { token: '--fs-1', size: '11px', use: '辅助信息 / 标签' }
]
</script>

<template>
  <div class="page">
    <header class="nav-bar">
      <button v-if="!route.meta.tab" class="nav-left press" aria-label="返回" @click="back">
        <svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12.2 4.6L6.8 10l5.4 5.4" />
        </svg>
      </button>
      <span>{{ title }}</span>
    </header>

    <div class="page-scroll">
      <section class="card rise">
        <h3>{{ title }}</h3>
        <p v-if="block" class="hint">
          该页面计划在 <b>块 {{ block }}</b> 实现。当前是<b>骨架阶段</b>（块 1），
          工程结构、设计令牌、请求层与路由已就绪。
        </p>
        <p v-else class="hint">没有匹配到这个地址对应的页面。</p>
      </section>

      <section v-if="apis.length" class="card rise rise-1">
        <h4>本页需要用到的接口</h4>
        <ul class="apilist">
          <li v-for="a in apis" :key="a">
            <code>{{ a }}</code>
          </li>
        </ul>
        <p class="hint small">接口依据：<code>docs/03-前端接口核对清单.md</code>（实测后端代码产出）</p>
      </section>

      <section class="card rise rise-2">
        <h4>色板</h4>
        <div class="swatches">
          <div v-for="s in SWATCHES" :key="s.name" class="sw">
            <span class="dot" :style="{ background: s.hex }" />
            <span class="nm">{{ s.name }}</span>
            <span class="hx">{{ s.hex }}</span>
          </div>
        </div>
        <p class="hint small">
          🔴 硬约束：<b>整屏粉色不超过 4 处</b>（价格 / 主按钮 / 选中态 / 一处图标）；
          语义色只用于状态标签，不做大面积填充。
        </p>
      </section>

      <section class="card rise rise-3">
        <h4>字号阶梯</h4>
        <ul class="steps">
          <li v-for="f in FONT_STEPS" :key="f.token">
            <span class="demo" :style="{ fontSize: f.size }">龙子湖 · 九成新</span>
            <span class="meta">{{ f.size }} · {{ f.use }}</span>
          </li>
        </ul>
      </section>

      <section class="card rise rise-4">
        <h4>组件基元</h4>
        <div class="row">
          <button class="btn-main" style="max-width: 160px" @click="toastOk('订单创建成功', '商品已为你锁定 30 分钟')">
            立即下单
          </button>
          <button class="btn-ghost press" style="padding: 0 18px" @click="toastError('下单失败', '手慢了，商品已被别人抢先买走')">
            触发异常
          </button>
        </div>
        <div class="row wrap">
          <span class="chip on">全部</span>
          <span class="chip">教材书籍</span>
          <span class="chip">数码电子</span>
        </div>
        <div class="row wrap">
          <span class="stag ok">{{ PRODUCT_STATUS_DESC[1] }}</span>
          <span class="stag warn">{{ PRODUCT_STATUS_DESC[2] }}</span>
          <span class="stag dead">{{ PRODUCT_STATUS_DESC[3] }}</span>
          <span class="stag err">{{ PRODUCT_STATUS_DESC[5] }}</span>
          <span class="stag warn">{{ ORDER_STATUS_DESC[0] }}</span>
        </div>
        <div class="row wrap">
          <span v-for="c in CONDITION_OPTIONS" :key="c.code" class="chip">{{ c.label }}</span>
        </div>
        <div class="row wrap">
          <span v-for="c in CAMPUS_LIST" :key="c" class="chip">{{ c }}校区</span>
        </div>
        <div class="row">
          <span class="avatar">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <circle cx="12" cy="8.4" r="4.1" />
              <path d="M12 14.4c-4.4 0-7.6 2.5-7.6 6.1h15.2c0-3.6-3.2-6.1-7.6-6.1z" />
            </svg>
          </span>
          <span class="hint small" style="margin: 0">
            头像一律为<b>渐变圆 + 白色人像图标</b>，禁止用姓氏首字
          </span>
        </div>
      </section>

      <div class="tail">骨架已就绪 · 块 1 / 共 6 块</div>
    </div>
  </div>
</template>

<style scoped>
.card {
  margin: var(--gap-card) var(--gap-page) 0;
  padding: 16px;
  border-radius: var(--r-card);
  background: #fff;
  box-shadow: var(--sh-card);
}
.card:first-child {
  margin-top: 14px;
}
.card h3 {
  font-size: var(--fs-3);
  font-weight: 600;
  letter-spacing: -0.2px;
}
.card h4 {
  font-size: var(--fs-2);
  font-weight: 600;
  color: #48484a;
  margin-bottom: 12px;
}
.hint {
  margin-top: 9px;
  font-size: var(--fs-1);
  color: var(--text-2);
  line-height: 1.7;
}
.hint.small {
  font-size: 10.5px;
  color: var(--text-3);
}
.hint b {
  color: var(--pink-dp);
  font-weight: 600;
}
code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 10.5px;
  color: var(--text-2);
  background: var(--field);
  padding: 1px 5px;
  border-radius: 5px;
}

.apilist li {
  padding: 7px 0;
  border-bottom: 1px solid var(--line);
}
.apilist li:last-child {
  border-bottom: none;
}

.swatches {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
.sw {
  display: flex;
  align-items: center;
  gap: 7px;
}
.sw .dot {
  width: 20px;
  height: 20px;
  flex: 0 0 20px;
  border-radius: 7px;
  box-shadow: inset 0 0 0 1px rgba(28, 28, 30, 0.06);
}
.sw .nm {
  font-size: var(--fs-1);
  color: #48484a;
}
.sw .hx {
  font-size: 10px;
  color: var(--text-3);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.steps li {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 0;
  border-bottom: 1px solid var(--line);
}
.steps li:last-child {
  border-bottom: none;
}
.steps .demo {
  color: var(--text);
  letter-spacing: -0.2px;
}
.steps .meta {
  font-size: 10px;
  color: var(--text-3);
}

.row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.row + .row {
  margin-top: 11px;
}
.row.wrap {
  flex-wrap: wrap;
  gap: 8px;
}

.tail {
  padding: 22px 0 28px;
  text-align: center;
  font-size: 10.5px;
  color: var(--text-3);
}
</style>
