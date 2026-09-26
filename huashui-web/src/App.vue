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
        <component :is="Component" />
      </RouterView>
    </div>

    <TabBar v-if="showTabBar" />
    <AppToast />
  </div>
</template>
