<script setup>
/* 底部选项面板（单选）。
   搜索结果页的「排序 / 校区 / 成色」筛选用它，避免三个自制浮层各写一套定位逻辑。
   形态沿用设计稿的下单确认层：遮罩淡入 + 面板自底部上滑。 */

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '请选择' },
  /** [{ value, label }]，value 为 null 表示「不限」 */
  options: { type: Array, default: () => [] },
  modelValue: { type: [String, Number, null], default: null }
})

const emit = defineEmits(['update:modelValue', 'close'])

function pick(value) {
  emit('update:modelValue', value)
  emit('close')
}
function close() {
  emit('close')
}
</script>

<template>
  <div class="mask" :class="{ on: visible }" @click.self="close">
    <div class="sheet">
      <h3>{{ title }}</h3>
      <button
        v-for="opt in options"
        :key="String(opt.value)"
        class="opt press"
        :class="{ on: opt.value === modelValue }"
        @click="pick(opt.value)"
      >
        <span>{{ opt.label }}</span>
        <svg v-if="opt.value === modelValue" width="16" height="16" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M4.6 10.4l3.4 3.4 7.4-7.6" />
        </svg>
      </button>
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
  background: #fff;
  border-radius: 24px 24px 0 0;
  padding: 22px 22px calc(30px + var(--safe-bottom));
  transform: translateY(102%);
  transition: transform 0.38s var(--ease);
  max-height: 70vh;
  overflow-y: auto;
}
.mask.on .sheet {
  transform: none;
}

h3 {
  font-size: var(--fs-3);
  font-weight: 600;
  letter-spacing: -0.2px;
  margin-bottom: 8px;
}

.opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 14px 2px;
  text-align: left;
  border-bottom: 1px solid var(--line);
  font-size: var(--fs-2);
  color: #3a3a3c;
}
.opt:last-child {
  border-bottom: none;
}
.opt.on {
  color: var(--pink-dp);
  font-weight: 500;
}
</style>
