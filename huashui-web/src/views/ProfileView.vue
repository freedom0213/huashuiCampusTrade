<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import FormField from '@/components/FormField.vue'
import { useUserStore } from '@/stores/user'
import * as userApi from '@/api/user'
import { uploadImage } from '@/api/file'
import { toastFromError, toastOk } from '@/composables/useToast'
import { ERR, IMAGE_ACCEPT } from '@/constants/enums'
import { RULES, validateField } from '@/utils/validate'

/* 个人资料页。
   手机号只读：后端没有「改手机号」接口（改手机号通常要重新验证，属另一阶段的功能）。
   修改密码独立成区块（设计稿没画，但接口已有）；成功后前端清 token 强制重登 ——
   后端不会失效旧 token（AUTH_BLACKLIST 只有常量、无实现），这里必须自己兜住。 */

const router = useRouter()
const store = useUserStore()

const form = ref({ nickname: '', dept: '', studentNo: '', avatar: '' })
const phone = ref('')
const errors = ref({ nickname: '', dept: '', studentNo: '' })
const saving = ref(false)

const pwd = ref({ old: '', next: '', again: '' })
const pwdErrors = ref({ old: '', next: '', again: '' })
const changingPwd = ref(false)

const avatar = computed(() => form.value.avatar || store.profile?.avatar || '')
const dirty = ref(false)
const fileInput = ref(null)

onMounted(async () => {
  try {
    const info = await userApi.getMyInfo()
    form.value = {
      nickname: info.nickname || '',
      dept: info.dept || '',
      studentNo: info.studentNo || '',
      avatar: info.avatar || ''
    }
    phone.value = info.phone || ''
    store.updateLocal(info)
  } catch (e) {
    toastFromError(e, '加载资料失败')
  }
})

function touch(field) {
  dirty.value = true
  errors.value[field] = validateField(field, form.value[field])
}

/* ── 头像 ──
   选图 → 立即上传拿 url → 只更新本地 state，随「保存」一起 PUT。
   不在上传成功时就发 PUT，避免「换了头像但没保存」产生不可撤销的中间态。 */
function pickAvatar() {
  fileInput.value?.click()
}
async function onFileChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  try {
    const vo = await uploadImage(file)
    form.value.avatar = vo.url
    dirty.value = true
    toastOk('头像已上传', '记得点「保存」生效')
  } catch (err) {
    toastFromError(err, '头像上传失败')
  }
}

async function save() {
  const ok = ['nickname', 'dept', 'studentNo'].map((f) => {
    errors.value[f] = validateField(f, form.value[f])
    return !errors.value[f]
  }).every(Boolean)
  if (!ok) return

  saving.value = true
  try {
    await userApi.updateMyInfo({
      nickname: form.value.nickname.trim(),
      avatar: form.value.avatar,
      studentNo: form.value.studentNo.trim(),
      dept: form.value.dept.trim()
    })
    store.updateLocal({
      nickname: form.value.nickname.trim(),
      avatar: form.value.avatar,
      studentNo: form.value.studentNo.trim(),
      dept: form.value.dept.trim()
    })
    dirty.value = false
    toastOk('资料已保存')
  } catch (e) {
    toastFromError(e, '保存失败')
  } finally {
    saving.value = false
  }
}

/* ── 修改密码 ── */
function checkPwd() {
  pwdErrors.value.old = pwd.value.old === '' ? '请填写原密码' : ''
  pwdErrors.value.next = validateField('password', pwd.value.next)
  if (!pwdErrors.value.next && pwd.value.next === pwd.value.old) {
    pwdErrors.value.next = '新密码不能与原密码相同'
  }
  pwdErrors.value.again = pwd.value.again !== pwd.value.next ? '两次输入的密码不一致' : ''
  return !pwdErrors.value.old && !pwdErrors.value.next && !pwdErrors.value.again
}

async function changePassword() {
  if (!checkPwd()) return
  changingPwd.value = true
  try {
    await userApi.changePassword({ oldPassword: pwd.value.old, newPassword: pwd.value.next })
    // 后端不会失效旧 token，这里主动清掉并要求重新登录
    store.logout()
    toastOk('密码已修改', '请重新登录')
    router.replace('/login')
  } catch (e) {
    // 原密码不正确 → 提示写在原密码输入框下，比 toast 可操作
    if (e.code === ERR.OLD_PASSWORD_ERROR) pwdErrors.value.old = e.message
    else toastFromError(e, '修改失败')
  } finally {
    changingPwd.value = false
  }
}

function back() {
  router.back()
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
      <span>个人资料</span>
    </header>

    <div class="page-scroll">
      <div class="profiletop">
        <span class="avatar big">
          <img v-if="avatar" :src="avatar" alt="" />
          <svg v-else width="26" height="26" viewBox="0 0 24 24" fill="currentColor">
            <circle cx="12" cy="8.4" r="4.1" />
            <path d="M12 14.4c-4.4 0-7.6 2.5-7.6 6.1h15.2c0-3.6-3.2-6.1-7.6-6.1z" />
          </svg>
        </span>
        <button class="chg press" @click="pickAvatar">更换头像</button>
        <input ref="fileInput" type="file" :accept="IMAGE_ACCEPT" hidden @change="onFileChange" />
      </div>

      <div class="formcard rise">
        <FormField v-model="form.nickname" variant="row" label="昵称" :maxlength="RULES.nickname.max" :error="errors.nickname" @update:model-value="touch('nickname')" />
        <FormField v-model="phone" variant="row" label="手机号" readonly />
        <FormField v-model="form.dept" variant="row" label="院系" :maxlength="RULES.dept.max" :error="errors.dept" @update:model-value="touch('dept')" />
        <FormField v-model="form.studentNo" variant="row" label="学号" placeholder="选填，用于校内身份认证" :maxlength="RULES.studentNo.max" :error="errors.studentNo" @update:model-value="touch('studentNo')" />
      </div>

      <p class="note">
        手机号会作为交易联系方式，<em>仅对已登录用户展示</em>，游客与爬虫看不到；
        学号仅用于校内身份认证，不对外可见。
      </p>

      <div class="pad">
        <button class="btn-main" :disabled="saving || !dirty" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>

      <div class="pwdcard rise rise-1">
        <h3>修改密码</h3>
        <FormField v-model="pwd.old" type="password" placeholder="原密码" autocomplete="current-password" :error="pwdErrors.old" @update:model-value="pwdErrors.old = ''" />
        <FormField v-model="pwd.next" type="password" placeholder="新密码（6–32 位）" autocomplete="new-password" :error="pwdErrors.next" @update:model-value="pwdErrors.next = ''" />
        <FormField v-model="pwd.again" type="password" placeholder="确认新密码" autocomplete="new-password" :error="pwdErrors.again" @update:model-value="pwdErrors.again = ''" />
        <button class="btn-ghost" :disabled="changingPwd" @click="changePassword">
          {{ changingPwd ? '提交中…' : '修改密码' }}
        </button>
        <p class="pwdnote">修改成功后需要重新登录</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profiletop {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 16px 6px;
}
.avatar.big {
  width: 76px;
  height: 76px;
  flex: 0 0 76px;
}
.chg {
  font-size: var(--fs-2);
  color: var(--pink-dp);
}

.formcard {
  margin: 6px 16px 0;
  padding: 6px 16px;
  border-radius: var(--r-card);
  background: #fff;
  box-shadow: var(--sh-card);
}

.note {
  padding: 13px 30px 0;
  font-size: var(--fs-1);
  color: var(--text-3);
  line-height: 1.7;
}
.note em {
  font-style: normal;
  color: var(--pink-dp);
}

.pad {
  padding: 0 16px;
  margin-top: 18px;
}

.pwdcard {
  margin: 26px 16px 30px;
  padding: 16px;
  border-radius: var(--r-card);
  background: #fff;
  box-shadow: var(--sh-card);
}
.pwdcard h3 {
  font-size: var(--fs-2);
  font-weight: 600;
  color: #48484a;
  margin-bottom: 13px;
}
.pwdnote {
  margin-top: 10px;
  text-align: center;
  font-size: var(--fs-1);
  color: var(--text-3);
}
</style>
