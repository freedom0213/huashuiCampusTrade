import request from './request'

/* 订单服务 —— 对应 /api/order/** */

/**
 * 创建订单（需登录）
 * 后端语义：商品必须处于「在售」；成功后商品被锁定，其他买家无法下单
 * 失败常见码：30002 手慢了，商品已被别人抢先买走 / 30004 不能购买自己发布的商品
 */
export const createOrder = (productId) => request.post('/order', { productId })

/**
 * 我的订单（需登录）
 * @param {object} params { role: 'all'|'buyer'|'seller', status, page, size(≤100) }
 */
export const listMyOrders = (params) => request.get('/order/mine', { params })

/** 订单详情（需登录，仅买卖双方可见） */
export const getOrderDetail = (orderNo) => request.get(`/order/detail/${orderNo}`)

/** 卖家历史成交笔数（游客可访问）。口径 = 已付款 + 交易完成，已取消不计 */
export const getSoldCount = (sellerId) => request.get(`/order/sold-count/${sellerId}`)

/** 确认已线下付款（仅买家）。模拟支付，资金不经手平台 */
export const payOrder = (orderNo) => request.put(`/order/${orderNo}/pay`)

/** 确认交易完成（买卖双方均可）。已付款之后不可取消 */
export const completeOrder = (orderNo) => request.put(`/order/${orderNo}/complete`)

/** 取消订单（买卖双方均可）。只有「待付款」可取消，取消后商品由 MQ 异步恢复在售 */
export const cancelOrder = (orderNo) => request.put(`/order/${orderNo}/cancel`)
