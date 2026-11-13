import { defineStore } from 'pinia'
import { getShopStatus, setShopStatus } from '@/api/shop'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'

/** 店铺营业状态（顶栏徽标 + 店铺设置页共享） */
export const useShopStore = defineStore('shop', {
  state: () => ({
    /** 1 营业中 / 0 已打烊 / null 未加载 */
    status: null as number | null,
    loading: false,
  }),
  getters: {
    isOpen: (s) => s.status === 1,
  },
  actions: {
    async fetchStatus() {
      try {
        this.status = await getShopStatus()
      } catch {
        this.status = null
      }
    },
    async toggle(): Promise<boolean> {
      const next: 0 | 1 = this.status === 1 ? 0 : 1
      this.loading = true
      try {
        await setShopStatus(next)
        this.status = next
        ElMessage.success(next === 1 ? '已切换为营业中' : '已打烊，注意休息')
        return true
      } catch {
        return false
      } finally {
        this.loading = false
      }
    },
  },
})
