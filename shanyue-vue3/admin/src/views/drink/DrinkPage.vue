<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">饮品管理</h2>
        <p class="page-sub">菜品（Drink）改造为饮品（Drink），支持结构化规格组编辑</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="router.push('/drink/edit')">新增饮品</el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="card card-pad toolbar">
      <div class="search-area">
        <el-input
          v-model.trim="query.name"
          placeholder="饮品名称，模糊搜索"
          clearable
          class="w-220"
          :prefix-icon="Search"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select v-model="query.categoryId" placeholder="全部分类" clearable class="w-160" @change="onSearch">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable class="w-140" @change="onSearch">
          <el-option label="起售中" :value="1" />
          <el-option label="已停售" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </div>
    </div>

    <!-- 表格 -->
    <div class="card table-wrap">
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column label="图片" width="90" align="center">
          <template #default="{ row }">
            <el-image
              :src="row.image"
              fit="cover"
              class="thumb"
              :preview-src-list="[row.image]"
              preview-teleported
              hide-on-click-modal
            >
              <template #error>
                <!-- mock 图片路径占位：品牌色渐变 + 首字 -->
                <div class="thumb-fallback">{{ row.name.charAt(0) }}</div>
              </template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="150">
          <template #default="{ row }">
            <div class="drink-name">
              {{ row.name }}
              <el-tag v-if="row.featured === 1" size="small" effect="plain" type="warning" round class="feat-tag">招牌</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分类" min-width="110">
          <template #default="{ row }">
            <!-- 优先后端联表带出的 categoryName（13.3 契约）；拿不到再拿分类列表反查兜底 -->
            {{ row.categoryName || categoryName(row.categoryId) }}
          </template>
        </el-table-column>
        <el-table-column label="价格" min-width="100">
          <template #default="{ row }">
            <span class="text-money">¥ {{ fmtMoney(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="monthSales" label="月售" min-width="90" sortable>
          <template #default="{ row }">
            <!-- 接口暂未返回 monthSales 时显示 —（契约升级后为近 30 天真实月售） -->
            <span class="text-secondary">{{ row.monthSales ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light" round>
              {{ row.status === 1 ? '起售中' : '已停售' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规格组" min-width="220">
          <template #default="{ row }">
            <span class="text-secondary">{{ specBrief(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="router.push(`/drink/edit/${row.id}`)">编辑</el-button>
              <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="onToggleStatus(row)">
                {{ row.status === 1 ? '停售' : '起售' }}
              </el-button>
              <el-button link type="danger" @click="onDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <div class="table-empty"><el-empty description="暂无饮品，点右上角「新增饮品」试试" :image-size="72" /></div>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import { deleteDrinks, getDrinkPage, updateDrinkStatus } from '@/api/drink'
import { getCategoryList } from '@/api/category'
import { fmtMoney } from '@/utils/format'
import type { Category, Drink } from '@/types'

const router = useRouter()
const loading = ref(false)
const records = ref<Drink[]>([])
const categories = ref<Category[]>([])
const total = ref(0)

const query = reactive<{ name: string; categoryId: number | undefined; status: number | undefined; page: number; pageSize: number }>({
  name: '',
  categoryId: undefined,
  status: undefined,
  page: 1,
  pageSize: 10,
})

function categoryName(id: number) {
  return categories.value.find((c) => c.id === id)?.name ?? '-'
}

function specBrief(row: Drink) {
  // 分页接口暂未返回 specGroups 时显示 —（不是"无规格"，别误读）；
  // v2.0 契约：规格组字段名为 options（旧版 specOptions 已废弃）
  // 选项明细直接展示（options 就是后端从 spec_option 表查出来的）：加价 ≠ 0 的选项带 +N，
  // 例：杯型：中杯/大杯+3/超大杯+6；温度：冰/热 —— 比旧的「杯型(3)」计数版更直观，
  // 列表页一眼就能和数据库 spec_option 的 name / price_delta 逐条对上
  if (row.specGroups == null) return '—'
  if (!row.specGroups.length) return '无规格'
  return row.specGroups
    .map((g) => {
      const names = (g.options ?? []).map((o) =>
        Number(o.priceDelta) ? `${o.name}+${Number(o.priceDelta)}` : o.name,
      )
      return `${g.name}：${names.length ? names.join('/') : '—'}`
    })
    .join('；')
}

async function fetchPage() {
  loading.value = true
  try {
    const res = await getDrinkPage({ ...query })
    records.value = res.records
    total.value = res.total
  } catch {
    /* 已提示 */
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  fetchPage()
}

function onReset() {
  query.name = ''
  query.categoryId = undefined
  query.status = undefined
  onSearch()
}

function onSizeChange() {
  query.page = 1
  fetchPage()
}

async function onToggleStatus(row: Drink) {
  const next: 0 | 1 = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      next === 0
        ? `停售「${row.name}」后 C 端将不可见该饮品，确定吗？`
        : `确定恢复起售「${row.name}」吗？`,
      next === 0 ? '停售饮品' : '起售饮品',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await updateDrinkStatus(next, row.id)
    ElMessage.success(next === 0 ? '已停售' : '已起售')
  } catch {
    /* 已提示 */
  } finally {
    fetchPage()
  }
}

async function onDelete(row: Drink) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.name}」吗？其规格组配置将一并删除。`,
      '删除饮品',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteDrinks([row.id])
    ElMessage.success('删除成功')
  } catch {
    /* 已提示 */
  } finally {
    fetchPage()
  }
}

onMounted(async () => {
  fetchPage()
  try {
    // 只拉饮品分类（type=1），套餐分类不进饮品页的筛选与反查（与 DrinkEditPage 口径一致）
    categories.value = await getCategoryList(1)
  } catch {
    /* 已提示 */
  }
})
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}

.search-area {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.w-220 { width: 220px; }
.w-160 { width: 160px; }
.w-140 { width: 140px; }

.table-wrap {
  padding: 6px 14px 12px;
}

.thumb {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  background: var(--paper);
}

.thumb-fallback {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--gold-soft), var(--gold));
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.drink-name {
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.feat-tag {
  transform: scale(0.9);
}
</style>
