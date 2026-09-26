<script setup>
defineProps({
  label: { type: String, default: '' },
  modelValue: { type: [String, Number], default: '' },
  type: { type: String, default: 'text' },
  placeholder: { type: String, default: '' },
  maxlength: { type: [String, Number], default: undefined },
  readonly: { type: Boolean, default: false },
  error: { type: String, default: '' },
  /** row：label 在左（个人资料页）／ block：无 label 的整行输入（登录/注册页） */
  variant: { type: String, default: 'block' },
  autocomplete: { type: String, default: 'off' }
})

defineEmits(['update:modelValue'])
</script>

<template>
  <div class="ff" :class="[variant, { invalid: !!error }]">
    <label v-if="variant === 'row' && label">{{ label }}</label>
    <div class="ctl">
      <input
        :type="type"
        :value="modelValue"
        :placeholder="placeholder"
        :maxlength="maxlength"
        :readonly="readonly"
        :autocomplete="autocomplete"
        @input="$emit('update:modelValue', $event.target.value)"
      />
    </div>
    <p v-if="error" class="err">{{ error }}</p>
  </div>
</template>

<style scoped>
.ff {
  margin-bottom: 12px;
}

/* ── block：整行输入（登录 / 注册） ── */
.ff.block .ctl input {
  width: 100%;
  height: 50px;
  padding: 0 16px;
  border-radius: 14px;
  background: var(--field);
  font-size: var(--fs-3);
  color: var(--text);
  transition: box-shadow 0.2s ease, background 0.2s ease;
}
.ff.block .ctl input:focus {
  outline: none;
  background: #fff;
  box-shadow: 0 0 0 1.5px var(--pink-lt);
}

/* ── row：label 在左（个人资料） ── */
.ff.row {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 52px;
  padding: 0 2px;
}
.ff.row label {
  flex: 0 0 44px;
  font-size: var(--fs-2);
  color: #6e6e73;
}
.ff.row .ctl {
  flex: 1;
  min-width: 0;
}
.ff.row .ctl input {
  width: 100%;
  height: 34px;
  font-size: var(--fs-2);
  color: var(--text);
  text-align: right;
}
.ff.row .ctl input:focus {
  outline: none;
}
.ff.row .ctl input[readonly] {
  color: var(--text-3);
}

/* 校验失败 */
.ff.invalid.block .ctl input {
  box-shadow: 0 0 0 1.5px var(--red);
}
.ff.invalid.row label {
  color: var(--red);
}
.err {
  margin-top: 6px;
  font-size: var(--fs-1);
  color: var(--red);
  line-height: 1.5;
}
.ff.row .err {
  flex-basis: 100%;
  text-align: right;
}
</style>
