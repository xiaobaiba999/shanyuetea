<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">分类管理</h2>
        <p class="page-sub">维护饮品/套餐分类及排序；分类下存在商品时不可删除</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增分类</el-button>
    </div>

    <div class="card table-wrap">
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="sort" label="排序号" width="90" align="center">
          <template #default="{ row }">
            <span class="sort-num">{{ row.sort }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="分类名称" min-width="180">
          <template #default="{ row }">
            <span class="cat-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.type === 1" type="success" size="small" effect="light" round>饮品分类</el-tag>
            <el-tag v-else-if="row.type === 2" type="warning" size="small" effect="light" round>套餐分类</el-tag>
            <el-tag v-else type="info" size="small" effect="plain" round>未设置</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="onToggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="170">
          <template #default="{ row }">
            <span class="text-secondary">{{ row.createTime }}</span>
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
          <div class="table-empty"><el-empty description="暂无分类数据" :image-size="72" /></div>
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
      :title="isEdit ? '编辑分类' : '新增分类'"
      width="440px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="分类类型" prop="type">
          <el-radio-group v-model="form.type" :disabled="isEdit">
            <el-radio-button :value="1">饮品分类</el-radio-button>
            <el-radio-button :value="2">套餐分类</el-radio-button>
          </el-radio-group>
          <span v-if="isEdit" class="form-tip">类型创建后不可修改</span>
        </el-form-item>
        <el-form-item label="分类名称" prop="name">
          <el-input v-model.trim="form.name" placeholder="如：季节限定" maxlength="16" />
        </el-form-item>
        <el-form-item label="排序号" prop="sort">
          <el-input-number v-model="form.sort" :min="1" :max="999" controls-position="right" />
          <span class="form-tip">数字越小越靠前</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saveLoading" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { Plus } from '@element-plus/icons-vue'
import { addCategory, deleteCategory, getCategoryPage, updateCategory, updateCategoryStatus } from '@/api/category'
import type { Category } from '@/types'

const loading = ref(false)
const saveLoading = ref(false)
const records = ref<Category[]>([])
const total = ref(0)
const query = reactive({ name: '', page: 1, pageSize: 10 })

async function fetchPage() {
  loading.value = true
  try {
    const res = await getCategoryPage({ ...query })
    records.value = res.records
    total.value = res.total
  } catch {
    /* 已提示 */
  } finally {
    loading.value = false
  }
}

function onSizeChange() {
  query.page = 1
  fetchPage()
}

/* ---------- 状态 ---------- */
async function onToggleStatus(row: Category) {
  const next: 0 | 1 = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      `确定${next === 0 ? '禁用' : '启用'}分类「${row.name}」吗？`,
      '切换分类状态',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await updateCategoryStatus(next, row.id)
    ElMessage.success('状态已更新')
  } catch {
    /* 已提示 */
  } finally {
    fetchPage()
  }
}

/* ---------- 删除（mock 校验：分类下有商品不可删） ---------- */
async function onDelete(row: Category) {
  try {
    await ElMessageBox.confirm(
      `确定删除分类「${row.name}」吗？删除后不可恢复。`,
      '删除分类',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteCategory(row.id)
    ElMessage.success('删除成功')
  } catch {
    /* 失败（如该分类下存在商品）已由 mock 以 code=0 弹错误提示 */
  } finally {
    fetchPage()
  }
}

/* ---------- 新增/编辑 ---------- */
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ id: undefined as number | undefined, type: 1 as 1 | 2, name: '', sort: 99 })

const rules: FormRules = {
  type: [{ required: true, message: '请选择分类类型', trigger: 'change' }],
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  sort: [{ required: true, message: '请输入排序号', trigger: 'blur' }],
}

function openAdd() {
  isEdit.value = false
  Object.assign(form, { id: undefined, type: 1, name: '', sort: 99 })
  dialogVisible.value = true
}

function openEdit(row: Category) {
  isEdit.value = true
  Object.assign(form, { id: row.id, type: row.type, name: row.name, sort: row.sort })
  dialogVisible.value = true
}

async function onSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saveLoading.value = true
  try {
    if (isEdit.value) {
      await updateCategory({ ...form })
      ElMessage.success('分类已更新')
    } else {
      await addCategory({ ...form })
      ElMessage.success('新增分类成功')
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
</script>

<style scoped>
.table-wrap {
  padding: 6px 14px 12px;
}

.sort-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 24px;
  padding: 0 8px;
  border-radius: 8px;
  background: #f8f2ea;
  color: #8b5e3c;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.cat-name {
  font-weight: 600;
}

.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #b3a08e;
}
</style>
