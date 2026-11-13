<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import { fmtPrice } from '@/utils/format'

/**
 * 下单成功页：大号取餐号 + 订单金额/预计等待 + 查询进度/继续点单
 */
const route = useRoute()
const router = useRouter()
const cart = useCartStore()

const pickUpCode = computed(() => String(route.params.pickUpCode || 'A01'))
const amount = computed(() => Number(route.query.amount || 0))
const discount = computed(() => Number(route.query.discount || 0))
const goods = computed(() => Number(route.query.goods || 0))
const wait = computed(() => Number(route.query.wait || 10))
const orderNumber = computed(() => String(route.query.no || ''))

function goQuery() {
  router.push({ path: '/query', query: { code: pickUpCode.value } })
}

/** 继续点单：跳转前确保本地购物车已清空 */
async function goHome() {
  try {
    await cart.clean()
  } catch {
    /* ignore */
  }
  router.replace('/')
}
</script>

<template>
  <div class="success-page">
    <div class="hero">
      <span class="hero-emoji">🎉</span>
      <h1>下单成功</h1>
      <p class="hero-sub">店家已收到您的订单，正在准备</p>
    </div>

    <!-- 取餐号大卡 -->
    <div class="code-card">
      <p class="code-label">取餐号</p>
      <p class="code">{{ pickUpCode }}</p>
      <p class="code-tip">请凭取餐号到店取餐</p>
    </div>

    <div class="meta-card">
      <template v-if="discount > 0">
        <div class="meta-row">
          <span>商品总额</span>
          <b>¥{{ fmtPrice(goods) }}</b>
        </div>
        <div class="meta-row saving">
          <span>优惠券抵扣</span>
          <b>-¥{{ fmtPrice(discount) }}</b>
        </div>
      </template>
      <div class="meta-row">
        <span>{{ discount > 0 ? '实付金额' : '支付金额' }}</span>
        <b>¥{{ fmtPrice(amount) }}</b>
      </div>
      <div class="meta-row">
        <span>预计等待</span>
        <b>{{ wait }} 分钟</b>
      </div>
      <div v-if="amount > 0" class="meta-row points-hint">
        <span>☕ 完成订单后可得积分</span>
        <b>+{{ Math.floor(amount / 100) }}</b>
      </div>
      <div v-if="orderNumber" class="meta-row">
        <span>订单号</span>
        <b class="order-no">{{ orderNumber }}</b>
      </div>
    </div>

    <div class="actions safe-bottom">
      <button class="btn-primary btn-gradient" @click="goQuery">查询订单进度</button>
      <button class="btn-ghost" @click="goHome">继续点单</button>
    </div>
  </div>
</template>

<style scoped>
.success-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 100dvh;
  padding: 40px 20px 0;
  background: var(--cream);
}

.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.hero-emoji {
  font-size: 44px;
}

.hero h1 {
  margin-top: 8px;
  font-size: 20px;
  font-weight: 800;
}

.hero-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-3);
}

/* 取餐号大卡 */
.code-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  margin-top: 24px;
  padding: 26px 0 22px;
  border-radius: var(--radius-card);
  background: linear-gradient(150deg, #fdf9f4, #f7ead9);
  border: 1px solid #f0dfc8;
  box-shadow: var(--shadow-card);
}

.code-label {
  font-size: 12px;
  letter-spacing: 2px;
  color: var(--ink-2);
}

.code {
  margin-top: 6px;
  font-size: 2.6rem;
  font-weight: 800;
  letter-spacing: 6px;
  line-height: 1.1;
  color: var(--caramel-deep);
}

.code-tip {
  margin-top: 10px;
  font-size: 11px;
  color: var(--ink-3);
}

/* 订单信息 */
.meta-card {
  width: 100%;
  margin-top: 14px;
  padding: 6px 16px;
  border-radius: var(--radius-card);
  background: var(--card);
  box-shadow: var(--shadow-card);
}

.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 11px 0;
  font-size: 13px;
  color: var(--ink-2);
}

.meta-row + .meta-row {
  border-top: 1px dashed #f0e8dc;
}

.meta-row b {
  font-weight: 700;
  color: var(--ink);
}

.meta-row.saving span,
.meta-row.saving b {
  color: #d4552e;
  font-weight: 700;
}

/* 积分提示行 */
.meta-row.points-hint span {
  color: #8b5e3c;
}

.meta-row.points-hint b {
  color: #c08552;
  font-weight: 800;
}

.order-no {
  font-size: 12px;
}

/* 操作按钮 */
.actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  margin-top: 26px;
  padding-left: 6px;
  padding-right: 6px;
}

.btn-primary {
  height: 44px;
  font-size: 14px;
}

.btn-ghost {
  height: 44px;
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-2);
  border-radius: var(--radius-btn);
  border: 1px solid #e3d7c6;
  background: var(--card);
}

.btn-ghost:active {
  transform: scale(0.97);
}
</style>
