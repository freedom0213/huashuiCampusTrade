import request from './request'

/* 收藏 —— 对应 /api/favorite/**，三个接口都需登录 */

/**
 * 收藏商品
 * 不能收藏自己发布的商品 → 错误码 20013；重复收藏 → 20007
 */
export const addFavorite = (productId) => request.post(`/favorite/${productId}`)

/** 取消收藏。未收藏过 → 20008 */
export const removeFavorite = (productId) => request.delete(`/favorite/${productId}`)

/**
 * 我的收藏（按收藏时间倒序）
 * 已下架 / 已售出的商品**仍会返回**并带出当前状态 —— 不要在前端过滤掉，
 * 否则用户会以为「我的收藏丢了」
 * @param {object} params { page, size(≤100) }
 */
export const listMyFavorites = (params) => request.get('/favorite/mine', { params })
