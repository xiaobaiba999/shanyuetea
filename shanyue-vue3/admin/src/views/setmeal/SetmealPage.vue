<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">套餐管理</h2>
        <p class="page-sub">套餐（Setmeal）= 多款饮品组合定价；新增后默认停售，起售前校验套餐内饮品须全部起售</p>
      </div>
      <div class="header-btns">
        <el-button :icon="Delete" type="danger" plain :disabled="!multipleSelection.length" @click="onBatchDelete">
          批量删除{{ multipleSelection.length ? `（${multipleSelection.length}）` : '' }}
        </el-button>
        <el-button type="primary" :icon="Plus" @click="openAdd">新增套餐</el-button>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="card card-pad toolbar">
      <div class="search-area">
        <el-input
          v-model.trim="query.name"
          placeholder="套餐名称，模糊搜索"
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
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="records"
        stripe
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="44" />
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
                <div class="thumb-fallback">{{ row.name.charAt(0) }}</div>
              </template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="160">
          <template #default="{ row }">
            <span class="setmeal-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分类" min-width="120">
          <template #default="{ row }">
            {{ row.categoryName || categoryName(row.categoryId) }}
          </template>
        </el-table-column>
        <el-table-column label="价格" min-width="100">
          <template #default="{ row }">
            <span class="text-money">¥ {{ fmtMoney(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="包含饮品" min-width="200">
          <template #default="{ row }">
            <span class="text-secondary drinkes-brief">{{ drinkesBrief(row.setmealDrinkes) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status"
              :active-value="1"
              :inactive-value="0"
              :loading="statusLoadingId === row.id"
              @change="(val: string | number | boolean) => onToggleStatus(row, val as 0 | 1)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" min-width="160">
          <template #default="{ row }">
            <span class="text-secondary">{{ row.updateTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="onDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <div class="table-empty"><el-empty description="暂无套餐，点右上角「新增套餐」试试" :image-size="72" /></div>
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
      :title="isEdit ? '编辑套餐' : '新增套餐'"
      width="980px"
      top="4vh"
      destroy-on-close
      class="setmeal-dialog"
    >
      <div v-loading="detailLoading" class="dialog-body">
        <!-- 基本信息 -->
        <div class="card-title-bar"><span class="card-title">基本信息</span></div>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="套餐名称" prop="name">
                <el-input v-model.trim="form.name" placeholder="如：晨光咖啡轻食套餐" maxlength="20" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="所属分类" prop="categoryId">
                <el-select v-model="form.categoryId" placeholder="选择套餐分类" class="w-full">
                  <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="套餐价（元）" prop="priceYuan">
                <el-input-number
                  v-model="form.priceYuan"
                  :min="0"
                  :max="9999"
                  :precision="2"
                  :step="1"
                  controls-position="right"
                  placeholder="0.00"
                  class="w-full"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="套餐图片">
                <el-upload accept="image/*" :show-file-list="false" :http-request="handleUpload">
                  <div v-loading="uploadLoading" class="image-box">
                    <img v-if="form.image" :src="form.image" class="image-preview" alt="套餐图" />
                    <div v-else class="image-empty">
                      <el-icon :size="20"><Plus /></el-icon>
                      <span>上传图片</span>
                    </div>
                    <div v-if="form.image" class="image-mask">
                      <el-icon><RefreshRight /></el-icon>
                      <span>更换图片</span>
                    </div>
                  </div>
                </el-upload>
              </el-form-item>
            </el-col>
            <el-col :span="16">
              <el-form-item label="套餐描述">
                <el-input
                  v-model.trim="form.description"
                  type="textarea"
                  :rows="4"
                  maxlength="80"
                  show-word-limit
                  placeholder="一句话卖点，如：生椰拿铁 + 黄油可颂，开启元气早晨"
                />
                <div class="form-tip">新增套餐默认「停售」，确认内容后可在列表手动起售</div>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <!-- 选择包含饮品 -->
        <div class="card-title-bar pick-head">
          <span class="card-title">选择包含饮品</span>
          <span class="pick-tip">勾选饮品并填写份数；起售时校验套餐内饮品须全部起售</span>
        </div>
        <el-table
          ref="pickerRef"
          :data="drinks"
          max-height="300"
          class="picker-table"
          @selection-change="onPickChange"
        >
          <el-table-column type="selection" width="44" />
          <el-table-column prop="name" label="饮品" min-width="150">
            <template #default="{ row }">
              <div class="pick-name">
                <el-image :src="row.image" fit="cover" class="pick-thumb">
                  <template #error>
                    <div class="pick-thumb-fallback">{{ row.name.charAt(0) }}</div>
                  </template>
                </el-image>
                <span>{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="分类" min-width="100">
            <template #default="{ row }">{{ row.categoryName || drinkCategoryName(row.categoryId) }}</template>
          </el-table-column>
          <el-table-column label="单价" min-width="90">
            <template #default="{ row }">
              <span class="text-money">¥ {{ fmtMoney(row.price) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="light" round>
                {{ row.status === 1 ? '起售中' : '已停售' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="份数" width="150" align="center">
            <template #default="{ row }">
              <el-input-number
                v-model="copiesMap[row.id]"
                :min="1"
                :max="99"
                size="small"
                controls-position="right"
                :disabled="!isSelected(row.id)"
              />
            </template>
          </el-table-column>
          <template #empty>
            <div class="table-empty"><el-empty description="暂无可选饮品，请先在饮品管理新增" :image-size="60" /></div>
          </template>
        </el-table>

        <!-- 已选汇总 -->
        <div class="pick-summary">
          <template v-if="pickedDrinks.length">
            <span>已选 <b>{{ pickedDrinks.length }}</b> 款饮品 · 合计原价
              <b class="text-money">¥ {{ fmtMoney(goodsTotal) }}</b>
            </span>
            <span class="summary-save">
              套餐价 ¥ {{ fmtMoney(form.priceYuan ?? 0) }}
              <template v-if="form.priceYuan != null && goodsTotal - (form.priceYuan ?? 0) > 0">
                · 较单点立省 <b>¥ {{ fmtMoney(goodsTotal - (form.priceYuan ?? 0)) }}</b>
              </template>
            </span>
          </template>
          <span v-else class="summary-empty">尚未选择饮品：套餐内至少需要一款饮品</span>
        </div>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saveLoading" @click="onSave">
          {{ isEdit ? '保存修改' : '创建套餐' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules, TableInstance, UploadRequestOptions } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { Delete, Plus, Refresh, RefreshRight, Search } from '@element-plus/icons-vue'
import { addSetmeal, deleteSetmeals, getSetmeal, getSetmealPage, updateSetmeal, updateSetmealStatus } from '@/api/setmeal'
import { getDrinkList } from '@/api/drink'
import { getCategoryList } from '@/api/category'
import { uploadImage } from '@/api/upload'
import { fmtMoney } from '@/utils/format'
import type { Category, Drink, Setmeal, SetmealDTO, SetmealDrink } from '@/types'

/* ---------------- 列表状态 ---------------- */
const loading = ref(false)
const records = ref<Setmeal[]>([])
const categories = ref<Category[]>([])
/** 饮品分类（type=1）：选品池把 categoryId 映射成分类名用（
 *  GET /admin/drink/list 契约返回 Drink 实体，只有 categoryId 数字不带分类名，
 *  所以前端自己拉一份饮品分类表做 id→name 映射，后端保持轻量契约不变） */
const drinkCategories = ref<Category[]>([])
const total = ref(0)
const tableRef = ref<TableInstance>()

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

/** 饮品分类 id→name 映射（选品池专用；两者后端将来若带 categoryName 依然兼容） */
function drinkCategoryName(id?: number) {
  if (id == null) return '-'
  return drinkCategories.value.find((c) => c.id === id)?.name ?? '-'
}

function drinkesBrief(drinkes: SetmealDrink[]) {
  if (!drinkes?.length) return '-'
  return drinkes.map((d) => `${d.name}×${d.copies}`).join('、')
}

const multipleSelection = ref<Setmeal[]>([])

function onSelectionChange(rows: Setmeal[]) {
  multipleSelection.value = rows
}

async function fetchPage() {
  loading.value = true
  try {
    const res = await getSetmealPage({ ...query })
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

/* ---------------- 起售/停售（POST /api/setmeal/status/{status}?id=） ---------------- */
const statusLoadingId = ref<number | null>(null)

async function onToggleStatus(row: Setmeal, val: 0 | 1) {
  const next: 0 | 1 = val
  try {
    await ElMessageBox.confirm(
      next === 1
        ? `确定起售「${row.name}」吗？起售前会校验套餐内饮品是否全部起售。`
        : `停售「${row.name}」后 C 端将不可见该套餐，确定吗？`,
      next === 1 ? '起售套餐' : '停售套餐',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return // 取消：受控 model-value 不变，无需回滚
  }
  statusLoadingId.value = row.id
  try {
    await updateSetmealStatus(next, row.id)
    ElMessage.success(next === 1 ? '已起售' : '已停售')
  } catch {
    /* 失败（如套餐内含停售饮品）已由 mock 以 code=0 弹错误提示 */
  } finally {
    statusLoadingId.value = null
    fetchPage()
  }
}

/* ---------------- 删除 ---------------- */
async function onDelete(row: Setmeal) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.name}」吗？其与饮品的关联配置将一并删除。`,
      '删除套餐',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteSetmeals([row.id])
    ElMessage.success('删除成功')
  } catch {
    /* 已提示 */
  } finally {
    fetchPage()
  }
}

async function onBatchDelete() {
  const rows = multipleSelection.value
  if (!rows.length) return
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${rows.length} 个套餐吗？（${rows.map((r) => r.name).join('、')}）`,
      '批量删除套餐',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteSetmeals(rows.map((r) => r.id))
    ElMessage.success(`已删除 ${rows.length} 个套餐`)
  } catch {
    /* 已提示 */
  } finally {
    tableRef.value?.clearSelection()
    fetchPage()
  }
}

/* ---------------- 新增/编辑弹窗 ---------------- */
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | undefined>(undefined)
const detailLoading = ref(false)
const saveLoading = ref(false)
const uploadLoading = ref(false)
const formRef = ref<FormInstance>()
const pickerRef = ref<TableInstance>()

/** 选品池：GET /api/drink/list 全量饮品 */
const drinks = ref<Drink[]>([])
/** 已勾选的饮品行 */
const pickedDrinks = ref<Drink[]>([])
/** drinkId → 份数（勾选时可编辑，默认 1） */
const copiesMap = reactive<Record<number, number>>({})

const form = reactive({
  name: '',
  categoryId: undefined as number | undefined,
  priceYuan: undefined as number | undefined,
  image: '',
  description: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入套餐名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择所属分类', trigger: 'change' }],
  priceYuan: [{ required: true, message: '请输入套餐价', trigger: 'blur' }],
}

const isSelected = (drinkId: number) => pickedDrinks.value.some((d) => d.id === drinkId)

function onPickChange(rows: Drink[]) {
  pickedDrinks.value = rows
  for (const d of rows) {
    if (!copiesMap[d.id]) copiesMap[d.id] = 1
  }
}

/** 合计原价 = Σ 饮品原价 × 份数（元） */
const goodsTotal = computed(() =>
  pickedDrinks.value.reduce((s, d) => s + d.price * (copiesMap[d.id] ?? 1), 0),
)

function resetForm() {
  form.name = ''
  form.categoryId = undefined
  form.priceYuan = undefined
  form.image = ''
  form.description = ''
  pickedDrinks.value = []
  Object.keys(copiesMap).forEach((k) => delete copiesMap[Number(k)])
}

function openAdd() {
  isEdit.value = false
  editingId.value = undefined
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: Setmeal) {
  isEdit.value = true
  editingId.value = row.id
  resetForm()
  dialogVisible.value = true
  detailLoading.value = true
  try {
    // 详情回显：SetmealVO 含 setmealDrinkes（drinkId/name/price/copies）
    const detail = await getSetmeal(row.id)
    form.name = detail.name
    form.categoryId = detail.categoryId
    // v2.1 契约：价格单位元，直出直存，无需 ÷100 换算
    form.priceYuan = detail.price
    form.image = detail.image
    form.description = detail.description
    // 回显勾选与份数（饮品可能已被删除，找不到的行跳过）
    for (const sd of detail.setmealDrinkes ?? []) {
      copiesMap[sd.drinkId] = sd.copies || 1
    }
    await nextTick()
    for (const sd of detail.setmealDrinkes ?? []) {
      const hit = drinks.value.find((d) => d.id === sd.drinkId)
      if (hit) pickerRef.value?.toggleRowSelection(hit, true)
    }
  } catch {
    /* 已提示 */
  } finally {
    detailLoading.value = false
  }
}

/* ---------- 图片上传（复用饮品页同款：mock→base64，真实→/api/common/upload） ---------- */
async function handleUpload(options: UploadRequestOptions) {
  const file = options.file as File
  if (!file.type.startsWith('image/')) {
    ElMessage.error('仅支持上传图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片不能超过 5MB')
    return
  }
  uploadLoading.value = true
  try {
    form.image = await uploadImage(file)
    ElMessage.success('图片已就绪')
  } catch {
    /* 已提示 */
  } finally {
    uploadLoading.value = false
  }
}

/* ---------- 保存 ---------- */
function validatePicks(): SetmealDrink[] | null {
  if (!pickedDrinks.value.length) {
    ElMessage.warning('请至少勾选一款饮品加入套餐')
    return null
  }
  return pickedDrinks.value.map((d) => ({
    drinkId: d.id,
    name: d.name,
    price: d.price,
    copies: copiesMap[d.id] ?? 1,
  }))
}

async function onSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const drinkes = validatePicks()
  if (!drinkes) return

  const payload: SetmealDTO = {
    id: isEdit.value ? editingId.value : undefined,
    name: form.name.trim(),
    categoryId: form.categoryId as number,
    price: form.priceYuan ?? 0,
    image: form.image,
    description: form.description.trim(),
    // setmealDrinkes 整体提交（后端先删旧后插新）
    setmealDrinkes: drinkes,
  }

  saveLoading.value = true
  try {
    if (isEdit.value) {
      await updateSetmeal(payload)
      ElMessage.success('套餐已保存')
    } else {
      await addSetmeal(payload)
      ElMessage.success('套餐已创建（默认停售，可手动起售）')
    }
    dialogVisible.value = false
  } catch {
    /* 已提示 */
  } finally {
    saveLoading.value = false
    fetchPage()
  }
}

onMounted(async () => {
  fetchPage()
  try {
    // 套餐分类（type=2）用于筛选与表单下拉
    categories.value = await getCategoryList(2)
  } catch {
    /* 已提示 */
  }
  try {
    // 饮品分类（type=1）：选品池「分类」列把 categoryId 翻译成分类名
    drinkCategories.value = await getCategoryList(1)
  } catch {
    /* 已提示 */
  }
  try {
    drinks.value = await getDrinkList()
  } catch {
    /* 已提示 */
  }
})
</script>

<style scoped>
.header-btns {
  display: flex;
  gap: 10px;
}

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
.w-full { width: 100%; }

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

.setmeal-name {
  font-weight: 600;
}

.drinkes-brief {
  font-size: 12px;
}

/* ---------- 弹窗 ---------- */
.dialog-body {
  min-height: 320px;
}

.card-title-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 2px 0 14px;
}

.card-title {
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
  color: var(--ink);
  letter-spacing: 0.5px;
}

/* 竖条装饰由全局 .card-title-bar::before 提供（茶金→墨绿），此处不再重复绘制 */

.pick-head {
  margin-top: 6px;
}

.pick-tip {
  font-size: 12px;
  color: #b3a08e;
}

.form-tip {
  font-size: 12px;
  color: #b3a08e;
  line-height: 1.6;
  margin-top: 2px;
}

/* 图片上传（与饮品编辑页同款视觉：茶金虚线描边） */
.image-box {
  width: 96px;
  height: 96px;
  border-radius: 12px;
  border: 1.5px dashed var(--gold-soft);
  background: var(--paper);
  overflow: hidden;
  position: relative;
  cursor: pointer;
  transition: border-color 0.2s;
}

.image-box:hover {
  border-color: var(--pine);
}

.image-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.image-empty {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #b3a08e;
  font-size: 12px;
}

.image-mask {
  position: absolute;
  inset: 0;
  background: rgba(43, 38, 32, 0.55);
  color: #fff;
  font-size: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.image-box:hover .image-mask {
  opacity: 1;
}

/* ---------- 选品表 ---------- */
.picker-table {
  border-radius: 12px;
}

.pick-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.pick-thumb {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: var(--paper);
  flex-shrink: 0;
}

.pick-thumb-fallback {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--gold-soft), var(--gold));
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pick-summary {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--pine-mist);
  border: 1px solid var(--el-color-primary-light-7);
  font-size: 13px;
  color: var(--ink-2);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.summary-save b {
  color: var(--terracotta);
}

.summary-empty {
  color: #b3a08e;
}
</style>
