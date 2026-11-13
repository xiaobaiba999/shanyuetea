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
          <p class="hero-brand">山月茶咖</p>
          <h1 class="hero-name">{{ shop?.name || '山月茶咖 · 科创园区店' }}</h1>
          <div class="hero-rating">
            <template v-if="stats && stats.total > 0">
              <span class="hr-score">{{ stats.avgScore.toFixed(1) }}</span>
              <span class="hr-stars" aria-hidden="true">
                <svg v-for="i in 5" :key="i" class="st on" viewBox="0 0 24 24" width="10" height="10" fill="currentColor"><path d="M12 2.5l2.7 5.8 6.3.7-4.7 4.3 1.3 6.2-5.6-3.2-5.6 3.2 1.3-6.2-4.7-4.3 6.3-.7L12 2.5z" /></svg>
              </span>
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
          <span class="st-item">每周小批烘焙</span>
          <span class="st-item">茶底当日现萃</span>
          <span class="st-item">鲜奶零植脂末</span>
        </div>
      </div>

      <!-- 门店信息 -->
      <div class="card info-card">
        <h2 class="card-title">门店信息</h2>
        <ul class="info-list">
          <li class="info-row">
            <span class="ir-label">营业时间</span>
            <span class="ir-value">
              <svg class="ir-ico" viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></svg>
              {{ shop?.openHours || '09:00-22:00' }}
            </span>
          </li>
          <li class="info-row">
            <span class="ir-label">门店地址</span>
            <span class="ir-value">
              <svg class="ir-ico" viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 21s-7-5.3-7-11a7 7 0 0 1 14 0c0 5.7-7 11-7 11z" /><circle cx="12" cy="10" r="2.6" /></svg>
              杭州市西湖区科创园区 3 号楼 1 层（园区东门进来右转第二间）
            </span>
          </li>
          <li class="info-row">
            <span class="ir-label">联系电话</span>
            <a class="ir-value ir-link" href="tel:0571-8888-6666">
              <svg class="ir-ico" viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 4h4l1.5 5-2.7 1.8a12 12 0 0 0 5.4 5.4L15 13.5l5 1.5v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z" /></svg>
              0571-8888-6666
            </a>
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
            <p class="sa-stars" :aria-label="`${stats.avgScore.toFixed(1)} 分`">
              <svg v-for="i in 5" :key="i" class="st" :class="{ on: i <= Math.round(stats.avgScore) }" viewBox="0 0 24 24" width="11" height="11" fill="currentColor" aria-hidden="true"><path d="M12 2.5l2.7 5.8 6.3.7-4.7 4.3 1.3 6.2-5.6-3.2-5.6 3.2 1.3-6.2-4.7-4.3 6.3-.7L12 2.5z" /></svg>
            </p>
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
              <span class="wi-stars" :aria-label="`${r.score} 星`">
                <svg v-for="i in 5" :key="i" class="st" :class="{ on: i <= r.score }" viewBox="0 0 24 24" width="11" height="11" fill="currentColor" aria-hidden="true"><path d="M12 2.5l2.7 5.8 6.3.7-4.7 4.3 1.3 6.2-5.6-3.2-5.6 3.2 1.3-6.2-4.7-4.3 6.3-.7L12 2.5z" /></svg>
              </span>
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
        评价为演示功能（mock 数据，存于本地浏览器）；演示评价挂在历史订单上
      </p>
    </div>
  </div>
</template>

<style scoped>
.shop-info-page {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
  background: var(--paper);
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
  border: 1px solid var(--card-line);
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
  background: linear-gradient(180deg, rgba(36, 62, 40, 0.32) 0%, rgba(36, 62, 40, 0.78) 100%);
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
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 1px;
}

.hero-rating {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 6px;
}

/* 评分：衬线数字 + SVG 星点（茶金） */
.hr-score {
  font-size: 22px;
  font-weight: 800;
  font-family: var(--font-serif);
  font-variant-numeric: tabular-nums;
  color: #e8c886;
}

.hr-stars {
  display: inline-flex;
  gap: 1px;
}

.hr-stars .st {
  color: #e8c886;
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
  background: rgba(62, 122, 78, 0.55);
  backdrop-filter: blur(6px);
}

.hero-chip.closed {
  color: #f3e9e2;
  background: rgba(43, 38, 32, 0.45);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #8cd3a5;
}

.hero-chip.closed .dot {
  background: #c9bcae;
}

/* ---------- 通用卡片 ---------- */
.card {
  margin-top: 12px;
  padding: 16px;
  background: var(--card);
  border: 1px solid var(--card-line);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--ink);
}

.card-title::before {
  content: '';
  width: 3px;
  height: 13px;
  border-radius: 2px;
  background: linear-gradient(180deg, #dcb87e, var(--gold));
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

/* 标签胶囊：pine-mist 底 + 墨绿字 */
.st-item {
  padding: 5px 12px;
  font-size: 11px;
  font-weight: 600;
  color: var(--pine);
  border-radius: 999px;
  background: var(--pine-mist);
  border: 1px solid rgba(47, 82, 51, 0.18);
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
  border-bottom: 1px dashed var(--card-line);
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
  display: flex;
  align-items: flex-start;
  gap: 5px;
  flex: 1;
  color: var(--ink);
}

.ir-ico {
  flex-shrink: 0;
  margin-top: 3px;
  color: var(--gold);
}

.ir-link {
  color: var(--pine);
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
  font-family: var(--font-serif);
  font-variant-numeric: tabular-nums;
  color: var(--pine);
}

.sa-stars {
  display: inline-flex;
  gap: 1px;
  margin-top: 6px;
}

.sa-stars .st {
  color: rgba(43, 38, 32, 0.15);
}

.sa-stars .st.on {
  color: var(--gold);
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
  background: var(--paper-deep);
  overflow: hidden;
}

.dr-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #dcb87e, var(--gold));
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
  border-top: 1px dashed var(--card-line);
}

.tc-chip {
  padding: 5px 12px;
  font-size: 11px;
  font-weight: 600;
  color: var(--pine);
  border-radius: 999px;
  background: var(--pine-mist);
  border: 1px solid rgba(47, 82, 51, 0.18);
}

.tc-chip i {
  font-style: normal;
  font-weight: 800;
  margin-left: 2px;
  color: var(--terracotta);
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
  border-bottom: 1px dashed var(--card-line);
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
  color: var(--pine);
  border-radius: 6px;
  background: var(--pine-mist);
}

.wi-stars {
  display: inline-flex;
  gap: 1px;
}

.wi-stars .st {
  color: rgba(43, 38, 32, 0.15);
}

.wi-stars .st.on {
  color: var(--gold);
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
  background: var(--paper-deep);
}

.wi-reply {
  margin: 8px 0 0;
  padding: 8px 10px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--ink-2);
  border-radius: 8px;
  background: var(--paper);
  border-left: 2px solid rgba(200, 155, 90, 0.5);
}

.wi-reply b {
  color: var(--pine);
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
  color: var(--pine);
  border-radius: 10px;
  border: 1px dashed rgba(47, 82, 51, 0.35);
  background: var(--card);
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
  background: linear-gradient(160deg, #3a6140, var(--pine) 55%, var(--pine-deep));
  box-shadow: 0 3px 10px rgba(47, 82, 51, 0.26);
}

.demo-note {
  margin-top: 16px;
  font-size: 10px;
  text-align: center;
  color: var(--ink-3);
}
</style>
