/** 分 -> 元 显示字符串：1590 -> "15.9"，1900 -> "19" */
export function fmtPrice(fen: number): string {
  const yuan = fen / 100
  return Number.isInteger(yuan) ? String(yuan) : yuan.toFixed(2)
}

/** 取当天 yyyyMMdd（mock 订单号 / 取餐号按天重置用） */
export function todayStr(d: Date = new Date()): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}${m}${day}`
}

/** yyyy-MM-dd HH:mm:ss */
export function fmtDateTime(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
