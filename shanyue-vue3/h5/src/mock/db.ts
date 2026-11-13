/**
 * Mock 数据库：localStorage 持久化（key 前缀 shanyue-h5-），刷新不丢
 * 「重置演示数据」= 清空全部前缀 key 并重新播种
 */
const PREFIX = 'shanyue-h5-'

export function dbGet<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(PREFIX + key)
    return raw ? (JSON.parse(raw) as T) : fallback
  } catch {
    return fallback
  }
}

export function dbSet<T>(key: string, value: T): void {
  try {
    localStorage.setItem(PREFIX + key, JSON.stringify(value))
  } catch {
    /* 存储不可用时静默降级为内存态 */
  }
}

export function dbRemove(key: string): void {
  try {
    localStorage.removeItem(PREFIX + key)
  } catch {
    /* ignore */
  }
}

/** 清空所有 shanyue-h5- 前缀的 mock 数据 */
export function dbClearAll(): void {
  try {
    const keys: string[] = []
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i)
      if (k && k.startsWith(PREFIX)) keys.push(k)
    }
    keys.forEach((k) => localStorage.removeItem(k))
  } catch {
    /* ignore */
  }
}

/** 模拟网络延迟 */
export function delay<T>(data: T, ms = 120): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(data), ms))
}

/** 成功 Result */
export function ok<T>(data: T) {
  return { code: 1 as const, msg: 'success', data }
}

/** 失败 Result */
export function fail(msg: string) {
  return { code: 0 as const, msg, data: null }
}
