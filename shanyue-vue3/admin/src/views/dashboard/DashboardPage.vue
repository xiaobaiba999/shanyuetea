<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <div>
        <h2 class="page-title">今日数据</h2>
        <p class="page-sub">{{ today }} · 数据每完成一笔订单即时更新（口径：营业额 = 今日已完成订单金额）</p>
      </div>
      <el-tag effect="plain" type="warning" round>实时经营看板</el-tag>
    </div>

    <!-- 统计卡 -->
    <div class="stat-grid">
      <div class="card stat-card">
        <div class="stat-icon si-1"><el-icon :size="24"><Money /></el-icon></div>
        <div class="stat-meta">
          <div class="stat-label">今日营业额（元）</div>
          <div class="stat-value">¥ {{ fmtMoney(todayData?.turnover) }}</div>
        </div>
      </div>
      <div class="card stat-card">
        <div class="stat-icon si-2"><el-icon :size="24"><Tickets /></el-icon></div>
        <div class="stat-meta">
          <div class="stat-label">今日订单数</div>
          <div class="stat-value">{{ todayData?.orderCount ?? '-' }}</div>
        </div>
      </div>
      <div class="card stat-card">
        <div class="stat-icon si-3"><el-icon :size="24"><CircleCheck /></el-icon></div>
        <div class="stat-meta">
          <div class="stat-label">已完成订单</div>
          <div class="stat-value">{{ todayData?.completedCount ?? '-' }}</div>
        </div>
      </div>
      <div class="card stat-card" :class="{ alert: (todayData?.toBeConfirmedCount ?? 0) > 0 }">
        <div class="stat-icon si-4"><el-icon :size="24"><Bell /></el-icon></div>
        <div class="stat-meta">
          <div class="stat-label">待接单</div>
          <div class="stat-value">{{ todayData?.toBeConfirmedCount ?? '-' }}</div>
        </div>
      </div>
    </div>

    <!-- 待办提醒 -->
    <div class="card card-pad todo-card">
      <div class="card-title-bar todo-head">
        <span class="card-title">待办提醒</span>
        <el-tag v-if="!hasTodos" size="small" type="success" effect="plain" round>井然有序</el-tag>
        <el-tag v-else size="small" type="warning" effect="plain" round>{{ todoCount }} 项待处理</el-tag>
      </div>
      <div class="todo-list">
        <button v-if="todoPending > 0" class="todo-item warn" @click="router.push({ path: '/order', query: { status: '2' } })">
          <span class="todo-icon"><svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm0 2a8 8 0 1 1 0 16 8 8 0 0 1 0-16Z"/><path d="M11.25 6.5h1.5v6.4l4.4 2.54-.75 1.3-5.15-2.97Z"/></svg></span>
          <div class="todo-text">
            <div class="tt">{{ todoPending }} 笔订单待接单</div>
            <div class="ts">{{ oldestPending ? `最早一单 ${oldestPending.pickupNumber || '—'} 已等待 ${oldestWaitMin} 分钟，尽快处理` : '点击前往订单管理处理' }}</div>
          </div>
          <span class="todo-badge">{{ todoPending }}</span>
        </button>
        <button v-if="todoCoupons.length > 0" class="todo-item hot" @click="router.push('/coupon')">
          <span class="todo-icon"><svg viewBox="0 0 24 24" fill="currentColor" fill-rule="evenodd" aria-hidden="true"><path d="M4 4h16a2 2 0 0 1 2 2v3.3a2.7 2.7 0 0 0 0 5.4V18a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-3.3a2.7 2.7 0 0 0 0-5.4V6a2 2 0 0 1 2-2Zm7.2 3.6h1.6v1.6h-1.6Zm0 3.4h1.6v1.6h-1.6Zm0 3.4h1.6v1.6h-1.6Z"/></svg></span>
          <div class="todo-text">
            <div class="tt">{{ todoCoupons[0].name }} 库存告急</div>
            <div class="ts">
              剩余 {{ todoCoupons[0].remainCount }}/{{ todoCoupons[0].totalCount }} 张（低于 20%）{{ todoCoupons.length > 1 ? `，另有 ${todoCoupons.length - 1} 张券告急` : '，及时补量或下架' }}
            </div>
          </div>
          <span class="todo-badge">{{ todoCoupons.length }}</span>
        </button>
        <button v-if="todoOffSale > 0" class="todo-item" @click="router.push('/drink')">
          <span class="todo-icon"><svg viewBox="0 0 24 24" fill="currentColor" fill-rule="evenodd" aria-hidden="true"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm0 2a8 8 0 1 1 0 16 8 8 0 0 1 0-16Z"/><path d="M7.1 4.9 4.9 7.1l12 12 2.2-2.2Z"/></svg></span>
          <div class="todo-text">
            <div class="tt">{{ todoOffSale }} 款饮品停售中</div>
            <div class="ts">检查原料库存，及时恢复上架</div>
          </div>
          <span class="todo-badge">{{ todoOffSale }}</span>
        </button>
        <button v-if="shopStore.status === 0" class="todo-item dim" @click="router.push('/shop')">
          <span class="todo-icon"><svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M13.3 2.6a9.4 9.4 0 1 0 8.1 8.1 7.4 7.4 0 0 1-8.1-8.1Z"/></svg></span>
          <div class="todo-text">
            <div class="tt">店铺当前为打烊状态</div>
            <div class="ts">C 端暂停下单，点击前往店铺设置恢复营业</div>
          </div>
        </button>
        <div v-if="!hasTodos" class="todo-empty">暂无待办事项，一切井然有序</div>
      </div>
    </div>

    <!-- 快捷操作 + 趋势图 -->
    <div class="bottom-grid">
      <div class="card card-pad quick-card">
        <div class="card-title-bar"><span class="card-title">快捷操作</span></div>
        <div class="quick-list">
          <button class="quick-item" @click="router.push({ path: '/order', query: { status: '2' } })">
            <el-icon :size="20"><BellFilled /></el-icon>
            <div>
              <div class="qi-title">去接单</div>
              <div class="qi-sub">处理待接单订单</div>
            </div>
            <span v-if="(todayData?.toBeConfirmedCount ?? 0) > 0" class="qi-badge">{{ todayData?.toBeConfirmedCount }}</span>
          </button>
          <button class="quick-item" @click="router.push('/drink/edit')">
            <el-icon :size="20"><CoffeeCup /></el-icon>
            <div>
              <div class="qi-title">新增饮品</div>
              <div class="qi-sub">配置规格与价格</div>
            </div>
          </button>
          <button class="quick-item" @click="router.push('/order')">
            <el-icon :size="20"><Tickets /></el-icon>
            <div>
              <div class="qi-title">查看订单</div>
              <div class="qi-sub">全部订单与详情</div>
            </div>
          </button>
          <button class="quick-item" @click="router.push('/shop')">
            <el-icon :size="20"><Shop /></el-icon>
            <div>
              <div class="qi-title">店铺设置</div>
              <div class="qi-sub">营业状态与公告</div>
            </div>
          </button>
        </div>
      </div>

      <div class="card card-pad chart-card">
        <div class="card-title-bar chart-head">
          <span class="card-title">营业额 · 近 7 日趋势</span>
          <span class="chart-unit">单位：元</span>
        </div>
        <div ref="chartRef" class="chart" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { Money, Tickets, CircleCheck, Bell, BellFilled, CoffeeCup, Shop } from '@element-plus/icons-vue'
import { getTurnoverStatistics } from '@/api/report'
import { getBusinessData, getOverviewOrders } from '@/api/workspace'
import { getDrinkPage } from '@/api/drink'
import { searchOrders } from '@/api/order'
import { getCouponPage } from '@/api/coupon'
import { useShopStore } from '@/stores/shop'
import { fmtMoney, ymd } from '@/utils/format'
import type { CouponTemplate, OrderVO, TurnoverReportVO } from '@/types'

const router = useRouter()
const shopStore = useShopStore()
const loading = ref(false)
const today = ymd(new Date())

/** 今日统计卡（由 workspace 两接口拼装：businessData + overviewOrders） */
interface TodayStat {
  turnover: number
  orderCount: number
  completedCount: number
  toBeConfirmedCount: number
}
const todayData = ref<TodayStat | null>(null)

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

/** 近 7 日区间（与营业额趋势接口契约一致：begin/end yyyy-MM-dd） */
function shiftDays(n: number): string {
  return ymd(new Date(Date.now() - n * 24 * 3600 * 1000))
}

async function loadAll() {
  loading.value = true
  try {
    // workspace 经营数据 + 订单概览（四张统计卡）+ 近 7 日趋势（今日柱与统计卡互相印证）
    const [biz, overview, trend] = await Promise.all([
      getBusinessData(),
      getOverviewOrders(),
      getTurnoverStatistics(shiftDays(6), ymd(new Date())),
    ])
    todayData.value = {
      turnover: biz.turnover,
      orderCount: overview.allOrders,
      completedCount: overview.completedOrders,
      toBeConfirmedCount: overview.waitingOrders,
    }
    await nextTick()
    if (chart) {
      chart.dispose()
      chart = null
    }
    renderChart(trend)
    await loadTodos()
  } catch {
    /* 忽略：拦截器已提示 */
  } finally {
    loading.value = false
  }
}

/* ---------------- 待办提醒 ---------------- */
const todoOffSale = ref(0)
const oldestPending = ref<OrderVO | null>(null)
/** 领券告急：发放中且剩余 < 20% 的券（按告急程度升序） */
const todoCoupons = ref<CouponTemplate[]>([])

const todoPending = computed(() => todayData.value?.toBeConfirmedCount ?? 0)
const hasTodos = computed(
  () => todoPending.value > 0 || todoOffSale.value > 0 || todoCoupons.value.length > 0 || shopStore.status === 0,
)
const todoCount = computed(
  () => todoPending.value + todoOffSale.value + todoCoupons.value.length + (shopStore.status === 0 ? 1 : 0),
)

/** 最早待接单订单已等待分钟数（orderTime: yyyy-MM-dd HH:mm:ss） */
const oldestWaitMin = computed(() => {
  if (!oldestPending.value) return 0
  const t = new Date(oldestPending.value.orderTime.replace(/-/g, '/')).getTime()
  return Math.max(0, Math.round((Date.now() - t) / 60000))
})

async function loadTodos() {
  // 停售饮品数（演示数据一次拉 100 条足够）
  const page = await getDrinkPage({ page: 1, pageSize: 100 }).catch(() => null)
  todoOffSale.value = page ? page.records.filter((d) => d.status === 0).length : 0
  // 最早一笔待接单（status=2；接口按下单时间倒序，这里取回一批后按升序选最早）
  const pending = await searchOrders({ status: 2, page: 1, pageSize: 50 }).catch(() => null)
  oldestPending.value =
    pending ? ([...pending.records].sort((a, b) => (a.orderTime < b.orderTime ? -1 : 1))[0] ?? null) : null
  // 领券告急：发放中且剩余 < 20%（剩余占比升序，最告急的在前）
  const coupons = await getCouponPage({ page: 1, pageSize: 100 }).catch(() => null)
  todoCoupons.value = coupons
    ? coupons.records
        .filter((c) => c.status === 1 && c.totalCount > 0 && c.remainCount / c.totalCount < 0.2)
        .sort((a, b) => a.remainCount / a.totalCount - b.remainCount / b.totalCount)
    : []
  shopStore.fetchStatus()
}

/** 来单模拟 / 接单完成 → 统计卡与趋势图实时联动（mock 演示闭环；真实后端换 WebSocket 推送同样适用） */
function onOrdersChanged() {
  loadAll()
}

onMounted(async () => {
  await loadAll()
  window.addEventListener('resize', resizeChart)
  window.addEventListener('shanyue:orders-changed', onOrdersChanged)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  window.removeEventListener('shanyue:orders-changed', onOrdersChanged)
  chart?.dispose()
  chart = null
})

function resizeChart() {
  chart?.resize()
}

function renderChart(trend: TurnoverReportVO) {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  // v2.0 契约：列表字段为逗号分隔字符串，页面负责 split 解析
  const values = trend.turnoverList.split(',').map(Number)
  // X 轴用短格式 MM-dd（接口返回 yyyy-MM-dd，与后端一致）
  const labels = trend.dateList.split(',').map((d) => d.slice(5))
  chart.setOption({
    grid: { left: 8, right: 14, top: 34, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#fff',
      borderColor: '#E8E2D4',
      textStyle: { color: '#2B2620' },
      formatter: (params: unknown) => {
        const p = (params as Array<{ name: string; value: number }>)[0]
        return `${p.name}<br/>营业额：¥ ${fmtMoney(p.value)}`
      },
    },
    xAxis: {
      type: 'category',
      data: labels,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#E8E2D4' } },
      axisLabel: { color: '#6B6155' },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#E8E2D4' } },
      axisLabel: { color: '#6B6155', formatter: (v: number) => `¥${Math.round(v / 100)}` },
    },
    series: [
      {
        name: '营业额',
        type: 'bar',
        barWidth: 30,
        data: values.map((v, i) => ({
          value: v,
          itemStyle: {
            // 主题色墨绿，今日柱用茶金高亮
            color: i === values.length - 1 ? '#C89B5A' : '#2F5233',
            borderRadius: [6, 6, 0, 0],
          },
        })),
        label: {
          show: true,
          position: 'top',
          color: '#6B6155',
          formatter: (p: { value: number }) => `${(p.value / 100).toFixed(0)}`,
        },
      },
    ],
  })
}
</script>

<style scoped>
/* 统计卡 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 22px 24px;
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(43, 38, 32, 0.1);
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

/* 图标位双色渐变：暖陶 / 墨绿 / 成绿 / 茶金 */
.si-1 { background: linear-gradient(135deg, var(--terracotta), var(--danger)); }
.si-2 { background: linear-gradient(135deg, var(--pine), var(--pine-deep)); }
.si-3 { background: linear-gradient(135deg, var(--el-color-success-light-3), var(--success)); }
.si-4 { background: linear-gradient(135deg, var(--gold), var(--el-color-warning-dark-2)); }

.stat-card.alert .stat-value {
  color: var(--danger);
}

.stat-label {
  font-size: 13px;
  color: var(--ink-2);
}

.stat-value {
  margin-top: 6px;
  font-family: var(--font-serif);
  font-size: 26px;
  font-weight: 700;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.5px;
}

/* 底部两栏 */
.bottom-grid {
  margin-top: 16px;
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 16px;
  align-items: stretch;
}

.quick-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--card-line);
  border-radius: 12px;
  background: var(--paper);
  cursor: pointer;
  text-align: left;
  color: var(--ink);
  transition: all 0.2s;
  position: relative;
}

.quick-item:hover {
  border-color: var(--card-line-hover);
  background: var(--pine-mist);
  transform: translateX(2px);
}

.quick-item .el-icon {
  color: var(--pine);
}

.qi-title {
  font-size: 14px;
  font-weight: 600;
}

.qi-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #8c7a6b;
}

.qi-badge {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--danger);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart-card {
  display: flex;
  flex-direction: column;
}

.chart-head {
  justify-content: space-between;
}

.chart-unit {
  font-size: 12px;
  color: #b3a08e;
}

.chart {
  flex: 1;
  min-height: 320px;
}

/* ---------- 待办提醒 ---------- */
.todo-card {
  margin-bottom: 16px;
}

.todo-head {
  margin-bottom: 10px;
}

.todo-head .el-tag {
  margin-left: auto;
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--card-line);
  border-radius: 12px;
  background: var(--paper);
  cursor: pointer;
  text-align: left;
  transition: all 0.18s;
}

.todo-item:hover {
  border-color: var(--card-line-hover);
  background: var(--pine-mist);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(47, 82, 51, 0.1);
}

.todo-item.warn {
  border-color: var(--el-color-warning-light-7);
  background: linear-gradient(135deg, var(--el-color-warning-light-9), var(--paper));
}

.todo-item.hot {
  border-color: var(--el-color-warning-light-5);
  background: linear-gradient(135deg, var(--el-color-warning-light-8), var(--paper));
}

.todo-item.dim {
  opacity: 0.82;
}

.todo-icon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid var(--card-line);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--ink-3);
  font-size: 17px;
}

.todo-icon svg {
  width: 17px;
  height: 17px;
  display: block;
}

.todo-item.warn .todo-icon {
  color: var(--danger);
}

.todo-item.hot .todo-icon {
  color: var(--gold);
}

.todo-text {
  flex: 1;
  min-width: 0;
}

.tt {
  font-size: 13px;
  font-weight: 700;
  color: var(--ink);
}

.ts {
  margin-top: 2px;
  font-size: 11px;
  color: #8c7a6b;
}

.todo-badge {
  flex-shrink: 0;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 11px;
  background: var(--danger);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.todo-item:not(.warn) .todo-badge {
  background: var(--el-color-warning-dark-2);
}

.todo-empty {
  padding: 14px 0 6px;
  text-align: center;
  font-size: 13px;
  color: var(--ink-3);
}

/* ---------- 门店口碑 ---------- */
.rep-card {
  display: block;
  width: 100%;
  margin-bottom: 14px;
  text-align: left;
  cursor: pointer;
  transition: box-shadow 0.2s ease, transform 0.15s ease;
}

.rep-card:hover {
  box-shadow: 0 4px 16px rgba(43, 38, 32, 0.1);
  transform: translateY(-1px);
}

.rep-head {
  margin-bottom: 10px;
}

.rep-go {
  margin-left: auto;
  font-size: 12px;
  font-weight: 600;
  color: var(--pine);
}

.rep-list {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 2px 4px 4px;
}

.rep-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.rep-num {
  font-family: var(--font-serif);
  font-size: 22px;
  font-weight: 700;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
}

.rep-num.amber {
  color: var(--gold);
}

.rep-num.green {
  color: var(--success);
}

.rep-num.danger {
  color: var(--danger);
}

.rep-label {
  font-size: 12px;
  color: #8c7a6b;
}

.rep-divider {
  width: 1px;
  height: 30px;
  background: var(--card-line);
}

@media (max-width: 720px) {
  .rep-list {
    gap: 16px;
  }
}

@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .bottom-grid {
    grid-template-columns: 1fr;
  }
}
</style>
