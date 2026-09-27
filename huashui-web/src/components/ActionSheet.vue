<script setup>
/* 通用底部浮层（遮罩 + 自底部上滑的面板），内容全部由插槽决定。
   与 OptionSheet 的分工：
     · OptionSheet  = 「单选列表」专用（排序 / 校区 / 成色筛选）
     · ActionSheet  = 通用容器（下单确认层、联系卖家层这类带按钮组的浮层）
   形态沿用设计稿 .sheet：遮罩淡入 + 面板上滑，圆角 24px 仅在上边两角。 */

defineProps({
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['close'])

function close() {
  emit('close')
}
</script>

<template>
  <div class="mask" :class="{ on: visible }" @click.self="close">
    <div class="sheet">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 150;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(28, 28, 30, 0.34);
  backdrop-filter: blur(5px);
  -webkit-backdrop-filter: blur(5px);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.24s ease;
}
.mask.on {
  opacity: 1;
  pointer-events: auto;
}

.sheet {
  width: 100%;
  max-width: 480px;
  max-height: 78vh;
  overflow-y: auto;
  background: #fff;
  border-radius: var(--r-sheet) var(--r-sheet) 0 0;
  padding: 24px 22px calc(34px + var(--safe-bottom));
  transform: translateY(102%);
  transition: transform 0.38s var(--ease);
}
.mask.on .sheet {
  transform: none;
}
</style>
