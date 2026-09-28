<script setup>
import { ref, watch } from 'vue'
import { toastFromError } from '@/composables/useToast'
import { formatPrice } from '@/utils/format'

/* ==========================================================
   驳回弹窗（管理端 · 商品审核）
   契约：PUT /api/product/admin/{id}/reject —— reason 必填 ≤255 字，
   空 → HTTP 400；驳回后状态 0 → 5，理由仅卖家可见（「我的发布」）。
   校验前端同步做一层（必填 + maxlength 截断），后端校验仍是准绳。
   ========================================================== */

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  product: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const reason = ref('')
const reasonError = ref('')
const confirming = ref(false)

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      reason.value = ''
      reasonError.value = ''
      confirming.value = false
    }
  }
)

function close() {
  if (confirming.value) return
  emit('update:modelValue', false)
}

function onConfirm() {
  const text = reason.value.trim()
  if (!text) {
    reasonError.value = '请填写驳回理由，将展示给卖家'
    return
  }
  if (text.length > 255) {
    reasonError.value = '驳回理由不能超过 255 字'
    return
  }
  reasonError.value = ''
  confirming.value = true
  Promise.resolve(emit('confirm', text, {
    done: () => {
      confirming.value = false
      emit('update:modelValue', false)
    },
    fail: (e) => {
      confirming.value = false
      toastFromError(e, '驳回失败')
    }
  }))
}
</script>

<template>
  <Teleport to="body">
    <div v-if="modelValue" class="dlg-mask" @click.self="close">
      <div class="dlg" role="dialog" aria-modal="true">
        <div class="dlg-head">
          <h3 class="dlg-title">驳回商品</h3>
          <button class="dlg-x" aria-label="关闭" @click="close">
            <svg viewBox="0 0 16 16" fill="none">
              <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
          </button>
        </div>

        <div v-if="product" class="dlg-product">
          <img class="p-thumb" :src="product.cover" alt="" />
          <div class="p-info">
            <p class="p-title">{{ product.title }}</p>
            <p class="p-price">¥{{ formatPrice(product.price) }}</p>
          </div>
        </div>

        <label class="dlg-label" for="reject-reason">驳回理由（必填）</label>
        <textarea
          id="reject-reason"
          v-model="reason"
          class="dlg-textarea"
          :class="{ invalid: reasonError }"
          rows="4"
          maxlength="255"
          placeholder="请填写驳回理由，将展示给卖家"
        ></textarea>
        <p class="dlg-error" :class="{ show: reasonError }">{{ reasonError || '　' }}</p>

        <p class="dlg-hint">驳回后商品状态变为「已驳回」，理由仅卖家可见；卖家编辑后重新提交会自动回到待审核。</p>

        <div class="dlg-actions">
          <button class="btn btn-ghost" :disabled="confirming" @click="close">取消</button>
          <button class="btn btn-danger" :disabled="confirming" @click="onConfirm">
            {{ confirming ? '提交中…' : '确认驳回' }}
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
  width: 440px;
  max-width: calc(100vw - 48px);
  background: #fff;
  border-radius: 16px;
  padding: 20px 24px 22px;
  box-shadow: 0 16px 48px rgba(23, 23, 26, 0.18);
  text-align: left;
}
.dlg-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.dlg-title {
  font-size: 16px;
  font-weight: 600;
  color: #1c1c1e;
  margin: 0;
}
.dlg-x {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  color: #9ca3af;
  display: flex;
  align-items: center;
  justify-content: center;
}
.dlg-x:hover {
  background: #f3f4f6;
  color: #374151;
}
.dlg-x svg {
  width: 14px;
  height: 14px;
}

.dlg-product {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #f9fafb;
  border-radius: 10px;
  padding: 10px 12px;
  margin-top: 14px;
}
.p-thumb {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  object-fit: cover;
  background: #f3f4f6;
}
.p-title {
  font-size: 13px;
  font-weight: 600;
  color: #1c1c1e;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.p-price {
  font-size: 12px;
  color: var(--pink);
  font-weight: 600;
  margin-top: 2px;
}

.dlg-label {
  display: block;
  font-size: 13px;
  color: #374151;
  font-weight: 600;
  margin: 16px 0 8px;
}
.dlg-textarea {
  width: 100%;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 13px;
  color: #1c1c1e;
  resize: none;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.dlg-textarea:focus {
  border-color: #4f45e4;
  box-shadow: 0 0 0 3px rgba(79, 69, 228, 0.12);
}
.dlg-textarea.invalid {
  border-color: #ef4444;
}
.dlg-error {
  min-height: 18px;
  font-size: 12px;
  color: #ef4444;
  margin: 4px 0 0;
  opacity: 0;
}
.dlg-error.show {
  opacity: 1;
}
.dlg-hint {
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.6;
  margin: 2px 0 0;
}

.dlg-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
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
