/** 全局环境开关：mock 演示数据（读 .env 的 VITE_USE_MOCK，缺省视为关闭——一切以后端真实返回为准，未实现模块显示空状态；想无后端预览界面时在 .env 里设 VITE_USE_MOCK=true） */
export const USE_MOCK = String(import.meta.env.VITE_USE_MOCK ?? 'false').toLowerCase() === 'true'
