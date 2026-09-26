import request from './request'

/* 用户服务 —— 对应 /api/user/** */

export const register = (data) => request.post('/user/register', data)

export const login = (data) => request.post('/user/login', data)

/** 当前登录用户完整信息（含本人手机号） */
export const getMyInfo = () => request.get('/user/info')

export const updateMyInfo = (data) => request.put('/user/info', data)

export const changePassword = (data) => request.put('/user/password', data)

/** 用户简要信息（昵称/头像/院系，**不含手机号**），游客可访问 */
export const getUserBrief = (id) => request.get(`/user/detail/${id}`)

/** 卖家联系方式 —— 需要登录，且是手机号在全站的唯一出口 */
export const getContact = (id) => request.get(`/user/${id}/contact`)

/** 无 logout 接口：退出登录只清本地 token */
