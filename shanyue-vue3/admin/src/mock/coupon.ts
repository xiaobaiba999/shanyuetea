/* =========================================================
   优惠券模板 mock：严格模拟 /admin/coupon/* 行为（模块 14）
   业务规则与真实后端对齐：
   - 名称唯一；满减券门槛必须 ≥ 面额（否则每单必亏，属于配置错误）
   - 修改时发放总量不可小于「已领取张数」（否则已发出的券无来源）
   - 已有用户领取的券不可删除，只能下架停止发放（保护已发出券的可用性）
   - 上/下架只影响 C 端券广场可见性，不影响已领取未使用的券
   ========================================================= */
import type { CouponDTO, CouponPageQuery, CouponTemplate, PageResult, Result } from '@/types'
import { clone, dbStore, fail, nextId, now, ok } from './db'

/** 已领取张数 */
function receivedOf(t: CouponTemplate): number {
  return Math.max(0, t.totalCount - t.remainCount)
}

/** 校验并规范化 DTO，失败返回错误信息 */
function validate(dto: CouponDTO, list: CouponTemplate[], selfId?: number): string | null {
  const name = dto.name?.trim()
  if (!name) return '优惠券名称不能为空'
  if (name.length > 20) return '优惠券名称不能超过 20 个字'
  if (list.some((c) => c.id !== selfId && c.name === name)) return '优惠券名称已存在'
  if (!dto.discount || dto.discount <= 0) return '优惠金额必须大于 0'
  if (dto.type === 1 && dto.threshold < dto.discount) {
    return '满减券的使用门槛不能低于优惠金额（如满 25 减 4）'
  }
  if (!dto.validDays || dto.validDays <= 0) return '有效期天数必须大于 0'
  if (!dto.totalCount || dto.totalCount <= 0) return '发放总量必须大于 0'
  return null
}

/** 14.1 优惠券分页查询 GET /admin/coupon/page */
export function mockCouponPage(query: CouponPageQuery): Result<PageResult<CouponTemplate>> {
  let list = dbStore.coupons.load()
  const name = query.name?.trim()
  if (name) list = list.filter((c) => c.name.includes(name))
  if (query.status === 0 || query.status === 1) list = list.filter((c) => c.status === query.status)
  // 发放中的在前，同状态按创建时间倒序
  list = [...list].sort((a, b) => b.status - a.status || b.createTime.localeCompare(a.createTime))
  const total = list.length
  const start = (query.page - 1) * query.pageSize
  const records = clone(list.slice(start, start + query.pageSize))
  return ok({ total, records })
}

/** 14.2 新增优惠券 POST /admin/coupon */
export function mockAddCoupon(dto: CouponDTO): Result<null> {
  const list = dbStore.coupons.load()
  const err = validate(dto, list)
  if (err) return fail(err)
  const t = now()
  const type = dto.type
  const threshold = type === 2 ? 0 : dto.threshold
  const description =
    dto.description?.trim() ||
    (type === 2 ? `无门槛立减 ${dto.discount} 元` : `满 ${threshold} 元减 ${dto.discount} 元`)
  list.push({
    id: nextId('coupon'),
    name: dto.name.trim(),
    type,
    threshold,
    discount: dto.discount,
    validDays: dto.validDays,
    totalCount: dto.totalCount,
    remainCount: dto.totalCount,
    status: 1,
    description,
    createTime: t,
    updateTime: t,
  })
  dbStore.coupons.save(list)
  return ok(null)
}

/** 14.3 修改优惠券 PUT /admin/coupon */
export function mockUpdateCoupon(dto: CouponDTO): Result<null> {
  const list = dbStore.coupons.load()
  const tpl = list.find((c) => c.id === dto.id)
  if (!tpl) return fail('优惠券不存在')
  const err = validate(dto, list, dto.id)
  if (err) return fail(err)
  const received = receivedOf(tpl)
  if (dto.totalCount < received) {
    return fail(`发放总量不能小于已领取张数（${received} 张），如需止损请下架`)
  }
  const type = dto.type
  tpl.name = dto.name.trim()
  tpl.type = type
  tpl.threshold = type === 2 ? 0 : dto.threshold
  tpl.discount = dto.discount
  tpl.validDays = dto.validDays
  tpl.totalCount = dto.totalCount
  tpl.remainCount = Math.max(0, tpl.totalCount - received)
  if (dto.description?.trim()) tpl.description = dto.description.trim()
  else tpl.description = type === 2 ? `无门槛立减 ${dto.discount} 元` : `满 ${tpl.threshold} 元减 ${dto.discount} 元`
  tpl.updateTime = now()
  dbStore.coupons.save(list)
  return ok(null)
}

/** 14.4 上架/下架 POST /admin/coupon/status/{status}?id= */
export function mockCouponStatus(status: 0 | 1, id: number): Result<null> {
  const list = dbStore.coupons.load()
  const tpl = list.find((c) => c.id === id)
  if (!tpl) return fail('优惠券不存在')
  tpl.status = status
  tpl.updateTime = now()
  dbStore.coupons.save(list)
  return ok(null)
}

/** 14.5 删除优惠券 DELETE /admin/coupon?id=（已有用户领取时 code=0） */
export function mockDeleteCoupon(id: number): Result<null> {
  const list = dbStore.coupons.load()
  const tpl = list.find((c) => c.id === id)
  if (!tpl) return fail('优惠券不存在')
  const received = receivedOf(tpl)
  if (received > 0) {
    return fail('该券已有用户领取，不可删除；如需停止发放请下架')
  }
  dbStore.coupons.save(list.filter((c) => c.id !== id))
  return ok(null)
}
