/** 金额与时间格式化工具（v2.1：价格单位「元」，与 API-SPEC v2.0 及后端 BigDecimal 直出一致）
 *  历史：v1.6 曾约定「分」并提供 fenToYuan/yuanToFen 换算；v2.0 契约统一改「元」后这两个
 *  函数已废弃删除——后端 BigDecimal 是元、表单输入是元、展示也是元，全程无需换算。 */

/** 金额格式化（元）：15.9 → '15.90'；空值按 0 处理 */
export function fmtMoney(n: number | null | undefined): string {
  return (n ?? 0).toFixed(2)
}

/** Date → 'yyyy-MM-dd HH:mm:ss' */
export function fmtDateTime(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** Date → 'yyyy-MM-dd' */
export function ymd(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** Date → 'MM-dd' */
export function md(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 规格快照 JSON 串 → 选项文案：'[{"groupName":"杯型","optionName":"大杯"}]' → '大杯·冰'
 *  （与 C 端 h5/小程序 parseSpecText 同款口径；解析失败时原样返回兜底） */
export function parseSpecSelected(str: string | null | undefined): string {
  if (!str) return ''
  let arr: unknown = null
  try {
    arr = JSON.parse(str)
  } catch {
    return String(str)
  }
  if (!Array.isArray(arr)) return String(str)
  return arr
    .map((g) => {
      if (g && typeof g === 'object' && 'optionName' in g) {
        const v = (g as { optionName?: unknown }).optionName
        return v == null ? '' : String(v)
      }
      return ''
    })
    .filter(Boolean)
    .join('·')
}
