<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FormField from '@/components/FormField.vue'
import * as userApi from '@/api/user'
import { toastFromError, toastOk } from '@/composables/useToast'
import { ERR } from '@/constants/enums'
import { RULES, validateField } from '@/utils/validate'

/* 注册页。
   只做四项（与设计稿一致）：用户名 / 密码 / 手机号 / 院系。
   昵称默认取用户名；学号与头像在「个人资料」里补 —— 信息不丢，注册路径最短。
   手机号旁的说明是产品卖点（隐私边界前置），不是普通提示文案。 */

const route = useRoute()
const router = useRouter()

const form = ref({
  username: String(route.query.username || ''),
  password: '',
  phone: '',
  dept: ''
})
const errors = ref({ username: '', password: '', phone: '', dept: '' })
const loading = ref(false)

const canSubmit = computed(
  () =>
    !loading.value &&
    form.value.username.trim() !== '' &&
    form.value.password !== '' &&
    form.value.phone.trim() !== ''
)

function check(field) {
  errors.value[field] = validateField(field, form.value[field])
  return !errors.value[field]
}

async function submit() {
  const ok = ['username', 'password', 'phone', 'dept'].map(check).every(Boolean)
  if (!ok) return

  loading.value = true
  try {
    await userApi.register({
      username: form.value.username.trim(),
      password: form.value.password,
      phone: form.value.phone.trim(),
      // 后端 nickname 可空：不填时展示层回落到用户名，不必让用户在注册页多填一项
      nickname: '',
      dept: form.value.dept.trim()
    })
    toastOk('注册成功', '请登录')
    router.replace({ path: '/login', query: { username: form.value.username.trim() } })
  } catch (e) {
    // 用户名已被注册 / 手机号已被注册 → 原因写在输入框下方，比 toast 更可操作
    if (e.code === ERR.USERNAME_EXISTS) errors.value.username = e.message
    else if (e.code === ERR.PHONE_EXISTS) errors.value.phone = e.message
    else toastFromError(e, '注册失败')
  } finally {
    loading.value = false
  }
}

function back() {
  router.back()
}
function goLogin() {
  router.replace('/login')
}
</script>

<template>
  <div class="page">
    <header class="nav-bar">
      <button class="nav-left press" aria-label="返回" @click="back">
        <svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12.2 4.6L6.8 10l5.4 5.4" />
        </svg>
      </button>
      <span>注册</span>
    </header>

    <div class="page-scroll">
      <div class="authwrap">
        <h1 class="title rise">创建账号</h1>
        <p class="subtitle rise">校园二手不用填一堆资料，四项就够了</p>

        <div class="rise rise-1">
          <FormField
            v-model="form.username"
            placeholder="用户名（登录用，4–20 位字母数字下划线）"
            autocomplete="username"
            :error="errors.username"
            @update:model-value="errors.username = ''"
          />
          <FormField
            v-model="form.password"
            type="password"
            placeholder="密码（6–32 位）"
            autocomplete="new-password"
            :error="errors.password"
            @update:model-value="errors.password = ''"
          />
          <FormField
            v-model="form.phone"
            type="tel"
            placeholder="手机号"
            maxlength="11"
            inputmode="numeric"
            :error="errors.phone"
            @update:model-value="errors.phone = ''"
          />

          <p class="privacy">
            手机号将作为交易联系方式，<em>仅对已登录用户展示</em>，游客看不到
          </p>

          <FormField
            v-model="form.dept"
            placeholder="院系（选填，如 信息工程学院）"
            :maxlength="RULES.dept.max"
            :error="errors.dept"
            @update:model-value="errors.dept = ''"
          />
        </div>

        <button class="btn-main wide rise rise-2" :disabled="!canSubmit" @click="submit">
          {{ loading ? '注册中…' : '注册' }}
        </button>
        <p class="agree rise rise-2">注册即表示同意 <em>《用户协议》</em> 与 <em>《隐私政策》</em></p>
        <div class="switchrow rise rise-3">已有账号？<b class="press" @click="goLogin">去登录</b></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.authwrap {
  padding: 8px 28px 30px;
}
.title {
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.4px;
}
.subtitle {
  margin-top: 9px;
  font-size: 12.5px;
  color: var(--text-2);
  line-height: 1.65;
}

.privacy {
  margin: 9px 0 12px;
  padding: 0 4px;
  font-size: var(--fs-1);
  color: var(--text-3);
  line-height: 1.6;
}
.privacy em {
  font-style: normal;
  color: var(--pink-dp);
}

.wide {
  width: 100%;
  margin-top: 24px;
}
.agree {
  margin-top: 20px;
  text-align: center;
  font-size: 10.5px;
  color: var(--text-3);
  line-height: 1.7;
}
.agree em {
  font-style: normal;
  color: var(--text-2);
}
.switchrow {
  margin-top: 14px;
  text-align: center;
  font-size: var(--fs-2);
  color: var(--text-2);
}
.switchrow b {
  color: var(--pink-dp);
  font-weight: 500;
  cursor: pointer;
  margin-left: 4px;
}
</style>
