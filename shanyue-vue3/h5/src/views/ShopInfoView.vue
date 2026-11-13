<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { ReviewVO, ShopReviewStats, ShopVO } from '@/types'
import { getShopStatus } from '@/api/shop'
import { getShopReviewStats, getMyReviews } from '@/api/review'
import { USE_MOCK } from '@/utils/env'

/**
 * 门店信息页：品牌故事 + 门店信息（营业时间/地址/电话）+ 门店评价墙
 * 评分与评价来自 16.3 门店评价统计 / 16.4 我的评价（演示单用户即「门店顾客」口径）
 */
const router = useRouter()

const shop = ref<ShopVO | null>(null)
const stats = ref<ShopReviewStats | null>(null)
const reviews = ref<ReviewVO[]>([])

/** 评价墙默认展示 4 条，可展开全部 */
const wallExpanded = ref(false)
const wallList = computed(() => (wallExpanded.value ? reviews.value : reviews.value.slice(0, 4)))

const maxDist = computed(() => Math.max(1, ...(stats.value?.distribution ?? [0])))

onMounted(async () => {
  const [s, st, rs] = await Promise.all([
    getShopStatus().catch(() => null),
    getShopReviewStats().catch(() => null),
    getMyReviews().catch(() => []),
  ])
  shop.value = s
  stats.value = st
  reviews.value = rs
})

/** 星星渲染：5 星制，实心 = i <= score */
function starsOf(score: number): string {
  return '★★★★★'.slice(0, score) + '☆☆☆☆☆'.slice(0, 5 - score)
}
</script>

<template>
  <div class="shop-info-page">
    <van-nav-bar title="门店信息" left-arrow safe-area-inset-top @click-left="$router.back()" />

    <div class="page-body">
      <!-- 门店头卡：banner 底 + 门店名 + 评分 -->
      <div class="shop-hero">
        <img class="hero-banner" src="/menu/banner.png" alt="山月茶咖门店" />
        <div class="hero-mask" />
        <div class="hero-body">
          <p class="hero-brand">🌙 山月茶咖</p>
          <h1 class="hero-name">{{ shop?.name || '山月茶咖 · 科创园区店' }}</h1>
          <div class="hero-rating">
            <template v-if="stats && stats.total > 0">
              <span class="hr-score">{{ stats.avgScore.toFixed(1) }}</span>
              <span class="hr-stars">★★★★★</span>
              <span class="hr-meta">{{ stats.total }} 条评价 · 好评率 {{ stats.goodRate }}%</span>
            </template>
            <span v-else class="hr-meta">暂无评价，等你来评第一条</span>
          </div>
        </div>
        <span class="hero-chip" :class="{ closed: shop?.status === 0 }">
          <i class="dot" />{{ shop?.status === 0 ? '已打烊' : '营业中' }}
        </span>
      </div>

      <!-- 品牌故事 -->
      <div class="card story-card">
        <h2 class="card-title">山月的故事</h2>
        <p class="story-text">
          「山月」取自「明月出天山」——山间明月不疾不徐，像一杯慢慢做好的茶。
        </p>
        <p class="story-text">
          我们把咖啡的醇厚与茶的清雅装进同一只杯子：豆子按周小批烘焙，茶底当日现萃，
          甜度交给你来定。山月入杯，茶咖相宜。
        </p>
        <div class="story-tags">
          <span class="st-item">☕ 每周小批烘焙</span>
          <span class="st-item">🍵 茶底当日现萃</span>
          <span class="st-item">🥛 鲜奶零植脂末</span>
        </div>
      </div>

      <!-- 门店信息 -->
      <div class="card info-card">
        <h2 class="card-title">门店信息</h2>
        <ul class="info-list">
          <li class="info-row">
            <span class="ir-label">营业时间</span>
            <span class="ir-value">🕐 {{ shop?.openHours || '09:00-22:00' }}</span>
          </li>
          <li class="info-row">
            <span class="ir-label">门店地址</span>
            <span class="ir-value">📍 杭州市西湖区科创园区 3 号楼 1 层（园区东门进来右转第二间）</span>
          </li>
          <li class="info-row">
            <span class="ir-label">联系电话</span>
            <a class="ir-value ir-link" href="tel:0571-8888-6666">📞 0571-8888-6666</a>
          </li>
          <li v-if="shop?.notice" class="info-row">
            <span class="ir-label">门店公告</span>
            <span class="ir-value">{{ shop.notice }}</span>
          </li>
        </ul>
      </div>

      <!-- 评价总览（星级分布 + 标签热度） -->
      <div v-if="stats && stats.total > 0" class="card stats-card">
        <h2 class="card-title">评价总览</h2>
        <div class="stats-body">
          <div class="stats-left">
            <p class="sa-score">{{ stats.avgScore.toFixed(1) }}</p>
            <p class="sa-stars">{{ starsOf(Math.round(stats.avgScore)) }}</p>
            <p class="sa-count">{{ stats.total }} 条评价</p>
          </div>
          <div class="stats-right">
            <div class="dist-rows">
              <div v-for="(n, i) in stats.distribution" :key="i" class="dist-row">
                <span class="dr-label">{{ 5 - i }} 星</span>
                <div class="dr-track">
                  <div class="dr-fill" :style="{ width: `${(n / maxDist) * 100}%` }" />
                </div>
                <span class="dr-num">{{ n }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-if="stats.tagStats.length" class="tag-cloud">
          <span v-for="t in stats.tagStats" :key="t.tag" class="tc-chip">
            {{ t.tag }} <i>{{ t.count }}</i>
          </span>
        </div>
      </div>

      <!-- 评价墙 -->
      <div class="card wall-card">
        <h2 class="card-title">评价墙</h2>
        <p class="wall-sub">来自取餐顾客的真实反馈（演示数据存于本地）</p>
        <div v-if="wallList.length" class="wall-list">
          <div v-for="r in wallList" :key="r.id" class="wall-item">
            <div class="wi-head">
              <span class="wi-code">{{ r.pickUpCode }}</span>
              <span class="wi-stars">{{ starsOf(r.score) }}</span>
            </div>
            <p v-if="r.content" class="wi-content">{{ r.content }}</p>
            <div v-if="r.tags.length" class="wi-tags">
              <span v-for="t in r.tags" :key="t" class="wi-tag">{{ t }}</span>
            </div>
            <p v-if="r.reply" class="wi-reply"><b>山月回复：</b>{{ r.reply }}</p>
            <p class="wi-time">{{ r.createTime.slice(0, 16) }}</p>
          </div>
          <button v-if="reviews.length > 4" class="wall-more" @click="wallExpanded = !wallExpanded">
            {{ wallExpanded ? '收起 ▲' : `展开全部 ${reviews.length} 条评价 ▼` }}
          </button>
        </div>
        <div v-else class="wall-empty">
          <p>还没有评价</p>
          <p class="we-hint">完成订单后可以在「订单查询」里评价这一杯</p>
          <button class="we-btn" @click="router.push('/query')">去查询订单</button>
        </div>
      </div>

      <p v-if="USE_MOCK" class="demo-note">
        💡 评价为演示功能（mock 数据，存于本地浏览器）；演示评价挂在历史订单上
      </p>
    </div>
  </div>
</template>

<style scoped>
.shop-info-page {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
  background: var(--cream);
}

.page-body {
  flex: 1;
  padding: 12px 12px 28px;
}

/* ---------- 门店头卡 ---------- */
.shop-hero {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.hero-banner {
  display: block;
  width: 100%;
  height: 150px;
  object-fit: cover;
}

.hero-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(43, 26, 16, 0.32) 0%, rgba(43, 26, 16, 0.78) 100%);
}

.hero-body {
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 12px;
  color: #fff;
}

.hero-brand {
  font-size: 10px;
  letter-spacing: 2px;
  opacity: 0.8;
}

.hero-name {
  margin-top: 3px;
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 0.5px;
}

.hero-rating {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 6px;
}

.hr-score {
  font-size: 22px;
  font-weight: 800;
  color: #f4c67c;
}

.hr-stars {
  font-size: 11px;
  color: #f4c67c;
  letter-spacing: 2px;
}

.hr-meta {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.78);
}

.hero-chip {
  position: absolute;
  top: 12px;
  right: 12px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  font-size: 10px;
  font-weight: 600;
  color: #eafff2;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  background: rgba(95, 141, 107, 0.55);
  backdrop-filter: blur(6px);
}

.hero-chip.closed {
  color: #f3e9e2;
  background: rgba(140, 122, 107, 0.6);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #5ed69b;
}

.hero-chip.closed .dot {
  background: #c9bcae;
}

/* ---------- 通用卡片 ---------- */
.card {
  margin-top: 12px;
  padding: 16px;
  background: var(--card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 14px;
  font-weight: 800;
  color: var(--ink);
}

.card-title::before {
  content: '';
  width: 3px;
  height: 13px;
  border-radius: 2px;
  background: linear-gradient(180deg, var(--amber-from), var(--amber-to));
}

/* ---------- 品牌故事 ---------- */
.story-text {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.9;
  color: var(--ink-2);
}

.story-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.st-item {
  padding: 5px 12px;
  font-size: 11px;
  font-weight: 600;
  color: var(--caramel-deep);
  border-radius: 999px;
  background: #fdf6ec;
  border: 1px solid #f2e4cc;
}

/* ---------- 门店信息 ---------- */
.info-list {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}

.info-row {
  display: flex;
  gap: 12px;
  padding: 9px 0;
  font-size: 12px;
  line-height: 1.6;
  border-bottom: 1px dashed #f0e8dc;
}

.info-row:last-child {
  border-bottom: none;
}

.ir-label {
  flex-shrink: 0;
  width: 60px;
  color: var(--ink-3);
}

.ir-value {
  flex: 1;
  color: var(--ink);
}

.ir-link {
  color: var(--caramel-deep);
  font-weight: 600;
  text-decoration: none;
}

/* ---------- 评价总览 ---------- */
.stats-body {
  display: flex;
  gap: 18px;
  align-items: center;
  margin-top: 14px;
}

.stats-left {
  flex-shrink: 0;
  text-align: center;
}

.sa-score {
  font-size: 34px;
  font-weight: 800;
  line-height: 1;
  color: var(--caramel-deep);
}

.sa-stars {
  margin-top: 6px;
  font-size: 11px;
  color: #e9a23b;
  letter-spacing: 2px;
}

.sa-count {
  margin-top: 5px;
  font-size: 10px;
  color: var(--ink-3);
}

.stats-right {
  flex: 1;
  min-width: 0;
}

.dist-rows {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.dist-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 10px;
  color: var(--ink-3);
}

.dr-label {
  flex-shrink: 0;
  width: 26px;
  text-align: right;
}

.dr-track {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: var(--cream-deep);
  overflow: hidden;
}

.dr-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--amber-from), var(--amber-to));
  transition: width 0.5s ease;
}

.dr-num {
  flex-shrink: 0;
  width: 18px;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed #f0e8dc;
}

.tc-chip {
  padding: 5px 12px;
  font-size: 11px;
  font-weight: 600;
  color: #a05e2b;
  border-radius: 999px;
  background: #fdf6ec;
  border: 1px solid #f2e0c4;
}

.tc-chip i {
  font-style: normal;
  font-weight: 800;
  margin-left: 2px;
  color: #d9892b;
}

/* ---------- 评价墙 ---------- */
.wall-sub {
  margin: 6px 0 0;
  font-size: 10px;
  color: var(--ink-3);
}

.wall-list {
  margin-top: 10px;
}

.wall-item {
  padding: 12px 0;
  border-bottom: 1px dashed #f0e8dc;
}

.wall-item:last-of-type {
  border-bottom: none;
}

.wi-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.wi-code {
  padding: 2px 8px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 1px;
  color: var(--caramel-deep);
  border-radius: 6px;
  background: #f9ecdd;
}

.wi-stars {
  font-size: 11px;
  color: #e9a23b;
  letter-spacing: 1px;
}

.wi-content {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-2);
}

.wi-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.wi-tag {
  padding: 2px 8px;
  font-size: 10px;
  color: var(--ink-3);
  border-radius: 999px;
  background: var(--cream-deep);
}

.wi-reply {
  margin: 8px 0 0;
  padding: 8px 10px;
  font-size: 11px;
  line-height: 1.6;
  color: #8a7f6f;
  border-radius: 8px;
  background: #faf6ef;
}

.wi-reply b {
  color: var(--caramel-deep);
  font-weight: 700;
}

.wi-time {
  margin: 6px 0 0;
  font-size: 10px;
  color: var(--ink-3);
}

.wall-more {
  width: 100%;
  height: 34px;
  margin-top: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--caramel-deep);
  border-radius: 10px;
  border: 1px dashed #e3cba8;
  background: #fdf9f3;
}

.wall-empty {
  padding: 26px 0 10px;
  text-align: center;
}

.wall-empty p {
  margin: 0;
  font-size: 13px;
  color: var(--ink-2);
}

.we-hint {
  margin-top: 6px !important;
  font-size: 11px !important;
  color: var(--ink-3) !important;
}

.we-btn {
  margin-top: 14px;
  height: 34px;
  padding: 0 22px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--amber-from), var(--amber-to));
}

.demo-note {
  margin-top: 16px;
  font-size: 10px;
  text-align: center;
  color: var(--ink-3);
}
</style>
