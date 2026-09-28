<script setup>
import { ref, watch } from 'vue'
import { toastFromError } from '@/composables/useToast'

/* ==========================================================
   强制下架确认弹窗（管理端 · 商品审核）
   契约：PUT /api/product/admin/{id}/force-off —— 前置状态在售(1)；
   已锁定(2)/已售出(3) 会被后端拒绝（20006 在途交易保护）。
   文案口径（设计稿定稿）：下架立即对买家不可见、**已创建订单不受影响**
   （与后端口径一致：商品下架只影响新订单）、卖家重新上架会回到待审核。
   ========================================================== */

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  product: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const confirming = ref(false)

watch(
  () => props.modelValue,
  (open) => {
    if (open) confirming.value = false
  }
)

function close() {
  if (confirming.value) return
  emit('update:modelValue', false)
}

function onConfirm() {
  confirming.value = true
  Promise.resolve(
    emit('confirm', {
      done: () => {
        confirming.value = false
        emit('update:modelValue', false)
      },
      fail: (e) => {
        confirming.value = false
        toastFromError(e, '强制下架失败')
      }
    })
  )
}
</script>

<template>
  <Teleport to="body">
    <div v-if="modelValue" class="dlg-mask" @click.self="close">
      <div class="dlg" role="dialog" aria-modal="true">
        <div class="warn-icon">
          <svg viewBox="0 0 24 24" fill="none">
            <path
              d="M12 3.5 21.2 19.2a1.2 1.2 0 0 1-1.04 1.8H3.84a1.2 1.2 0 0 1-1.04-1.8L12 3.5Z"
              stroke="#f59e0b"
              stroke-width="1.8"
              stroke-linejoin="round"
            />
            <path d="M12 9.5v4.2" stroke="#f59e0b" stroke-width="1.8" stroke-linecap="round" />
            <circle cx="12" cy="16.8" r="1.05" fill="#f59e0b" />
          </svg>
        </div>

        <h3 class="dlg-title">确认强制下架该商品？</h3>
        <p class="dlg-text" v-if="product">
          下架后「{{ product.title }}」将立即对买家不可见，已创建的订单不受影响。该操作可在商品列表中重新上架。
        </p>

        <div class="dlg-actions">
          <button class="btn btn-ghost" :disabled="confirming" @click="close">取消</button>
          <button class="btn btn-danger" :disabled="confirming" @click="onConfirm">
            {{ confirming ? '提交中…' : '确认下架' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.dlg-mask {
  position: fixed;
  inset: 0;
  z-index: 90;
  background: rgba(23, 23, 26, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
}
.dlg {
  width: 400px;
  max-width: calc(100vw - 48px);
  background: #fff;
  border-radius: 16px;
  padding: 26px 26px 22px;
  box-shadow: 0 16px 48px rgba(23, 23, 26, 0.18);
  text-align: left;
}
.warn-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #fef3c7;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 14px;
}
.warn-icon svg {
  width: 22px;
  height: 22px;
}
.dlg-title {
  font-size: 16px;
  font-weight: 600;
  color: #1c1c1e;
  margin: 0 0 8px;
}
.dlg-text {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.7;
  margin: 0;
}
.dlg-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}
.btn {
  padding: 8px 18px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  transition: opacity 0.15s, background 0.15s;
}
.btn:disabled {
  opacity: 0.6;
  cursor: default;
}
.btn-ghost {
  background: #fff;
  border: 1px solid #e5e7eb;
  color: #374151;
}
.btn-ghost:hover:not(:disabled) {
  background: #f9fafb;
}
.btn-danger {
  background: #ef4444;
  color: #fff;
}
.btn-danger:hover:not(:disabled) {
  background: #dc2626;
}
</style>
