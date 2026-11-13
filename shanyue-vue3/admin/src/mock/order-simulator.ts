/* =========================================================
   山月茶咖 · 来单提醒模拟器（仅 mock 模式）
   对应真实后端的 WebSocket 推送（/ws/{shopId} → NEW_ORDER）：
   真实模式下由 C 端下单触发服务端推送；mock 下用定时器模拟顾客下单，
   便于单人演示「新订单 → 右上角通知 → 提示音 → 订单列表自动刷新」闭环。
   ========================================================= */
import { h } from 'vue'
import { ElNotification } from 'element-plus'
import 'element-plus/es/components/notification/style/css'
import { USE_MOCK } from '@/utils/env'
import { fmtMoney } from '@/utils/format'
import { mockCreateOrder } from './order'
import type { OrderVO } from '@/types'

const PREF_KEY = 'shanyue-admin-sim-notify'
const INTERVAL_MS = 45_000

let timer: ReturnType<typeof setInterval> | null = null
let goOrderPage: (() => void) | null = null

/** 用户偏好（默认开启），重置演示数据不会清除该设置 */
export function isSimEnabled(): boolean {
  try {
    return localStorage.getItem(PREF_KEY) !== 'off'
  } catch {
    return true
  }
}

export function setSimEnabled(on: boolean): void {
  try {
    localStorage.setItem(PREF_KEY, on ? 'on' : 'off')
  } catch {
    /* ignore */
  }
  if (on) startSim(goOrderPage ?? undefined)
  else stopSim()
}

/** 提示音：Web Audio 合成两声「叮咚」，无外部音频资源 */
function playDing(): void {
  try {
    const Ctx = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!Ctx) return
    const ctx = new Ctx()
    void ctx.resume()
    const tone = (freq: number, start: number, dur: number) => {
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.connect(gain)
      gain.connect(ctx.destination)
      osc.type = 'sine'
      osc.frequency.setValueAtTime(freq, ctx.currentTime + start)
      gain.gain.setValueAtTime(0.0001, ctx.currentTime + start)
      gain.gain.exponentialRampToValueAtTime(0.16, ctx.currentTime + start + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + start + dur)
      osc.start(ctx.currentTime + start)
      osc.stop(ctx.currentTime + start + dur + 0.05)
    }
    tone(880, 0, 0.28) // 叮
    tone(1174, 0.16, 0.4) // 咚
    window.setTimeout(() => void ctx.close().catch(() => undefined), 1200)
  } catch {
    /* 自动播放策略限制时静默降级 */
  }
}

/** 生成一条随机新订单并弹出来单提醒 */
function fireOnce(): void {
  const res = mockCreateOrder()
  if (res.code !== 1 || !res.data) return
  const order = res.data as OrderVO
  playDing()
  const itemsBrief = order.orderDetailList.map((it) => `${it.name}×${it.number}`).join('、')
  const modeText = order.deliveryMode === 2 ? '外卖配送' : '门店自取'
  const instance = ElNotification({
    title: '🔔 来单提醒（WebSocket 模拟）',
    message: h('div', { class: 'new-order-notify' }, [
      h(
        'div',
        { style: 'display:flex;align-items:baseline;gap:10px;margin-bottom:6px;' },
        [
          h(
            'span',
            {
              style:
                'font-size:26px;font-weight:800;letter-spacing:2px;color:#b0702e;font-family:Consolas,monospace;',
            },
            order.pickupNumber || '—',
          ),
          h('span', { style: 'font-size:16px;font-weight:700;color:#3f2a1a;' }, `¥ ${fmtMoney(order.amount)}`),
          h(
            'span',
            {
              style:
                'font-size:11px;padding:2px 8px;border-radius:999px;background:#f8ede0;color:#b0702e;font-weight:600;',
            },
            modeText,
          ),
        ],
      ),
      h('div', { style: 'font-size:12px;color:#8c7a6b;line-height:1.7;' }, [
        h('div', null, `品项：${itemsBrief}`),
        order.deliveryMode === 2 && order.consignee
          ? h('div', null, `收货：${order.consignee} ${order.phone ?? ''}（${order.deliveryAddress ?? ''}）`)
          : null,
        order.remark ? h('div', null, `备注：${order.remark}`) : null,
        h('div', { style: 'margin-top:4px;color:#c08552;font-weight:600;' }, '点击查看订单 →'),
      ]),
    ]),
    placement: 'top-right',
    duration: 8000,
    showClose: true,
    onClick: () => {
      instance.close()
      goOrderPage?.()
    },
  })
  // 通知全局（订单页/仪表盘监听后自动刷新数据）
  window.dispatchEvent(new CustomEvent('shanyue:orders-changed', { detail: { pickupNumber: order.pickupNumber } }))
}

/** 启动定时模拟（mock 模式且用户未关闭时生效） */
export function startSim(goOrder?: () => void): void {
  if (!USE_MOCK) return
  if (goOrder) goOrderPage = goOrder
  stopSim()
  if (!isSimEnabled()) return
  timer = setInterval(fireOnce, INTERVAL_MS)
}

export function stopSim(): void {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

/** 供 QA/演示手动触发一条新订单（window.__shanyueNewOrder()） */
export function exposeTestHook(): void {
  if (!USE_MOCK) return
  ;(window as unknown as Record<string, unknown>).__shanyueNewOrder = fireOnce
}
