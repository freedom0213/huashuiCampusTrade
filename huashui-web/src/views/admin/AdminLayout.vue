<script setup>
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  startAdminPendingPolling,
  stopAdminPendingPolling,
  useAdminPending
} from '@/composables/useAdminPending'

/* ==========================================================
   管理端布局 /admin/*
   桌面后台（设计稿 1440×900）：左侧深色侧边栏 + 右侧页面区。
   与移动端共用同一 SPA —— App.vue 的 .app-shell 有 max-width:480px，
   由 base.css 的 `.app-shell:has(.admin-shell)` 突破（纯 CSS，无 DOM 操作）。
   权限：进入本布局由路由守卫 adminOnly 把关（role === 2），
   这里不再判权限；后端 requireAdmin() 仍是真正的防线。
   ========================================================== */

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/* 管理员会话在后台闭环：admin01 是纯后台账号，退出即回登录页，
   不和移动端的浏览/交易场景混用（登录页对管理员也会自动跳回 /admin）。 */
function doLogout() {
  userStore.logout()
  router.replace('/login')
}

/* 待审核徽章：进入管理端开始轮询，离开即停（失败静默，见 composable 注释） */
const { pending } = useAdminPending()
onMounted(() => startAdminPendingPolling(30000))
onUnmounted(stopAdminPendingPolling)

/* 导航：当前只有「商品审核」一项；后续加页面在这里追加即可 */
const NAV_GROUPS = [
  {
    title: '内容管理',
    items: [{ name: 'adminAudit', path: '/admin/audit', label: '商品审核' }]
  }
]

const nickname = computed(() => userStore.profile?.nickname || userStore.profile?.username || '管理员')
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-side">
      <div class="side-brand">
        <div class="brand-logo">华</div>
        <div class="brand-text">
          <p class="brand-name">华水闲置</p>
          <p class="brand-sub">管理控制台</p>
        </div>
      </div>

      <nav class="side-nav">
        <div v-for="group in NAV_GROUPS" :key="group.title" class="nav-group">
          <p class="nav-group-title">{{ group.title }}</p>
          <RouterLink
            v-for="item in group.items"
            :key="item.name"
            :to="item.path"
            class="nav-item"
            :class="{ active: route.name === item.name }"
          >
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" aria-hidden="true">
              <rect x="3" y="3" width="6" height="6" rx="1.5" stroke="currentColor" stroke-width="1.5" />
              <rect x="11" y="3" width="6" height="6" rx="1.5" stroke="currentColor" stroke-width="1.5" />
              <rect x="3" y="11" width="6" height="6" rx="1.5" stroke="currentColor" stroke-width="1.5" />
              <rect x="11" y="11" width="6" height="6" rx="1.5" stroke="currentColor" stroke-width="1.5" />
            </svg>
            <span>{{ item.label }}</span>
            <!-- 有待审核商品时点亮：用户发布后管理端能看到提醒 -->
            <span v-if="item.name === 'adminAudit' && pending > 0" class="nav-badge">
              {{ pending }}
            </span>
          </RouterLink>
        </div>
      </nav>

      <div class="side-footer">
        <div class="footer-avatar">{{ nickname.slice(0, 1).toUpperCase() }}</div>
        <div class="footer-text">
          <p class="footer-name">{{ nickname }}</p>
          <p class="footer-sub">管理员</p>
        </div>
        <button class="footer-logout press" aria-label="退出登录" title="退出登录" @click="doLogout">
          <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
            <path d="M12.5 3.5H15A1.5 1.5 0 0116.5 5v10a1.5 1.5 0 01-1.5 1.5h-2.5M8 6.5L4.5 10 8 13.5M4.5 10h7.5"
                  stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </div>
    </aside>

    <main class="admin-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.admin-shell {
  display: flex;
  width: 100%;
  min-height: 100vh;
  background: linear-gradient(180deg, #ffffff 0%, #fefbfc 55%, #fbe1eb 100%);
  text-align: left;
}

/* —— 侧边栏 —— */
.admin-side {
  display: flex;
  flex-direction: column;
  width: 232px;
  flex-shrink: 0;
  min-height: 100vh;
  background: #17171a;
  padding: 20px 14px;
}

.side-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 8px 20px;
}
.brand-logo {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--grad-btn);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.brand-name {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.3;
}
.brand-sub {
  color: #9ca3af;
  font-size: 11px;
  line-height: 1.4;
}

.side-nav {
  flex: 1;
  margin-top: 8px;
}
.nav-group-title {
  color: #6b7280;
  font-size: 11px;
  padding: 8px 10px 6px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 4px;
  border-radius: 8px;
  color: #9ca3af;
  font-size: 14px;
  text-decoration: none;
  transition: background 0.15s, color 0.15s;
}
.nav-item:hover {
  color: #e5e7eb;
  background: rgba(255, 255, 255, 0.06);
}
.nav-item.active {
  background: #4f45e4;
  color: #fff;
}
.nav-icon {
  width: 18px;
  height: 18px;
}
.nav-badge {
  margin-left: auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  text-align: center;
}

.side-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}
.footer-text {
  flex: 1;
  min-width: 0;
}
.footer-logout {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border-radius: 8px;
  color: #9ca3af;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, color 0.15s;
}
.footer-logout svg {
  width: 17px;
  height: 17px;
}
.footer-logout:hover {
  color: #f87171;
  background: rgba(248, 113, 113, 0.1);
}
.footer-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  color: #e5e7eb;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.footer-name {
  color: #f3f4f6;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.3;
}
.footer-sub {
  color: #6b7280;
  font-size: 11px;
  line-height: 1.4;
}

/* —— 主区 —— */
.admin-main {
  flex: 1;
  min-width: 0;
  padding: 28px 32px;
}
</style>
