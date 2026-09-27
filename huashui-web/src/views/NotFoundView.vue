<script setup>
import { useRouter } from 'vue-router'

/* 404。

   🔴 这里原先复用的是块 1 的「骨架占位页」——那页会渲染色板、字号阶梯、组件基元，
      还有一句「该页面计划在块 N 实现」。那是**开发期的自检页**，
      地址敲错时看到它，用户/演示者会以为「这个页面做坏了」。
      14 个页面全部实现后占位页已无用，这里换成干净的 404。 */

const router = useRouter()

function back() {
  if (window.history.state?.back) router.back()
  else router.replace('/')
}
function home() {
  router.replace('/')
}
</script>

<template>
  <div class="page">
    <div class="page-scroll">
      <div class="wrap rise">
        <span class="ico">
          <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="6.8" />
            <path d="M16 16l4.4 4.4" />
            <path d="M8.6 11h4.8" />
          </svg>
        </span>

        <h1>没有找到这个页面</h1>
        <p>地址可能输错了，或者里面的内容已经被卖家下架。</p>

        <div class="acts">
          <button class="btn-ghost press" @click="back">返回上一页</button>
          <button class="btn-main" @click="home">回到首页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 96px 32px 40px;
}
.ico {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--pink-bg);
  color: var(--pink);
}
h1 {
  margin-top: 20px;
  font-size: var(--fs-3);
  font-weight: 600;
  letter-spacing: -0.2px;
}
p {
  margin-top: 9px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--text-2);
}
.acts {
  display: flex;
  gap: 10px;
  width: 100%;
  margin-top: 28px;
}
.acts button {
  flex: 1;
  height: 46px;
  border-radius: var(--r-btn);
  font-size: var(--fs-2);
}
.acts .btn-main {
  font-size: var(--fs-3);
}
</style>
