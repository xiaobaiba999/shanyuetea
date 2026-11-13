<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">订单管理</h2>
        <p class="page-sub">6 状态订单：待付款 → 待接单 → 制作中 → 待取餐/派送中 → 已完成/已取消；自取/外送双模式</p>
      </div>
      <el-button :icon="Refresh" :loading="statsLoading" @click="refreshAll">刷新</el-button>
    </div>

    <!-- 状态 tab（带数量徽标） -->
    <div class="card status-tabs">
      <button
        v-for="t in STATUS_TABS"
        :key="t.key"
        class="status-pill"
        :class="[`pill-${t.key}`, { active: query.status === t.key }]"
        @click="switchTab(t.key)"
      >
        {{ t.label }}
        <span v-if="countOf(t.key) > 0" class="pill-badge">{{ countOf(t.key) }}</span>
      </button>
    </div>

    <!-- 搜索栏 -->
    <div class="card card-pad toolbar">
      <div class="search-area">
        <el-input v-model.trim="query.number" placeholder="订单号" clearable class="w-200" @keyup.enter="onSearch" @clear="onSearch" />
        <el-input v-model.trim="query.phone" placeholder="手机号" clearable class="w-160" @keyup.enter="onSearch" @clear="onSearch" />
        <el-select v-model="query.deliveryMode" placeholder="全部取餐方式" clearable class="w-160" @change="onSearch">
          <el-option label="自取" :value="1" />
          <el-option label="外送" :value="2" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </div>
    </div>

    <!-- 表格 -->
    <div class="card table-wrap">
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column label="取餐号" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.deliveryMode === 1" class="pickup-code">{{ row.pickupNumber || '—' }}</span>
            <span v-else class="pickup-code delivery">外送</span>
          </template>
        </el-table-column>
        <el-table-column prop="number" label="订单号" min-width="150">
          <template #default="{ row }">
            <span class="order-no">{{ row.number }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" min-width="100">
          <template #default="{ row }">
            <span class="text-money">¥ {{ fmtMoney(row.amount) }}</span>
            <el-tag v-if="row.payStatus === 0 && row.status !== 6" size="small" type="warning" effect="plain" round class="pay-tag">
              {{ row.status === 1 ? '待支付' : '货到付款' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <span class="status-tag" :class="`st-${row.status}`">{{ statusText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商品" min-width="180">
          <template #default="{ row }">
            <span class="text-secondary items-brief">{{ itemsBrief(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="orderTime" label="下单时间" min-width="160">
          <template #default="{ row }">
            <span class="text-secondary">{{ row.orderTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="265" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <template v-if="row.status === 1">
                <el-button link type="danger" @click="onCancel(row)">取消</el-button>
              </template>
              <template v-else-if="row.status === 2">
                <el-button link type="primary" @click="onConfirm(row)">接单</el-button>
                <el-button link type="warning" @click="onReject(row)">拒单</el-button>
                <el-button link type="danger" @click="onCancel(row)">取消</el-button>
              </template>
              <template v-else-if="row.status === 3">
                <el-button link type="primary" @click="onDelivery(row)">{{ row.deliveryMode === 2 ? '开始派送' : '出餐叫号' }}</el-button>
              </template>
              <template v-else-if="row.status === 4">
                <el-button link type="success" @click="onComplete(row)">{{ row.deliveryMode === 2 ? '确认送达' : '确认取餐' }}</el-button>
              </template>
              <el-button link type="primary" plain @click="openDetail(row)">详情</el-button>
              <el-button link type="info" plain @click="openReceipt(row)">小票</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <div class="table-empty"><el-empty description="该筛选条件下暂无订单" :image-size="72" /></div>
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-sizes="[10, 20, 50]"
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          @current-change="fetchPage"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <!-- 订单详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="订单详情" size="520px" destroy-on-close>
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <!-- 头部：自取取餐号 / 外送标识 -->
          <div class="d-head">
            <div>
              <div class="d-pickup">{{ detail.deliveryMode === 1 ? (detail.pickupNumber || '—') : '外送' }}</div>
              <div class="d-mode-row">
                <span class="mode-tag" :class="detail.deliveryMode === 2 ? 'mode-delivery' : 'mode-pickup'">{{ detail.deliveryMode === 2 ? '外送' : '自取' }}</span>
                <span class="d-pay">{{ payText(detail) }}</span>
              </div>
            </div>
            <div class="d-head-right">
              <span class="status-tag" :class="`st-${detail.status}`">{{ statusText(detail) }}</span>
              <div class="d-amount">¥ {{ fmtMoney(detail.amount) }}</div>
            </div>
          </div>

          <!-- 外送收货信息 -->
          <div v-if="detail.deliveryMode === 2" class="card d-section">
            <div class="d-section-title">收货信息（外送）</div>
            <div class="d-info-row"><span class="d-info-label">收货人</span><span>{{ detail.consignee || '-' }}</span></div>
            <div class="d-info-row"><span class="d-info-label">联系电话</span><span>{{ detail.phone || '-' }}</span></div>
            <div class="d-info-row"><span class="d-info-label">收货地址</span><span>{{ detail.deliveryAddress || '-' }}</span></div>
            <div v-if="detail.estimatedDeliveryTime" class="d-info-row"><span class="d-info-label">预计送达</span><span>{{ detail.estimatedDeliveryTime }}</span></div>
          </div>

          <!-- 明细列表 -->
          <div class="card d-section">
            <div class="d-section-title">商品明细（{{ detail.orderDetailList.length }}）</div>
            <div v-for="item in detail.orderDetailList" :key="item.id" class="d-item">
              <el-image :src="item.image" fit="cover" class="d-item-img">
                <template #error>
                  <div class="d-item-img-fallback">{{ item.name.charAt(0) }}</div>
                </template>
              </el-image>
              <div class="d-item-main">
                <div class="d-item-name">{{ item.name }}</div>
                <div class="d-item-spec">{{ parseSpecSelected(item.specSelected) || '无规格' }}</div>
              </div>
              <div class="d-item-right">
                <div class="d-item-price">¥ {{ fmtMoney(item.amount) }}</div>
                <div class="d-item-qty">× {{ item.number }}</div>
              </div>
              <div class="d-item-subtotal">¥ {{ fmtMoney(item.amount * item.number) }}</div>
            </div>
            <div class="d-total">
              <span>商品小计</span>
              <span class="text-money">¥ {{ fmtMoney(goodsTotal(detail)) }}</span>
            </div>
            <div v-if="detail.deliveryMode === 2 && detail.deliveryFee" class="d-total">
              <span>配送费</span>
              <span class="text-money">¥ {{ fmtMoney(detail.deliveryFee) }}</span>
            </div>
            <div class="d-total d-grand">
              <span>合计</span>
              <span class="text-money d-total-num">¥ {{ fmtMoney(detail.amount) }}</span>
            </div>
          </div>

          <!-- 信息 -->
          <div class="card d-section">
            <div class="d-section-title">订单信息</div>
            <div class="d-info-row"><span class="d-info-label">订单号</span><span>{{ detail.number }}</span></div>
            <div class="d-info-row"><span class="d-info-label">下单时间</span><span>{{ detail.orderTime }}</span></div>
            <div class="d-info-row"><span class="d-info-label">支付时间</span><span>{{ detail.checkoutTime || '-' }}</span></div>
            <div class="d-info-row"><span class="d-info-label">支付状态</span><span>{{ payText(detail) }}</span></div>
            <div v-if="detail.deliveryMode === 1" class="d-info-row"><span class="d-info-label">联系手机</span><span>{{ detail.phone || '未留手机号' }}</span></div>
            <div class="d-info-row"><span class="d-info-label">备注</span><span>{{ detail.remark || '无' }}</span></div>
            <div v-if="detail.rejectionReason" class="d-info-row cancel">
              <span class="d-info-label">拒单原因</span><span>{{ detail.rejectionReason }}</span>
            </div>
            <div v-else-if="detail.cancelReason" class="d-info-row cancel">
              <span class="d-info-label">取消原因</span><span>{{ detail.cancelReason }}</span>
            </div>
          </div>
        </template>
      </div>
    </el-drawer>
    <!-- 小票打印预览（58mm 热敏小票样式） -->
    <el-dialog v-model="receiptVisible" width="360px" class="receipt-dialog" :show-close="false" destroy-on-close>
      <div v-loading="receiptLoading" class="receipt-wrap">
        <template v-if="receiptOrder">
          <div id="receipt-print-area" class="receipt-sheet">
            <!-- 店铺头 -->
            <div class="rc-shop">山月茶咖 · 科创园区店</div>
            <div class="rc-en">SHANYUE TEA &amp; COFFEE</div>
            <div class="rc-type">{{ receiptOrder.deliveryMode === 2 ? '外送配送' : '门店自取' }}</div>

            <div class="rc-perf"><i class="notch l" /><i class="notch r" /></div>

            <!-- 取餐号 -->
            <div class="rc-pickup-label">{{ receiptOrder.deliveryMode === 2 ? '配 送 单' : '取 餐 号' }}</div>
            <div class="rc-pickup">{{ receiptOrder.deliveryMode === 1 ? (receiptOrder.pickupNumber || '—') : '外送' }}</div>
            <div class="rc-status">{{ statusText(receiptOrder) }}</div>

            <div class="rc-dash" />

            <!-- 订单信息 -->
            <div class="rc-row"><span>订单号</span><span>{{ receiptOrder.number }}</span></div>
            <div class="rc-row"><span>下单时间</span><span>{{ receiptOrder.orderTime }}</span></div>
            <div class="rc-row"><span>联系手机</span><span>{{ receiptOrder.phone || '未留手机号' }}</span></div>
            <template v-if="receiptOrder.deliveryMode === 2">
              <div class="rc-row"><span>收货人</span><span>{{ receiptOrder.consignee || '-' }}</span></div>
              <div class="rc-row"><span>收货地址</span><span>{{ receiptOrder.deliveryAddress || '-' }}</span></div>
            </template>

            <div class="rc-dash" />

            <!-- 商品明细 -->
            <div v-for="item in receiptOrder.orderDetailList" :key="item.id" class="rc-item">
              <div class="rc-item-name">{{ item.name }} <span class="rc-qty">x{{ item.number }}</span></div>
              <div class="rc-item-spec">{{ parseSpecSelected(item.specSelected) }}</div>
              <div class="rc-item-amount">¥{{ fmtMoney(item.amount * item.number) }}</div>
            </div>

            <div class="rc-dash" />

            <!-- 金额 -->
            <div class="rc-row"><span>商品总额</span><span>¥{{ fmtMoney(goodsTotal(receiptOrder)) }}</span></div>
            <div v-if="receiptOrder.deliveryMode === 2 && receiptOrder.deliveryFee" class="rc-row">
              <span>配送费</span><span>¥{{ fmtMoney(receiptOrder.deliveryFee) }}</span>
            </div>
            <div class="rc-total"><span>实付</span><span>¥{{ fmtMoney(receiptOrder.amount) }}</span></div>
            <div v-if="receiptOrder.remark" class="rc-remark">备注：{{ receiptOrder.remark }}</div>

            <div class="rc-perf"><i class="notch l" /><i class="notch r" /></div>

            <!-- 条形码（由订单号生成的装饰性竖条） -->
            <div class="rc-barcode" aria-hidden="true">
              <i v-for="(w, i) in barcodeBars" :key="i" :style="{ width: `${w}px`, marginRight: `${(i % 2) + 1}px` }" />
            </div>
            <div class="rc-barcode-no">{{ receiptOrder.number }}</div>

            <div class="rc-slogan">山月入杯，茶咖相宜</div>
            <div class="rc-thanks">谢谢惠顾，欢迎再次光临</div>
          </div>

          <div class="rc-actions">
            <el-button @click="receiptVisible = false">关闭</el-button>
            <el-button type="primary" @click="printReceipt">🖨 打印小票</el-button>
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { Refresh, Search } from '@element-plus/icons-vue'
import { cancelOrder, completeOrder, confirmOrder, deliveryOrder, getOrderDetails, getOrderStatistics, rejectOrder, searchOrders } from '@/api/order'
import { fmtMoney, parseSpecSelected } from '@/utils/format'
import type { OrderStatistics, OrderVO } from '@/types'

const route = useRoute()

/* 苍穹 6 状态机：1 待付款 / 2 待接单 / 3 制作中 / 4 派送中·待取餐 / 5 已完成 / 6 已取消 */
const STATUS_TEXT: Record<number, string> = {
  1: '待付款',
  2: '待接单',
  3: '制作中',
  5: '已完成',
  6: '已取消',
}

/** 状态 4 按配送模式区分文案：自取=待取餐 / 外送=派送中 */
function statusText(row: OrderVO): string {
  if (row.status === 4) return row.deliveryMode === 2 ? '派送中' : '待取餐'
  return STATUS_TEXT[row.status] ?? '未知'
}

/** 支付状态文案：payStatus 1 已支付 / 0 未支付（待付款单=待支付，外送货到付款） */
function payText(row: OrderVO): string {
  if (row.payStatus === 1) return '已支付'
  return row.status === 1 ? '待支付' : '未支付（货到付款）'
}

/** 商品小计 = Σ 明细单价 × 数量（苍穹口径：明细 amount 为单价） */
function goodsTotal(row: OrderVO): number {
  return (row.orderDetailList ?? []).reduce((s, it) => s + it.amount * it.number, 0)
}

const STATUS_TABS: Array<{ key: number; label: string }> = [
  { key: -1, label: '全部' },
  { key: 1, label: '待付款' },
  { key: 2, label: '待接单' },
  { key: 3, label: '制作中' },
  { key: 4, label: '待取餐/派送中' },
  { key: 5, label: '已完成' },
  { key: 6, label: '已取消' },
]

const loading = ref(false)
const statsLoading = ref(false)
const detailLoading = ref(false)
const records = ref<OrderVO[]>([])
const total = ref(0)
const statistics = ref<OrderStatistics | null>(null)
const drawerVisible = ref(false)
const detail = ref<OrderVO | null>(null)

/* ---------- 小票打印 ---------- */
const receiptVisible = ref(false)
const receiptLoading = ref(false)
const receiptOrder = ref<OrderVO | null>(null)

/** 装饰性条形码：订单号每字符映射为不同宽度的竖条 */
const barcodeBars = computed<number[]>(() => {
  const no = receiptOrder.value?.number ?? ''
  const bars: number[] = []
  for (const ch of no) {
    const n = Number.parseInt(ch, 16)
    if (Number.isNaN(n)) continue
    bars.push(1 + (n % 3))
  }
  return bars.length ? bars : [2, 1, 3, 1, 2]
})

async function openReceipt(row: OrderVO) {
  receiptVisible.value = true
  receiptLoading.value = true
  receiptOrder.value = null
  try {
    receiptOrder.value = await getOrderDetails(row.id)
  } catch {
    /* 已提示 */
  } finally {
    receiptLoading.value = false
  }
}

/** 调起浏览器打印：@media print 仅保留 #receipt-print-area 可见 */
function printReceipt() {
  window.print()
}

const query = reactive({
  page: 1,
  pageSize: 10,
  number: '',
  phone: '',
  /** -1 = 全部（发请求前由 api 层剥掉）；状态口径：1 待付款…6 已取消 */
  status: Number(route.query.status ?? -1),
  /** undefined = 全部方式；1 自取 / 2 外送（el-select 需 undefined 才显示 placeholder） */
  deliveryMode: undefined as number | undefined,
})

const allCount = computed(() => {
  const s = statistics.value
  if (!s) return 0
  return s.toBeConfirmed + s.confirmed + s.deliveryInProgress
})

function countOf(key: number): number {
  const s = statistics.value
  if (!s) return 0
  // 统计三卡口径：待接单(2) / 制作中(3) / 派送中·待取餐(4)；其余状态无徽标
  const field = ({ 2: 'toBeConfirmed', 3: 'confirmed', 4: 'deliveryInProgress' } as Record<number, keyof OrderStatistics>)[key]
  return (field ? s[field] : 0) ?? 0
}

function itemsBrief(row: OrderVO) {
  if (!row.orderDetailList?.length) return '-'
  return row.orderDetailList.map((it) => `${it.name}×${it.number}`).join('、')
}

async function fetchPage() {
  loading.value = true
  try {
    const res = await searchOrders({ ...query })
    records.value = res.records
    total.value = res.total
  } catch {
    /* 已提示 */
  } finally {
    loading.value = false
  }
}

async function fetchStatistics() {
  statsLoading.value = true
  try {
    statistics.value = await getOrderStatistics()
  } catch {
    /* 已提示 */
  } finally {
    statsLoading.value = false
  }
}

async function refreshAll() {
  await Promise.all([fetchPage(), fetchStatistics()])
}

/** 新订单到达（mock 模拟/真实 WebSocket）时自动刷新，无需手动点「刷新」 */
function onOrdersChanged(): void {
  void refreshAll()
}

function switchTab(key: number) {
  query.status = key
  query.page = 1
  fetchPage()
}

function onSearch() {
  query.page = 1
  fetchPage()
}

function onReset() {
  query.number = ''
  query.phone = ''
  query.deliveryMode = undefined
  switchTab(query.status)
}

function onSizeChange() {
  query.page = 1
  fetchPage()
}

/* ---------- 状态流转 ---------- */
async function act(action: () => Promise<unknown>, successMsg: string) {
  try {
    await action()
    ElMessage.success(successMsg)
  } catch {
    /* 非法流转已由 mock 以 code=0 弹错误 */
  } finally {
    refreshAll()
  }
}

function onConfirm(row: OrderVO) {
  ElMessageBox.confirm(`确认接单 ${row.deliveryMode === 1 ? row.pickupNumber : row.number} 并开始制作吗？`, '接单', {
    confirmButtonText: '确认接单',
    cancelButtonText: '取消',
    type: 'info',
  })
    .then(() => act(() => confirmOrder(row.id), '已接单，进入制作中'))
    .catch(() => undefined)
}

function onReject(row: OrderVO) {
  ElMessageBox.prompt('请输入拒单原因（会展示给顾客）', `拒单 ${row.number}`, {
    confirmButtonText: '确定拒单',
    cancelButtonText: '再想想',
    inputPlaceholder: '如：原料售罄',
    inputValidator: (v: string) => (v && v.trim().length > 0 ? true : '拒单原因不能为空'),
    type: 'warning',
  })
    .then(({ value }) => act(() => rejectOrder(row.id, value.trim()), '已拒单'))
    .catch(() => undefined)
}

/** 出餐/派送：自取=出餐叫号（3→4 待取餐）；外送=开始派送（3→4 派送中） */
function onDelivery(row: OrderVO) {
  const isDelivery = row.deliveryMode === 2
  ElMessageBox.confirm(
    isDelivery ? `确认 ${row.number} 已交给骑手，开始派送吗？` : `确认 ${row.pickupNumber} 出餐并叫号吗？`,
    isDelivery ? '开始派送' : '出餐叫号',
    { confirmButtonText: isDelivery ? '开始派送' : '出餐叫号', cancelButtonText: '取消', type: 'info' },
  )
    .then(() => act(() => deliveryOrder(row.id), isDelivery ? '已开始派送' : '已出餐，等待顾客取餐'))
    .catch(() => undefined)
}

function onComplete(row: OrderVO) {
  const isDelivery = row.deliveryMode === 2
  ElMessageBox.confirm(
    isDelivery ? `确认 ${row.number} 已送达顾客，完成订单吗？` : `确认顾客已取走 ${row.pickupNumber}，完成订单吗？`,
    isDelivery ? '确认送达' : '确认取餐',
    { confirmButtonText: '完成订单', cancelButtonText: '取消', type: 'success' },
  )
    .then(() => act(() => completeOrder(row.id), '订单已完成'))
    .catch(() => undefined)
}

function onCancel(row: OrderVO) {
  ElMessageBox.prompt('请输入取消原因（会展示给顾客）', `取消订单 ${row.number}`, {
    confirmButtonText: '确定取消',
    cancelButtonText: '再想想',
    inputPlaceholder: '如：原料售罄',
    inputValidator: (v: string) => (v && v.trim().length > 0 ? true : '取消原因不能为空'),
    type: 'warning',
  })
    .then(({ value }) => act(() => cancelOrder(row.id, value.trim()), '订单已取消'))
    .catch(() => undefined)
}

/* ---------- 详情抽屉 ---------- */
async function openDetail(row: OrderVO) {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getOrderDetails(row.id)
  } catch {
    /* 已提示 */
  } finally {
    detailLoading.value = false
  }
}

onMounted(refreshAll)

/** 监听来单提醒模拟器（mock）或未来真实 WebSocket 层广播：订单数据变化时自动刷新 */
window.addEventListener('shanyue:orders-changed', onOrdersChanged)
onUnmounted(() => {
  window.removeEventListener('shanyue:orders-changed', onOrdersChanged)
})
</script>

<style scoped>
/* ---------- 状态 tab ---------- */
.status-tabs {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid transparent;
  background: transparent;
  padding: 8px 16px;
  border-radius: 999px;
  font-size: 13px;
  color: #5c4632;
  cursor: pointer;
  transition: all 0.2s;
}

.status-pill:hover {
  background: #faf5ee;
}

.status-pill.active {
  background: linear-gradient(135deg, #8b5e3c, #a9846b);
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(139, 94, 60, 0.3);
}

.pill-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #f0e6da;
  color: #8b5e3c;
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.status-pill.active .pill-badge {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
}

/* ---------- 搜索 ---------- */
.toolbar {
  margin-bottom: 16px;
}

.search-area {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.w-200 { width: 200px; }
.w-160 { width: 160px; }

/* ---------- 表格 ---------- */
.table-wrap {
  padding: 6px 14px 12px;
}

.pickup-code {
  display: inline-block;
  min-width: 52px;
  padding: 3px 10px;
  border-radius: 8px;
  background: #f8ede0;
  color: #b0702e;
  font-size: 15px;
  font-weight: 800;
  letter-spacing: 1px;
  font-family: 'SFMono-Regular', Consolas, 'PingFang SC', monospace;
}

.order-no {
  font-variant-numeric: tabular-nums;
  color: #5c4632;
}

.items-brief {
  font-size: 12px;
}

/* 6 色状态 tag（1 待付款…6 已取消） */
.status-tag {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
}

.st-1 { color: #8c7a6b; background: #f6f3f0; border: 1px solid #e4ddd4; } /* 待付款 灰褐 */
.st-2 { color: #b0702e; background: #f9f3ee; border: 1px solid #edded0; } /* 待接单 琥珀 */
.st-3 { color: #8b5e3c; background: #f3efec; border: 1px solid #dccfc5; } /* 制作中 摩卡 */
.st-4 { color: #4a7ba6; background: #eef4fa; border: 1px solid #d8e5f1; } /* 待取餐/派送中 青蓝 */
.st-5 { color: #5f8d6b; background: #eff4f0; border: 1px solid #d5e3d9; } /* 已完成 茶园绿 */
.st-6 { color: #c0564a; background: #f9f0ed; border: 1px solid #edd3cf; } /* 已取消 陶红 */

/* 待支付/货到付款徽标 */
.pay-tag {
  margin-left: 6px;
  transform: scale(0.92);
}

/* 详情抽屉：自取/外送模式 tag */
.d-mode-row {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.mode-tag {
  display: inline-block;
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1px;
}

.mode-pickup {
  background: rgba(233, 185, 104, 0.24);
  color: #f5d9a4;
  border: 1px solid rgba(233, 185, 104, 0.45);
}

.mode-delivery {
  background: rgba(74, 123, 166, 0.26);
  color: #b7d4ee;
  border: 1px solid rgba(122, 168, 209, 0.45);
}

.d-pay {
  font-size: 12px;
  color: rgba(250, 246, 241, 0.78);
}

.d-grand {
  margin-top: 6px;
  font-weight: 700;
}

/* ---------- 详情抽屉 ---------- */
.detail-body {
  min-height: 300px;
}

.d-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #46301e, #6f4b30);
  border-radius: 14px;
  padding: 18px 20px;
  color: #faf6f1;
  margin-bottom: 14px;
}

.d-pickup {
  font-size: 34px;
  font-weight: 800;
  letter-spacing: 3px;
  font-family: 'SFMono-Regular', Consolas, 'PingFang SC', monospace;
}

.d-head-right {
  text-align: right;
}

.d-amount {
  margin-top: 6px;
  font-size: 18px;
  font-weight: 700;
}

.d-section {
  padding: 14px 16px;
  margin-bottom: 12px;
}

.d-section-title {
  font-size: 13px;
  font-weight: 700;
  color: #8c7a6b;
  margin-bottom: 12px;
}

.d-item {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto auto;
  gap: 12px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px dashed #f3ede3;
}

.d-item:last-of-type {
  border-bottom: none;
}

.d-item-img {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  background: #f8f2ea;
}

.d-item-img-fallback {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  background: linear-gradient(135deg, #e8d5c0, #c08552);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.d-item-name {
  font-weight: 600;
}

.d-item-spec {
  margin-top: 3px;
  font-size: 12px;
  color: #8c7a6b;
}

.d-item-right {
  text-align: right;
}

.d-item-price {
  font-size: 13px;
}

.d-item-qty {
  margin-top: 3px;
  font-size: 12px;
  color: #8c7a6b;
}

.d-item-subtotal {
  min-width: 72px;
  text-align: right;
  font-weight: 700;
  color: #8b5e3c;
  font-variant-numeric: tabular-nums;
}

.d-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid #f3ede3;
  font-size: 13px;
  color: #5c4632;
}

.d-total-num {
  font-size: 18px;
}

.d-info-row {
  display: flex;
  gap: 14px;
  padding: 6px 0;
  font-size: 13px;
}

.d-info-label {
  width: 64px;
  flex-shrink: 0;
  color: #8c7a6b;
}

.d-info-row.cancel {
  color: #c0564a;
}

/* ---------- 小票打印预览 ---------- */
.receipt-wrap {
  min-height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.receipt-sheet {
  width: 300px;
  background: #fff;
  color: #2c2c2c;
  padding: 18px 18px 14px;
  border-radius: 4px;
  box-shadow: 0 6px 22px rgba(63, 42, 26, 0.14);
  font-family: 'SFMono-Regular', 'Menlo', 'Consolas', 'Courier New', monospace;
  position: relative;
}

.rc-shop {
  text-align: center;
  font-size: 16px;
  font-weight: 800;
  letter-spacing: 1px;
}

.rc-en {
  margin-top: 3px;
  text-align: center;
  font-size: 9px;
  letter-spacing: 2px;
  color: #999;
}

.rc-type {
  margin: 8px auto 0;
  width: fit-content;
  font-size: 10px;
  padding: 2px 10px;
  border: 1px solid #2c2c2c;
  border-radius: 999px;
}

/* 撕票线 + 两侧缺口 */
.rc-perf {
  position: relative;
  margin: 14px -18px;
  border-top: 2px dashed #d9d9d9;
}

.rc-perf .notch {
  position: absolute;
  top: -9px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff; /* 与 dialog 白底一致，形成「缺口」错觉 */
}

.rc-perf .notch.l {
  left: -9px;
}

.rc-perf .notch.r {
  right: -9px;
}

.rc-pickup-label {
  margin-top: 4px;
  text-align: center;
  font-size: 11px;
  letter-spacing: 6px;
  color: #888;
}

.rc-pickup {
  margin-top: 2px;
  text-align: center;
  font-size: 46px;
  font-weight: 800;
  letter-spacing: 6px;
  line-height: 1.2;
}

.rc-status {
  text-align: center;
  font-size: 11px;
  color: #b0702e;
  font-weight: 700;
}

.rc-dash {
  border-top: 1px dashed #ddd;
  margin: 10px 0;
}

.rc-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 11px;
  line-height: 1.9;
  color: #444;
}

.rc-row.rc-discount {
  color: #c0564a;
}

.rc-item {
  position: relative;
  padding: 3px 0;
}

.rc-item-name {
  font-size: 12px;
  font-weight: 700;
  padding-right: 64px;
}

.rc-qty {
  font-weight: 400;
  color: #888;
}

.rc-item-spec {
  font-size: 10px;
  color: #999;
}

.rc-item-amount {
  position: absolute;
  right: 0;
  top: 3px;
  font-size: 12px;
  font-weight: 700;
}

.rc-total {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-top: 4px;
  font-size: 13px;
  font-weight: 800;
}

.rc-total span:last-child {
  font-size: 20px;
}

.rc-remark {
  margin-top: 6px;
  font-size: 10px;
  color: #c0564a;
  background: #fdf1ef;
  border-radius: 4px;
  padding: 4px 8px;
}

.rc-barcode {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  height: 40px;
  overflow: hidden;
}

.rc-barcode i {
  display: inline-block;
  height: 100%;
  background: #2c2c2c;
}

.rc-barcode-no {
  margin-top: 4px;
  text-align: center;
  font-size: 11px;
  letter-spacing: 3px;
  color: #444;
}

.rc-slogan {
  margin-top: 10px;
  text-align: center;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2px;
}

.rc-thanks {
  margin-top: 3px;
  text-align: center;
  font-size: 10px;
  color: #999;
}

.rc-actions {
  margin-top: 16px;
  display: flex;
  justify-content: center;
  gap: 10px;
}

/* 打印态：只保留小票本体 */
@media print {
  body * {
    visibility: hidden !important;
  }

  #receipt-print-area,
  #receipt-print-area * {
    visibility: visible !important;
  }

  #receipt-print-area {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    box-shadow: none;
    border-radius: 0;
  }

  .rc-perf .notch {
    background: #fff;
  }
}
</style>

<style>
/* 小票 dialog：隐藏默认头部、压缩内边距（dialog 结构由 Element Plus 渲染，需全局样式） */
.receipt-dialog .el-dialog__header {
  display: none;
}

.receipt-dialog .el-dialog__body {
  padding: 22px 24px 20px;
}
</style>
