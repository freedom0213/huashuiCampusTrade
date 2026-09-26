import request from './request'

/* 商品服务 —— 对应 /api/product/** */

/**
 * 商品列表（游客可访问）
 * @param {object} params ProductQueryDTO
 *   page, size(≤50), categoryId, kw, campus, conditionLevel, sellerId, status, sort
 *   说明：conditionLevel 语义是「≥ 某档」；公开列表不传 status 即只出在售商品
 */
export const listProducts = (params) => request.get('/product/list', { params })

/** 我发布的商品（需登录），可用 status 按状态筛 */
export const listMyProducts = (params) => request.get('/product/mine', { params })

/**
 * 商品详情（游客可访问）
 * 🔴 owned / favorited 只在带 token 的请求里才准；游客态恒为 false
 */
export const getProductDetail = (id) => request.get(`/product/detail/${id}`)

/** 发布商品，返回新商品 id（字符串） */
export const publishProduct = (data) => request.post('/product', data)

/** 编辑商品（仅本人） */
export const updateProduct = (id, data) => request.put(`/product/${id}`, data)

export const offShelf = (id) => request.put(`/product/${id}/off-shelf`)

export const onShelf = (id) => request.put(`/product/${id}/on-shelf`)

export const deleteProduct = (id) => request.delete(`/product/${id}`)
