import axios from 'axios'
import { showFailToast } from 'vant'
import type { Result } from '@/types'
import { API_BASE_URL } from './env'

/**
 * 网络层（C端匿名访问，不携带 token，理论上不会出现 401）
 * - code===0：Toast 提示失败原因并 reject
 * - 成功：直接返回 res.data.data（解包 Result）
 */
const service = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
})

service.interceptors.response.use(
  (response) => {
    const res = response.data as Result
    if (!res || typeof res.code !== 'number') {
      showFailToast('响应格式异常')
      return Promise.reject(new Error('响应格式异常'))
    }
    if (res.code === 0) {
      showFailToast(res.msg || '请求失败')
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    return response
  },
  (error) => {
    showFailToast('网络异常，请稍后再试')
    return Promise.reject(error)
  },
)

async function httpGet<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await service.get<Result<T>>(url, { params })
  return res.data.data
}

async function httpPost<T>(url: string, data?: unknown): Promise<T> {
  const res = await service.post<Result<T>>(url, data)
  return res.data.data
}

async function httpPut<T>(url: string, data?: unknown): Promise<T> {
  const res = await service.put<Result<T>>(url, data)
  return res.data.data
}

async function httpDelete<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await service.delete<Result<T>>(url, { params })
  return res.data.data
}

export { httpGet, httpPost, httpPut, httpDelete }
