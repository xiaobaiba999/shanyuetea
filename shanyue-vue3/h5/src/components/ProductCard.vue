<script setup lang="ts">
import { showSuccessToast } from 'vant'
import type { DrinkVO } from '@/types'
import { useCartStore } from '@/stores/cart'
import { fmtPrice } from '@/utils/format'
import ProductImage from './ProductImage.vue'

/**
 * 商品卡片：无规格「+」直接加购（弹性动画）；有规格打开规格面板
 */
const props = defineProps<{ drink: DrinkVO }>()
const emit = defineEmits<{ (e: 'spec', drink: DrinkVO): void }>()

const cart = useCartStore()
const hasSpecs = props.drink.specGroups.length > 0

async function handleAdd() {
  if (hasSpecs) {
    emit('spec', props.drink)
    return
  }
  await cart.add(props.drink.id, [])
  showSuccessToast(`已加入购物车：${props.drink.name}`)
}
</script>

<template>
  <div class="product-card" :class="{ sold: drink.status === 0 }">
    <div class="thumb">
      <ProductImage :src="drink.image" :alt="drink.name" :name="drink.name" :food="drink.categoryId === 5" />
      <span v-if="drink.featured === 1" class="featured-tag">招牌</span>
      <span v-if="drink.status === 0" class="sold-mark">今日售罄</span>
    </div>

    <div class="info">
      <h3 class="name">{{ drink.name }}</h3>
      <p class="desc line-clamp-1">{{ drink.description }}</p>
      <p class="meta">月售 {{ drink.sold }}</p>

      <div class="bottom">
        <div class="price">
          <span class="yen">¥</span>
          <span class="num price-num">{{ fmtPrice(drink.price) }}</span>
          <del v-if="drink.originPrice && drink.originPrice > drink.price" class="origin">
            ¥{{ fmtPrice(drink.originPrice) }}
          </del>
        </div>

        <button v-if="hasSpecs" class="spec-btn btn-primary" @click="handleAdd">选规格</button>
        <button v-else class="add-btn plus-pop" aria-label="加入购物车" @click="handleAdd">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round">
            <path d="M12 5v14M5 12h14" />
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.product-card {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: var(--card);
  border: 1px solid var(--card-line);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

@media (hover: hover) {
  .product-card:hover {
    transform: translateY(-2px);
    border-color: #d9cdb2;
    box-shadow: 0 1px 2px rgba(43, 38, 32, 0.05), 0 10px 26px rgba(43, 38, 32, 0.1);
  }
}

.product-card:active {
  transform: translateY(-2px);
  border-color: #d9cdb2;
  box-shadow: 0 1px 2px rgba(43, 38, 32, 0.05), 0 10px 26px rgba(43, 38, 32, 0.1);
}

/* 售罄态：茶金虚线描边 */
.product-card.sold {
  border: 1px dashed var(--gold);
}

.thumb {
  position: relative;
  flex-shrink: 0;
  width: 80px;
  height: 80px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--paper-deep);
}

.featured-tag {
  position: absolute;
  left: 0;
  top: 0;
  padding: 2px 8px;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--pine);
  border-radius: 0 10px 0 10px;
  background: rgba(232, 240, 233, 0.94);
  border-right: 1px solid rgba(47, 82, 51, 0.18);
  border-bottom: 1px solid rgba(47, 82, 51, 0.18);
}

/* 售罄角标（宣纸底 + 衬线小字） */
.sold-mark {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-family: var(--font-serif);
  font-size: 11px;
  letter-spacing: 3px;
  color: var(--terracotta);
  background: rgba(246, 243, 236, 0.82);
  pointer-events: none;
}

.info {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.name {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.35;
}

.desc {
  margin-top: 2px;
  font-size: 11px;
  color: var(--ink-3);
}

.meta {
  margin-top: 4px;
  font-size: 10px;
  color: var(--ink-3);
}

.bottom {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 6px;
}

.price {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.yen {
  font-size: 11px;
  font-weight: 700;
  color: var(--terracotta);
}

.num {
  font-size: 18px;
  line-height: 1;
}

.origin {
  margin-left: 4px;
  font-size: 10px;
  color: var(--ink-3);
}

.spec-btn {
  height: 30px;
  padding: 0 14px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1px;
  border-radius: 999px;
  box-shadow: 0 2px 8px rgba(47, 82, 51, 0.24);
}

/* 「+」加购：墨绿小圆钮（按压回弹由全局 .plus-pop 提供） */
.add-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(160deg, #3a6140, var(--pine) 55%, var(--pine-deep));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.12),
    0 3px 10px rgba(47, 82, 51, 0.3);
}
</style>
