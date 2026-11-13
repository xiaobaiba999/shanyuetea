/* 图片上传接口：POST /api/common/upload (multipart/form-data, 字段名 file) → data 为 url 字符串 */
import { request } from '@/utils/request'
import { USE_MOCK } from '@/utils/env'
import { unwrapMock } from './_base'
import { mockUploadImage } from '@/mock/upload'

/**
 * mock 模式：File → canvas 压缩 → base64 dataURL 直接作为 url 返回（可直接预览/保存）
 * 真实模式：FormData 上传，后端返回的 data 直接是 url 字符串（本地磁盘存储或 OSS）
 */
export async function uploadImage(file: File): Promise<string> {
  if (USE_MOCK) return unwrapMock(await mockUploadImage(file))
  const formData = new FormData()
  formData.append('file', file)
  const res = await request.post<string>('/common/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data
}
