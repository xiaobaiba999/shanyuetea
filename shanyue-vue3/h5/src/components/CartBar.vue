<script setup lang="ts">
import { ref } from 'vue'
import { showFailToast, showSuccessToast, showToast } from 'vant'
import type { ShoppingCartVO } from '@/types'
import { useCartStore } from '@/stores/cart'
import { fmtPrice } from '@/utils/format'
import ProductImage from './ProductImage.vue'

/**
 * 底部购物车栏 + 购物车浮层（明细行：名称+规格+单价、加减、删除整行、清空）
 */
const props = withDefaults(defineProps<{ shopOpen?: boolean }>(), { shopOpen: true })
const emit = defineEmits<{ (e: 'checkout'): void }>()

const cart = useCartStore()
const panelOpen = ref(false)

function togglePanel() {
  if (cart.totalCount === 0) {
    showToast('先挑一杯喜欢的咖啡吧 ☕')
    return
  }
  panelOpen.value = !panelOpen.value
}

function handleCheckout() {
  if (!props.shopOpen) {
    showFailToast('门店已打烊，明天再来吧 🌙')
    return
  }
  if (cart.totalCount === 0) {
    showToast('先挑一杯喜欢的咖啡吧 ☕')
    return
  }
  panelOpen.value = false
  emit('checkout')
}

async function onPlus(row: ShoppingCartVO) {
  try {
    await cart.addByKey(row)
  } catch {
    /* 网络层已 toast */
  }
}

async function onMinus(row: ShoppingCartVO) {
  try {
    await cart.sub(row)
  } catch {
    /* ignore */
  }
}

async function onRemove(row: ShoppingCartVO) {
  try {
    await cart.removeRow(row)
    showToast('已删除')
  } catch {
    /* ignore */
  }
}

async function onClean() {
  await cart.clean()
  panelOpen.value = false
  showSuccessToast('购物车已清空')
}
</script>

<template>
  <div class="cart-bar-wrap">
    <!-- 购物车浮层 -->
    <Transition name="cart-pop">
      <div v-if="panelOpen && cart.items.length" class="cart-panel">
        <div class="panel-head">
          <p class="panel-title">购物车 <span class="panel-count">({{ cart.totalCount }})</span></p>
          <button class="clean-btn" @click="onClean">🗑 清空购物车</button>
        </div>
        <ul class="panel-list no-scrollbar">
          <li v-for="row in cart.items" :key="row.id" class="row">
            <div class="row-thumb">
              <ProductImage :src="row.image" :alt="row.drinkName" :name="row.drinkName" />
            </div>
            <div class="row-info">
              <p class="row-name">{{ row.drinkName }}</p>
              <p v-if="row.specText" class="row-spec line-clamp-1">{{ row.specText }}</p>
              <p class="row-price">¥{{ fmtPrice(row.amount) }}</p>
            </div>
            <div class="row-ops">
              <button class="op minus" aria-label="减少" @click="onMinus(row)">
                <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round">
                  <path d="M5 12h14" />
                </svg>
              </button>
              <span class="row-num">{{ row.number }}</span>
              <button class="op plus plus-pop" aria-label="增加" @click="onPlus(row)">
                <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round">
                  <path d="M12 5v14M5 12h14" />
                </svg>
              </button>
              <button class="op remove" aria-label="删除" @click="onRemove(row)">
                <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                  <path d="M3 6h18M8 6V4h8v2m-9 0v14a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2V6" />
                </svg>
              </button>
            </div>
          </li>
        </ul>
      </div>
    </Transition>

    <!-- 底部栏 -->
    <div class="cart-bar safe-bottom">
      <div class="bar-main">
        <button class="cart-btn" :class="{ active: cart.totalCount > 0 }" aria-label="查看购物车" @click="togglePanel">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="9" cy="21" r="1.6" />
            <circle cx="18" cy="21" r="1.6" />
            <path d="M2.5 3h2l2.6 12.4a2 2 0 0 0 2 1.6h7.9a2 2 0 0 0 2-1.6L20.5 7H6" />
          </svg>
          <span v-if="cart.totalCount > 0" class="badge">{{ cart.totalCount > 99 ? '99+' : cart.totalCount }}</span>
        </button>

        <div class="total">
          <p class="total-price">¥{{ fmtPrice(cart.totalPrice) }}</p>
          <p class="total-sub">免打包费 · 门店自取</p>
        </div>

        <button class="checkout-btn btn-gradient" :disabled="cart.totalCount === 0 || !props.shopOpen" @click="handleCheckout">
          {{ props.shopOpen ? '去结算' : '已打烊' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cart-bar-wrap {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 0;
  width: 100%;
  max-width: 480px;
  z-index: 88;
  pointer-events: none;
}

.cart-bar-wrap > * {
  pointer-events: auto;
}

/* 浮层 */
.cart-panel {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: calc(100% - 6px);
  max-height: 46vh;
  display: flex;
  flex-direction: column;
  background: var(--card);
  border-radius: var(--radius-card);
  box-shadow: 0 10px 32px rgba(30, 17, 8, 0.22);
  overflow: hidden;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0e8dc;
}

.panel-title {
  font-size: 13px;
  font-weight: 700;
}

.panel-count {
  font-weight: 400;
  color: var(--ink-3);
}

.clean-btn {
  font-size: 11px;
  color: var(--ink-3);
}

.panel-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
}

.row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.row + .row {
  margin-top: 12px;
}

.row-thumb {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--cream-deep);
}

.row-info {
  flex: 1;
  min-width: 0;
}

.row-name {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row-spec {
  margin-top: 1px;
  font-size: 10px;
  color: var(--ink-3);
}

.row-price {
  margin-top: 2px;
  font-size: 13px;
  font-weight: 700;
  color: var(--caramel-deep);
}

.row-ops {
  display: flex;
  align-items: center;
  gap: 8px;
}

.op {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
}

.op.minus {
  border: 1px solid #ddd0bf;
  color: var(--ink-2);
}

.op.plus {
  background: linear-gradient(135deg, var(--amber-from), var(--amber-to));
  color: #fff;
}

.op.remove {
  margin-left: 4px;
  color: #c9b8a5;
}

.op.remove:active {
  color: var(--danger);
}

.row-num {
  min-width: 16px;
  text-align: center;
  font-size: 13px;
  font-weight: 700;
}

/* 底栏 */
.cart-bar {
  padding-left: 12px;
  padding-right: 12px;
}

.bar-main {
  display: flex;
  align-items: center;
  height: 56px;
  padding: 0 6px 0 7px;
  border-radius: 999px;
  background: #2b1a10;
  box-shadow: 0 10px 26px rgba(30, 17, 8, 0.35);
}

.cart-btn {
  position: relative;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  color: rgba(255, 255, 255, 0.45);
  background: rgba(255, 255, 255, 0.08);
  transition: transform 0.15s ease;
}

.cart-btn:active {
  transform: scale(0.94);
}

.cart-btn.active {
  color: #fff;
  background: linear-gradient(135deg, var(--amber-from), var(--amber-to));
  box-shadow: 0 4px 14px rgba(192, 133, 82, 0.5);
}

.badge {
  position: absolute;
  top: -4px;
  right: -6px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: grid;
  place-items: center;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  border-radius: 999px;
  background: #f43f3f;
  box-shadow: 0 0 0 2px #2b1a10;
}

.total {
  flex: 1;
  min-width: 0;
  margin-left: 10px;
}

.total-price {
  font-size: 16px;
  font-weight: 800;
  color: #fff;
  line-height: 1.15;
}

.total-sub {
  font-size: 9px;
  color: rgba(255, 255, 255, 0.45);
  line-height: 1.2;
}

.checkout-btn {
  flex-shrink: 0;
  height: 40px;
  padding: 0 20px;
  font-size: 13px;
  border-radius: 999px;
}
</style>
