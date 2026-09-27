<script setup>
/* 列表三态统一：加载中 / 空 / 失败。
   设计规范（docs/02-前端设计V1.md §三）要求每个列表页都有这三态，
   集中成一个组件，避免各页各写一套。 */

defineProps({
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  empty: { type: Boolean, default: false },
  emptyText: { type: String, default: '这里还没有内容' },
  emptyHint: { type: String, default: '' },
  /** 加载骨架用几列（与父级栅格一致） */
  skeletonCount: { type: Number, default: 4 }
})

defineEmits(['retry'])
</script>

<template>
  <!-- 骨架：形状与商品卡一致，避免加载完成时的位置跳变 -->
  <div v-if="loading" class="grid">
    <div v-for="n in skeletonCount" :key="n" class="sk">
      <div class="sk-thumb" />
      <div class="sk-line" />
      <div class="sk-line short" />
    </div>
  </div>

  <div v-else-if="error" class="state">
    <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
      <circle cx="12" cy="12" r="8.6" />
      <path d="M12 7.8v4.6M12 16.1h.02" />
    </svg>
    <p class="msg">{{ error }}</p>
    <button class="retry press" @click="$emit('retry')">重试</button>
  </div>

  <div v-else-if="empty" class="state">
    <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round">
      <rect x="3.6" y="5" width="16.8" height="14" rx="2.4" />
      <path d="M3.6 15.4l4.4-3.8 3.2 2.8 3-2.4 4.8 4" />
    </svg>
    <p class="msg">{{ emptyText }}</p>
    <p v-if="emptyHint" class="hint">{{ emptyHint }}</p>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--gap-card);
}

/* ── 骨架 ── */
.sk {
  border-radius: 15px;
  background: #fff;
  box-shadow: var(--sh-card);
  overflow: hidden;
  padding-bottom: 12px;
}
.sk-thumb {
  aspect-ratio: 1 / 1;
  background: linear-gradient(100deg, #f4f4f6 30%, #ececf0 50%, #f4f4f6 70%);
  background-size: 220% 100%;
  animation: shimmer 1.25s linear infinite;
}
.sk-line {
  height: 9px;
  margin: 10px 10px 0;
  border-radius: 5px;
  background: linear-gradient(100deg, #f4f4f6 30%, #ececf0 50%, #f4f4f6 70%);
  background-size: 220% 100%;
  animation: shimmer 1.25s linear infinite;
}
.sk-line.short {
  width: 46%;
}
@keyframes shimmer {
  from { background-position: 140% 0; }
  to { background-position: -40% 0; }
}

/* ── 空 / 错 ── */
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 54px 30px;
  color: var(--text-4);
}
.state .msg {
  font-size: var(--fs-2);
  color: var(--text-2);
}
.state .hint {
  font-size: var(--fs-1);
  color: var(--text-3);
  text-align: center;
  line-height: 1.6;
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
</style>
