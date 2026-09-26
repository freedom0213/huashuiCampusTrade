import request from './request'
import { IMAGE_ACCEPT_EXT } from '@/constants/enums'

/* 图片上传 —— 对应 /api/file/**，需登录 */

/**
 * 上传单张图片，返回 FileUploadVO { url, originalName, size }
 * url 是可直接用于 <img src> 的访问路径（/uploads/**，网关直通、游客可看）
 *
 * ⚠️ 先做本地预校验再上传：格式与体积不合格时后端会拒（20010 / 20011），
 *    本地拦掉能省一次往返，且提示更即时。
 */
export function uploadImage(file) {
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (!IMAGE_ACCEPT_EXT.includes(ext)) {
    return Promise.reject(new Error('只支持 jpg / jpeg / png / gif / webp 格式的图片'))
  }
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 批量上传（发布页九宫格用），返回 url 数组；顺序与入参一致 */
export async function uploadImages(files) {
  const results = []
  for (const file of files) {
    const vo = await uploadImage(file)
    results.push(vo.url)
  }
  return results
}
