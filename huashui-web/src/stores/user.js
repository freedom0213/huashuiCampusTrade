import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as userApi from '@/api/user'
import { clearAuth, getStoredUser, getToken, setStoredUser, setToken } from '@/api/request'
import { resetNotice } from '@/composables/useNotice'

/* 当前登录用户。
   ⚠️ 没有 /api/user/logout 接口 —— 退出登录只清本地 token 与缓存。 */
export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const profile = ref(getStoredUser())

  const isLogin = computed(() => !!token.value)
  /** 1 学生 / 2 管理员 */
  const role = computed(() => profile.value?.role ?? null)

  function applyLogin(loginVO) {
    token.value = loginVO.token
    setToken(loginVO.token)
    profile.value = {
      userId: loginVO.userId,
      username: loginVO.username,
      nickname: loginVO.nickname,
      avatar: loginVO.avatar,
      role: loginVO.role
    }
    setStoredUser(profile.value)
  }

  async function login(form) {
    const vo = await userApi.login(form)
    applyLogin(vo)
    // 登录后拉一次完整资料，拿到手机号等字段
    await fetchProfile()
    return vo
  }

  async function register(form) {
    return userApi.register(form)
  }

  async function fetchProfile() {
    if (!token.value) return null
    const info = await userApi.getMyInfo()
    profile.value = { ...(profile.value || {}), ...info }
    setStoredUser(profile.value)
    return info
  }

  function updateLocal(patch) {
    profile.value = { ...(profile.value || {}), ...patch }
    setStoredUser(profile.value)
  }

  function logout() {
    token.value = ''
    profile.value = null
    clearAuth()
    // 通知红点是模块级状态，不清掉下个账号会看到上一个账号的红点
    resetNotice()
  }

  return { token, profile, isLogin, role, login, register, fetchProfile, updateLocal, logout }
})
