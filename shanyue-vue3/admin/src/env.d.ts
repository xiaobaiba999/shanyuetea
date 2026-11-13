/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const component: DefineComponent<Record<string, never>, Record<string, never>, any>
  export default component
}

interface ImportMetaEnv {
  /** 'true' | 'false'：是否启用前端 mock */
  readonly VITE_USE_MOCK: string
  /** axios baseURL，默认 /api */
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
