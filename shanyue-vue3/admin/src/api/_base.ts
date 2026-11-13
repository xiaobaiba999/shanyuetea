/* api 层公共：mock Result 解包（失败时弹 ElMessage.error 并 reject，与真实模式行为一致） */
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import type { Result } from '@/types'

export function unwrapMock<T>(result: Result<T>): T {
  if (result.code !== 1) {
    ElMessage.error(result.msg || '操作失败')
    throw new Error(result.msg || '操作失败')
  }
  return result.data
}
