<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { ShopVO } from '@/types'
import { getCouponTemplates, getMyCoupons } from '@/api/coupon'
import { getShopReviewStats } from '@/api/review'

/**
 * 门店横幅：banner 背景 + 品牌信息 + 营业状态灯点 + 公告跑马灯
 * 公告栏同时作为运营位：券广场有可领模板时，点击直达会员中心领券
 * 品牌行可点击进入门店信息页（评分/品牌故事/评价墙）
 */
defineProps<{ shop: ShopVO }>()

const router = useRouter()
const bannerError = ref(false)
/** 券广场可领张数（未被自己领过 + 库存 > 0 + 发放中） */
const claimable = ref(0)
/** 门店评分（16.3 统计；无评价时不展示评分位） */
const avgScore = ref(0)
const reviewTotal = ref(0)

onMounted(async () => {
  try {
    const [templates, my] = await Promise.all([getCouponTemplates(), getMyCoupons()])
    const claimed = new Set(my.map((c) => c.couponId))
    claimable.value = templates.filter((t) => t.remainCount > 0 && !claimed.has(t.id)).length
  } catch {
    /* 静默失败，公告栏仍正常展示 */
  }
  try {
    const stats = await getShopReviewStats()
    avgScore.value = stats.avgScore
    reviewTotal.value = stats.total
  } catch {
    /* 评分位静默隐藏 */
  }
})

function onNoticeClick() {
  if (claimable.value > 0) router.push('/member')
}
</script>

<template>
  <header class="store-header">
    <!-- banner 背景（失败时露出底下的深咖渐变） -->
    <img v-if="!bannerError" class="banner" src="/menu/banner.png" alt="山月茶咖门店" @error="bannerError = true" />
    <div class="banner-overlay" />

    <div class="header-body">
      <!-- 品牌行（点击进入门店信息页） -->
      <div class="brand-row" role="button" tabindex="0" aria-label="查看门店信息" @click="router.push('/shop-info')" @keydown.enter="router.push('/shop-info')">
        <div class="brand-left">
          <div class="logo">🌙</div>
          <div>
            <h1 class="brand-name">{{ shop.name }}</h1>
            <p class="slogan">山月入杯，茶咖相宜</p>
          </div>
        </div>
        <div class="brand-right">
          <div class="pickup-chip">🛍️ 门店自取</div>
          <span v-if="reviewTotal > 0" class="rating-chip" aria-label="门店评分">★ {{ avgScore.toFixed(1) }}</span>
        </div>
      </div>

      <!-- 状态行 -->
      <div class="status-row">
        <span class="status-item">
          <span class="dot" :class="shop.status === 1 ? 'dot-open dot-pulse' : 'dot-closed'" />
          <b>{{ shop.status === 1 ? '营业中' : '已打烊' }}</b>
        </span>
        <span class="status-item">🕐 {{ shop.openHours }}</span>
        <span class="row-links">
          <button class="query-link" @click="router.push('/shop-info')">门店信息 ›</button>
          <button class="query-link" @click="router.push('/member')">会员·领券 ›</button>
          <button class="query-link" @click="router.push('/query')">查订单 ›</button>
        </span>
      </div>

      <!-- 公告跑马灯（CSS 无限横向滚动 + 两端渐隐遮罩）；运营位：点击进入会员中心领券 -->
      <button v-if="shop.notice" class="notice marquee" :class="{ clickable: claimable > 0 }" @click="onNoticeClick">
        <span class="notice-icon">📢</span>
        <div class="notice-viewport">
          <div class="marquee-track">
            <span>{{ shop.notice }}</span>
            <span aria-hidden="true">{{ shop.notice }}</span>
          </div>
        </div>
        <span v-if="claimable > 0" class="notice-coupon-hint">
          🎫 {{ claimable }} 张券可领 <i class="hint-arrow">›</i>
        </span>
      </button>
    </div>
  </header>
</template>

<style scoped>
.store-header {
  position: relative;
  flex-shrink: 0;
  overflow: hidden;
  /* 图片加载失败时的渐变兜底 */
  background: linear-gradient(150deg, #2b1a10 0%, #4a2c17 55%, #2b1a10 100%);
}

.banner {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.banner-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(43, 26, 16, 0.72) 0%, rgba(58, 36, 21, 0.62) 50%, rgba(43, 26, 16, 0.88) 100%);
}

.header-body {
  position: relative;
  z-index: 1;
  padding: 18px 16px 14px;
  color: #fff;
}

.brand-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
  border-radius: 12px;
  transition: background 0.15s ease;
}

.brand-row:active {
  background: rgba(255, 255, 255, 0.08);
}

.brand-right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 5px;
}

/* 门店评分徽标（金星级） */
.rating-chip {
  padding: 2px 8px;
  font-size: 10px;
  font-weight: 800;
  color: #3b2413;
  border-radius: 999px;
  background: linear-gradient(135deg, #f4d9a6, #e9b968);
  box-shadow: 0 2px 6px rgba(233, 185, 104, 0.35);
}

.brand-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.logo {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  font-size: 20px;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(6px);
}

.brand-name {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.slogan {
  margin-top: 2px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.7);
}

.pickup-chip {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(6px);
}

.status-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin-top: 12px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.82);
}

.status-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.dot-open {
  background: #5ed69b;
}

.dot-closed {
  background: #a89888;
}

.row-links {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.query-link {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.85);
  padding: 2px 4px;
}

.query-link:active {
  opacity: 0.7;
}

.notice {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(6px);
  width: 100%;
  font: inherit;
  text-align: left;
}

/* 遮罩从整条公告栏移到文字视口：右侧领券徽标不被渐隐淡出 */
.notice.marquee {
  -webkit-mask-image: none;
  mask-image: none;
  overflow: visible;
}

/* 可领券时变为可点击运营位 */
.notice.clickable {
  cursor: pointer;
  border-color: rgba(233, 185, 104, 0.45);
  background: linear-gradient(90deg, rgba(233, 185, 104, 0.16), rgba(255, 255, 255, 0.08) 55%);
  transition: transform 0.15s ease, filter 0.15s ease;
}

.notice.clickable:active {
  transform: scale(0.985);
  filter: brightness(1.1);
}

.notice-coupon-hint {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 10px;
  font-weight: 700;
  color: #2b1a10;
  padding: 3px 8px;
  border-radius: 999px;
  background: linear-gradient(135deg, #f4d9a6, #e9b968);
  box-shadow: 0 2px 6px rgba(233, 185, 104, 0.35);
  white-space: nowrap;
}

.notice-coupon-hint .hint-arrow {
  font-style: normal;
  font-weight: 700;
  margin-left: 1px;
  animation: hint-nudge 1.4s ease-in-out infinite;
}

@keyframes hint-nudge {
  0%,
  100% {
    transform: translateX(0);
  }
  50% {
    transform: translateX(2px);
  }
}

.notice-icon {
  flex-shrink: 0;
  font-size: 11px;
}

.notice-viewport {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 7%, #000 93%, transparent);
  mask-image: linear-gradient(90deg, transparent, #000 7%, #000 93%, transparent);
}

.marquee-track span {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.85);
}
</style>
