<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <div>
        <h2 class="page-title">数据统计</h2>
        <p class="page-sub">营业额口径：已完成订单金额之和 · 有效订单：非已取消 · 每完成一笔订单即时更新</p>
      </div>
      <div class="header-ops">
        <el-date-picker
          v-model="range"
          type="daterange"
          size="default"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          :clearable="false"
          :disabled-date="disableFuture"
          @change="loadAll"
        />
        <el-button :icon="Download" :loading="exporting" @click="onExport">导出报表</el-button>
      </div>
    </div>

    <!-- 区间快捷切换 -->
    <div class="range-tabs" role="tablist" aria-label="日期范围快捷切换">
      <button
        v-for="q in QUICK_RANGES"
        :key="q.key"
        class="range-tab"
        :class="{ active: quickKey === q.key }"
        role="tab"
        :aria-selected="quickKey === q.key"
        @click="applyQuick(q.key)"
      >
        {{ q.label }}
      </button>
    </div>

    <!-- KPI 卡 -->
    <div class="kpi-grid">
      <div class="card kpi-card">
        <div class="kpi-label">区间总营业额</div>
        <div class="kpi-value">¥ {{ fmtMoney(kpi.totalTurnover) }}</div>
        <div class="kpi-foot">{{ rangeText }} · 含今日真实数据</div>
      </div>
      <div class="card kpi-card">
        <div class="kpi-label">区间有效订单</div>
        <div class="kpi-value">{{ kpi.totalOrderCount }}</div>
        <div class="kpi-foot">有效订单 = 非已取消</div>
      </div>
      <div class="card kpi-card">
        <div class="kpi-label">平均客单价</div>
        <div class="kpi-value">¥ {{ fmtMoney(kpi.avgPrice) }}</div>
        <div class="kpi-foot">总营业额 ÷ 有效订单数</div>
      </div>
      <div class="card kpi-card">
        <div class="kpi-label">日均营业额</div>
        <div class="kpi-value">¥ {{ fmtMoney(kpi.avgTurnover) }}</div>
        <div class="kpi-foot">共 {{ dayCount }} 天</div>
      </div>
    </div>

    <!-- 营业额趋势 -->
    <div class="card card-pad chart-card">
      <div class="chart-head">
        <div class="card-title-wrap">
          <span class="card-title">营业额趋势</span>
          <el-tag effect="plain" type="warning" size="small" round>GET /api/report/turnoverStatistics</el-tag>
        </div>
        <span class="chart-unit">单位：元</span>
      </div>
      <div ref="turnoverRef" class="chart turnover-chart" />
    </div>

    <!-- 订单统计 + TOP10 -->
    <div class="bottom-grid">
      <div class="card card-pad chart-card">
        <div class="chart-head">
          <div class="card-title-wrap">
            <span class="card-title">订单统计</span>
            <el-tag effect="plain" type="info" size="small" round>ordersStatistics</el-tag>
          </div>
          <span class="chart-unit">单位：单</span>
        </div>
        <div ref="ordersRef" class="chart" />
      </div>

      <div class="card card-pad chart-card">
        <div class="chart-head">
          <div class="card-title-wrap">
            <span class="card-title">销量 TOP10</span>
            <el-tag effect="plain" type="success" size="small" round>top10</el-tag>
          </div>
          <span class="chart-unit">已完成订单口径</span>
        </div>
        <div ref="topRef" class="chart" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { getOrdersStatistics, getTop10Statistics, getTurnoverStatistics } from '@/api/report'
import { fmtMoney, ymd } from '@/utils/format'
import { USE_MOCK } from '@/utils/env'
import type { OrdersReportVO, SalesTop10ReportVO, TurnoverReportVO } from '@/types'

/* ---------------- 区间状态 ---------------- */
const QUICK_RANGES = [
  { key: '7d', label: '近 7 日', days: 7 },
  { key: '14d', label: '近 14 日', days: 14 },
  { key: '30d', label: '近 30 日', days: 30 },
] as const
type QuickKey = (typeof QUICK_RANGES)[number]['key']

function shiftDays(n: number): string {
  return ymd(new Date(Date.now() - n * 24 * 3600 * 1000))
}

const quickKey = ref<QuickKey>('7d')
const range = ref<[string, string]>([shiftDays(6), ymd(new Date())])

const applyQuick = (key: QuickKey) => {
  quickKey.value = key
  const days = QUICK_RANGES.find((q) => q.key === key)!.days
  range.value = [shiftDays(days - 1), ymd(new Date())]
  loadAll()
}

/** 禁选未来日期 */
function disableFuture(d: Date): boolean {
  return d.getTime() > Date.now()
}

const dayCount = computed(() => {
  const [b, e] = range.value
  return Math.round((new Date(`${e}T00:00:00`).getTime() - new Date(`${b}T00:00:00`).getTime()) / 86400000) + 1
})

const rangeText = computed(() => `${range.value[0]} ~ ${range.value[1]}`)

/* ---------------- 数据（v2.0 契约：列表字段为逗号分隔字符串，页面负责 split 解析） ---------------- */
const loading = ref(false)
const exporting = ref(false)
const turnover = ref<TurnoverReportVO | null>(null)
const orders = ref<OrdersReportVO | null>(null)
const top10 = ref<SalesTop10ReportVO | null>(null)

function splitNums(s: string | undefined): number[] {
  return (s ?? '').split(',').filter(Boolean).map(Number)
}

const kpi = computed(() => {
  const t = turnover.value
  const o = orders.value
  const totalTurnover = t ? splitNums(t.turnoverList).reduce((s, v) => s + v, 0) : 0
  const totalOrderCount = o?.validOrderCount ?? 0
  return {
    totalTurnover,
    totalOrderCount,
    avgPrice: totalOrderCount > 0 ? Math.round(totalTurnover / totalOrderCount) : 0,
    avgTurnover: dayCount.value > 0 ? Math.round(totalTurnover / dayCount.value) : 0,
  }
})

async function loadAll() {
  loading.value = true
  try {
    const [b, e] = range.value
    const [t, o, top] = await Promise.all([getTurnoverStatistics(b, e), getOrdersStatistics(b, e), getTop10Statistics(b, e)])
    turnover.value = t
    orders.value = o
    top10.value = top
    await nextTick()
    renderTurnover()
    renderOrders()
    renderTop10()
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

/* ---------------- ECharts ---------------- */
const turnoverRef = ref<HTMLDivElement>()
const ordersRef = ref<HTMLDivElement>()
const topRef = ref<HTMLDivElement>()
let chartT: echarts.ECharts | null = null
let chartO: echarts.ECharts | null = null
let chartP: echarts.ECharts | null = null

const AXIS_LABEL = { color: '#6B6155', fontSize: 11 }
const SPLIT_LINE = { lineStyle: { color: '#E8E2D4' } }
const TOOLTIP_BG = { backgroundColor: '#FFFFFF', borderColor: '#E8E2D4', textStyle: { color: '#2B2620' } }

function renderTurnover() {
  const t = turnover.value
  if (!turnoverRef.value || !t) return
  chartT = chartT || echarts.init(turnoverRef.value)
  const dates = t.dateList.split(',').filter(Boolean)
  const values = splitNums(t.turnoverList)
  const totalTurnover = values.reduce((s, v) => s + v, 0)
  const labels = dates.map((d) => d.slice(5))
  chartT.setOption({
    grid: { left: 8, right: 30, top: 40, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      ...TOOLTIP_BG,
      formatter: (ps: unknown) => {
        const p = (ps as Array<{ dataIndex: number; value: number }>)[0]
        return `${dates[p.dataIndex]}<br/>营业额：<b>¥ ${fmtMoney(p.value)}</b>`
      },
    },
    xAxis: { type: 'category', boundaryGap: false, data: labels, axisTick: { show: false }, axisLine: { lineStyle: { color: '#E8E2D4' } }, axisLabel: AXIS_LABEL },
    yAxis: { type: 'value', splitLine: SPLIT_LINE, axisLabel: { ...AXIS_LABEL, formatter: (v: number) => `¥${Math.round(v / 100)}` } },
    series: [
      {
        name: '营业额',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: t.turnoverList,
        lineStyle: { width: 3, color: '#2F5233' },
        itemStyle: { color: '#2F5233', borderColor: '#FFFFFF', borderWidth: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(47, 82, 51, 0.28)' },
            { offset: 1, color: 'rgba(47, 82, 51, 0.02)' },
          ]),
        },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: '#9C927F', type: 'dashed' },
          label: {
            color: '#6B6155',
            position: 'insideStartTop',
            formatter: (p: { value: number }) => `日均 ¥${fmtMoney(p.value)}`,
          },
          data: [{ yAxis: Math.round(totalTurnover / dates.length) }],
        },
      },
    ],
  })
}

function renderOrders() {
  const o = orders.value
  if (!ordersRef.value || !o) return
  chartO = chartO || echarts.init(ordersRef.value)
  const dates = o.dateList.split(',').filter(Boolean)
  const values = splitNums(o.validOrderCountList)
  const labels = dates.map((d) => d.slice(5))
  chartO.setOption({
    grid: { left: 8, right: 12, top: 40, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      ...TOOLTIP_BG,
      formatter: (ps: unknown) => {
        const p = (ps as Array<{ dataIndex: number; value: number }>)[0]
        return `${dates[p.dataIndex]}<br/>有效订单：<b>${p.value} 单</b>`
      },
    },
    xAxis: { type: 'category', data: labels, axisTick: { show: false }, axisLine: { lineStyle: { color: '#E8E2D4' } }, axisLabel: { ...AXIS_LABEL, interval: 'auto' } },
    yAxis: { type: 'value', minInterval: 1, splitLine: SPLIT_LINE, axisLabel: AXIS_LABEL },
    series: [
      {
        name: '有效订单',
        type: 'bar',
        barMaxWidth: 22,
        data: values.map((v, i) => ({
          value: v,
          itemStyle: {
            // 今日柱茶金高亮，历史柱墨绿
            color: i === values.length - 1 ? '#C89B5A' : '#2F5233',
            borderRadius: [5, 5, 0, 0],
          },
        })),
      },
    ],
  })
}

function renderTop10() {
  const top = top10.value
  if (!topRef.value || !top) return
  chartP = chartP || echarts.init(topRef.value)
  const names = top.nameList.split(',').filter(Boolean)
  const numbers = splitNums(top.numberList)
  chartP.setOption({
    grid: { left: 8, right: 42, top: 10, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      ...TOOLTIP_BG,
      formatter: (ps: unknown) => {
        const p = (ps as Array<{ name: string; value: number }>)[0]
        return `${p.name}<br/>销量：<b>${p.value} 件</b>`
      },
    },
    xAxis: { type: 'value', minInterval: 1, splitLine: SPLIT_LINE, axisLabel: AXIS_LABEL },
    yAxis: { type: 'category', inverse: true, data: names, axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: '#2B2620', fontSize: 12, fontWeight: 600 } },
    series: [
      {
        name: '销量',
        type: 'bar',
        barMaxWidth: 16,
        data: numbers.map((v, i) => ({
          value: v,
          itemStyle: {
            color:
              i === 0
                ? new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                    { offset: 0, color: '#D9B47E' },
                    { offset: 1, color: '#C89B5A' },
                  ])
                : '#8CA38F',
            borderRadius: [0, 8, 8, 0],
          },
        })),
        label: { show: true, position: 'right', color: '#6B6155', fontWeight: 600 },
      },
    ],
  })
}

function resizeAll() {
  chartT?.resize()
  chartO?.resize()
  chartP?.resize()
}

/* 来单模拟 / 接单完成 → 今日统计实时联动 */
function onOrdersChanged() {
  loadAll()
}

/* ---------------- CSV 导出 ----------------
 * mock 模式：前端直接把区间逐日数据导出为 CSV（Excel 可打开）。
 * 真实后端：dev-guide 阶段三可用 Apache POI 实现 GET /api/report/export，
 * 前端把 onExport 里的 CSV 逻辑换成 blob 下载即可（后端 course 原版就是 POI 写法）。
 */
async function onExport() {
  exporting.value = true
  try {
    const [b, e] = range.value
    const [t, o] = await Promise.all([getTurnoverStatistics(b, e), getOrdersStatistics(b, e)])
    const dates = t.dateList.split(',').filter(Boolean)
    const turnovers = splitNums(t.turnoverList)
    const orderCounts = splitNums(o.validOrderCountList)
    const rows = dates.map((date, i) => ({
      date,
      turnoverYuan: ((turnovers[i] ?? 0) / 100).toFixed(2),
      orderCount: orderCounts[i] ?? 0,
    }))
    const header = '日期,营业额(元),有效订单数(单)'
    const csv = '\uFEFF' + header + '\n' + rows.map((r) => `${r.date},${r.turnoverYuan},${r.orderCount}`).join('\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `山月茶咖-数据统计_${b}_${e}.csv`
    a.click()
    URL.revokeObjectURL(a.href)
    ElMessage.success(`已导出 ${rows.length} 天数据${USE_MOCK ? '（mock：前端 CSV；真实后端可换 POI Excel）' : ''}`)
  } catch {
    ElMessage.error('导出失败，请重试')
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', resizeAll)
  window.addEventListener('shanyue:orders-changed', onOrdersChanged)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeAll)
  window.removeEventListener('shanyue:orders-changed', onOrdersChanged)
  chartT?.dispose()
  chartO?.dispose()
  chartP?.dispose()
  chartT = chartO = chartP = null
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.header-ops {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

/* 区间快捷 tab */
.range-tabs {
  display: inline-flex;
  gap: 4px;
  margin: 14px 0 4px;
  padding: 4px;
  background: var(--paper-deep);
  border-radius: 12px;
  width: fit-content;
}

.range-tab {
  padding: 6px 18px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-2);
  border-radius: 9px;
  transition: all 0.18s;
  cursor: pointer;
}

.range-tab:hover {
  color: var(--ink);
}

.range-tab.active {
  background: var(--card);
  color: var(--pine);
  box-shadow: 0 2px 6px rgba(43, 38, 32, 0.12);
}

/* KPI 卡 */
.kpi-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.kpi-card {
  padding: 18px 22px;
  transition: transform 0.2s, box-shadow 0.2s;
}

.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(43, 38, 32, 0.1);
}

.kpi-label {
  font-size: 13px;
  color: var(--ink-2);
}

.kpi-value {
  margin-top: 8px;
  font-family: var(--font-serif);
  font-size: 24px;
  font-weight: 700;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.5px;
}

.kpi-foot {
  margin-top: 8px;
  font-size: 11px;
  color: #b3a08e;
}

/* 图表卡 */
.chart-card {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
}

.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.card-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chart-unit {
  font-size: 12px;
  color: #b3a08e;
}

.chart {
  flex: 1;
  min-height: 280px;
}

.turnover-chart {
  min-height: 320px;
}

.bottom-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: stretch;
}

.bottom-grid .chart-card {
  margin-top: 0;
}

@media (max-width: 1100px) {
  .kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .bottom-grid {
    grid-template-columns: 1fr;
  }
}
</style>
