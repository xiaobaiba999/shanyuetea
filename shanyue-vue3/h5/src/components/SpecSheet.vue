<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { showSuccessToast } from 'vant'
import type { DrinkVO, SpecPair } from '@/types'
import { useCartStore } from '@/stores/cart'
import { fmtPrice } from '@/utils/format'
import ProductImage from './ProductImage.vue'

/**
 * 规格弹窗（Vant Popup bottom）
 * - 动态渲染规格组：组名 + 必选星标；required=0 的组额外提供「无需」chip（虚拟选项 id=0）
 * - 默认每组选中第一项；价格 = 基础价 + Σ priceDelta，实时计算
 */
const props = defineProps<{ product: DrinkVO | null; show: boolean }>()
const emit = defineEmits<{ (e: 'update:show', v: boolean): void }>()

const cart = useCartStore()
const selected = ref<Record<number, number>>({})
const qty = ref(1)

// 切换商品时重置选择状态（同原型 key 重置思路）
watch(
  () => props.product,
  (p) => {
    if (!p) return
    const init: Record<number, number> = {}
    ;[...p.specGroups]
      .sort((a, b) => a.sort - b.sort)
      .forEach((g) => {
        if (g.specOptions[0]) init[g.id] = g.specOptions[0].id
      })
    selected.value = init
    qty.value = 1
  },
  { immediate: true },
)

const sortedGroups = computed(() =>
  [...(props.product?.specGroups ?? [])].sort((a, b) => a.sort - b.sort),
)

/** 选中规格 -> 提交形态（「无需」不提交） */
const chosenPairs = computed<SpecPair[]>(() => {
  const pairs: SpecPair[] = []
  sortedGroups.value.forEach((g) => {
    const oid = selected.value[g.id]
    if (!oid) return
    const opt = g.specOptions.find((o) => o.id === oid)
    if (opt) pairs.push({ groupName: g.name, optionName: opt.name })
  })
  return pairs
})

/** 单价 = 基础价 + Σ priceDelta */
const unitPrice = computed(() => {
  const p = props.product
  if (!p) return 0
  let delta = 0
  p.specGroups.forEach((g) => {
    const opt = g.specOptions.find((o) => o.id === selected.value[g.id])
    if (opt) delta += opt.priceDelta
  })
  return p.price + delta
})

async function handleAdd() {
  const p = props.product
  if (!p) return
  await cart.add(p.id, chosenPairs.value)
  showSuccessToast('已加入购物车')
  emit('update:show', false)
}

function close() {
  emit('update:show', false)
}
</script>

<template>
  <van-popup :show="show" position="bottom" class="spec-popup" close-on-click-overlay :style="{ maxHeight: '82%' }" @update:show="close">
    <div v-if="product" class="spec-sheet">
      <!-- 抓手条 -->
      <span class="sheet-handle" aria-hidden="true" />
      <!-- 顶部商品信息 -->
      <div class="sheet-head">
        <div class="thumb">
          <ProductImage :src="product.image" :alt="product.name" :name="product.name" :food="product.categoryId === 5" />
        </div>
        <div class="head-info">
          <h3>{{ product.name }}</h3>
          <p class="head-desc line-clamp-2">{{ product.description }}</p>
          <p class="head-price">
            <span class="yen">¥</span>
            <span class="num price-num">{{ fmtPrice(unitPrice) }}</span>
          </p>
        </div>
        <button class="close-btn" aria-label="关闭" @click="close">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </div>

      <!-- 规格组 -->
      <div class="sheet-body no-scrollbar">
        <div v-for="g in sortedGroups" :key="g.id" class="group">
          <p class="group-name">
            {{ g.name }}
            <span v-if="g.required === 1" class="required-star">*</span>
            <span v-else class="optional-hint">（可选）</span>
          </p>
          <div class="chips">
            <button
              v-for="o in g.specOptions"
              :key="o.id"
              class="chip"
              :class="{ active: selected[g.id] === o.id }"
              @click="selected[g.id] = o.id"
            >
              {{ o.name }}
              <span v-if="o.priceDelta > 0" class="chip-delta">+¥{{ fmtPrice(o.priceDelta) }}</span>
            </button>
            <button v-if="g.required === 0" class="chip" :class="{ active: !selected[g.id] }" @click="selected[g.id] = 0">
              无需
            </button>
          </div>
        </div>
      </div>

      <!-- 底部数量 + 加购 -->
      <div class="sheet-footer safe-bottom">
        <div class="qty-block">
          <span class="qty-label">数量</span>
          <van-stepper v-model="qty" :min="1" :max="99" integer button-size="26" />
        </div>
        <button class="confirm-btn btn-primary" @click="handleAdd">
          选好了 ¥{{ fmtPrice(unitPrice * qty) }}
        </button>
      </div>
    </div>
  </van-popup>
</template>

<style scoped>
/* 弹层圆角 18px（去掉 Vant round 默认 16px，统一新中式大圆角） */
.spec-popup {
  border-radius: 18px 18px 0 0;
}

/* 顶部抓手条 */
.sheet-handle {
  display: block;
  width: 36px;
  height: 4px;
  margin: 8px auto 4px;
  border-radius: 999px;
  background: var(--paper-deep);
}

.spec-sheet {
  display: flex;
  flex-direction: column;
  padding: 16px 16px 12px;
}

.sheet-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.thumb {
  flex-shrink: 0;
  width: 64px;
  height: 64px;
  border-radius: 12px;
  overflow: hidden;
  background: var(--paper-deep);
}

.head-info {
  flex: 1;
  min-width: 0;
}

.head-info h3 {
  font-size: 16px;
  font-weight: 700;
}

.head-desc {
  margin-top: 2px;
  font-size: 11px;
  color: var(--ink-3);
}

.head-price {
  margin-top: 6px;
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.head-price .yen {
  font-size: 11px;
  font-weight: 700;
  color: var(--terracotta);
}

.head-price .num {
  font-size: 20px;
  line-height: 1;
}

.close-btn {
  flex-shrink: 0;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--paper-deep);
  color: var(--ink-2);
}

.sheet-body {
  flex: 1;
  overflow-y: auto;
  max-height: 46vh;
  margin-top: 14px;
  padding-bottom: 4px;
}

.group + .group {
  margin-top: 16px;
}

.group-name {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-2);
}

.required-star {
  margin-left: 2px;
  color: var(--danger);
}

.optional-hint {
  font-weight: 400;
  font-size: 10px;
  color: var(--ink-3);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  padding: 7px 14px;
  font-size: 12px;
  color: var(--ink-2);
  border-radius: 999px;
  border: 1px solid var(--card-line);
  background: var(--paper);
  transition: all 0.15s ease;
}

.chip:active {
  transform: scale(0.95);
}

/* 选中项：墨绿实底胶囊 */
.chip.active {
  border-color: transparent;
  color: #fff;
  font-weight: 600;
  background: linear-gradient(160deg, #3a6140, var(--pine) 55%, var(--pine-deep));
  box-shadow: 0 3px 10px rgba(47, 82, 51, 0.28);
}

.chip-delta {
  font-size: 10px;
}

.chip:not(.active) .chip-delta {
  color: var(--terracotta);
}

.sheet-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--card-line);
}

.qty-block {
  display: flex;
  align-items: center;
  gap: 10px;
}

.qty-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--ink-2);
}

/* 底部按钮：全局 .btn-primary 光泽款（墨绿渐变+扫光） */
.confirm-btn {
  flex: 1;
  max-width: 220px;
  height: 44px;
  padding: 0 18px;
  font-size: 14px;
  border-radius: 999px;
}
</style>
