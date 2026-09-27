<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FormField from '@/components/FormField.vue'
import { useUserStore } from '@/stores/user'
import { toastFromError, toastOk } from '@/composables/useToast'
import { requireValue } from '@/utils/validate'

/* 登录页。
   设计稿图注：「不做整站强制登录，只在点收藏 / 下单 / 发布时拦截；登录成功后原地返回并继续原动作。」
   → 所以这里必须支持 ?redirect= 回跳。 */

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const username = ref(String(route.query.username || ''))
const password = ref('')
const errors = ref({ username: '', password: '' })
const loading = ref(false)

const canSubmit = computed(() => username.value.trim() !== '' && password.value !== '' && !loading.value)

async function submit() {
  errors.value.username = requireValue(username.value, '用户名')
  errors.value.password = requireValue(password.value, '密码')
  if (errors.value.username || errors.value.password) return

  loading.value = true
  try {
    await store.login({ username: username.value.trim(), password: password.value })
    toastOk('登录成功')
    const redirect = String(route.query.redirect || '/')
    router.replace(redirect)
  } catch (e) {
    // 后端刻意把「用户不存在」与「密码错误」合成同一个 10004，防止枚举用户名
    toastFromError(e, '登录失败')
  } finally {
    loading.value = false
  }
}

function goRegister() {
  router.push('/register')
}
</script>

<template>
  <div class="page">
    <div class="page-scroll">
      <div class="authwrap">
        <div class="brand rise">
          <div class="logo">华水</div>
          <h1>华水闲置</h1>
          <p>校园闲置 · 当面自提</p>
        </div>

        <!-- 表单块（用户名 + 密码）：整个页面的视觉重心，垂直居中于视口 -->
        <div class="formblock rise rise-1">
          <FormField
            v-model="username"
            placeholder="用户名"
            autocomplete="username"
            :error="errors.username"
            @update:model-value="errors.username = ''"
          />
          <FormField
            v-model="password"
            type="password"
            placeholder="密码"
            autocomplete="current-password"
            :error="errors.password"
            @keyup.enter="submit"
            @update:model-value="errors.password = ''"
          />
        </div>

        <div class="tail">
          <button class="btn-main wide rise rise-2" :disabled="!canSubmit" @click="submit">
            {{ loading ? '登录中…' : '登录' }}
          </button>

          <div class="switchrow rise rise-3">
            还没有账号？<b class="press" @click="goRegister">立即注册</b>
          </div>
          <p class="agree rise rise-3">登录即表示同意 <em>《用户协议》</em> 与 <em>《隐私政策》</em></p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ══════════ 垂直布局：让「用户名 + 密码」落在视口垂直中心 ══════════
   🔴 为什么不用「整体加个 margin-top」：那样只是把内容整体推下去，
      表单仍然偏上（因为表单下方还有按钮 / 注册链接 / 协议三段）。

   🔴 为什么用 grid 的 1fr 而不是 flex 的 flex:1 1 0：
      实测 flex 下 `flex-basis: 0` **并不会严格平分** —— 因为 flex item 的
      `min-height: auto` 会以「内容高度」作为最小尺寸，上下两块内容量不同
      （brand 内容 164 / tail 内容 168）就会被分到不同高度。
      实测：品牌块得 361、尾部块得 327，表单中心 417，比视口中心 **偏下 11px**。
      `grid-template-rows: minmax(0,1fr) auto minmax(0,1fr)` 则严格平分剩余空间，
      与内容多少完全无关（minmax(0,…) 是为了解除 min-content 约束）。
      实测表单中心 = 406 = 视口中心，**偏差 0**。 */
.page-scroll {
  /* 必须是 flex column：否则 .authwrap 的 flex:1 没有 flex 上下文，
     高度会退化成内容高度（实测 459），grid 的 1fr 也就无从平分。 */
  display: flex;
  flex-direction: column;
  /* 只留安全区；额外留白交给 grid，否则上下不对称会破坏居中 */
  padding-top: var(--safe-top);
  padding-bottom: var(--safe-top);
}

.authwrap {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto minmax(0, 1fr);
  padding: 0 28px;
}

.brand {
  align-self: end;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  text-align: center;
  padding: 0 0 34px;
  min-height: 0;
}

.formblock {
  min-height: 0;
}
/* 🔴 去掉最后一个字段的 12px 底部间距：
   它不可见但要占高度 → 表单块高 124 而可见内容只有 112 →
   可见区域中心落在 400，比视口中心 406 偏上 6px。
   去掉后表单块 = 可见内容 112，中心精确等于 406。 */
.formblock :deep(.ff:last-child) {
  margin-bottom: 0;
}

.tail {
  min-height: 0;
}

/* ── 矮屏兜底 ──
   阈值推算（视口高 H）：grid 两行各得 (H − 表单块 112) / 2，
   要放得下上方内容 164 → H ≥ 440；要放得下下方内容 168 → H ≥ 448。
   取 480 留出余量：480 以上一律居中（覆盖 iPhone SE 667 起的全部机型），
   480 以下才退回「自然高度 + 靠上排列 + 可滚动」，保证内容完整可读。 */
@media (max-height: 480px) {
  .authwrap {
    flex: 0 0 auto;
    grid-template-rows: auto auto auto;
  }
  .brand {
    align-self: start;
    padding: 26px 0 34px;
  }
}
.logo {
  width: 62px;
  height: 62px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 20px;
  background: var(--grad-avatar);
  color: #fff;
  font-size: 19px;
  font-weight: 600;
  letter-spacing: 1px;
  box-shadow: 0 8px 22px rgba(236, 110, 156, 0.3);
}
.brand h1 {
  margin-top: 15px;
  font-size: var(--fs-4);
  font-weight: 600;
  letter-spacing: -0.4px;
}
.brand p {
  margin-top: 7px;
  font-size: var(--fs-1);
  color: var(--text-2);
  letter-spacing: 1px;
}

.wide {
  width: 100%;
  /* 40 = 原设计的 28，加上被移除的字段尾部间距 12，保持按钮与表单的视觉间距不变 */
  margin-top: 40px;
}

.switchrow {
  margin-top: 24px;
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

.agree {
  margin-top: 30px;
  text-align: center;
  font-size: 10.5px;
  color: var(--text-3);
  line-height: 1.7;
}
.agree em {
  font-style: normal;
  color: var(--text-2);
}
</style>
