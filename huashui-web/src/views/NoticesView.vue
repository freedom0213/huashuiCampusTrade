<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageState from '@/components/PageState.vue'
import { loadNotices, isUnread, markAllRead, markRead } from '@/composables/useNotice'
import { fromNow } from '@/utils/format'
import { toastOk } from '@/composables/useToast'

/* ==========================================================
   通知中心 /notices —— **聚合视图，不建表**（设计文档 §2.4 / §7.4）

   两次请求拼出全部通知：
     ① GET /api/product/mine?size=50         审核状态（待审核 / 审核通过 / 审核未通过）
     ② GET /api/order/mine?role=all&size=50  订单状态（新订单 / 待付款 / 交易完成）
   拼装逻辑在 composables/useNotice.js 的 `buildNotices()`，与顶部红点**共用同一份口径**，
   所以红点点亮时，进这一页一定看得到东西。

   四类来自设计稿（08 号屏）：审核通过 / 新订单 / 审核未通过 / 交易完成。
   另外两类是为了与红点口径一致补的：
     · 你的订单等待付款（买家侧）—— 设计稿的「新订单」只覆盖卖家视角，
       而买家有未付款订单时同样需要提醒，否则红了点进来反而没有对应条目
     · 商品已提交，等待审核 —— 「审核通过」的对照项，也是发布后的即时反馈

   🔴 未读用**时间水位线**判定（localStorage 存上次「全部已读」的时刻），
      不建已读表。代价：审核通过类通知的时间实际是**提交时间**
      （后端没有审核时间字段），这类时间只能近似。
   ========================================================== */

const router = useRouter()

const notices = ref([])
const loading = ref(true)
const error = ref('')

/* isUnread 读的是 localStorage，不是响应式数据 —— 用这个计数器在「全部已读」后强制重算 */
const stamp = ref(0)

const unreadCount = computed(() => {
  void stamp.value
  return notices.value.filter(isUnread).length
})

function unread(n) {
  void stamp.value
  return isUnread(n)
}

async function fetchAll() {
  loading.value = true
  error.value = ''
  try {
    notices.value = await loadNotices()
  } catch (e) {
    error.value = e.message || '通知加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(fetchAll)

function doMarkAll() {
  if (!unreadCount.value) return
  markAllRead()
  stamp.value += 1
  toastOk('已全部标记为已读')
}

function open(n) {
  // 🔴 点开即已读：只标记这一条（不必再靠右上角「全部已读」）
  markRead(n.key)
  stamp.value += 1 // isUnread 读的是 localStorage，非响应式 —— 靠它强制重算
  if (n.to) router.push(n.to)
}

function back() {
  if (window.history.state?.back) router.back()
  else router.replace('/')
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
      <span class="ttl">通知中心</span>
      <button class="act press" :disabled="!unreadCount" @click="doMarkAll">全部已读</button>
    </header>

    <div class="page-scroll">
      <div class="nlist">
        <PageState
          :loading="loading"
          :error="error"
          :empty="!loading && !error && !notices.length"
          empty-text="暂时没有通知"
          empty-hint="商品通过审核、收到订单、交易完成时会在这里提醒"
          :skeleton-count="3"
          @retry="fetchAll"
        />

        <template v-if="notices.length">
          <article
            v-for="(n, i) in notices"
            :key="n.key"
            class="nitem rise"
            :style="{ animationDelay: `${((i % 6) + 1) * 0.05}s` }"
            @click="open(n)"
          >
            <span class="nico" :class="n.tone">
              <!-- 审核通过 / 交易完成：实心对勾 -->
              <svg v-if="n.icon === 'check'" width="15" height="15" viewBox="0 0 16 16" fill="currentColor">
                <path d="M8 1.6a6.4 6.4 0 100 12.8A6.4 6.4 0 008 1.6zm3 4.9l-3.7 3.7a.8.8 0 01-1.1 0L4.9 8.9a.8.8 0 111.1-1.1l.8.8 3.1-3.1a.8.8 0 111.1 1.1z" />
              </svg>
              <!-- 审核未通过：实心感叹号 -->
              <svg v-else-if="n.icon === 'bang'" width="15" height="15" viewBox="0 0 16 16" fill="currentColor">
                <path d="M8 1.6a6.4 6.4 0 100 12.8A6.4 6.4 0 008 1.6zM7.2 4.4h1.6v4.8H7.2V4.4zm0 6.2h1.6v1.6H7.2v-1.6z" />
              </svg>
              <!-- 订单相关：单据 -->
              <svg v-else-if="n.icon === 'doc'" width="15" height="15" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round">
                <rect x="4.2" y="2.6" width="11.6" height="14.8" rx="1.6" />
                <path d="M7.2 7.2h5.6M7.2 10.4h5.6M7.2 13.6h3.4" />
              </svg>
              <!-- 等待类：时钟 -->
              <svg v-else width="15" height="15" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
                <circle cx="8" cy="8" r="6.1" />
                <path d="M8 4.5V8l2.5 1.8" />
              </svg>
            </span>

            <div class="txt">
              <b>{{ n.title }}</b>
              <p>{{ n.body }}</p>
              <time>{{ fromNow(n.time) }}</time>
            </div>

            <i v-if="unread(n)" class="ndot" />
          </article>
        </template>
      </div>
    </div>
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
/* 没有未读时置灰：点了没反应比置灰更让人困惑 */
.subnav .act:disabled {
  color: var(--text-4);
  cursor: not-allowed;
}

.nlist {
  padding: 8px 16px 20px;
}

.nitem {
  display: flex;
  gap: 11px;
  padding: 14px;
  margin-bottom: 9px;
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--sh-card);
  cursor: pointer;
}
.nitem .txt {
  flex: 1;
  min-width: 0;
}
.nitem b {
  display: block;
  font-size: 13.5px;
  font-weight: 600;
}
.nitem p {
  margin-top: 4px;
  font-size: 12px;
  color: #6e6e73;
  line-height: 1.55;
}
.nitem time {
  display: block;
  margin-top: 6px;
  font-size: 10.5px;
  color: var(--text-3);
}

/* 图标底色只承担「哪一类」，语义色不做大面积填充 */
.nico {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--field);
  color: var(--text-2);
}
.nico.ok {
  background: #eaf8ef;
  color: #1f8a45;
}
.nico.pink {
  background: var(--pink-bg);
  color: var(--pink-dp);
}
.nico.err {
  background: #ffedec;
  color: #d7372c;
}
.nico.wait {
  background: #fff4e5;
  color: #c97a00;
}
.nico.dead {
  background: #f2f2f5;
  color: #8e8e93;
}

.ndot {
  width: 7px;
  height: 7px;
  flex: 0 0 7px;
  border-radius: 50%;
  background: var(--pink);
  margin-top: 5px;
}
</style>
