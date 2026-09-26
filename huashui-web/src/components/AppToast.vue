<script setup>
import { useToast } from '@/composables/useToast'

const { state } = useToast()
</script>

<template>
  <Teleport to="body">
    <div class="toast-layer" :class="{ on: state.visible }">
      <div class="toast" :class="[state.type, { show: state.visible }]">
        <!-- ✓ 成功 -->
        <svg
          v-if="state.type === 'ok'"
          viewBox="0 0 52 52"
          fill="none"
          stroke="#34C759"
          stroke-width="3.4"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <circle class="ring" cx="26" cy="26" r="22.5" stroke-width="3" />
          <path class="mark" d="M15.6 27.2l7.1 7.1L36.8 19.6" />
        </svg>
        <!-- ！异常（唯一的一种非成功反馈） -->
        <svg
          v-else
          viewBox="0 0 52 52"
          fill="none"
          stroke="#FF9500"
          stroke-width="3.4"
          stroke-linecap="round"
        >
          <circle class="ring" cx="26" cy="26" r="22.5" stroke-width="3" />
          <path class="mark" d="M26 15.2v14.6M26 37.4h.05" />
        </svg>

        <div class="msg">{{ state.message }}</div>
        <div v-if="state.sub" class="sub">{{ state.sub }}</div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
/* 挂在 body 上并居中于「应用外壳」，而不是整个浏览器窗口 */
.toast-layer {
  position: fixed;
  inset: 0;
  margin: 0 auto;
  max-width: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  z-index: 200;
}

.toast {
  min-width: 138px;
  max-width: 238px;
  padding: 22px 24px 20px;
  border-radius: 26px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  box-shadow: 0 14px 44px rgba(28, 28, 30, 0.16);
  opacity: 0;
}
.toast.show {
  animation: toastIn 1.6s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}
@keyframes toastIn {
  0% {
    opacity: 0;
    transform: scale(0.86);
  }
  12% {
    opacity: 1;
    transform: scale(1.04);
  }
  19% {
    transform: scale(1);
  }
  84% {
    opacity: 1;
    transform: scale(1);
  }
  100% {
    opacity: 0;
    transform: scale(0.97);
  }
}

.toast svg {
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
}
.toast .msg {
  font-size: 13.5px;
  font-weight: 600;
  color: #1c1c1e;
  letter-spacing: -0.1px;
  text-align: center;
  line-height: 1.4;
}
.toast .sub {
  font-size: 11.5px;
  font-weight: 400;
  color: #8e8e93;
  text-align: center;
  line-height: 1.55;
  margin-top: -2px;
}

/* SVG 描线动画：圈先画完，勾/叹号再画出来 */
.ring {
  stroke-dasharray: 150;
  stroke-dashoffset: 150;
  animation: draw 0.42s ease 0.05s forwards;
}
.mark {
  stroke-dasharray: 46;
  stroke-dashoffset: 46;
  animation: draw 0.32s ease 0.38s forwards;
}
@keyframes draw {
  to {
    stroke-dashoffset: 0;
  }
}
</style>
