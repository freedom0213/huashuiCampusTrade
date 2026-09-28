<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import TabBar from '@/components/TabBar.vue'
import AppToast from '@/components/AppToast.vue'

/* 应用外壳：负责「底栏是否显示」与「全局反馈提示」，页面内容由路由渲染。
   手机框只是设计稿的展示画框，真实代码里只有这个自适应外壳。 */
const route = useRoute()
const showTabBar = computed(() => !!route.meta.tab)
</script>

<template>
  <div class="app-shell">
    <div class="app-body">
      <RouterView v-slot="{ Component }">
        <!-- 只缓存两个「无路由参数」的列表页：从详情返回时数据原样复活、零请求，
             是消掉「缩回动画结束后卡一下才出列表」的关键（见 composables/useListReturn.js）。
             刻意不含 SellerView（:id 参数页，复用缓存会串卖家）与 FavoritesView（账号数据，
             换账号登录有串数据风险）。 -->
        <KeepAlive include="HomeView,SearchView">
          <component :is="Component" />
        </KeepAlive>
      </RouterView>
    </div>

    <TabBar v-if="showTabBar" />
    <AppToast />
  </div>
</template>
