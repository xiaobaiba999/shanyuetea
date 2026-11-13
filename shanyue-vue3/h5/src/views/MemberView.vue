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

/** 明细行图标：衬线单字（签到 / 消费 / 兑换），印章风 */
const TYPE_ICON: Record<number, string> = { 1: '签', 2: '茶', 3: '兑' }

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
  if (pointSummary.value?.checkedToday) return '今日已签到'
  if (checkingIn.value) return '签到中…'
  const s = pointSummary.value?.streak ?? 0
  if ((s + 1) % STREAK_TARGET === 0) return `立即签到 · 达成连签 +${7 + 20} 积分`
  return '立即签到 +7 积分'
})

/* ---------------- 连签达成庆祝动效（衬线字彩带 + 中央印章） ---------------- */
const celebrating = ref(false)
const bonusAmount = ref(0)
const confetti = ref<{ char: string; style: Record<string, string> }[]>([])
const CONFETTI_CHARS = ['山', '月', '茶', '金', '客', '缘', '盏', '香']
let celeTimer: number | null = null

function triggerCelebration(amount: number) {
  bonusAmount.value = amount
  confetti.value = Array.from({ length: 26 }, (_, i) => ({
    char: CONFETTI_CHARS[i % CONFETTI_CHARS.length],
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
          <div class="seal vip-logo-seal" aria-hidden="true"><i>山</i><i>月</i></div>
          <div class="vip-user">
            <p class="vip-name">山月茶友</p>
            <p class="vip-no">{{ summary?.memberNo || 'SHY-2026-0001' }}</p>
          </div>
          <span class="vip-tier">茶咖会员</span>
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
              <h2 class="ck-title">每日签到</h2>
              <p class="ck-sub">每日 +7 积分，积分可兑优惠券</p>
            </div>
            <span v-if="(pointSummary?.streak ?? 0) > 0" class="ck-streak">连续 {{ pointSummary?.streak }} 天</span>
          </div>
          <div class="ck-week">
            <div
              v-for="d in weekDays"
              :key="d.ymd"
              class="ck-day"
              :class="{ checked: d.checked, today: d.isToday, future: d.isFuture }"
            >
              <span class="ck-dow">{{ d.label }}</span>
              <span class="ck-mark">{{ d.checked ? '签' : d.isFuture ? '·' : '＋7' }}</span>
            </div>
          </div>

          <!-- 连签奖励进度：每连签 7 天额外 +20 积分 -->
          <div class="ck-progress">
            <div class="ckp-head">
              <span class="ckp-label">
                {{ bonusProgress.done ? '连签奖励已达成' : `已连签 ${pointSummary?.streak ?? 0} 天` }}
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
              <span class="ckp-gift" :class="{ lit: bonusProgress.done }" aria-hidden="true">
                <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="8" width="18" height="4" rx="1" />
                  <path d="M5 12v8h14v-8" />
                  <path d="M12 8v12" />
                  <path d="M12 8c-1.5 0-4-.7-4-2.5S10.5 3 12 5c1.5-2 4-1.7 4 0S13.5 8 12 8z" />
                </svg>
              </span>
            </div>
            <button
              v-if="USE_MOCK && !pointSummary?.checkedToday"
              class="ck-demo"
              :disabled="seeding"
              @click="handleSeedStreak"
            >
              {{ seeding ? '注入中…' : `演示：补签 ${STREAK_TARGET - 1} 天` }}
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
          <h2>积分兑换</h2>
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
          <h2>领券中心</h2>
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
          <h2>我的优惠券</h2>
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
        <div v-else class="my-empty">还没有可用优惠券，上方领一张吧</div>

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
          <h2>积分明细</h2>
          <span class="section-tip">共 {{ pointRecords.length }} 条</span>
        </div>
        <div v-if="visibleRecords.length" class="pt-timeline">
          <div v-for="r in visibleRecords" :key="r.id" class="pt-row">
            <span class="pt-icon">{{ TYPE_ICON[r.type] }}</span>
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
        <div v-else class="my-empty">还没有积分记录，签个到或点一杯就有了</div>
      </div>

      <!-- 我的评价 -->
      <div class="section">
        <div class="section-head">
          <h2>我的评价</h2>
          <span class="section-tip">共 {{ myReviews.length }} 条</span>
        </div>
        <div v-if="visibleReviews.length" class="rv-list">
          <div v-for="r in visibleReviews" :key="r.id" class="rv-item">
            <div class="rv-head">
              <span class="rv-code">{{ r.pickUpCode }}</span>
              <span class="rv-stars" :aria-label="`${r.score} 星`">
                <svg v-for="i in 5" :key="i" class="st" :class="{ on: i <= r.score }" viewBox="0 0 24 24" width="11" height="11" fill="currentColor" aria-hidden="true"><path d="M12 2.5l2.7 5.8 6.3.7-4.7 4.3 1.3 6.2-5.6-3.2-5.6 3.2 1.3-6.2-4.7-4.3 6.3-.7L12 2.5z" /></svg>
              </span>
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

      <p v-if="USE_MOCK" class="demo-note">优惠券与积分为演示功能（mock 数据，存于本地浏览器）</p>
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
          >{{ c.char }}</span>
          <div class="cele-card">
            <span class="cele-seal" aria-hidden="true">签</span>
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
  background: var(--paper);
}

.page-body {
  flex: 1;
  padding: 12px 12px 28px;
}

/* ---------- 会员卡：墨绿卡面 + 茶金描边印章 ---------- */
.vip-card {
  position: relative;
  overflow: hidden;
  border-radius: 18px;
  padding: 18px 18px 14px;
  background: linear-gradient(135deg, var(--pine-deep) 0%, var(--pine) 55%, #3a6140 100%);
  color: #f6f3ec;
  box-shadow: 0 12px 28px rgba(36, 62, 40, 0.3);
}

/* 卡面装饰：右上角茶金光晕 */
.vip-card::after {
  content: '';
  position: absolute;
  top: -46px;
  right: -30px;
  width: 140px;
  height: 140px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(200, 155, 90, 0.3), transparent 65%);
  pointer-events: none;
}

.vip-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 印章 logo（品牌位，墨绿卡面上用宣纸底 + 茶金描边） */
.vip-logo-seal {
  width: 42px;
  height: 42px;
  background: rgba(246, 243, 236, 0.94);
  color: var(--pine);
  box-shadow: 0 3px 10px rgba(36, 62, 40, 0.35);
}

.vip-logo-seal i {
  font-size: 13px;
  line-height: 1.12;
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

/* 会员等级徽标：茶金实底 */
.vip-tier {
  flex-shrink: 0;
  font-size: 10px;
  padding: 4px 10px;
  border-radius: 999px;
  background: linear-gradient(135deg, #dcb87e, var(--gold));
  color: var(--ink);
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
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 1px;
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
  border: 1px solid var(--card-line);
}

.tpl-card.received {
  opacity: 0.62;
}

.tpl-left {
  flex-shrink: 0;
  min-width: 86px;
  text-align: center;
  padding-right: 12px;
  border-right: 1px dashed var(--card-line);
}

.tpl-amount {
  font-size: 24px;
  font-weight: 800;
  font-family: var(--font-serif);
  font-variant-numeric: tabular-nums;
  color: var(--terracotta);
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
  color: var(--ink-3);
}

.tpl-btn {
  flex-shrink: 0;
  min-width: 64px;
  height: 32px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(160deg, #3a6140, var(--pine) 55%, var(--pine-deep));
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 3px 10px rgba(47, 82, 51, 0.26);
  transition: transform 0.12s, box-shadow 0.12s;
}

.tpl-btn:not(:disabled):active {
  transform: scale(0.95);
}

.tpl-btn:disabled {
  background: var(--paper-deep);
  color: var(--ink-3);
  box-shadow: none;
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
  border: 1px solid var(--card-line);
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
  border-right: 1px dashed var(--card-line);
}

.mc-amount {
  font-size: 20px;
  font-weight: 800;
  font-family: var(--font-serif);
  font-variant-numeric: tabular-nums;
  color: var(--terracotta);
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
  border: 1px solid rgba(47, 82, 51, 0.4);
  border-radius: 999px;
  background: var(--card);
  color: var(--pine);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.mc-use:active {
  background: var(--pine-mist);
}

.mc-flag {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--ink-3);
  font-weight: 700;
}

.my-empty {
  padding: 22px 0;
  text-align: center;
  font-size: 13px;
  color: var(--ink-3);
  background: var(--card);
  border-radius: 14px;
  border: 1px dashed var(--card-line);
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

/* ---------- 每日签到卡：宣纸渐变 + 茶金光晕 ---------- */
.checkin-card {
  position: relative;
  overflow: hidden;
  border-radius: 14px;
  padding: 14px;
  background: linear-gradient(135deg, var(--card), var(--paper));
  border: 1px solid var(--card-line);
  box-shadow: var(--shadow-card);
}

/* 卡面装饰：左上茶金光晕 */
.checkin-card::before {
  content: '';
  position: absolute;
  top: -30px;
  left: -20px;
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(200, 155, 90, 0.22), transparent 65%);
  pointer-events: none;
}

.ck-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.ck-title {
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 1px;
}

.ck-sub {
  margin-top: 3px;
  font-size: 11px;
  color: var(--ink-3);
}

/* 连签天数徽标：暖陶胶囊 */
.ck-streak {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 800;
  color: var(--terracotta);
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(199, 84, 47, 0.08);
  border: 1px solid rgba(199, 84, 47, 0.22);
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
  background: var(--card);
  border: 1px solid var(--card-line);
  transition: transform 0.15s;
}

.ck-dow {
  font-size: 10px;
  color: var(--ink-3);
}

.ck-mark {
  font-family: var(--font-serif);
  font-size: 11px;
  font-weight: 700;
  color: var(--ink-3);
}

.ck-day.future .ck-mark {
  color: var(--card-line);
}

.ck-day.checked {
  background: linear-gradient(160deg, #3a6140, var(--pine) 55%, var(--pine-deep));
  border-color: transparent;
  box-shadow: 0 4px 10px rgba(47, 82, 51, 0.3);
}

.ck-day.checked .ck-mark,
.ck-day.checked .ck-dow {
  color: #fff;
}

.ck-day.today:not(.checked) {
  border: 1.5px dashed var(--gold);
  animation: ck-pulse 1.8s ease-in-out infinite;
}

@keyframes ck-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(200, 155, 90, 0.35);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(200, 155, 90, 0.12);
  }
}

/* 签到主按钮：墨绿光泽款（与全局 .btn-primary 同族） */
.ck-btn {
  position: relative;
  overflow: hidden;
  margin-top: 12px;
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(160deg, #3a6140, var(--pine) 55%, var(--pine-deep));
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 1px;
  cursor: pointer;
  transition: transform 0.12s, box-shadow 0.2s;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 6px 16px rgba(47, 82, 51, 0.3);
}

.ck-btn:not(:disabled):active {
  transform: scale(0.97);
}

.ck-btn:disabled {
  background: var(--paper-deep);
  color: var(--ink-3);
  box-shadow: none;
  cursor: default;
}

/* ---------- 连签奖励进度 ---------- */
.ck-progress {
  margin-top: 12px;
  padding: 10px 12px 8px;
  border-radius: 12px;
  background: rgba(246, 243, 236, 0.6);
  border: 1px dashed rgba(200, 155, 90, 0.4);
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
  background: rgba(43, 38, 32, 0.08);
  transition: background 0.3s ease;
}

.ckp-seg.lit {
  background: linear-gradient(90deg, #dcb87e, var(--gold));
  box-shadow: 0 1px 4px rgba(200, 155, 90, 0.4);
}

.ckp-seg.goal:not(.lit) {
  background: repeating-linear-gradient(45deg, rgba(200, 155, 90, 0.2), rgba(200, 155, 90, 0.2) 3px, rgba(200, 155, 90, 0.08) 3px, rgba(200, 155, 90, 0.08) 6px);
}

/* 礼物标记（SVG 描边）：未点亮墨灰，点亮茶金 */
.ckp-gift {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-58%);
  display: grid;
  place-items: center;
  color: var(--ink-3);
  opacity: 0.55;
  transition: all 0.3s ease;
}

.ckp-gift.lit {
  color: var(--gold);
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
  color: #9a7135;
  border: 1px dashed rgba(200, 155, 90, 0.55);
  border-radius: 999px;
  background: rgba(200, 155, 90, 0.1);
}

.ck-demo:active {
  transform: scale(0.96);
}

/* ---------- 连签达成庆祝动效：衬线字彩带（茶金/墨绿） + 印章 ---------- */
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
  font-family: var(--font-serif);
  font-weight: 700;
  color: var(--gold);
  animation-name: confetti-fall;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
}

.confetti:nth-child(even) {
  color: var(--pine);
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
  background: var(--card);
  border: 2px solid var(--gold);
  box-shadow:
    0 18px 60px rgba(36, 62, 40, 0.28),
    0 0 0 8px rgba(200, 155, 90, 0.15);
  animation: cele-pop 0.5s cubic-bezier(0.22, 1.4, 0.36, 1) both;
}

/* 中央印章：茶金描边 + 墨绿衬线「签」 */
.cele-seal {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border: 2px solid var(--gold);
  border-radius: 50%;
  background: var(--paper);
  font-family: var(--font-serif);
  font-size: 22px;
  font-weight: 700;
  color: var(--pine);
  transform: rotate(-4deg);
}

.cele-title {
  font-family: var(--font-serif);
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 3px;
  color: var(--ink);
}

.cele-sub {
  font-size: 12px;
  color: var(--ink-2);
}

.cele-sub b {
  font-size: 15px;
  font-weight: 800;
  color: var(--terracotta);
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
  border: 1px solid var(--card-line);
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
  border-right: 1px dashed var(--card-line);
}

.sh-cost {
  font-size: 18px;
  font-weight: 800;
  font-family: var(--font-serif);
  font-variant-numeric: tabular-nums;
  color: var(--pine);
  white-space: nowrap;
}

.sh-cost i {
  font-style: normal;
  font-size: 22px;
  margin-right: 3px;
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
  color: var(--ink-3);
}

.sh-btn {
  flex-shrink: 0;
  min-width: 68px;
  height: 32px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(160deg, #3a6140, var(--pine) 55%, var(--pine-deep));
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 3px 10px rgba(47, 82, 51, 0.26);
  transition: transform 0.12s;
}

.sh-btn:not(:disabled):active {
  transform: scale(0.95);
}

.sh-btn:disabled {
  background: var(--paper-deep);
  color: var(--ink-3);
  box-shadow: none;
  cursor: not-allowed;
}

/* ---------- 积分明细时间线 ---------- */
.pt-timeline {
  background: var(--card);
  border-radius: 14px;
  border: 1px solid var(--card-line);
  box-shadow: var(--shadow-card);
  padding: 4px 14px;
}

.pt-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px dashed var(--card-line);
  position: relative;
}

.pt-row:last-of-type {
  border-bottom: none;
}

/* 明细行图标：衬线单字印章风（签 / 茶 / 兑） */
.pt-icon {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--pine-mist);
  border: 1px solid rgba(47, 82, 51, 0.18);
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
  color: var(--pine);
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
  color: var(--success);
}

.pt-points.minus {
  color: var(--terracotta);
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
  color: var(--pine);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  border-top: 1px dashed var(--card-line);
}

.pt-more:active {
  background: var(--pine-mist);
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
  border-bottom: 1px dashed var(--card-line);
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
  color: var(--pine);
  border-radius: 6px;
  background: var(--pine-mist);
}

/* 星级：SVG 星点，未选墨灰 / 选中茶金 */
.rv-stars {
  display: inline-flex;
  gap: 1px;
}

.rv-stars .st {
  color: rgba(43, 38, 32, 0.15);
}

.rv-stars .st.on {
  color: var(--gold);
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
  background: var(--paper-deep);
}

.rv-reply {
  margin: 8px 0 0;
  padding: 8px 10px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--ink-2);
  border-radius: 8px;
  background: var(--paper);
  border-left: 2px solid rgba(200, 155, 90, 0.5);
}

.rv-reply b {
  color: var(--pine);
}

.rv-more {
  width: 100%;
  padding: 10px 0;
  border: none;
  background: transparent;
  color: var(--pine);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  border-top: 1px dashed var(--card-line);
}

.rv-more:active {
  background: var(--pine-mist);
}

.rv-go {
  margin-top: 10px;
  font-size: 11px;
  font-weight: 600;
  color: var(--pine);
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
