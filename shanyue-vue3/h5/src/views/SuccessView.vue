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
      <div class="seal hero-seal" aria-hidden="true"><i>山</i><i>月</i></div>
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
        <span>完成订单后可得积分</span>
        <b>+{{ Math.floor(amount / 100) }}</b>
      </div>
      <div v-if="orderNumber" class="meta-row">
        <span>订单号</span>
        <b class="order-no">{{ orderNumber }}</b>
      </div>
    </div>

    <div class="actions safe-bottom">
      <button class="btn-primary" @click="goQuery">查询订单进度</button>
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
  background: var(--paper);
}

.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* 印章（品牌标识）：茶金描边 + 墨绿衬线「山月」 */
.hero-seal {
  width: 54px;
  height: 54px;
  background: var(--card);
  box-shadow: 0 4px 14px rgba(43, 38, 32, 0.12);
}

.hero-seal i {
  font-size: 17px;
  line-height: 1.12;
}

.hero h1 {
  margin-top: 10px;
  font-family: var(--font-serif);
  font-size: 21px;
  font-weight: 700;
  letter-spacing: 4px;
}

.hero-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-3);
}

/* 取餐号大卡：白底描边 + 内层茶金虚线框（信笺感） */
.code-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  margin-top: 24px;
  padding: 26px 0 22px;
  border-radius: var(--radius-card);
  background: var(--card);
  border: 1px solid var(--card-line);
  box-shadow: var(--shadow-card);
}

.code-card::before {
  content: '';
  position: absolute;
  inset: 6px;
  border: 1px dashed rgba(200, 155, 90, 0.5);
  border-radius: 10px;
  pointer-events: none;
}

.code-label {
  font-size: 12px;
  letter-spacing: 2px;
  color: var(--ink-2);
}

.code {
  margin-top: 6px;
  font-family: var(--font-serif);
  font-size: 2.6rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  letter-spacing: 6px;
  line-height: 1.1;
  color: var(--pine);
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
  border: 1px solid var(--card-line);
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
  border-top: 1px dashed var(--card-line);
}

.meta-row b {
  font-weight: 700;
  color: var(--ink);
}

.meta-row.saving span,
.meta-row.saving b {
  color: var(--terracotta);
  font-weight: 700;
}

/* 积分提示行 */
.meta-row.points-hint span {
  color: var(--ink-2);
}

.meta-row.points-hint b {
  color: var(--pine);
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
  border: 1px solid var(--card-line);
  background: var(--card);
  transition: transform 0.15s ease, border-color 0.2s ease;
}

.btn-ghost:active {
  transform: scale(0.97);
  border-color: var(--gold);
}
</style>
