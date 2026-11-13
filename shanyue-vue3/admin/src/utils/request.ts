import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import type { Result } from '@/types'

/** token / 用户名 的本地存储 key（注意：mock 数据使用 shanyue-admin-db- 前缀，重置演示数据不会清掉登录态） */
export const TOKEN_KEY = 'shanyue-admin-token'
export const USER_KEY = 'shanyue-admin-name'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

/* 请求拦截：自动携带 token（与苍穹外卖一致，header 名就叫 token） */
instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers = config.headers ?? {}
    ;(config.headers as Record<string, unknown>)['token'] = token
  }
  return config
})

/* 响应拦截：统一处理 code=0 业务错误 与 401 登录失效 */
instance.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code !== 1) {
        ElMessage.error(res.msg || '操作失败')
        return Promise.reject(new Error(res.msg || '操作失败'))
      }
      return res
    }
    return res
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      clearAuth()
      ElMessage.error('登录已失效，请重新登录')
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    } else {
      ElMessage.error(error?.response?.data?.msg || error?.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  },
)

/**
 * 类型包装：拦截器已把响应解为 Result<T>，
 * 业务层用法：const res = await request.get<PageResult<Drink>>('/admin/drink/page', { params }); res.data 即数据
 */
interface TypedAxios {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<Result<T>>
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<Result<T>>
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<Result<T>>
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<Result<T>>
}

export const request = instance as unknown as TypedAxios
