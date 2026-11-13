/* 图片上传 mock：POST /api/common/upload
   mock 模式不产生网络请求：File → 压缩 → base64 dataURL 直接回填为 url 字符串
   （canvas 压缩到最长边 640px、jpeg 0.85，避免 base64 撑爆 localStorage） */
import type { Result } from '@/types'
import { fail, ok } from './db'

const MAX_EDGE = 640

export function mockUploadImage(file: File): Promise<Result<string>> {
  return new Promise((resolve) => {
    if (!file.type.startsWith('image/')) {
      resolve(fail('仅支持上传图片文件'))
      return
    }
    const reader = new FileReader()
    reader.onerror = () => resolve(fail('读取图片失败'))
    reader.onload = () => {
      const dataUrl = String(reader.result)
      const img = new Image()
      img.onerror = () => resolve(ok(dataUrl))
      img.onload = () => {
        try {
          const scale = Math.min(1, MAX_EDGE / Math.max(img.width, img.height))
          const canvas = document.createElement('canvas')
          canvas.width = Math.max(1, Math.round(img.width * scale))
          canvas.height = Math.max(1, Math.round(img.height * scale))
          const ctx = canvas.getContext('2d')
          if (!ctx) {
            resolve(ok(dataUrl))
            return
          }
          ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
          resolve(ok(canvas.toDataURL('image/jpeg', 0.85)))
        } catch {
          resolve(ok(dataUrl))
        }
      }
      img.src = dataUrl
    }
    reader.readAsDataURL(file)
  })
}
