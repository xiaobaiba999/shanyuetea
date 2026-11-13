<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">优惠券管理</h2>
        <p class="page-sub">配置 C 端领券中心的优惠券模板；下架后 C 端不可再领取，已领取的券仍可使用</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增优惠券</el-button>
    </div>

    <!-- 经营概览小卡 -->
    <div class="stat-row">
      <div class="stat-chip">
        <span class="stat-num amber">{{ stats.active }}</span>
        <span class="stat-label">发放中券种</span>
      </div>
      <div class="stat-chip">
        <span class="stat-num">{{ stats.paused }}</span>
        <span class="stat-label">已下架券种</span>
      </div>
      <div class="stat-chip">
        <span class="stat-num green">{{ stats.received }}</span>
        <span class="stat-label">累计领取（张）</span>
      </div>
      <div class="stat-chip">
        <span class="stat-num">{{ stats.remain }}</span>
        <span class="stat-label">剩余可领（张）</span>
      </div>
    </div>

    <div class="card table-wrap">
      <!-- 搜索区 -->
      <div class="search-bar">
        <el-input
          v-model.trim="query.name"
          placeholder="券名称"
          clearable
          style="width: 200px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
          <el-option label="发放中" :value="1" />
          <el-option label="已下架" :value="0" />
        </el-select>
        <el-button :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" plain @click="onReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column label="券名称" min-width="210">
          <template #default="{ row }">
            <div class="coupon-cell">
              <span class="coupon-name" :class="{ off: row.status === 0 }">{{ row.name }}</span>
              <span class="coupon-desc">{{ row.description }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.type === 2 ? 'success' : 'warning'" effect="light" round>
              {{ row.type === 2 ? '无门槛' : '满减券' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="面额" width="96" align="right">
          <template #default="{ row }">
            <span class="amount">¥{{ fmtMoney(row.discount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="门槛" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.threshold > 0" class="threshold">满 {{ fmtMoney(row.threshold) }} 可用</span>
            <span v-else class="threshold none">无门槛</span>
          </template>
        </el-table-column>
        <el-table-column label="有效期" width="130" align="center">
          <template #default="{ row }">
            <span class="text-secondary">领取后 {{ row.validDays }} 天内</span>
          </template>
        </el-table-column>
        <el-table-column label="发放进度" min-width="170">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress
                :percentage="progressOf(row)"
                :stroke-width="8"
                :color="progressColor(row)"
                :show-text="false"
              />
              <span class="progress-text">
                已领 {{ row.totalCount - row.remainCount }} / {{ row.totalCount }}
                <em v-if="row.remainCount <= 0" class="sold-out">已领罄</em>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="onToggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="176" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="warning" :disabled="row.status === 0" @click="openRestock(row)">补量</el-button>
              <el-button link type="danger" :disabled="row.totalCount - row.remainCount > 0" @click="onDelete(row)">
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <div class="table-empty"><el-empty description="暂无优惠券，点右上角新增一张吧" :image-size="72" /></div>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑优惠券' : '新增优惠券'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="券名称" prop="name">
          <el-input v-model.trim="form.name" placeholder="如：秋日限定券" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="券类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio-button :value="1">满减券</el-radio-button>
            <el-radio-button :value="2">无门槛券</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="优惠金额" prop="discountYuan">
          <el-input-number v-model="form.discountYuan" :min="0.5" :max="200" :precision="1" :step="1" controls-position="right" />
          <span class="form-tip">元</span>
        </el-form-item>
        <el-form-item v-if="form.type === 1" label="使用门槛" prop="thresholdYuan">
          <el-input-number v-model="form.thresholdYuan" :min="0.5" :max="1000" :precision="1" :step="1" controls-position="right" />
          <span class="form-tip">元，需不低于优惠金额</span>
        </el-form-item>
        <el-form-item label="有效天数" prop="validDays">
          <el-input-number v-model="form.validDays" :min="1" :max="90" :step="1" controls-position="right" />
          <span class="form-tip">领取后 N 天内可用</span>
        </el-form-item>
        <el-form-item label="发放总量" prop="totalCount">
          <el-input-number v-model="form.totalCount" :min="1" :max="100000" :step="10" controls-position="right" />
          <span class="form-tip">领取 1 张扣 1 个库存（防超发可用 Redis 原子扣减）</span>
        </el-form-item>
        <el-form-item label="券说明" prop="description">
          <el-input
            v-model.trim="form.description"
            :placeholder="descriptionPlaceholder"
            maxlength="40"
            show-word-limit
          />
          <span class="form-tip">留空则按类型自动生成</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saveLoading" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 库存补量弹窗：运营补量高频操作，避免进编辑表单改总量 -->
    <el-dialog v-model="restockVisible" title="券库存补量" width="420px" destroy-on-close>
      <div v-if="restockRow" class="restock-body">
        <div class="restock-coupon">
          <span class="restock-name">{{ restockRow.name }}</span>
          <span class="restock-meta">已领 {{ restockRow.totalCount - restockRow.remainCount }} / 总量 {{ restockRow.totalCount }} 张</span>
        </div>
        <div class="restock-chips">
          <button
            v-for="n in RESTOCK_PRESETS"
            :key="n"
            type="button"
            class="restock-chip"
            :class="{ active: restockAmount === n }"
            @click="restockAmount = n"
          >
            +{{ n }}
          </button>
        </div>
        <el-form label-width="92px" @submit.prevent>
          <el-form-item label="补充数量">
            <el-input-number v-model="restockAmount" :min="1" :max="100000" :step="50" controls-position="right" />
            <span class="form-tip">张</span>
          </el-form-item>
        </el-form>
        <p class="restock-preview">
          补后总量 <b>{{ restockRow.totalCount + (restockAmount || 0) }}</b> 张，剩余可领
          <b>{{ restockRow.totalCount + (restockAmount || 0) - (restockRow.totalCount - restockRow.remainCount) }}</b> 张
        </p>
        <p class="restock-hint">💡 已领取张数只增不减，服务端会校验新总量 ≥ 已领取；面试点：高并发领券可用 Redis 原子扣减库存</p>
      </div>
      <template #footer>
        <el-button @click="restockVisible = false">取消</el-button>
        <el-button type="primary" :loading="restockLoading" @click="onRestock">确认补量</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { addCoupon, deleteCoupon, getCouponPage, updateCoupon, updateCouponStatus } from '@/api/coupon'
import { fmtMoney } from '@/utils/format'
import type { CouponTemplate } from '@/types'

const loading = ref(false)
const saveLoading = ref(false)
const records = ref<CouponTemplate[]>([])
const total = ref(0)
const query = reactive<{ name: string; status: number | undefined; page: number; pageSize: number }>({
  name: '',
  status: undefined,
  page: 1,
  pageSize: 10,
})

/** 全量概览（demo 数据量小，按 pageSize=100 拉一次；真实后端建议单独的统计接口） */
const allCoupons = ref<CouponTemplate[]>([])
async function fetchStats() {
  try {
    const res = await getCouponPage({ page: 1, pageSize: 100 })
    allCoupons.value = res.records
  } catch {
    /* 已提示 */
  }
}

const stats = computed(() => ({
  active: allCoupons.value.filter((c) => c.status === 1).length,
  paused: allCoupons.value.filter((c) => c.status === 0).length,
  received: allCoupons.value.reduce((s, c) => s + Math.max(0, c.totalCount - c.remainCount), 0),
  remain: allCoupons.value.reduce((s, c) => s + Math.max(0, c.remainCount), 0),
}))

async function fetchPage() {
  loading.value = true
  try {
    const res = await getCouponPage({ ...query })
    records.value = res.records
    total.value = res.total
  } catch {
    /* 已提示 */
  } finally {
    loading.value = false
  }
  fetchStats()
}

function onSearch() {
  query.page = 1
  fetchPage()
}

function onReset() {
  query.name = ''
  query.status = undefined
  onSearch()
}

function onSizeChange() {
  query.page = 1
  fetchPage()
}

/* ---------- 发放进度 ---------- */
function progressOf(row: CouponTemplate): number {
  if (row.totalCount <= 0) return 0
  const received = Math.max(0, row.totalCount - row.remainCount)
  return Math.min(100, Math.round((received / row.totalCount) * 100))
}

function progressColor(row: CouponTemplate): string {
  if (row.remainCount <= 0) return '#d97706'
  if (progressOf(row) >= 80) return '#e9a23b'
  return '#8b5e3c'
}

/* ---------- 状态切换 ---------- */
async function onToggleStatus(row: CouponTemplate) {
  const next: 0 | 1 = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      next === 0
        ? `下架后「${row.name}」将不再出现在 C 端领券中心，已领取的券仍可正常使用。`
        : `上架后「${row.name}」将重新出现在 C 端领券中心。`,
      next === 0 ? '下架优惠券' : '上架优惠券',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await updateCouponStatus(next, row.id)
    ElMessage.success(next === 0 ? '已下架' : '已上架')
  } catch {
    /* 已提示 */
  } finally {
    fetchPage()
  }
}

/* ---------- 删除（已领取不可删） ---------- */
async function onDelete(row: CouponTemplate) {
  try {
    await ElMessageBox.confirm(
      `确定删除优惠券「${row.name}」吗？删除后不可恢复。`,
      '删除优惠券',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteCoupon(row.id)
    ElMessage.success('删除成功')
  } catch {
    /* 失败（如已有用户领取）已由 mock 以 code=0 弹错误提示 */
  } finally {
    fetchPage()
  }
}

/* ---------- 新增/编辑 ---------- */
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: undefined as number | undefined,
  name: '',
  type: 1 as 1 | 2,
  discountYuan: 5,
  thresholdYuan: 25,
  validDays: 7,
  totalCount: 100,
  description: '',
  /** 编辑前的原始已领取数（校验发放总量下限用） */
  originReceived: 0,
})

const descriptionPlaceholder = computed(() =>
  form.type === 2 ? `无门槛立减 ${form.discountYuan} 元` : `满 ${form.thresholdYuan} 元减 ${form.discountYuan} 元`,
)

const rules: FormRules = {
  name: [{ required: true, message: '请输入券名称', trigger: 'blur' }],
  discountYuan: [
    { required: true, message: '请输入优惠金额', trigger: 'blur' },
    {
      validator: (_r, v: number, cb) => {
        if (!v || v <= 0) cb(new Error('优惠金额必须大于 0'))
        else cb()
      },
      trigger: 'blur',
    },
  ],
  thresholdYuan: [
    {
      validator: (_r, v: number, cb) => {
        if (form.type !== 1) return cb()
        if (!v || v <= 0) cb(new Error('满减券需设置使用门槛'))
        else if (v < form.discountYuan) cb(new Error('使用门槛不能低于优惠金额（如满 25 减 4）'))
        else cb()
      },
      trigger: 'blur',
    },
  ],
  validDays: [{ required: true, message: '请输入有效天数', trigger: 'blur' }],
  totalCount: [
    { required: true, message: '请输入发放总量', trigger: 'blur' },
    {
      validator: (_r, v: number, cb) => {
        if (isEdit.value && v < form.originReceived) {
          cb(new Error(`不能小于已领取张数（${form.originReceived}）`))
        } else cb()
      },
      trigger: 'blur',
    },
  ],
}

function openAdd() {
  isEdit.value = false
  Object.assign(form, {
    id: undefined,
    name: '',
    type: 1,
    discountYuan: 5,
    thresholdYuan: 25,
    validDays: 7,
    totalCount: 100,
    description: '',
    originReceived: 0,
  })
  dialogVisible.value = true
}

function openEdit(row: CouponTemplate) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    name: row.name,
    type: row.type,
    discountYuan: row.discount,
    thresholdYuan: row.threshold > 0 ? row.threshold : 0,
    validDays: row.validDays,
    totalCount: row.totalCount,
    description: row.description,
    originReceived: Math.max(0, row.totalCount - row.remainCount),
  })
  dialogVisible.value = true
}

async function onSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saveLoading.value = true
  try {
    const dto = {
      id: form.id,
      name: form.name,
      type: form.type,
      threshold: form.type === 2 ? 0 : form.thresholdYuan,
      discount: form.discountYuan,
      validDays: form.validDays,
      totalCount: form.totalCount,
      description: form.description || undefined,
    }
    if (isEdit.value) {
      await updateCoupon(dto)
      ElMessage.success('优惠券已更新')
    } else {
      await addCoupon(dto)
      ElMessage.success('新增优惠券成功')
    }
    dialogVisible.value = false
  } catch {
    /* 已提示 */
  } finally {
    saveLoading.value = false
    fetchPage()
  }
}

onMounted(fetchPage)

/* ---------- 库存补量（复用 14.3 修改接口，只改 totalCount） ---------- */
const RESTOCK_PRESETS = [50, 100, 200, 500]
const restockVisible = ref(false)
const restockLoading = ref(false)
const restockRow = ref<CouponTemplate | null>(null)
const restockAmount = ref(100)

function openRestock(row: CouponTemplate) {
  restockRow.value = row
  restockAmount.value = row.remainCount <= 0 ? 100 : 50
  restockVisible.value = true
}

async function onRestock() {
  const row = restockRow.value
  const amount = restockAmount.value
  if (!row || !amount || amount < 1) {
    ElMessage.warning('请输入有效的补充数量')
    return
  }
  restockLoading.value = true
  try {
    // 只改发放总量，其余字段原样回传（后端 14.3 修改接口）
    await updateCoupon({
      id: row.id,
      name: row.name,
      type: row.type,
      threshold: row.threshold,
      discount: row.discount,
      validDays: row.validDays,
      totalCount: row.totalCount + amount,
      description: row.description,
    })
    ElMessage.success(`已为「${row.name}」补充 ${amount} 张`)
    restockVisible.value = false
  } catch {
    /* 已提示（如总量 < 已领取） */
  } finally {
    restockLoading.value = false
    fetchPage()
  }
}
</script>

<style scoped>
/* ---------- 概览小卡 ---------- */
.stat-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 14px;
}

.stat-chip {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 12px 20px;
  min-width: 132px;
  border-radius: 14px;
  background: #fffdf9;
  border: 1px solid #f3ede3;
  box-shadow: 0 1px 4px rgba(139, 94, 60, 0.06);
}

.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: #3f2a1a;
  font-variant-numeric: tabular-nums;
}

.stat-num.amber {
  color: #b0702e;
}

.stat-num.green {
  color: #4c7156;
}

.stat-label {
  font-size: 12px;
  color: #8c7a6b;
}

/* ---------- 搜索区 ---------- */
.search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 2px 12px;
  flex-wrap: wrap;
}

.table-wrap {
  padding: 12px 14px;
}

/* ---------- 券名称列 ---------- */
.coupon-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.coupon-name {
  font-weight: 600;
  color: #3f2a1a;
}

.coupon-name.off {
  color: #b3a08e;
  text-decoration: line-through;
  text-decoration-color: rgba(179, 160, 142, 0.5);
}

.coupon-desc {
  font-size: 12px;
  color: #8c7a6b;
}

.amount {
  font-weight: 700;
  color: #b0702e;
  font-variant-numeric: tabular-nums;
}

.threshold {
  font-size: 12px;
  color: #6b5847;
  background: #f8f2ea;
  border-radius: 999px;
  padding: 2px 10px;
}

.threshold.none {
  color: #4c7156;
  background: #eff7f1;
}

/* ---------- 进度 ---------- */
.progress-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.progress-text {
  font-size: 12px;
  color: #8c7a6b;
  font-variant-numeric: tabular-nums;
}

.progress-text em.sold-out {
  font-style: normal;
  margin-left: 6px;
  color: #b4432f;
  font-weight: 600;
}

.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #b3a08e;
}

/* ---------- 补量弹窗 ---------- */
.restock-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.restock-coupon {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #fffdf9;
  border: 1px solid #f3ede3;
}

.restock-name {
  font-weight: 700;
  color: var(--el-color-primary, #8b5e3c);
}

.restock-meta {
  font-size: 12px;
  color: #b3a08e;
}

.restock-chips {
  display: flex;
  gap: 10px;
}

.restock-chip {
  flex: 1;
  height: 34px;
  font-size: 13px;
  font-weight: 700;
  color: #8b5e3c;
  border: 1px solid #e8ddcc;
  border-radius: 10px;
  background: #fffdf9;
  transition: all 0.15s ease;
}

.restock-chip:hover {
  border-color: #d8a86f;
}

.restock-chip.active {
  color: #fff;
  border-color: transparent;
  background: linear-gradient(135deg, #e9a23b, #d9822b);
  box-shadow: 0 2px 8px rgba(217, 130, 43, 0.35);
}

.restock-preview {
  font-size: 13px;
  color: #6f7d6f;
}

.restock-preview b {
  font-weight: 800;
  color: #d9822b;
}

.restock-hint {
  font-size: 11px;
  line-height: 1.6;
  color: #b3a08e;
  padding: 8px 10px;
  border-radius: 8px;
  background: #faf6ef;
}
</style>
