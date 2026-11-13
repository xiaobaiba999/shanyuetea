<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showFailToast } from 'vant'
import type { CouponTemplateVO, ExchangeRuleVO, MemberSummaryVO, PointRecordVO, PointSummaryVO, ReviewVO, UserCouponVO } from '@/types'
import { getCouponTemplates, getMemberSummary, getMyCoupons, receiveCoupon } from '@/api/coupon'
import { checkinToday, exchangeCoupon, getExchangeShelf, getPointRecords, getPointSummary, seedStreakDemo } from '@/api/points'
import { getMyReviews } from '@/api/review'
import { USE_MOCK } from '@/utils/env'
import { fmtPrice } from '@/utils/format'

/**
 * 会员中心：会员卡（积分/累计消费）+ 每日签到 + 积分兑换 + 券广场（领取）+ 我的优惠券 + 积分明细
 * 积分口径：余额 = 消费积分（已完成订单实付/100）+ 签到积分（7/天）- 兑换消耗
 */
const router = useRouter()

const summary = ref<MemberSummaryVO | null>(null)
const templates = ref<CouponTemplateVO[]>([])
const myCoupons = ref<UserCouponVO[]>([])
const receiving = ref<number | null>(null)

/* ---------------- 积分体系 ---------------- */
const pointSummary = ref<PointSummaryVO | null>(null)
const pointRecords = ref<PointRecordVO[]>([])
const shelf = ref<ExchangeRuleVO[]>([])
const checkingIn = ref(false)
const exchangingId = ref<number | null>(null)
const recordsExpanded = ref(false)
const RECORD_PREVIEW = 6

/* ---------------- 我的评价（模块 16） ---------------- */
const myReviews = ref<ReviewVO[]>([])
const reviewsExpanded = ref(false)
const REVIEW_PREVIEW = 2
const visibleReviews = computed(() =>
  reviewsExpanded.value ? myReviews.value : myReviews.value.slice(0, REVIEW_PREVIEW),
)

async function loadMyReviews() {
  myReviews.value = await getMyReviews().catch(() => [])
}

/** 星星渲染：5 星制 */
function starsOf(score: number): string {
  return '★★★★★'.slice(0, score) + '☆☆☆☆☆'.slice(0, 5 - score)
}

/** 已领取的模板 id 集合（每模板限领 1 张） */
const receivedIds = computed(() => new Set(myCoupons.value.map((c) => c.couponId)))

const usableCoupons = computed(() => myCoupons.value.filter((c) => c.status === 1))
const usedCoupons = computed(() => myCoupons.value.filter((c) => c.status !== 1))

/** 已签到日期集合（由积分明细反查，签到行 createTime 前 10 位） */
const checkedDates = computed(() => {
  const set = new Set<string>()
  for (const r of pointRecords.value) {
    if (r.type === 1) set.add(r.createTime.slice(0, 10))
  }
  if (pointSummary.value?.checkedToday) set.add(todayYmd())
  return set
})

/** 本周一~周日签到日历格子 */
const weekDays = computed(() => {
  const now = new Date()
  const dow = now.getDay() || 7 // 周一=1 … 周日=7
  const monday = new Date(now)
  monday.setDate(now.getDate() - (dow - 1))
  const labels = ['一', '二', '三', '四', '五', '六', '日']
  const today = todayYmd()
  return labels.map((label, i) => {
    const d = new Date(monday)
    d.setDate(monday.getDate() + i)
    const ymd = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    return { ymd, label, checked: checkedDates.value.has(ymd), isToday: ymd === today, isFuture: ymd > today }
  })
})

function todayYmd(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const visibleRecords = computed(() =>
  recordsExpanded.value ? pointRecords.value : pointRecords.value.slice(0, RECORD_PREVIEW),
)

onMounted(async () => {
  await Promise.all([loadSummary(), loadTemplates(), loadMyCoupons(), loadPoints(), loadShelf(), loadMyReviews()]).catch(() => {})
})

async function loadSummary() {
  summary.value = await getMemberSummary().catch(() => null)
}

async function loadTemplates() {
  templates.value = await getCouponTemplates().catch(() => [])
}

async function loadMyCoupons() {
  myCoupons.value = await getMyCoupons().catch(() => [])
}

async function loadPoints() {
  const [s, records] = await Promise.all([getPointSummary().catch(() => null), getPointRecords().catch(() => [])])
  pointSummary.value = s
  pointRecords.value = records
}

async function loadShelf() {
  shelf.value = await getExchangeShelf().catch(() => [])
}

/** 每日签到：成功后刷新概要与明细（余额/连续天数联动）；触发连签奖励时播放庆祝动效 */
async function handleCheckin() {
  if (checkingIn.value || pointSummary.value?.checkedToday) return
  checkingIn.value = true
  try {
    const res = await checkinToday()
    pointSummary.value = res
    await getPointRecords().then((r) => (pointRecords.value = r))
    if (res.justBonus > 0) {
      triggerCelebration(res.justBonus)
      showSuccessToast(`连签 ${res.streak} 天达成！额外 +${res.justBonus} 积分`)
    } else {
      showSuccessToast(`签到成功 +7 积分，已连续 ${res.streak} 天`)
    }
  } catch (e) {
    showFailToast(e instanceof Error ? e.message : '签到失败')
  } finally {
    checkingIn.value = false
  }
}

/** 积分兑换：余额校验由服务端做（客户端值不可信），成功后刷新积分/券/库存 */
async function handleExchange(rule: ExchangeRuleVO) {
  if (exchangingId.value != null) return
  exchangingId.value = rule.templateId
  try {
    pointSummary.value = await exchangeCoupon(rule.templateId)
    await Promise.all([getPointRecords().then((r) => (pointRecords.value = r)), loadShelf(), loadMyCoupons(), loadSummary()])
    showSuccessToast(`已兑换「${rule.name}」，快去下单使用吧`)
  } catch (e) {
    showFailToast(e instanceof Error ? e.message : '兑换失败')
  } finally {
    exchangingId.value = null
  }
}

/** 领券：成功后刷新券列表与会员概要（可用券数） */
async function handleReceive(t: CouponTemplateVO) {
  if (receiving.value != null) return
  receiving.value = t.id
  try {
    await receiveCoupon(t.id)
    showSuccessToast(`已领取「${t.name}」`)
    await Promise.all([loadMyCoupons(), loadTemplates(), loadSummary()])
  } catch (e) {
    showFailToast(e instanceof Error ? e.message : '领取失败')
  } finally {
    receiving.value = null
  }
}

/** 明细行图标 */
const TYPE_EMOJI: Record<number, string> = { 1: '☀️', 2: '☕', 3: '🎫' }

/* ---------------- 连签奖励（每连签 7 天额外 +20） ---------------- */
const STREAK_TARGET = 7

/** 连签奖励进度：达成日满格，否则按 streak % 7 显示 */
const bonusProgress = computed(() => {
  const streak = pointSummary.value?.streak ?? 0
  const done = streak > 0 && streak % STREAK_TARGET === 0
  const lit = done ? STREAK_TARGET : streak % STREAK_TARGET
  return { done, lit, pct: Math.round((lit / STREAK_TARGET) * 100) }
})

/** 签到按钮文案：今天签到将达成连签时给出明确预期 */
const checkinBtnText = computed(() => {
  if (pointSummary.value?.checkedToday) return '今日已签到 ✓'
  if (checkingIn.value) return '签到中…'
  const s = pointSummary.value?.streak ?? 0
  if ((s + 1) % STREAK_TARGET === 0) return `立即签到 · 达成连签 +${7 + 20} 积分 🎉`
  return '立即签到 +7 积分'
})

/* ---------------- 连签达成庆祝动效（emoji 彩带 + 中央徽章） ---------------- */
const celebrating = ref(false)
const bonusAmount = ref(0)
const confetti = ref<{ emoji: string; style: Record<string, string> }[]>([])
const CONFETTI_EMOJIS = ['🎉', '✨', '☕', '🍵', '🎫', '🧋', '⭐', '💛']
let celeTimer: number | null = null

function triggerCelebration(amount: number) {
  bonusAmount.value = amount
  confetti.value = Array.from({ length: 26 }, (_, i) => ({
    emoji: CONFETTI_EMOJIS[i % CONFETTI_EMOJIS.length],
    style: {
      left: `${(i * 37) % 100}%`,
      fontSize: `${14 + ((i * 13) % 12)}px`,
      animationDelay: `${((i * 7) % 10) / 10}s`,
      animationDuration: `${1.8 + ((i * 11) % 14) / 10}s`,
    },
  }))
  celebrating.value = true
  if (celeTimer != null) window.clearTimeout(celeTimer)
  celeTimer = window.setTimeout(() => (celebrating.value = false), 3200)
}

/* ---------------- 演示：注入连签历史（仅 mock） ---------------- */
const seeding = ref(false)

async function handleSeedStreak() {
  if (seeding.value) return
  seeding.value = true
  try {
    pointSummary.value = await seedStreakDemo()
    await getPointRecords().then((r) => (pointRecords.value = r))
    showSuccessToast(`已注入 ${STREAK_TARGET - 1} 天连签历史，点签到触发连签奖励`)
  } catch {
    showFailToast('操作失败')
  } finally {
    seeding.value = false
  }
}
</script>

<template>
  <div class="member-page">
    <van-nav-bar title="会员中心" left-arrow safe-area-inset-top @click-left="$router.back()" />

    <div class="page-body">
      <!-- 会员卡 -->
      <div class="vip-card">
        <div class="vip-head">
          <span class="vip-logo">🌙</span>
          <div class="vip-user">
            <p class="vip-name">山月茶友</p>
            <p class="vip-no">{{ summary?.memberNo || 'SHY-2026-0001' }}</p>
          </div>
          <span class="vip-tier">☕ 茶咖会员</span>
        </div>
        <div class="vip-stats">
          <div class="vs-item">
            <b>{{ pointSummary?.balance ?? summary?.points ?? 0 }}</b>
            <span>积分</span>
          </div>
          <div class="vs-divider" />
          <div class="vs-item">
            <b>¥{{ fmtPrice(summary?.totalSpent ?? 0) }}</b>
            <span>累计消费</span>
          </div>
          <div class="vs-divider" />
          <div class="vs-item">
            <b>{{ summary?.finishedOrders ?? 0 }}</b>
            <span>完成订单</span>
          </div>
          <div class="vs-divider" />
          <div class="vs-item">
            <b>{{ summary?.couponsAvailable ?? 0 }}</b>
            <span>可用券</span>
          </div>
        </div>
      </div>

      <!-- 每日签到 -->
      <div class="section">
        <div class="checkin-card">
          <div class="ck-head">
            <div class="ck-title-wrap">
              <h2 class="ck-title">☀️ 每日签到</h2>
              <p class="ck-sub">每日 +7 积分，积分可兑优惠券</p>
            </div>
            <span v-if="(pointSummary?.streak ?? 0) > 0" class="ck-streak">🔥 连续 {{ pointSummary?.streak }} 天</span>
          </div>
          <div class="ck-week">
            <div
              v-for="d in weekDays"
              :key="d.ymd"
              class="ck-day"
              :class="{ checked: d.checked, today: d.isToday, future: d.isFuture }"
            >
              <span class="ck-dow">{{ d.label }}</span>
              <span class="ck-mark">{{ d.checked ? '✓' : d.isFuture ? '·' : '＋7' }}</span>
            </div>
          </div>

          <!-- 连签奖励进度：每连签 7 天额外 +20 积分 -->
          <div class="ck-progress">
            <div class="ckp-head">
              <span class="ckp-label">
                {{ bonusProgress.done ? '🎉 连签奖励已达成' : `已连签 ${pointSummary?.streak ?? 0} 天` }}
              </span>
              <span class="ckp-tip">
                {{ bonusProgress.done ? '再签 7 天可再得 +20' : `再签 ${pointSummary?.nextBonusIn ?? 7} 天额外 +20 积分` }}
              </span>
            </div>
            <div class="ckp-segs" role="img" :aria-label="`连签进度 ${bonusProgress.lit}/${7}`">
              <span
                v-for="i in STREAK_TARGET"
                :key="i"
                class="ckp-seg"
                :class="{ lit: i <= bonusProgress.lit, goal: i === STREAK_TARGET }"
              />
              <span class="ckp-gift" :class="{ lit: bonusProgress.done }">🎁</span>
            </div>
            <button
              v-if="USE_MOCK && !pointSummary?.checkedToday"
              class="ck-demo"
              :disabled="seeding"
              @click="handleSeedStreak"
            >
              {{ seeding ? '注入中…' : `⏩ 演示：补签 ${STREAK_TARGET - 1} 天` }}
            </button>
          </div>

          <button class="ck-btn" :class="{ done: pointSummary?.checkedToday }" :disabled="checkingIn || pointSummary?.checkedToday" @click="handleCheckin">
            {{ checkinBtnText }}
          </button>
        </div>
      </div>

      <!-- 积分兑换 -->
      <div v-if="shelf.length" class="section">
        <div class="section-head">
          <h2>✨ 积分兑换</h2>
          <span class="section-tip">余额 {{ pointSummary?.balance ?? 0 }} 分 · 1 积分 ≈ 0.1 元</span>
        </div>
        <div class="shelf-list">
          <div v-for="r in shelf" :key="r.templateId" class="shelf-card" :class="{ lack: (pointSummary?.balance ?? 0) < r.cost }">
            <div class="sh-left">
              <p class="sh-cost"><i>{{ r.cost }}</i>积分</p>
              <p class="sh-worth">价值 ¥{{ fmtPrice(r.discount) }} 券</p>
            </div>
            <div class="sh-main">
              <p class="sh-name">{{ r.name }}</p>
              <p class="sh-desc">{{ r.description }}</p>
              <p class="sh-remain">剩余 {{ r.remainCount }} 张</p>
            </div>
            <button
              class="sh-btn"
              :disabled="(pointSummary?.balance ?? 0) < r.cost || r.remainCount <= 0 || exchangingId === r.templateId"
              @click="handleExchange(r)"
            >
              {{ exchangingId === r.templateId ? '兑换中…' : (pointSummary?.balance ?? 0) < r.cost ? `差 ${r.cost - (pointSummary?.balance ?? 0)} 分` : '兑换' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 券广场 -->
      <div class="section">
        <div class="section-head">
          <h2>🎫 领券中心</h2>
          <span class="section-tip">每张限领 1 张</span>
        </div>
        <div class="tpl-list">
          <div v-for="t in templates" :key="t.id" class="tpl-card" :class="{ received: receivedIds.has(t.id) || t.remainCount <= 0 }">
            <div class="tpl-left">
              <p class="tpl-amount"><i>¥</i>{{ fmtPrice(t.discount) }}</p>
              <p class="tpl-threshold">{{ t.threshold ? `满${fmtPrice(t.threshold)}可用` : '无门槛' }}</p>
            </div>
            <div class="tpl-main">
              <p class="tpl-name">{{ t.name }}</p>
              <p class="tpl-desc">{{ t.description }} · 领取后 {{ t.validDays }} 天内有效</p>
              <p class="tpl-remain">剩余 {{ t.remainCount }} 张</p>
            </div>
            <button
              class="tpl-btn"
              :disabled="receivedIds.has(t.id) || t.remainCount <= 0 || receiving === t.id"
              @click="handleReceive(t)"
            >
              {{ receivedIds.has(t.id) ? '已领取' : t.remainCount <= 0 ? '已领完' : receiving === t.id ? '领取中…' : '领取' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 我的优惠券 -->
      <div class="section">
        <div class="section-head">
          <h2>🎟 我的优惠券</h2>
          <span class="section-tip">可用 {{ usableCoupons.length }} 张</span>
        </div>

        <div v-if="usableCoupons.length" class="my-list">
          <div v-for="c in usableCoupons" :key="c.id" class="my-coupon">
            <div class="mc-left">
              <p class="mc-amount"><i>¥</i>{{ fmtPrice(c.discount) }}</p>
              <p class="mc-threshold">{{ c.threshold ? `满${fmtPrice(c.threshold)}可用` : '无门槛' }}</p>
            </div>
            <div class="mc-main">
              <p class="mc-name">{{ c.name }}</p>
              <p class="mc-expire">有效期至 {{ c.expireTime.slice(0, 10) }}</p>
            </div>
            <button class="mc-use" @click="router.push('/')">去使用</button>
          </div>
        </div>
        <div v-else class="my-empty">还没有可用优惠券，上方领一张吧 ✨</div>

        <div v-if="usedCoupons.length" class="history">
          <p class="history-title">历史券</p>
          <div v-for="c in usedCoupons" :key="c.id" class="my-coupon used">
            <div class="mc-left">
              <p class="mc-amount"><i>¥</i>{{ fmtPrice(c.discount) }}</p>
            </div>
            <div class="mc-main">
              <p class="mc-name">{{ c.name }}</p>
              <p class="mc-expire">
                {{ c.status === 2 ? `已于订单 ${c.usedOrderNumber || '-'} 核销` : `已过期（${c.expireTime.slice(0, 10)}）` }}
              </p>
            </div>
            <span class="mc-flag">{{ c.status === 2 ? '已核销' : '已过期' }}</span>
          </div>
        </div>
      </div>

      <!-- 积分明细 -->
      <div class="section">
        <div class="section-head">
          <h2>📖 积分明细</h2>
          <span class="section-tip">共 {{ pointRecords.length }} 条</span>
        </div>
        <div v-if="visibleRecords.length" class="pt-timeline">
          <div v-for="r in visibleRecords" :key="r.id" class="pt-row">
            <span class="pt-icon">{{ TYPE_EMOJI[r.type] }}</span>
            <div class="pt-main">
              <p class="pt-title">{{ r.title }}</p>
              <p class="pt-sub">{{ r.sub }}</p>
            </div>
            <div class="pt-right">
              <p class="pt-points" :class="r.points >= 0 ? 'plus' : 'minus'">{{ r.points >= 0 ? '+' : '' }}{{ r.points }}</p>
              <p class="pt-time">{{ r.createTime.slice(5, 16) }}</p>
            </div>
          </div>
          <button v-if="pointRecords.length > RECORD_PREVIEW" class="pt-more" @click="recordsExpanded = !recordsExpanded">
            {{ recordsExpanded ? '收起 ▲' : `展开全部 ${pointRecords.length} 条 ▼` }}
          </button>
        </div>
        <div v-else class="my-empty">还没有积分记录，签个到或点一杯就有了 ☕</div>
      </div>

      <!-- 我的评价 -->
      <div class="section">
        <div class="section-head">
          <h2>⭐ 我的评价</h2>
          <span class="section-tip">共 {{ myReviews.length }} 条</span>
        </div>
        <div v-if="visibleReviews.length" class="rv-list">
          <div v-for="r in visibleReviews" :key="r.id" class="rv-item">
            <div class="rv-head">
              <span class="rv-code">{{ r.pickUpCode }}</span>
              <span class="rv-stars">{{ starsOf(r.score) }}</span>
              <span class="rv-time">{{ r.createTime.slice(0, 16) }}</span>
            </div>
            <p v-if="r.content" class="rv-content">{{ r.content }}</p>
            <div v-if="r.tags.length" class="rv-tags">
              <span v-for="t in r.tags" :key="t" class="rv-tag">{{ t }}</span>
            </div>
            <p v-if="r.reply" class="rv-reply"><b>山月回复：</b>{{ r.reply }}</p>
          </div>
          <button v-if="myReviews.length > REVIEW_PREVIEW" class="rv-more" @click="reviewsExpanded = !reviewsExpanded">
            {{ reviewsExpanded ? '收起 ▲' : `展开全部 ${myReviews.length} 条 ▼` }}
          </button>
        </div>
        <div v-else class="my-empty">
          还没有评价过<br />
          <button class="rv-go" @click="router.push('/query')">完成后去「查订单」评价这一杯 →</button>
        </div>
      </div>

      <p v-if="USE_MOCK" class="demo-note">💡 优惠券与积分为演示功能（mock 数据，存于本地浏览器）</p>
    </div>

    <!-- 连签达成庆祝动效（全屏无事件穿透，自动消失） -->
    <Teleport to="body">
      <Transition name="cele-fade">
        <div v-if="celebrating" class="cele-layer" aria-hidden="true">
          <span
            v-for="(c, i) in confetti"
            :key="i"
            class="confetti"
            :style="c.style"
          >{{ c.emoji }}</span>
          <div class="cele-card">
            <span class="cele-emoji">🎉</span>
            <p class="cele-title">连签达成</p>
            <p class="cele-sub">额外奖励 <b>+{{ bonusAmount }}</b> 积分已入账</p>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.member-page {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
  background: var(--cream);
}

.page-body {
  flex: 1;
  padding: 12px 12px 28px;
}

/* ---------- 会员卡 ---------- */
.vip-card {
  position: relative;
  overflow: hidden;
  border-radius: 18px;
  padding: 18px 18px 14px;
  background: linear-gradient(135deg, #46301e 0%, #6f4b30 55%, #8b5e3c 100%);
  color: #faf6f1;
  box-shadow: 0 12px 28px rgba(70, 48, 30, 0.28);
}

/* 卡面装饰：右上角月牙光晕 */
.vip-card::after {
  content: '';
  position: absolute;
  top: -46px;
  right: -30px;
  width: 140px;
  height: 140px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(240, 200, 140, 0.35), transparent 65%);
  pointer-events: none;
}

.vip-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.vip-logo {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.22);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}

.vip-user {
  flex: 1;
  min-width: 0;
}

.vip-name {
  font-size: 16px;
  font-weight: 800;
  letter-spacing: 1px;
}

.vip-no {
  margin-top: 3px;
  font-size: 10px;
  opacity: 0.75;
  font-family: 'SFMono-Regular', Consolas, monospace;
  letter-spacing: 0.5px;
}

.vip-tier {
  flex-shrink: 0;
  font-size: 10px;
  padding: 4px 10px;
  border-radius: 999px;
  background: linear-gradient(135deg, #f0d9b5, #d9b083);
  color: #5c4632;
  font-weight: 800;
}

.vip-stats {
  display: flex;
  align-items: center;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.16);
}

.vs-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.vs-item b {
  font-size: 16px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.vs-item span {
  font-size: 10px;
  opacity: 0.72;
}

.vs-divider {
  width: 1px;
  height: 22px;
  background: rgba(255, 255, 255, 0.16);
}

/* ---------- 区块 ---------- */
.section {
  margin-top: 16px;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 4px 10px;
}

.section-head h2 {
  font-size: 15px;
  font-weight: 800;
}

.section-tip {
  font-size: 11px;
  color: var(--ink-3);
}

/* ---------- 领券中心 ---------- */
.tpl-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tpl-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-radius: 14px;
  background: var(--card);
  box-shadow: var(--shadow-card);
  border: 1px solid #f1e7d7;
}

.tpl-card.received {
  opacity: 0.62;
}

.tpl-left {
  flex-shrink: 0;
  min-width: 86px;
  text-align: center;
  padding-right: 12px;
  border-right: 1px dashed #e0d2bd;
}

.tpl-amount {
  font-size: 24px;
  font-weight: 800;
  color: #d4552e;
}

.tpl-amount i {
  font-style: normal;
  font-size: 13px;
  margin-right: 1px;
}

.tpl-threshold {
  margin-top: 2px;
  font-size: 10px;
  color: var(--ink-3);
}

.tpl-main {
  flex: 1;
  min-width: 0;
}

.tpl-name {
  font-size: 14px;
  font-weight: 700;
}

.tpl-desc {
  margin-top: 3px;
  font-size: 11px;
  color: var(--ink-3);
}

.tpl-remain {
  margin-top: 2px;
  font-size: 10px;
  color: #c08552;
}

.tpl-btn {
  flex-shrink: 0;
  min-width: 64px;
  height: 32px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #c08552, #b0702e);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.12s, box-shadow 0.12s;
}

.tpl-btn:not(:disabled):active {
  transform: scale(0.95);
}

.tpl-btn:disabled {
  background: #e8ddcc;
  color: #a89a86;
  cursor: not-allowed;
}

/* ---------- 我的优惠券 ---------- */
.my-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.my-coupon {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 14px;
  border-radius: 14px;
  background: var(--card);
  border: 1px solid #f1e7d7;
  box-shadow: var(--shadow-card);
}

.my-coupon.used {
  opacity: 0.55;
  filter: grayscale(0.4);
}

.mc-left {
  flex-shrink: 0;
  min-width: 76px;
  text-align: center;
  padding-right: 12px;
  border-right: 1px dashed #e0d2bd;
}

.mc-amount {
  font-size: 20px;
  font-weight: 800;
  color: #d4552e;
}

.mc-amount i {
  font-style: normal;
  font-size: 12px;
}

.mc-threshold {
  margin-top: 2px;
  font-size: 10px;
  color: var(--ink-3);
}

.mc-main {
  flex: 1;
  min-width: 0;
}

.mc-name {
  font-size: 13px;
  font-weight: 700;
}

.mc-expire {
  margin-top: 3px;
  font-size: 10px;
  color: var(--ink-3);
}

.mc-use {
  flex-shrink: 0;
  height: 30px;
  padding: 0 14px;
  border: 1px solid #b0702e;
  border-radius: 999px;
  background: #fff;
  color: #b0702e;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.mc-use:active {
  background: #fdf3e6;
}

.mc-flag {
  flex-shrink: 0;
  font-size: 11px;
  color: #a89a86;
  font-weight: 700;
}

.my-empty {
  padding: 22px 0;
  text-align: center;
  font-size: 13px;
  color: var(--ink-3);
  background: var(--card);
  border-radius: 14px;
  border: 1px dashed #eadfcc;
}

/* 历史券 */
.history {
  margin-top: 12px;
}

.history-title {
  padding: 0 4px 8px;
  font-size: 12px;
  color: var(--ink-3);
}

/* ---------- 每日签到卡 ---------- */
.checkin-card {
  position: relative;
  overflow: hidden;
  border-radius: 14px;
  padding: 14px;
  background: linear-gradient(135deg, #fffdf8, #fdf3e4);
  border: 1px solid #f3e3c8;
  box-shadow: var(--shadow-card);
}

/* 卡面装饰：左上月牙光晕 */
.checkin-card::before {
  content: '';
  position: absolute;
  top: -30px;
  left: -20px;
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(240, 200, 140, 0.28), transparent 65%);
  pointer-events: none;
}

.ck-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.ck-title {
  font-size: 15px;
  font-weight: 800;
}

.ck-sub {
  margin-top: 3px;
  font-size: 11px;
  color: var(--ink-3);
}

.ck-streak {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 800;
  color: #c0562e;
  padding: 4px 10px;
  border-radius: 999px;
  background: #fdeee2;
  border: 1px solid #f5d9c2;
}

.ck-week {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 6px;
  margin-top: 12px;
}

.ck-day {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 0 7px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #f1e7d7;
  transition: transform 0.15s;
}

.ck-dow {
  font-size: 10px;
  color: var(--ink-3);
}

.ck-mark {
  font-size: 11px;
  font-weight: 800;
  color: #c9b896;
}

.ck-day.future .ck-mark {
  color: #ddd0bc;
}

.ck-day.checked {
  background: linear-gradient(135deg, #e9b26f, #c08552);
  border-color: transparent;
  box-shadow: 0 4px 10px rgba(192, 133, 82, 0.35);
}

.ck-day.checked .ck-mark,
.ck-day.checked .ck-dow {
  color: #fff;
}

.ck-day.today:not(.checked) {
  border: 1.5px dashed #c08552;
  animation: ck-pulse 1.8s ease-in-out infinite;
}

@keyframes ck-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(192, 133, 82, 0.35);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(192, 133, 82, 0.12);
  }
}

.ck-btn {
  margin-top: 12px;
  width: 100%;
  height: 42px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #c08552, #a5623a);
  color: #fff;
  font-size: 14px;
  font-weight: 800;
  letter-spacing: 1px;
  cursor: pointer;
  transition: transform 0.12s, box-shadow 0.2s;
  box-shadow: 0 6px 16px rgba(165, 98, 58, 0.35);
}

.ck-btn:not(:disabled):active {
  transform: scale(0.97);
}

.ck-btn:disabled {
  background: #efe6d6;
  color: #a89a86;
  box-shadow: none;
  cursor: default;
}

/* ---------- 连签奖励进度 ---------- */
.ck-progress {
  margin-top: 12px;
  padding: 10px 12px 8px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.65);
  border: 1px dashed #ecdcc6;
}

.ckp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ckp-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--ink-2);
}

.ckp-tip {
  font-size: 10px;
  color: var(--ink-3);
  text-align: right;
}

.ckp-segs {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 8px;
  padding-right: 22px;
  position: relative;
}

.ckp-seg {
  flex: 1;
  height: 7px;
  border-radius: 999px;
  background: #f0e6d8;
  transition: background 0.3s ease;
}

.ckp-seg.lit {
  background: linear-gradient(90deg, var(--amber-from), var(--amber-to));
  box-shadow: 0 1px 4px rgba(233, 162, 59, 0.35);
}

.ckp-seg.goal:not(.lit) {
  background: repeating-linear-gradient(45deg, #f4e3c8, #f4e3c8 3px, #f9efe0 3px, #f9efe0 6px);
}

.ckp-gift {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-58%);
  font-size: 15px;
  filter: grayscale(1);
  opacity: 0.45;
  transition: all 0.3s ease;
}

.ckp-gift.lit {
  filter: none;
  opacity: 1;
  animation: gift-bounce 0.9s ease infinite alternate;
}

@keyframes gift-bounce {
  from {
    transform: translateY(-58%) scale(1);
  }
  to {
    transform: translateY(-70%) scale(1.12);
  }
}

.ck-demo {
  display: block;
  margin: 8px 0 2px auto;
  padding: 4px 10px;
  font-size: 10px;
  font-weight: 600;
  color: #a05e2b;
  border: 1px dashed #d8a86f;
  border-radius: 999px;
  background: linear-gradient(135deg, #fdf6ec, #faeeda);
}

.ck-demo:active {
  transform: scale(0.96);
}

/* ---------- 连签达成庆祝动效 ---------- */
.cele-layer {
  position: fixed;
  inset: 0;
  z-index: 9999;
  overflow: hidden;
  pointer-events: none;
}

.confetti {
  position: absolute;
  top: -5vh;
  animation-name: confetti-fall;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
}

@keyframes confetti-fall {
  0% {
    transform: translateY(0) rotate(0deg);
    opacity: 1;
  }
  100% {
    transform: translateY(114vh) rotate(520deg);
    opacity: 0.85;
  }
}

.cele-card {
  position: absolute;
  left: 50%;
  top: 40%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 26px 44px 24px;
  border-radius: 24px;
  background: linear-gradient(160deg, #fffdf8, #fff3dd);
  border: 2px solid #e8b568;
  box-shadow:
    0 18px 60px rgba(160, 94, 43, 0.35),
    0 0 0 8px rgba(232, 181, 104, 0.15);
  animation: cele-pop 0.5s cubic-bezier(0.22, 1.4, 0.36, 1) both;
}

.cele-emoji {
  font-size: 42px;
  line-height: 1;
}

.cele-title {
  font-size: 19px;
  font-weight: 800;
  letter-spacing: 2px;
  color: var(--ink);
}

.cele-sub {
  font-size: 12px;
  color: var(--ink-2);
}

.cele-sub b {
  font-size: 15px;
  font-weight: 800;
  color: #d9822b;
}

@keyframes cele-pop {
  from {
    transform: translate(-50%, -50%) scale(0.55);
    opacity: 0;
  }
  to {
    transform: translate(-50%, -50%) scale(1);
    opacity: 1;
  }
}

.cele-fade-enter-active,
.cele-fade-leave-active {
  transition: opacity 0.3s ease;
}

.cele-fade-enter-from,
.cele-fade-leave-to {
  opacity: 0;
}


/* ---------- 积分兑换货架 ---------- */
.shelf-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.shelf-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 14px;
  border-radius: 14px;
  background: var(--card);
  border: 1px solid #f1e7d7;
  box-shadow: var(--shadow-card);
}

.shelf-card.lack {
  opacity: 0.68;
}

.sh-left {
  flex-shrink: 0;
  min-width: 88px;
  text-align: center;
  padding-right: 12px;
  border-right: 1px dashed #e0d2bd;
}

.sh-cost {
  font-size: 18px;
  font-weight: 800;
  color: #8b5e3c;
  white-space: nowrap;
}

.sh-cost i {
  font-style: normal;
  font-size: 22px;
  margin-right: 3px;
  background: linear-gradient(135deg, #c08552, #8b5e3c);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.sh-worth {
  margin-top: 2px;
  font-size: 10px;
  color: var(--ink-3);
}

.sh-main {
  flex: 1;
  min-width: 0;
}

.sh-name {
  font-size: 14px;
  font-weight: 700;
}

.sh-desc {
  margin-top: 3px;
  font-size: 11px;
  color: var(--ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sh-remain {
  margin-top: 2px;
  font-size: 10px;
  color: #c08552;
}

.sh-btn {
  flex-shrink: 0;
  min-width: 68px;
  height: 32px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #8b5e3c, #6f4b30);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.12s;
}

.sh-btn:not(:disabled):active {
  transform: scale(0.95);
}

.sh-btn:disabled {
  background: #e8ddcc;
  color: #a89a86;
  cursor: not-allowed;
}

/* ---------- 积分明细时间线 ---------- */
.pt-timeline {
  background: var(--card);
  border-radius: 14px;
  border: 1px solid #f1e7d7;
  box-shadow: var(--shadow-card);
  padding: 4px 14px;
}

.pt-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px dashed #f3ede3;
  position: relative;
}

.pt-row:last-of-type {
  border-bottom: none;
}

.pt-icon {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #fdf6ec;
  border: 1px solid #f3e3c8;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
}

.pt-main {
  flex: 1;
  min-width: 0;
}

.pt-title {
  font-size: 13px;
  font-weight: 700;
}

.pt-sub {
  margin-top: 2px;
  font-size: 11px;
  color: var(--ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pt-right {
  flex-shrink: 0;
  text-align: right;
}

.pt-points {
  font-size: 15px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.pt-points.plus {
  color: #5f8d6b;
}

.pt-points.minus {
  color: #d4552e;
}

.pt-time {
  margin-top: 2px;
  font-size: 10px;
  color: var(--ink-3);
}

.pt-more {
  width: 100%;
  padding: 10px 0;
  border: none;
  background: transparent;
  color: #b0702e;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  border-top: 1px dashed #f3ede3;
}

.pt-more:active {
  background: #fdfaf6;
}

.demo-note {
  margin-top: 18px;
  text-align: center;
  font-size: 11px;
  color: var(--ink-3);
}

/* ---------- 我的评价 ---------- */
.rv-list {
  margin-top: 6px;
}

.rv-item {
  padding: 12px 0;
  border-bottom: 1px dashed #f3ede3;
}

.rv-item:last-of-type {
  border-bottom: none;
}

.rv-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rv-code {
  padding: 2px 8px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 1px;
  color: var(--caramel-deep);
  border-radius: 6px;
  background: #f9ecdd;
}

.rv-stars {
  font-size: 11px;
  color: #e9a23b;
  letter-spacing: 1px;
}

.rv-time {
  margin-left: auto;
  font-size: 10px;
  color: var(--ink-3);
}

.rv-content {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-2);
}

.rv-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.rv-tag {
  padding: 2px 8px;
  font-size: 10px;
  color: var(--ink-3);
  border-radius: 999px;
  background: var(--cream-deep);
}

.rv-reply {
  margin: 8px 0 0;
  padding: 8px 10px;
  font-size: 11px;
  line-height: 1.6;
  color: #8a7f6f;
  border-radius: 8px;
  background: #faf6ef;
}

.rv-reply b {
  color: var(--caramel-deep);
}

.rv-more {
  width: 100%;
  padding: 10px 0;
  border: none;
  background: transparent;
  color: #b0702e;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  border-top: 1px dashed #f3ede3;
}

.rv-more:active {
  background: #fdfaf6;
}

.rv-go {
  margin-top: 10px;
  font-size: 11px;
  font-weight: 600;
  color: #b0702e;
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
