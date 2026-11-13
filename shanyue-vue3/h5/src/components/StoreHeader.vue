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
    <!-- banner 背景（失败时露出底下的墨绿渐变） -->
    <img v-if="!bannerError" class="banner" src="/menu/banner.png" alt="山月茶咖门店" @error="bannerError = true" />
    <div class="banner-overlay" />

    <div class="header-body">
      <!-- 品牌行（点击进入门店信息页） -->
      <div class="brand-row" role="button" tabindex="0" aria-label="查看门店信息" @click="router.push('/shop-info')" @keydown.enter="router.push('/shop-info')">
        <div class="brand-left">
          <div class="seal logo-seal" aria-hidden="true"><i>山</i><i>月</i></div>
          <div>
            <h1 class="brand-name">{{ shop.name }}</h1>
            <p class="slogan">山月入杯，茶咖相宜</p>
          </div>
        </div>
        <div class="brand-right">
          <div class="pickup-chip">门店自取</div>
          <span v-if="reviewTotal > 0" class="rating-chip" aria-label="门店评分">
            <svg viewBox="0 0 24 24" width="9" height="9" fill="currentColor" aria-hidden="true"><path d="M12 2.5l2.7 5.8 6.3.7-4.7 4.3 1.3 6.2-5.6-3.2-5.6 3.2 1.3-6.2-4.7-4.3 6.3-.7L12 2.5z" /></svg>
            {{ avgScore.toFixed(1) }}
          </span>
        </div>
      </div>

      <!-- 状态行 -->
      <div class="status-row">
        <span class="status-item">
          <span class="dot" :class="shop.status === 1 ? 'dot-open dot-pulse' : 'dot-closed'" />
          <b>{{ shop.status === 1 ? '营业中' : '已打烊' }}</b>
        </span>
        <span class="status-item">
          <svg class="st-ico" viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></svg>
          {{ shop.openHours }}
        </span>
        <span class="row-links">
          <button class="query-link" @click="router.push('/shop-info')">门店信息 ›</button>
          <button class="query-link" @click="router.push('/member')">会员·领券 ›</button>
          <button class="query-link" @click="router.push('/query')">查订单 ›</button>
        </span>
      </div>

      <!-- 公告跑马灯（CSS 无限横向滚动 + 两端渐隐遮罩）；运营位：点击进入会员中心领券 -->
      <button v-if="shop.notice" class="notice marquee" :class="{ clickable: claimable > 0 }" @click="onNoticeClick">
        <svg class="notice-ico" viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 10.5v3a1 1 0 0 0 1 1h2l3.5 3.5v-12L6 9.5H4a1 1 0 0 0-1 1z" /><path d="M14.5 8.5a5 5 0 0 1 0 7" /><path d="M17.5 5.5a9 9 0 0 1 0 13" /></svg>
        <div class="notice-viewport">
          <div class="marquee-track">
            <span>{{ shop.notice }}</span>
            <span aria-hidden="true">{{ shop.notice }}</span>
          </div>
        </div>
        <span v-if="claimable > 0" class="notice-coupon-hint">
          {{ claimable }} 张券可领 <i class="hint-arrow">›</i>
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
  /* 图片加载失败时的墨绿渐变兜底 */
  background: linear-gradient(150deg, var(--pine-deep) 0%, var(--pine) 55%, var(--pine-deep) 100%);
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
  background: linear-gradient(180deg, rgba(36, 62, 40, 0.68) 0%, rgba(36, 62, 40, 0.44) 50%, rgba(36, 62, 40, 0.86) 100%);
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

/* 印章 logo（品牌位） */
.logo-seal {
  width: 42px;
  height: 42px;
  background: rgba(246, 243, 236, 0.94);
  box-shadow: 0 3px 10px rgba(36, 62, 40, 0.35);
}

.logo-seal i {
  display: block;
  font-size: 13px;
  line-height: 1.12;
  letter-spacing: 0.5px;
}

/* 门店评分徽标（茶金底 + 墨字，衬线数字） */
.rating-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 9px;
  font-size: 10px;
  font-weight: 700;
  font-family: var(--font-serif);
  color: var(--ink);
  border-radius: 999px;
  background: linear-gradient(135deg, #dcb87e, var(--gold));
  box-shadow: 0 2px 6px rgba(200, 155, 90, 0.35);
}

.brand-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.brand-name {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.slogan {
  margin-top: 3px;
  font-size: 11px;
  letter-spacing: 1px;
  color: rgba(246, 243, 236, 0.75);
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
  border: 1px solid rgba(246, 243, 236, 0.4);
  background: rgba(246, 243, 236, 0.16);
  -webkit-backdrop-filter: blur(6px);
  backdrop-filter: blur(6px);
}

.status-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin-top: 12px;
  font-size: 11px;
  color: rgba(246, 243, 236, 0.85);
}

.status-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.st-ico {
  opacity: 0.8;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.dot-open {
  background: var(--success);
}

.dot-closed {
  background: var(--ink-3);
}

.row-links {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.query-link {
  font-size: 11px;
  color: rgba(246, 243, 236, 0.9);
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
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid rgba(200, 155, 90, 0.5);
  background: var(--paper-deep);
  width: 100%;
  font: inherit;
  text-align: left;
  transition: transform 0.15s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

/* 遮罩从整条公告栏移到文字视口：右侧领券徽标不被渐隐淡出 */
.notice.marquee {
  -webkit-mask-image: none;
  mask-image: none;
  overflow: visible;
}

.notice-ico {
  flex-shrink: 0;
  color: var(--gold);
}

/* 可领券时变为可点击运营位 */
.notice.clickable {
  cursor: pointer;
  border-color: var(--gold);
  box-shadow: 0 3px 12px rgba(200, 155, 90, 0.28);
}

.notice.clickable:active {
  transform: scale(0.985);
}

.notice-coupon-hint {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 10px;
  font-weight: 700;
  color: var(--ink);
  padding: 3px 8px;
  border-radius: 999px;
  background: linear-gradient(135deg, #dcb87e, var(--gold));
  box-shadow: 0 2px 6px rgba(200, 155, 90, 0.35);
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

.notice-viewport {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 7%, #000 93%, transparent);
  mask-image: linear-gradient(90deg, transparent, #000 7%, #000 93%, transparent);
}

.marquee-track span {
  font-size: 11px;
  color: var(--ink-2);
}
</style>
