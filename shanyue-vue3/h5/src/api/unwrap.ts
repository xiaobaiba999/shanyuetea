import type { Result } from '@/types'

/**
 * mock 函数统一返回 Result<T>，这里解包为 data：
 * code=0 时抛错（与 request.ts 网络层行为对齐）
 */
export async function unwrap<T>(res: Promise<Result<T>>): Promise<T> {
  const r = await res
  if (r.code === 0) throw new Error(r.msg || '请求失败')
  return r.data
}

/** mock 返回 data 为 null 的成功结果（增删改类接口） */
export async function unwrapNull(res: Promise<Result<null>>): Promise<void> {
  await unwrap(res)
}

/** mock 返回 data 为数组的接口 */
export function unwrapData<T>(res: Promise<Result<T>>): Promise<T> {
  return unwrap(res)
}
