/** Mock 开关：读取 .env 的 VITE_USE_MOCK（缺省视为 true，保证开箱即用） */
export const USE_MOCK: boolean = String(import.meta.env.VITE_USE_MOCK).toLowerCase() !== 'false'

/** axios baseURL */
export const API_BASE_URL: string = String(import.meta.env.VITE_API_BASE_URL || '/api')
