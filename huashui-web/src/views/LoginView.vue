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
        <!-- 三行结构：「表单上方全部内容」/「登录按钮」/「按钮下方全部内容」
             中间行是按钮 → 两侧 1fr 严格平分，按钮中心恒等于视口中心 -->
        <div class="head">
          <div class="brand rise">
            <div class="logo">华水</div>
            <h1>华水闲置</h1>
            <p>校园闲置 · 当面自提</p>
          </div>

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
        </div>

        <button class="btn-main wide rise rise-2" :disabled="!canSubmit" @click="submit">
          {{ loading ? '登录中…' : '登录' }}
        </button>

        <div class="foot">
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
/* ══════════ 垂直布局：让「登录按钮」落在视口垂直中心 ══════════
   设计稿实测：图 09 的按钮中心 423（相对含 50px 状态栏的屏），本就在中心附近。
   用户口径（2026-09-27 修正）：居中的是**登录按钮**，不是用户名密码 ——
   上一版把用户名密码居中，视觉上"重心偏下、头轻脚重"，用户明确否掉了。

   做法：把内容切成三行，中间行就是按钮本身：
       行1  .head  = 品牌 + 用户名 + 密码     align-self: end  → 贴向按钮
       行2  button = 按钮                      auto
       行3  .foot  = 注册入口 + 协议          align-self: start → 贴向按钮
   两侧 1fr **严格平分**剩余空间 ⇒ 按钮中心恒等于视口中心。

   🔴 两点必须注意：
   ① 要用 grid 的 1fr，**不能用 flex 的 flex:1 1 0** —— flex item 的
      `min-height:auto` 以内容高度为最小尺寸，上下内容量不同就分不到相等高度
      （实测 361 vs 327）。grid 的 1fr 与内容无关。
   ② `.page-scroll` 必须保留 `display:flex; flex-direction:column`，
      否则 `.authwrap` 的 flex:1 没有 flex 上下文、高度退化成内容高度。

   按钮上方的间距放在 `.head` 的 padding-bottom（而不是按钮的 margin-top）：
   grid 行高 = 元素高 + margin，若用 margin 会抬高行2 从而把按钮挤偏下
   （实测偏下 14px）；放 padding 里则不影响行2 高度。 */
.page-scroll {
  display: flex;
  flex-direction: column;
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

.head {
  align-self: end;
  min-height: 0;
  padding-bottom: 28px;
}

.foot {
  align-self: start;
  min-height: 0;
}

.brand {
  text-align: center;
  padding: 26px 0 34px;
}

.formblock {
  min-height: 0;
}
/* 🔴 去掉最后一个字段的 12px 底部间距：它不可见但要占高度，
   会让 .head 的内容比可见内容高 12px。 */
.formblock :deep(.ff:last-child) {
  margin-bottom: 0;
}

/* ── 矮屏处理（断点由实测内容高度反推，不是估的）──
   .head 实测高 330（品牌 190 + 表单 112 + 按钮上方间距 28）。
   需要「行1 ≥ .head」：
     行1 = (H − 按钮 48) / 2 ≥ 330  →  H ≥ 708

   ① 720 以下：压缩品牌留白（26/34 → 12/20，省 28px）→
      .head 降到 298，覆盖到 H ≥ 644，**iPhone SE（667）仍能精确居中**。
      实测未压缩时 700 高度下品牌 top = −4（被裁 4px），压缩后不再裁。
      ② 640 以下：实在放不下，退回「自然高度 + 靠上排列 + 可滚动」，
      优先保证内容完整可读（内容总高 471，480 以上都能完整显示）。 */
@media (max-height: 720px) {
  .brand {
    padding: 12px 0 20px;
  }
  .head {
    padding-bottom: 24px;
  }
}

@media (max-height: 640px) {
  .authwrap {
    flex: 0 0 auto;
    grid-template-rows: auto auto auto;
  }
  .head {
    align-self: start;
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
  /* 按钮上方的间距由 .head 的 padding-bottom 提供（见上方说明），故此处归零 */
  margin-top: 0;
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
