import request from './request'

/* 分类 —— 对应 /api/category/**，游客可访问 */

/** 分类列表，按 sort 升序。首页胶囊行 / 搜索筛选 / 发布页下拉都用它 */
export const listCategories = () => request.get('/category/list')
