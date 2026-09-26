<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/* 底栏 3 格：首页 / 发布 / 我的
   刻意不做 5 tab —— 项目没有站内聊天，「消息」tab 无内容可放 */

const route = useRoute()
const router = useRouter()

const TABS = [
  { key: 'home', label: '首页', to: '/', icon: 'home' },
  { key: 'publish', label: '发布', to: '/publish', icon: 'plus', primary: true },
  { key: 'mine', label: '我的', to: '/mine', icon: 'user' }
]

const active = computed(() => route.meta.tab || '')

function go(tab) {
  if (route.path === tab.to) return
  router.push(tab.to)
}
</script>

<template>
  <nav class="tabbar">
    <button
      v-for="tab in TABS"
      :key="tab.key"
      class="tab press"
      :class="{ on: active === tab.key, primary: tab.primary }"
      @click="go(tab)"
    >
      <span class="ico">
        <svg
          v-if="tab.icon === 'home'"
          width="21"
          height="21"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linejoin="round"
        >
          <path d="M4 10.4L12 3.8l8 6.6V20a1.2 1.2 0 01-1.2 1.2H5.2A1.2 1.2 0 014 20z" />
        </svg>
        <svg
          v-else-if="tab.icon === 'plus'"
          width="22"
          height="22"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.1"
          stroke-linecap="round"
        >
          <path d="M12 5.6v12.8M5.6 12h12.8" />
        </svg>
        <svg
          v-else
          width="21"
          height="21"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linejoin="round"
        >
          <circle cx="12" cy="8.2" r="3.9" />
          <path d="M4.6 20.4c.9-3.7 3.8-5.6 7.4-5.6s6.5 1.9 7.4 5.6" />
        </svg>
      </span>
      <span class="lbl">{{ tab.label }}</span>
    </button>
  </nav>
</template>

<style scoped>
.tabbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  height: 54px;
  padding-bottom: var(--safe-bottom);
  height: calc(54px + var(--safe-bottom));
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  box-shadow: var(--sh-bar);
}

.tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  color: var(--text-3);
  transition: color 0.22s ease;
}
.tab .lbl {
  font-size: 10px;
  letter-spacing: 0.2px;
}
.tab.on {
  color: var(--pink-dp);
}

/* 发布：抬到视觉核心位，渐变圆 */
.tab.primary {
  color: #fff;
}
.tab.primary .ico {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  margin-top: -16px;
  border-radius: 50%;
  background: var(--grad-btn);
  box-shadow: 0 6px 16px rgba(236, 110, 156, 0.34);
}
.tab.primary .lbl {
  margin-top: -2px;
  color: var(--text-3);
  font-size: 10px;
}
</style>
