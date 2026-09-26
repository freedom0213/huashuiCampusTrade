/**
 * 表单校验规则
 *
 * 🔴 规则与后端 `RegisterDTO` / `UpdateProfileDTO` / `ChangePasswordDTO` 的
 *    `@Pattern` / `@Size` / `@NotBlank` 一一对应。后端改了注解，这里必须同步改。
 * 目的：前置拦截低级错误省一次往返；**最终判定仍以后端为准**，这里拦不住的由后端兜。
 */

export const RULES = {
  username: {
    pattern: /^[a-zA-Z0-9_]{4,20}$/,
    message: '用户名需为 4-20 位字母、数字或下划线'
  },
  password: {
    min: 6,
    max: 32,
    message: '密码长度需为 6-32 位'
  },
  phone: {
    pattern: /^1[3-9]\d{9}$/,
    message: '手机号格式不正确'
  },
  nickname: {
    max: 20,
    message: '昵称最长 20 个字符'
  },
  studentNo: {
    max: 32,
    message: '学号最长 32 个字符'
  },
  dept: {
    max: 64,
    message: '院系最长 64 个字符'
  }
}

/**
 * 校验单个字段
 * @returns {string} 通过返回 ''，不通过返回错误文案
 */
export function validateField(field, value) {
  const rule = RULES[field]
  if (!rule) return ''
  const v = String(value ?? '')

  if (rule.pattern && v !== '' && !rule.pattern.test(v)) return rule.message
  if (rule.min !== undefined && v !== '' && v.length < rule.min) {
    return `长度不能少于 ${rule.min} 位`
  }
  if (rule.max !== undefined && v.length > rule.max) return rule.message
  return ''
}

/** 非空校验（用于登录这类只要求「填了就行」的表单） */
export function requireValue(value, label) {
  const v = String(value ?? '').trim()
  return v === '' ? `请填写${label}` : ''
}
