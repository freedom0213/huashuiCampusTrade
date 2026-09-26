/**
 * 前端枚举常量
 * 来源：后端 `huashui-common` 的 ProductStatus / OrderStatus / ProductCondition / Campus
 * 以及 `ResultCode`（错误码）
 *
 * ⚠️ 注意：列表与详情接口**已经返回 `statusDesc` / `conditionDesc`**，
 *    展示时优先直接用后端文案；本文件的状态表用于「按状态决定按钮与样式」这类逻辑判断。
 */

/* ---------------- 商品状态 ---------------- */
export const PRODUCT_STATUS = {
  PENDING_AUDIT: 0,
  ON_SALE: 1,
  LOCKED: 2,
  SOLD: 3,
  OFF_SHELF: 4,
  REJECTED: 5
}

export const PRODUCT_STATUS_DESC = {
  0: '待审核',
  1: '在售',
  2: '已锁定',
  3: '已售出',
  4: '已下架',
  5: '已驳回'
}

/** 对应 .stag 的样式修饰类，语义色只用于状态标签 */
export const PRODUCT_STATUS_STYLE = {
  0: 'warn',
  1: 'ok',
  2: 'warn',
  3: 'dead',
  4: 'dead',
  5: 'err'
}

/* ---------------- 订单状态 ---------------- */
export const ORDER_STATUS = {
  WAITING_PAY: 0,
  PAID: 1,
  COMPLETED: 2,
  CANCELLED: 3
}

export const ORDER_STATUS_DESC = {
  0: '待付款',
  1: '已付款',
  2: '交易完成',
  3: '已取消'
}

export const ORDER_STATUS_STYLE = {
  0: 'warn',
  1: 'ok',
  2: 'ok',
  3: 'dead'
}

/* ---------------- 成色 ----------------
   🔴 数值越小越新；筛选「≥ 某档」在后端换算成 condition_level <= code */
export const CONDITION_OPTIONS = [
  { code: 0, label: '全新' },
  { code: 1, label: '九成新' },
  { code: 2, label: '七成新' },
  { code: 3, label: '五成新及以下' }
]

export const CONDITION_DESC = {
  0: '全新',
  1: '九成新',
  2: '七成新',
  3: '五成新及以下'
}

/* ---------------- 校区与地标（两级联动） ----------------
   江淮校区在信阳，与郑州两校区不通勤 → 校区筛选有真实业务意义 */
export const CAMPUS_LIST = ['龙子湖', '花园', '江淮']

const COMMON_PLACES = ['图书馆', '食堂', '教学楼', '宿舍区', '校门口', '快递站', '体育馆', '操场']

export const CAMPUS_PLACES = {
  龙子湖: COMMON_PLACES,
  花园: COMMON_PLACES,
  江淮: COMMON_PLACES
}

/** 拼接落库用的 trade_place（后端仍只存一个字符串字段，不改表） */
export function buildTradePlace(campus, place) {
  if (!campus) return ''
  if (!place) return campus
  return place.startsWith(campus) ? place : `${campus} · ${place}`
}

/* ---------------- 排序 ----------------
   ⚠️ 后端只确认了默认值 `newest`，其余取值需在联调时验证后回填 */
export const SORT_OPTIONS = [
  { value: 'newest', label: '最新' },
  { value: 'priceAsc', label: '价格 ↑' },
  { value: 'priceDesc', label: '价格 ↓' },
  { value: 'views', label: '最多浏览' }
]

/* ---------------- 错误码 ----------------
   🔴 只有在需要「前置拦截」时才用错误码；其余情况直接把后端 message 展示给用户 */
export const ERR = {
  UNAUTHORIZED: 401,
  PARAM_ERROR: 400,

  USERNAME_EXISTS: 10001,
  PHONE_EXISTS: 10002,
  USER_NOT_FOUND: 10003,
  USERNAME_OR_PASSWORD_ERROR: 10004,
  USER_DISABLED: 10005,
  OLD_PASSWORD_ERROR: 10006,

  PRODUCT_NOT_FOUND: 20003,
  PRODUCT_NOT_ON_SALE: 20004,
  PRODUCT_NOT_OWNED: 20005,
  PRODUCT_STATUS_ILLEGAL: 20006,
  ALREADY_FAVORITED: 20007,
  NOT_FAVORITED: 20008,
  IMAGE_TYPE_NOT_ALLOWED: 20010,
  IMAGE_TOO_LARGE: 20011,
  CANNOT_FAVORITE_OWN_PRODUCT: 20013,

  ORDER_NOT_FOUND: 30001,
  PRODUCT_LOCK_FAILED: 30002,
  ORDER_STATUS_ILLEGAL: 30003,
  CANNOT_BUY_OWN_PRODUCT: 30004,
  ORDER_NOT_OWNED: 30005
}

/** 需要做按钮置灰的前置拦截场景 */
export function shouldDisableActions(owned) {
  return owned === true
}

/* ---------------- 图片上传 ----------------
   后端限制：jpg / jpeg / png / gif / webp；最多 9 张（发布接口 imageUrls 上限） */
export const IMAGE_ACCEPT = 'image/jpeg,image/png,image/gif,image/webp'
export const IMAGE_ACCEPT_EXT = ['jpg', 'jpeg', 'png', 'gif', 'webp']
export const MAX_IMAGE_COUNT = 9
