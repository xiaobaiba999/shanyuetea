<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">员工管理</h2>
        <p class="page-sub">管理门店员工账号、联系方式与启用状态</p>
      </div>
    </div>

    <!-- 搜索 + 操作 -->
    <div class="card card-pad toolbar">
      <div class="search-area">
        <el-input
          v-model.trim="query.name"
          placeholder="按姓名模糊搜索"
          clearable
          class="search-input"
          :prefix-icon="Search"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </div>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增员工</el-button>
    </div>

    <!-- 表格 -->
    <div class="card table-wrap">
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="name" label="姓名" min-width="110">
          <template #default="{ row }">
            <span class="emp-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" min-width="130" />
        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column label="性别" width="80" align="center">
          <template #default="{ row }">
            {{ row.sex === '1' ? '男' : '女' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status"
              :active-value="1"
              :inactive-value="0"
              :disabled="row.username === 'admin'"
              @change="onToggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160">
          <template #default="{ row }">
            <span class="text-secondary">{{ row.createTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <div class="table-empty"><el-empty description="暂无员工数据" :image-size="72" /></div>
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
      :title="isEdit ? '编辑员工' : '新增员工'"
      width="480px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model.trim="form.name" placeholder="如：沈山月" maxlength="20" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model.trim="form.username" placeholder="登录账号" maxlength="20" :disabled="isEdit" />
          <div v-if="isEdit" class="form-tip">编辑时用户名不可修改（与后端约束一致）</div>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model.trim="form.phone" placeholder="11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="性别" prop="sex">
          <el-radio-group v-model="form.sex">
            <el-radio value="1">男</el-radio>
            <el-radio value="0">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="身份证号" prop="idNumber">
          <el-input v-model.trim="form.idNumber" placeholder="选填" maxlength="18" />
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
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { addEmployee, getEmployeePage, updateEmployee, updateEmployeeStatus } from '@/api/employee'
import type { EmployeeVO } from '@/types'

const loading = ref(false)
const saveLoading = ref(false)
const records = ref<EmployeeVO[]>([])
const total = ref(0)

const query = reactive({ name: '', page: 1, pageSize: 10 })

async function fetchPage() {
  loading.value = true
  try {
    const res = await getEmployeePage({ ...query })
    records.value = res.records
    total.value = res.total
  } catch {
    /* 拦截器已提示 */
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
  onSearch()
}

function onSizeChange() {
  query.page = 1
  fetchPage()
}

/* ---------- 启用/禁用 ---------- */
async function onToggleStatus(row: EmployeeVO) {
  const next: 0 | 1 = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      `确定${next === 0 ? '禁用' : '启用'}员工「${row.name}」吗？${next === 0 ? '禁用后该账号将无法登录。' : ''}`,
      next === 0 ? '禁用员工' : '启用员工',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await updateEmployeeStatus(next, row.id)
    ElMessage.success(next === 0 ? '已禁用' : '已启用')
  } catch {
    /* 已提示 */
  } finally {
    fetchPage()
  }
}

/* ---------- 新增/编辑 ---------- */
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ id: undefined as number | undefined, name: '', username: '', phone: '', sex: '1', idNumber: '' })

const rules: FormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度 3~20 位', trigger: 'blur' },
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  sex: [{ required: true, message: '请选择性别', trigger: 'change' }],
  idNumber: [
    {
      pattern: /(^\d{15}$)|(^\d{17}[\dXx]$)/,
      message: '身份证号格式不正确',
      trigger: 'blur',
    },
  ],
}

function openAdd() {
  isEdit.value = false
  Object.assign(form, { id: undefined, name: '', username: '', phone: '', sex: '1', idNumber: '' })
  dialogVisible.value = true
}

function openEdit(row: EmployeeVO) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    name: row.name,
    username: row.username,
    phone: row.phone,
    sex: row.sex,
    idNumber: row.idNumber ?? '',
  })
  dialogVisible.value = true
}

async function onSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saveLoading.value = true
  try {
    if (isEdit.value) {
      await updateEmployee({ ...form })
      ElMessage.success('员工信息已更新')
    } else {
      await addEmployee({ ...form })
      ElMessage.success('新增员工成功')
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
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.search-area {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search-input {
  width: 260px;
}

.table-wrap {
  padding: 6px 14px 12px;
}

.emp-name {
  font-weight: 600;
}

.form-tip {
  font-size: 12px;
  color: #b3a08e;
  line-height: 1.5;
  margin-top: 2px;
}
</style>
