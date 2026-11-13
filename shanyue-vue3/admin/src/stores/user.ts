import { defineStore } from 'pinia'
import { USER_KEY, TOKEN_KEY } from '@/utils/request'
import type { LoginResult } from '@/types'

/** 当前登录管理员（token + 姓名，localStorage 持久化） */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    name: localStorage.getItem(USER_KEY) || '',
  }),
  getters: {
    isLogin: (s) => !!s.token,
  },
  actions: {
    setLogin(info: LoginResult) {
      this.token = info.token
      this.name = info.name
      localStorage.setItem(TOKEN_KEY, info.token)
      localStorage.setItem(USER_KEY, info.name)
    },
    logout() {
      this.token = ''
      this.name = ''
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
  },
})
