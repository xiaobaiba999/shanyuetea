<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast, showToast } from 'vant'
import type { ShopVO, UserCouponVO } from '@/types'
import { getShopStatus } from '@/api/shop'
import { submitOrder } from '@/api/order'
import { getUsableCoupons } from '@/api/coupon'
import { useCartStore } from '@/stores/cart'
import { fmtPrice } from '@/utils/format'
import ProductImage from '@/components/ProductImage.vue'

/**
 * 确认下单页：清单 + 备注 + 手机号（选填）+ 优惠券选择 + 营业状态校验 + 提交
 * 优惠抵扣链路：拉取可用券 → 选券 → 提交带 userCouponId → 服务端校验并重算实付 → 核销
 */
const route = useRoute()
const router = useRouter()
const cart = useCartStore()
const shop = ref<ShopVO | null>(null)
const remark = ref('')
const phone = ref('')
const submitting = ref(false)

/* ---------- 优惠券选择 ---------- */
const couponPopup = ref(false)
const usableCoupons = ref<UserCouponVO[]>([])
const selectedCouponId = ref<number | null>(null)

const selectedCoupon = computed(() => usableCoupons.value.find((c) => c.id === selectedCouponId.value) ?? null)
/** 抵扣金额：不超过商品总额（后端仍会重算校验） */
const discount = computed(() => (selectedCoupon.value ? Math.min(selectedCoupon.value.discount, cart.totalPrice) : 0))
const payAmount = computed(() => Math.max(0, cart.totalPrice - discount.value))

const closed = computed(() => shop.value?.status === 0)

onMounted(async () => {
  shop.value = await getShopStatus().catch(() => null)
  await cart.refresh().catch(() => {})
  await loadUsableCoupons().catch(() => {})
})

/** 按当前购物车金额拉取可用券（下单前服务端还会再验一次） */
async function loadUsableCoupons() {
  usableCoupons.value = await getUsableCoupons(cart.totalPrice).catch(() => [])
  // 已选券若不再可用（金额变动/过期），自动取消选择
  if (selectedCouponId.value && !usableCoupons.value.some((c) => c.id === selectedCouponId.value)) {
    selectedCouponId.value = null
  }
}

async function openCouponPopup() {
  await loadUsableCoupons()
  couponPopup.value = true
}

function chooseCoupon(c: UserCouponVO | null) {
  selectedCouponId.value = c?.id ?? null
  couponPopup.value = false
}

function goBack() {
  router.back()
}

/** 手机号跳过校验：仅在填写了内容时校验格式 */
async function handleSubmit() {
  if (submitting.value) return
  if (!cart.items.length) {
    showToast('购物车是空的，先去点单吧')
    return
  }
  if (phone.value && !/^1\d{10}$/.test(phone.value)) {
    showFailToast('手机号格式不正确（可不填）')
    return
  }
  submitting.value = true
  try {
    // 提交前再校验一次营业状态（防止浏览期间打烊）
    const shopNow = await getShopStatus()
    if (shopNow.status === 0) {
      showFailToast('门店已打烊，暂时不能下单')
      return
    }
    const goodsAmount = cart.totalPrice // 先记录商品总额（下单后购物车会被清空）
    const vo = await submitOrder({
      remark: remark.value.trim() || undefined,
      phone: phone.value.trim() || undefined,
      userCouponId: selectedCouponId.value ?? undefined,
    })
    await cart.refresh() // 下单成功后端已清购物车，同步本地
    router.replace(
      `/success/${vo.pickUpCode}?amount=${vo.orderAmount}&discount=${vo.discountAmount}&goods=${goodsAmount}&wait=${vo.estimatedWaitMinutes}&no=${vo.orderNumber}`,
    )
  } catch {
    /* 网络层已 toast */
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="confirm-page">
    <van-nav-bar title="确认下单" left-arrow safe-area-inset-top @click-left="goBack" />

    <div class="page-body">
      <!-- 商品清单 -->
      <div class="card list-card">
        <p class="card-title">取餐商品</p>
        <ul>
          <li v-for="row in cart.items" :key="row.id" class="item">
            <div class="thumb">
              <ProductImage :src="row.image" :alt="row.drinkName" :name="row.drinkName" />
            </div>
            <div class="info">
              <p class="name">{{ row.drinkName }}</p>
              <p v-if="row.specText" class="spec line-clamp-1">{{ row.specText }}</p>
              <p class="unit">¥{{ fmtPrice(row.amount) }} × {{ row.number }}</p>
            </div>
            <p class="subtotal">¥{{ fmtPrice(row.amount * row.number) }}</p>
          </li>
        </ul>
        <van-empty v-if="!cart.items.length" description="购物车是空的">
          <van-button round type="primary" size="small" @click="router.replace('/')">去点单</van-button>
        </van-empty>
      </div>

      <!-- 备注 / 手机号 -->
      <div class="card form-card">
        <van-field
          v-model="remark"
          label="备注"
          type="textarea"
          rows="2"
          maxlength="50"
          show-word-limit
          autosize
          placeholder="口味偏好、其他要求（如少冰、不要搅拌棒）"
        />
        <van-field
          v-model="phone"
          label="手机号"
          type="tel"
          maxlength="11"
          clearable
          placeholder="选填，方便店家联系您"
        />
      </div>

      <!-- 优惠券 -->
      <div class="card coupon-card">
        <button class="coupon-row" :disabled="!cart.items.length" @click="openCouponPopup">
          <span class="cr-label">优惠券</span>
          <span class="cr-value" :class="{ negative: !!selectedCoupon }">
            {{
              selectedCoupon
                ? `-¥${fmtPrice(discount)}`
                : usableCoupons.length
                  ? `${usableCoupons.length} 张可用`
                  : '暂无可用'
            }}
          </span>
          <span class="cr-arrow">›</span>
        </button>
      </div>

      <p class="tip">自取免运费 · 取餐号下单后自动生成</p>
    </div>

    <!-- 底部合计 + 提交 -->
    <div class="footer safe-bottom">
      <div class="total">
        <span class="total-label">合计</span>
        <span class="yen">¥</span>
        <span class="num price-num">{{ fmtPrice(payAmount) }}</span>
        <span v-if="discount > 0" class="saved">已优惠 ¥{{ fmtPrice(discount) }}</span>
      </div>
      <van-button
        class="submit-btn"
        type="primary"
        round
        :loading="submitting"
        loading-text="提交中…"
        :disabled="!cart.items.length || closed"
        @click="handleSubmit"
      >
        {{ closed ? '已打烊' : '提交订单' }}
      </van-button>
    </div>

    <!-- 优惠券选择弹层 -->
    <van-popup v-model:show="couponPopup" position="bottom" round>
      <div class="coupon-sheet">
        <div class="cs-head">
          <span>选择优惠券</span>
          <button class="cs-close" aria-label="关闭" @click="couponPopup = false">
            <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>
        <div class="cs-list">
          <button class="cs-item none" :class="{ active: !selectedCouponId }" @click="chooseCoupon(null)">
            <span class="csi-none">不使用优惠券</span>
            <svg v-if="!selectedCouponId" class="csi-check" viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M4 12.5l5 5L20 6.5" />
            </svg>
          </button>
          <button
            v-for="c in usableCoupons"
            :key="c.id"
            class="cs-item coupon"
            :class="{ active: c.id === selectedCouponId }"
            @click="chooseCoupon(c)"
          >
            <div class="csi-left">
              <p class="csi-amount"><i>¥</i>{{ fmtPrice(c.discount) }}</p>
              <p class="csi-threshold">{{ c.threshold ? `满${fmtPrice(c.threshold)}可用` : '无门槛' }}</p>
            </div>
            <div class="csi-main">
              <p class="csi-name">{{ c.name }}</p>
              <p class="csi-expire">有效期至 {{ c.expireTime.slice(0, 10) }}</p>
            </div>
            <span v-if="c.id === selectedCouponId" class="csi-check">
              <svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M4 12.5l5 5L20 6.5" />
              </svg>
            </span>
          </button>
          <div v-if="!usableCoupons.length" class="cs-empty">
            暂无可用优惠券，去「会员中心」领券吧
          </div>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.confirm-page {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
  background: var(--paper);
}

.page-body {
  flex: 1;
  padding: 12px 12px 0;
}

.card {
  background: var(--card);
  border: 1px solid var(--card-line);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.list-card {
  padding: 14px 16px;
}

.card-title {
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 700;
}

.item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.item + .item {
  margin-top: 12px;
}

.thumb {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--paper-deep);
}

.info {
  flex: 1;
  min-width: 0;
}

.name {
  font-size: 14px;
  font-weight: 600;
}

.spec {
  margin-top: 1px;
  font-size: 10px;
  color: var(--ink-3);
}

.unit {
  margin-top: 2px;
  font-size: 11px;
  color: var(--ink-3);
}

.subtotal {
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 700;
  font-family: var(--font-serif);
  font-variant-numeric: tabular-nums;
  color: var(--terracotta);
}

.form-card {
  margin-top: 12px;
  padding: 4px 0;
  overflow: hidden;
}

/* ---------- 优惠券行 ---------- */
.coupon-card {
  margin-top: 12px;
  overflow: hidden;
}

.coupon-row {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 8px;
  padding: 14px 16px;
  background: transparent;
  border: none;
  cursor: pointer;
  text-align: left;
}

.coupon-row:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.cr-label {
  flex-shrink: 0;
  width: 52px;
  font-size: 13px;
  font-weight: 600;
}

.cr-value {
  flex: 1;
  text-align: right;
  font-size: 13px;
  color: var(--ink-3);
}

.cr-value.negative {
  color: var(--terracotta);
  font-weight: 800;
}

.cr-arrow {
  flex-shrink: 0;
  color: var(--ink-3);
  font-size: 16px;
  line-height: 1;
}

/* ---------- 券选择弹层 ---------- */
.coupon-sheet {
  padding: 16px 14px calc(16px + env(safe-area-inset-bottom));
}

.cs-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 12px;
  font-size: 15px;
  font-weight: 800;
}

.cs-close {
  display: grid;
  place-items: center;
  border: none;
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  padding: 4px 8px;
}

.cs-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 52vh;
  overflow-y: auto;
}

.cs-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 14px;
  border: 1.5px dashed rgba(200, 155, 90, 0.45);
  border-radius: 12px;
  background: var(--card);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, background 0.15s;
}

.cs-item.active {
  border-color: var(--pine);
  border-style: solid;
  background: var(--pine-mist);
}

.csi-none {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
}

.csi-left {
  flex-shrink: 0;
  min-width: 84px;
  text-align: center;
  padding-right: 12px;
  border-right: 1px dashed var(--card-line);
}

.csi-amount {
  font-size: 22px;
  font-weight: 800;
  font-family: var(--font-serif);
  font-variant-numeric: tabular-nums;
  color: var(--terracotta);
}

.csi-amount i {
  font-style: normal;
  font-size: 12px;
  margin-right: 1px;
}

.csi-threshold {
  margin-top: 2px;
  font-size: 10px;
  color: var(--ink-3);
}

.csi-main {
  flex: 1;
  min-width: 0;
}

.csi-name {
  font-size: 14px;
  font-weight: 700;
}

.csi-expire {
  margin-top: 3px;
  font-size: 11px;
  color: var(--ink-3);
}

.csi-check {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--pine);
  color: #fff;
}

.cs-empty {
  padding: 28px 0 12px;
  text-align: center;
  font-size: 13px;
  color: var(--ink-3);
}

.tip {
  margin: 12px 4px 16px;
  font-size: 11px;
  color: var(--ink-3);
}

.footer {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  background: var(--card);
  border-top: 1px solid var(--card-line);
}

.total {
  flex: 1;
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.total-label {
  margin-right: 4px;
  font-size: 12px;
  color: var(--ink-2);
}

.yen {
  font-size: 12px;
  font-weight: 700;
  color: var(--terracotta);
}

.num {
  font-size: 22px;
}

.saved {
  margin-left: 8px;
  font-size: 11px;
  font-weight: 700;
  color: var(--terracotta);
  background: rgba(199, 84, 47, 0.08);
  border-radius: 999px;
  padding: 2px 8px;
}

.submit-btn {
  flex-shrink: 0;
  min-width: 132px;
  height: 42px;
  font-weight: 700;
}
</style>
