<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import type { OrderStatus, OrderVO, ReviewVO } from '@/types'
import { getOrderByPickUpCode, reminderOrder, cancelOrder, advanceOrderDemo } from '@/api/order'
import { createReview, getReviewByOrderId, REVIEW_TAG_POOL } from '@/api/review'
import { getShopStatus, setShopStatusDemo } from '@/api/shop'
import { USE_MOCK } from '@/utils/env'
import { fmtPrice } from '@/utils/format'
import { dbClearAll } from '@/mock/db'
import { mockResetOrders } from '@/mock/order'
import { mockResetCoupons } from '@/mock/coupon'
import { mockResetReviews } from '@/mock/review'
import { resolveSpecPairs } from '@/utils/specs'
import { useCartStore } from '@/stores/cart'
import ProductImage from '@/components/ProductImage.vue'

/**
 * 订单查询页：取餐号查询 + 状态步骤条 + 催单/取消 + 最近查询记录 + 演示工具
 */
const route = useRoute()
const router = useRouter()
const cart = useCartStore()

const RECENT_KEY = 'shanyue-h5-recent-codes'
const STEPS = ['提交订单', '商家接单', '制作中', '待取餐', '完成']
const STATUS_TEXT: Record<OrderStatus, string> = {
  1: '待接单',
  2: '制作中',
  3: '待取餐',
  4: '已完成',
  5: '已取消',
}
const STATUS_CLASS: Record<OrderStatus, string> = {
  '1': 'st-1',
  '2': 'st-2',
  '3': 'st-3',
  '4': 'st-4',
  '5': 'st-5',
}

const code = ref('')
const order = ref<OrderVO | null>(null)
const searched = ref(false)
const querying = ref(false)
const recentCodes = ref<string[]>(loadRecent())
const shopStatus = ref<0 | 1>(1)

const canRemind = computed(() => order.value && (order.value.status === 1 || order.value.status === 2))
const canCancel = computed(() => order.value && order.value.status === 1)

/* ---------------- 评价（模块 16）：仅已完成订单可评，一单一评 ---------------- */
const reviewedReview = ref<ReviewVO | null>(null)
const showReviewSheet = ref(false)
const reviewScore = ref(5)
const reviewTags = ref<string[]>([])
const reviewContent = ref('')
const submittingReview = ref(false)

/** 星级感性文案（评价弹层标题旁） */
const SCORE_TEXT: Record<number, string> = {
  1: '很不搭',
  2: '一般般',
  3: '还不错的',
  4: '挺满意的',
  5: '太棒啦！',
}

/** 查询/推进到已完成态时拉取评价状态，控制「评价」按钮 vs 「已评价」徽标 */
async function refreshReviewState(orderId: number) {
  try {
    reviewedReview.value = await getReviewByOrderId(orderId)
  } catch {
    reviewedReview.value = null
  }
}

function openReviewSheet() {
  reviewScore.value = 5
  reviewTags.value = []
  reviewContent.value = ''
  showReviewSheet.value = true
}

function toggleReviewTag(tag: string) {
  const i = reviewTags.value.indexOf(tag)
  if (i >= 0) reviewTags.value.splice(i, 1)
  else reviewTags.value.push(tag)
}

async function submitReview() {
  if (!order.value || submittingReview.value) return
  if (!reviewScore.value) {
    showFailToast('先点几颗星吧')
    return
  }
  submittingReview.value = true
  try {
    reviewedReview.value = await createReview({
      orderId: order.value.id,
      score: reviewScore.value,
      tags: [...reviewTags.value],
      content: reviewContent.value.trim(),
    })
    showReviewSheet.value = false
    showSuccessToast('评价成功，感谢反馈 💛')
  } catch (e) {
    showFailToast(e instanceof Error ? e.message : '评价失败')
  } finally {
    submittingReview.value = false
  }
}
const stepActive = computed(() => {
  const s = order.value?.status ?? 1
  return s >= 4 ? 4 : s
})

onMounted(async () => {
  const qc = route.query.code
  if (qc) {
    code.value = String(qc).toUpperCase()
    void query()
  }
  shopStatus.value = (await getShopStatus().catch(() => null))?.status ?? 1
})

function loadRecent(): string[] {
  try {
    const raw = localStorage.getItem(RECENT_KEY)
    return raw ? (JSON.parse(raw) as string[]) : []
  } catch {
    return []
  }
}

function saveRecent(c: string) {
  const next = [c, ...recentCodes.value.filter((x) => x !== c)].slice(0, 5)
  recentCodes.value = next
  try {
    localStorage.setItem(RECENT_KEY, JSON.stringify(next))
  } catch {
    /* ignore */
  }
}

async function query(c?: string) {
  const target = (c ?? code.value).trim().toUpperCase()
  if (!target) {
    showFailToast('请输入取餐号，如 A01')
    return
  }
  code.value = target
  querying.value = true
  try {
    order.value = await getOrderByPickUpCode(target)
    saveRecent(target)
    if (order.value.status === 4) void refreshReviewState(order.value.id)
    else reviewedReview.value = null
  } catch {
    order.value = null
  } finally {
    searched.value = true
    querying.value = false
  }
}

/** 催单（仅 1/2 状态可催） */
async function handleRemind() {
  if (!order.value) return
  try {
    await reminderOrder(order.value.id)
    showSuccessToast('已收到催单')
  } catch {
    /* 网络层已 toast */
  }
}

/** 取消订单（仅状态 1 可取消） */
function handleCancel() {
  if (!order.value) return
  showConfirmDialog({
    title: '取消订单',
    message: '确认取消当前订单吗？',
  })
    .then(async () => {
      await cancelOrder(order.value!.id)
      order.value = { ...order.value!, status: 5, cancelReason: '用户主动取消' }
      showSuccessToast('订单已取消')
    })
    .catch(() => {})
}

/** 演示推进（仅 mock）：模拟商家侧沿状态机推进一步 1→2→3→4 */
const ADVANCE_TIP: Record<number, string> = {
  2: '商家已接单 · 制作中 ☕',
  3: '制作完成 · 请叫号取餐 📣',
  4: '顾客已取餐 · 订单完成 ✅',
}

async function handleAdvance() {
  if (!order.value) return
  try {
    const next = await advanceOrderDemo(order.value.id)
    order.value = next
    showSuccessToast(ADVANCE_TIP[next.status] ?? '状态已推进')
    if (next.status === 4) void refreshReviewState(next.id)
  } catch {
    /* 网络层已 toast */
  }
}

/* ---------------- 再来一单：按原规格回购（已完成/已取消订单可用） ----------------
 * 逐品项用 specKey 反查规格后复用 addToCart 接口（服务端重算金额、合并同规格行）；
 * 部分品项已停售时跳过并提示，不让整单失败。 */
const reordering = ref(false)
const canReorder = computed(() => order.value?.status === 4 || order.value?.status === 5)

async function handleReorder() {
  const o = order.value
  if (!o || reordering.value) return
  reordering.value = true
  let added = 0
  const failedNames = new Set<string>()
  try {
    for (const it of o.orderItems) {
      let itemOk = true
      for (let n = 0; n < it.number; n++) {
        try {
          const specs = await resolveSpecPairs(it.drinkId, it.specKey)
          await cart.add(it.drinkId, specs)
          added += 1
        } catch {
          itemOk = false
          break
        }
      }
      if (!itemOk) failedNames.add(it.drinkName)
    }
  } finally {
    reordering.value = false
  }
  if (added > 0 && failedNames.size === 0) {
    showSuccessToast('已按原规格加入购物车')
    router.push('/')
  } else if (added > 0) {
    showSuccessToast(`已加入 ${added} 件，「${[...failedNames].join('、')}」已停售未能加入`)
    router.push('/')
  } else {
    showFailToast('商品已停售，去菜单看看新品吧')
  }
}

/** 演示工具：重置 mock 数据 */
function resetDemo() {
  showConfirmDialog({
    title: '重置演示数据',
    message: '将清空本地演示数据（购物车/订单/查询记录/营业状态/优惠券/积分/评价）并重新播种，确定吗？',
  })
    .then(() => {
      dbClearAll()
      mockResetOrders()
      mockResetCoupons()
      mockResetReviews()
      try {
        localStorage.removeItem('shanyue-h5-cart-cache')
        localStorage.removeItem(RECENT_KEY)
      } catch {
        /* ignore */
      }
      window.location.reload()
    })
    .catch(() => {})
}

/** 演示工具：模拟打烊 / 恢复营业（仅 mock 模式） */
async function toggleShop() {
  const next: 0 | 1 = shopStatus.value === 1 ? 0 : 1
  try {
    await setShopStatusDemo(next)
    shopStatus.value = next
    showSuccessToast(next === 1 ? '已恢复营业（回首页看效果）' : '已切换为打烊（回首页看效果）')
  } catch {
    /* ignore */
  }
}
</script>

<template>
  <div class="query-page">
    <van-nav-bar title="订单查询" left-arrow safe-area-inset-top @click-left="$router.back()" />

    <div class="page-body">
      <!-- 查询框 -->
      <div class="search-card">
        <van-field
          v-model="code"
          class="code-field"
          placeholder="输入取餐号，如 A01"
          maxlength="6"
          clearable
          :formatter="(v: string) => v.toUpperCase()"
        />
        <van-button class="query-btn" type="primary" round :loading="querying" loading-text="查询中…" @click="query()">
          查询
        </van-button>
      </div>

      <!-- 最近查询 -->
      <div v-if="recentCodes.length" class="recent">
        <span class="recent-label">最近查询：</span>
        <button v-for="c in recentCodes" :key="c" class="recent-chip" @click="query(c)">{{ c }}</button>
      </div>

      <!-- 查询结果 -->
      <template v-if="order">
        <div class="card order-card">
          <div class="order-head">
            <div>
              <p class="pickup-label">取餐号</p>
              <p class="pickup-code">{{ order.pickUpCode }}</p>
            </div>
            <van-tag v-if="order.status === 5" type="danger" size="large">已取消</van-tag>
            <span v-else class="status-tag" :class="STATUS_CLASS[order.status]">{{ STATUS_TEXT[order.status] }}</span>
          </div>

          <!-- 状态步骤条 -->
          <van-steps v-if="order.status !== 5" class="steps" :active="stepActive" active-color="#C08552">
            <van-step v-for="s in STEPS" :key="s">{{ s }}</van-step>
          </van-steps>
          <p v-else class="cancel-reason">取消原因：{{ order.cancelReason || '-' }}</p>

          <div class="meta">
            <p class="meta-row"><span>订单号</span><b>{{ order.orderNumber }}</b></p>
            <p class="meta-row"><span>下单时间</span><b>{{ order.orderTime }}</b></p>
            <p v-if="order.status !== 5" class="meta-row">
              <span>预计等待</span><b>{{ order.estimatedWaitMinutes }} 分钟</b>
            </p>
            <p v-if="order.remark" class="meta-row"><span>备注</span><b>{{ order.remark }}</b></p>
          </div>

          <!-- 商品明细 -->
          <ul class="items">
            <li v-for="it in order.orderItems" :key="it.id" class="item">
              <div class="thumb">
                <ProductImage :src="it.image" :alt="it.drinkName" />
              </div>
              <div class="info">
                <p class="name">{{ it.drinkName }} × {{ it.number }}</p>
                <p v-if="it.specText" class="spec line-clamp-1">{{ it.specText }}</p>
              </div>
              <p class="amount">¥{{ fmtPrice(it.amount) }}</p>
            </li>
          </ul>

          <div v-if="order.discountAmount > 0" class="discount-row">
            <span>🎫 券抵扣</span>
            <b>-¥{{ fmtPrice(order.discountAmount) }}</b>
          </div>

          <div class="total-row">
            <span>合计</span>
            <b>¥{{ fmtPrice(order.orderAmount) }}</b>
          </div>

          <!-- 操作 -->
          <div class="order-actions">
            <button v-if="canRemind" class="action-ghost" @click="handleRemind">🔔 催单</button>
            <button v-if="canCancel" class="action-danger" @click="handleCancel">取消订单</button>
            <button
              v-if="order.status === 4 && !reviewedReview"
              class="action-review"
              @click="openReviewSheet"
            >
              ⭐ 评价
            </button>
            <button v-if="canReorder" class="action-reorder" :disabled="reordering" @click="handleReorder">
              {{ reordering ? '加入中…' : '🛒 再来一单' }}
            </button>
            <span v-if="order.status === 4 && reviewedReview" class="reviewed-flag" role="status">
              已评价 ★ {{ reviewedReview.score }}.0
            </span>
            <button
              v-if="USE_MOCK && order.status >= 1 && order.status <= 3"
              class="action-ghost action-advance"
              @click="handleAdvance"
            >
              ⏩ 演示推进（模拟商家{{ order.status === 1 ? '接单' : order.status === 2 ? '出餐' : '取餐' }}）
            </button>
          </div>
        </div>
      </template>

      <van-empty
        v-else-if="searched && !querying"
        image="search"
        description="未查询到订单，请核对取餐号"
      />
      <van-empty v-else-if="!searched" description="输入取餐号查询订单状态" />

      <!-- 演示工具 -->
      <div v-if="USE_MOCK" class="demo-tools">
        <span class="demo-label">演示工具（mock）</span>
        <div class="demo-btns">
          <button v-if="shopStatus === 1" class="demo-btn" @click="toggleShop">🌑 模拟打烊</button>
          <button v-else class="demo-btn" @click="toggleShop">☀️ 恢复营业</button>
          <button class="demo-btn" @click="resetDemo">♻️ 重置演示数据</button>
        </div>
        <p class="demo-hint">演示取餐号：A01（待接单）/ A02（制作中）/ A03（待取餐）</p>
      </div>
    </div>

    <!-- 评价弹层（模块 16：星级 + 标签 + 文字，一单一评） -->
    <van-popup v-model:show="showReviewSheet" position="bottom" round class="review-popup" safe-area-inset-bottom>
      <div class="rp-body">
        <div class="rp-head">
          <h3 class="rp-title">评价这单体验</h3>
          <button class="rp-close" aria-label="关闭评价" @click="showReviewSheet = false">✕</button>
        </div>
        <p class="rp-order">取餐号 {{ order?.pickUpCode }} · {{ order?.orderItems.map((i) => i.drinkName).join('、') }}</p>

        <!-- 星级 -->
        <div class="rp-score">
          <div class="rp-stars" role="radiogroup" aria-label="评分（1~5 星）">
            <button
              v-for="s in 5"
              :key="s"
              class="rp-star"
              :class="{ on: s <= reviewScore }"
              role="radio"
              :aria-checked="s === reviewScore"
              :aria-label="`${s} 星`"
              @click="reviewScore = s"
            >
              ★
            </button>
          </div>
          <span class="rp-score-text">{{ SCORE_TEXT[reviewScore] }}</span>
        </div>

        <!-- 标签快选 -->
        <p class="rp-label">挑几个印象（可多选）</p>
        <div class="rp-tags">
          <button
            v-for="t in REVIEW_TAG_POOL"
            :key="t"
            class="rp-tag"
            :class="{ on: reviewTags.includes(t) }"
            :aria-pressed="reviewTags.includes(t)"
            @click="toggleReviewTag(t)"
          >
            {{ t }}
          </button>
        </div>

        <!-- 文字评价 -->
        <div class="rp-input">
          <textarea
            v-model="reviewContent"
            class="rp-textarea"
            rows="3"
            maxlength="50"
            placeholder="这一杯有什么想对山月说的？（选填，最多 50 字）"
          />
          <span class="rp-count" :class="{ warn: reviewContent.length >= 50 }">{{ reviewContent.length }}/50</span>
        </div>

        <button class="rp-submit" :disabled="submittingReview" @click="submitReview">
          {{ submittingReview ? '提交中…' : '发布评价' }}
        </button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.query-page {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
  background: var(--cream);
}

.page-body {
  flex: 1;
  padding: 12px 12px 24px;
}

/* 查询框 */
.search-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px 8px 4px;
  background: var(--card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.code-field {
  flex: 1;
  background: transparent;
}

.code-field :deep(.van-field__control) {
  font-weight: 700;
  letter-spacing: 2px;
}

.query-btn {
  flex-shrink: 0;
  width: 84px;
  height: 36px;
  font-weight: 700;
}

/* 最近查询 */
.recent {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding: 0 4px;
}

.recent-label {
  font-size: 11px;
  color: var(--ink-3);
}

.recent-chip {
  padding: 4px 12px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--caramel-deep);
  border-radius: 999px;
  border: 1px solid #ecdcc6;
  background: #fdf9f3;
}

/* 订单卡 */
.order-card {
  margin-top: 12px;
  padding: 16px;
  background: var(--card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.order-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.pickup-label {
  font-size: 11px;
  letter-spacing: 2px;
  color: var(--ink-3);
}

.pickup-code {
  margin-top: 2px;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 4px;
  color: var(--caramel-deep);
}

.status-tag {
  padding: 4px 10px;
  font-size: 11px;
  font-weight: 700;
  border-radius: 999px;
}

.st-1 {
  color: #a96c3c;
  background: #f9ecdd;
}

.st-2 {
  color: #8b5e3c;
  background: #f3e7db;
}

.st-3 {
  color: #3f7d54;
  background: #e6f2e9;
}

.st-4 {
  color: #6f7d6f;
  background: #eef0ec;
}

.cancel-reason {
  margin-top: 10px;
  font-size: 12px;
  color: var(--danger);
}

.steps {
  margin-top: 12px;
}

.steps :deep(.van-step__title) {
  font-size: 10px;
}

.meta {
  margin-top: 12px;
  padding: 4px 0;
  border-top: 1px dashed #f0e8dc;
}

.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 0;
  font-size: 12px;
  color: var(--ink-3);
}

.meta-row b {
  font-weight: 600;
  color: var(--ink);
  max-width: 70%;
  text-align: right;
}

.items {
  margin-top: 8px;
  border-top: 1px dashed #f0e8dc;
  padding-top: 10px;
}

.item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.item + .item {
  margin-top: 10px;
}

.thumb {
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--cream-deep);
}

.info {
  flex: 1;
  min-width: 0;
}

.name {
  font-size: 13px;
  font-weight: 600;
}

.spec {
  margin-top: 1px;
  font-size: 10px;
  color: var(--ink-3);
}

.amount {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--caramel-deep);
}

.total-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #f0e8dc;
  font-size: 13px;
  color: var(--ink-2);
}

.total-row b {
  font-size: 16px;
  font-weight: 800;
  color: var(--caramel-deep);
}

.discount-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #f0e8dc;
  font-size: 12px;
  color: #d4552e;
}

.discount-row b {
  font-weight: 800;
}

.order-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.action-ghost,
.action-danger,
.action-reorder {
  height: 32px;
  padding: 0 16px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 999px;
  transition: transform 0.15s ease;
}

/* 评价：金星描边样式，区别于普通幽灵按钮 */
.action-review {
  height: 32px;
  padding: 0 16px;
  font-size: 12px;
  font-weight: 700;
  border-radius: 999px;
  color: #a05e2b;
  border: 1px solid #ecc98f;
  background: linear-gradient(135deg, #fdf3e0, #fbeecb);
  transition: transform 0.15s ease;
}

.action-review:active {
  transform: scale(0.96);
}

/* 已评价徽标：不可再点的静态态 */
.reviewed-flag {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 14px;
  font-size: 12px;
  font-weight: 700;
  color: #8a7f6f;
  border-radius: 999px;
  background: #f4f0e9;
  border: 1px dashed #ddd3c2;
}

.action-ghost {
  color: var(--caramel-deep);
  border: 1px solid #ecdcc6;
  background: #fdf9f3;
}

.action-danger {
  color: #fff;
  background: var(--danger);
}

/* 再来一单：主操作级琥珀实底 */
.action-reorder {
  color: #fff;
  background: linear-gradient(135deg, var(--amber-from), var(--amber-to));
  box-shadow: 0 3px 10px rgba(217, 130, 43, 0.3);
}

.action-reorder:disabled {
  opacity: 0.6;
}

.action-ghost:active,
.action-danger:active,
.action-reorder:active {
  transform: scale(0.96);
}

/* 演示推进：琥珀描边 + 呼吸脉冲，明确区别于真实操作 */
.action-advance {
  flex-basis: 100%;
  height: 36px;
  color: #a05e2b;
  border: 1px dashed #d8a86f;
  background: linear-gradient(135deg, #fdf6ec, #faeeda);
  animation: advance-pulse 2.2s ease-in-out infinite;
}

.action-advance:active {
  animation: none;
  transform: scale(0.97);
}

@keyframes advance-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(192, 133, 82, 0.28);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(192, 133, 82, 0.06);
  }
}

/* 演示工具 */
.demo-tools {
  margin-top: 20px;
  padding: 14px 16px;
  border-radius: var(--radius-card);
  border: 1px dashed #e3d3bc;
  background: rgba(255, 255, 255, 0.6);
}

.demo-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--ink-3);
}

.demo-btns {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}

.demo-btn {
  padding: 7px 12px;
  font-size: 11px;
  color: var(--ink-2);
  border-radius: 8px;
  border: 1px solid #e3d7c6;
  background: var(--card);
}

.demo-hint {
  margin-top: 10px;
  font-size: 10px;
  color: var(--ink-3);
}

/* ---------- 评价弹层 ---------- */
.rp-body {
  padding: 18px 18px 14px;
}

.rp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rp-title {
  font-size: 16px;
  font-weight: 800;
  color: var(--ink);
}

.rp-close {
  width: 28px;
  height: 28px;
  font-size: 13px;
  color: var(--ink-3);
  border-radius: 50%;
  background: var(--cream-deep);
}

.rp-order {
  margin-top: 6px;
  font-size: 11px;
  color: var(--ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rp-score {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
}

.rp-stars {
  display: inline-flex;
  gap: 6px;
}

.rp-star {
  font-size: 30px;
  line-height: 1;
  color: #e5ddcd;
  transition: transform 0.12s ease, color 0.12s ease;
}

.rp-star.on {
  color: #e9a23b;
  text-shadow: 0 2px 6px rgba(233, 162, 59, 0.35);
}

.rp-star:active {
  transform: scale(1.2);
}

.rp-score-text {
  font-size: 12px;
  font-weight: 700;
  color: #c07f36;
}

.rp-label {
  margin-top: 16px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-2);
}

.rp-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.rp-tag {
  padding: 6px 14px;
  font-size: 12px;
  color: var(--ink-2);
  border-radius: 999px;
  border: 1px solid #e8ddcb;
  background: var(--card);
  transition: all 0.15s ease;
}

.rp-tag.on {
  color: #fff;
  font-weight: 600;
  border-color: transparent;
  background: linear-gradient(135deg, var(--amber-from), var(--amber-to));
  box-shadow: 0 2px 8px rgba(217, 130, 43, 0.28);
}

.rp-input {
  position: relative;
  margin-top: 14px;
}

.rp-textarea {
  width: 100%;
  padding: 10px 12px 22px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--ink);
  border-radius: 12px;
  border: 1px solid #eee3d2;
  background: var(--cream-deep);
  resize: none;
  outline: none;
}

.rp-textarea:focus {
  border-color: #d8a86f;
}

.rp-count {
  position: absolute;
  right: 12px;
  bottom: 8px;
  font-size: 10px;
  color: var(--ink-3);
}

.rp-count.warn {
  color: var(--danger);
}

.rp-submit {
  width: 100%;
  height: 42px;
  margin-top: 14px;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--amber-from), var(--amber-to));
  box-shadow: 0 4px 14px rgba(217, 130, 43, 0.32);
}

.rp-submit:disabled {
  opacity: 0.6;
}
</style>
