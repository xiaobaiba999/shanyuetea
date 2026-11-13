<template>
  <div class="page" v-loading="pageLoading">
    <div class="page-header">
      <div class="header-left">
        <el-button class="back-btn" :icon="ArrowLeft" circle @click="router.back()" />
        <div>
          <h2 class="page-title">{{ isNew ? '新增饮品' : '编辑饮品' }}</h2>
          <p class="page-sub">
            {{ isNew ? '配置基本信息与规格组，规格组决定 C 端点单弹窗的选项与加价' : '修改后规格组整体保存（先删旧后插新）' }}
          </p>
        </div>
      </div>
    </div>

    <div class="edit-grid">
      <!-- ============ 左：基本信息 ============ -->
      <div class="card card-pad basic-card">
        <div class="card-title-bar"><span class="card-title">基本信息</span></div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
          <el-form-item label="饮品名称" prop="name">
            <el-input v-model.trim="form.name" placeholder="如：生椰拿铁" maxlength="24" show-word-limit />
          </el-form-item>

          <el-form-item label="所属分类" prop="categoryId">
            <el-select v-model="form.categoryId" placeholder="选择分类" class="w-full">
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="售价（元）" prop="priceYuan">
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
            <div class="form-tip">以元为单位填写，提交时自动转为分（如 15.90 元 → 1590 分）</div>
          </el-form-item>

          <el-form-item label="商品图片">
            <el-upload accept="image/*" :show-file-list="false" :http-request="handleUpload">
              <div v-loading="uploadLoading" class="image-box">
                <img v-if="form.image" :src="form.image" class="image-preview" alt="饮品图" />
                <div v-else class="image-empty">
                  <el-icon :size="22"><Plus /></el-icon>
                  <span>上传图片</span>
                </div>
                <div v-if="form.image" class="image-mask">
                  <el-icon><RefreshRight /></el-icon>
                  <span>更换图片</span>
                </div>
              </div>
            </el-upload>
            <div class="form-tip">支持 JPG/PNG，≤5MB；mock 模式下压缩为本地预览图，真实模式走 /admin/common/upload</div>
          </el-form-item>

          <el-form-item label="商品描述">
            <el-input
              v-model.trim="form.description"
              type="textarea"
              :rows="3"
              maxlength="100"
              show-word-limit
              placeholder="一句话卖点，如：厚椰乳与浓缩咖啡的经典相遇"
            />
          </el-form-item>

          <el-form-item label="招牌推荐">
            <el-switch v-model="form.featured" :active-value="1" :inactive-value="0" />
            <span class="form-tip switch-tip">开启后 C 端「招牌推荐」分类展示</span>
          </el-form-item>
        </el-form>
      </div>

      <!-- ============ 右：规格组编辑器（核心） ============ -->
      <div class="card card-pad spec-card">
        <div class="card-title-bar spec-head">
          <span class="card-title">规格组与选项</span>
          <el-tooltip content="常见的奶茶/咖啡规格：杯型影响加价，温度、糖度为必选偏好" placement="top">
            <el-icon class="help-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
          <div class="spec-head-right">
            <el-button :icon="Plus" @click="addGroup">新增规格组</el-button>
          </div>
        </div>

        <!-- 快捷模板 -->
        <div class="tpl-bar">
          <span class="tpl-label">一键添加模板：</span>
          <el-button
            v-for="t in TEMPLATES"
            :key="t.label"
            size="small"
            round
            plain
            type="primary"
            @click="applyTemplate(t)"
          >
            {{ t.label }}
          </el-button>
        </div>

        <!-- 空态 -->
        <el-empty
          v-if="!form.specGroups.length"
          description="暂无规格组：点击上方模板一键添加，或手动「新增规格组」"
          :image-size="90"
        />

        <!-- 规格组列表 -->
        <TransitionGroup name="sg" tag="div" class="group-list">
          <div v-for="(g, gi) in form.specGroups" :key="gi" class="spec-group">
            <!-- 组头 -->
            <div class="sg-head">
              <span class="sg-index">{{ gi + 1 }}</span>
              <el-input v-model="g.name" placeholder="规格组名称，如：杯型" maxlength="10" class="sg-name" />
              <div class="sg-required">
                <span>必选</span>
                <el-switch v-model="g.required" :active-value="1" :inactive-value="0" size="small" />
              </div>
              <div class="sg-actions">
                <el-tooltip content="上移" placement="top">
                  <el-button :icon="ArrowUp" circle size="small" :disabled="gi === 0" @click="moveGroup(gi, -1)" />
                </el-tooltip>
                <el-tooltip content="下移" placement="top">
                  <el-button :icon="ArrowDown" circle size="small" :disabled="gi === form.specGroups.length - 1" @click="moveGroup(gi, 1)" />
                </el-tooltip>
                <el-tooltip content="删除规格组" placement="top">
                  <el-button :icon="Delete" circle size="small" type="danger" plain @click="removeGroup(gi)" />
                </el-tooltip>
              </div>
            </div>

            <!-- 选项行 -->
            <div class="opt-rows">
              <div v-for="(o, oi) in g.options" :key="oi" class="opt-row">
                <el-input v-model="o.name" placeholder="选项名称，如：大杯" maxlength="12" class="opt-name" />
                <div class="opt-delta">
                  <span class="opt-delta-label">加价</span>
                  <el-input-number
                    v-model="o.priceDeltaYuan"
                    :min="0"
                    :max="999"
                    :precision="1"
                    :step="0.5"
                    controls-position="right"
                    class="opt-delta-input"
                  />
                  <span class="opt-delta-unit">元</span>
                </div>
                <div class="opt-actions">
                  <el-button :icon="ArrowUp" circle size="small" text :disabled="oi === 0" @click="moveOption(gi, oi, -1)" />
                  <el-button :icon="ArrowDown" circle size="small" text :disabled="oi === g.options.length - 1" @click="moveOption(gi, oi, 1)" />
                  <el-button :icon="Close" circle size="small" text type="danger" @click="removeOption(gi, oi)" />
                </div>
              </div>

              <el-button class="add-opt" :icon="Plus" @click="addOption(gi)">添加选项</el-button>
            </div>
          </div>
        </TransitionGroup>
      </div>
    </div>

    <!-- 底部保存条 -->
    <div class="edit-footer">
      <div class="footer-inner card">
        <span class="footer-summary">
          共 {{ form.specGroups.length }} 个规格组 ·
          {{ form.specGroups.reduce((s, g) => s + g.options.length, 0) }} 个选项
        </span>
        <div class="footer-btns">
          <el-button size="large" @click="router.back()">取消</el-button>
          <el-button type="primary" size="large" :loading="saveLoading" @click="onSave">
            {{ isNew ? '保存并返回' : '保存修改' }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules, UploadRequestOptions } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import {
  ArrowLeft, ArrowUp, ArrowDown, Close, Delete, Plus, QuestionFilled, RefreshRight,
} from '@element-plus/icons-vue'
import { addDrink, getDrink, updateDrink } from '@/api/drink'
import { getCategoryList } from '@/api/category'
import { uploadImage } from '@/api/upload'
import type { Category, DrinkDTO, SpecGroup } from '@/types'

/* ---------- 路由与状态 ---------- */
const route = useRoute()
const router = useRouter()
const id = route.params.id ? Number(route.params.id) : undefined
const isNew = computed(() => id === undefined)

const pageLoading = ref(false)
const saveLoading = ref(false)
const uploadLoading = ref(false)
const categories = ref<Category[]>([])
const formRef = ref<FormInstance>()

/* 编辑态：价格与加价均以「元」呈现，提交时统一转分 */
interface EditOption {
  name: string
  priceDeltaYuan: number
}
interface EditGroup {
  name: string
  required: number
  options: EditOption[]
}

const form = reactive({
  name: '',
  categoryId: undefined as number | undefined,
  priceYuan: undefined as number | undefined,
  image: '',
  description: '',
  featured: 0,
  specGroups: [] as EditGroup[],
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入饮品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择所属分类', trigger: 'change' }],
  priceYuan: [{ required: true, message: '请输入售价', trigger: 'blur' }],
}

/* ---------- 规格模板（参考奶茶点单常见规格） ---------- */
function tplGroup(name: string, options: Array<[string, number]>, required = 1): EditGroup {
  return { name, required, options: options.map(([n, p]) => ({ name: n, priceDeltaYuan: p })) }
}

const TEMPLATES: Array<{ label: string; groups: EditGroup[] }> = [
  { label: '杯型', groups: [tplGroup('杯型', [['中杯', 0], ['大杯', 3]])] },
  { label: '温度', groups: [tplGroup('温度', [['热', 0], ['冰', 0], ['去冰', 0]])] },
  { label: '糖度', groups: [tplGroup('糖度', [['全糖', 0], ['七分糖', 0], ['五分糖', 0], ['三分糖', 0], ['无糖', 0]])] },
  { label: '浓度', groups: [tplGroup('浓度', [['标准', 0], ['加浓', 2]])] },
  {
    label: '饮品全套（四组）',
    groups: [
      tplGroup('杯型', [['中杯', 0], ['大杯', 3]]),
      tplGroup('温度', [['热', 0], ['冰', 0], ['去冰', 0]]),
      tplGroup('糖度', [['全糖', 0], ['七分糖', 0], ['五分糖', 0], ['三分糖', 0], ['无糖', 0]]),
      tplGroup('浓度', [['标准', 0], ['加浓', 2]]),
    ],
  },
]

function applyTemplate(tpl: { label: string; groups: EditGroup[] }) {
  let added = 0
  let skipped = 0
  for (const g of tpl.groups) {
    if (form.specGroups.some((x) => x.name === g.name)) {
      skipped++
      continue
    }
    form.specGroups.push(JSON.parse(JSON.stringify(g)) as EditGroup)
    added++
  }
  if (added) ElMessage.success(`已添加 ${added} 个规格组`)
  if (skipped) ElMessage.warning(`已存在同名规格组，跳过 ${skipped} 个`)
}

/* ---------- 规格组操作 ---------- */
function addGroup() {
  form.specGroups.push({ name: '', required: 1, options: [{ name: '', priceDeltaYuan: 0 }] })
}

function removeGroup(gi: number) {
  ElMessageBox.confirm('确定删除该规格组及其全部选项吗？', '删除规格组', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      form.specGroups.splice(gi, 1)
    })
    .catch(() => undefined)
}

function moveGroup(gi: number, dir: -1 | 1) {
  const target = gi + dir
  if (target < 0 || target >= form.specGroups.length) return
  const [g] = form.specGroups.splice(gi, 1)
  form.specGroups.splice(target, 0, g)
}

function addOption(gi: number) {
  form.specGroups[gi].options.push({ name: '', priceDeltaYuan: 0 })
}

function removeOption(gi: number, oi: number) {
  form.specGroups[gi].options.splice(oi, 1)
}

function moveOption(gi: number, oi: number, dir: -1 | 1) {
  const opts = form.specGroups[gi].options
  const target = oi + dir
  if (target < 0 || target >= opts.length) return
  const [o] = opts.splice(oi, 1)
  opts.splice(target, 0, o)
}

/* ---------- 图片上传（mock: File→base64；真实: /admin/common/upload） ---------- */
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
    // uploadImage 直接返回 url 字符串（mock 为 base64 dataURL，真实为服务端地址）
    form.image = await uploadImage(file)
    ElMessage.success('图片已就绪')
  } catch {
    /* 已提示 */
  } finally {
    uploadLoading.value = false
  }
}

/* ---------- 回显 / 保存 ---------- */
async function loadDrink() {
  if (isNew.value) {
    // 新增：默认预填常见规格组（杯型/温度/糖度）
    form.specGroups = JSON.parse(JSON.stringify([TEMPLATES[0].groups[0], TEMPLATES[1].groups[0], TEMPLATES[2].groups[0]])) as EditGroup[]
    return
  }
  pageLoading.value = true
  try {
    const d = await getDrink(id as number)
    form.name = d.name
    form.categoryId = d.categoryId
    // v2.1 契约：价格单位元，后端 BigDecimal 直出即元，无需再 ÷100（旧分口径遗留已清除）
    form.priceYuan = d.price
    form.image = d.image
    form.description = d.description
    form.featured = d.featured
    form.specGroups = (d.specGroups ?? []).map((g) => ({
      name: g.name,
      required: g.required,
      options: (g.options ?? []).map((o) => ({
        name: o.name,
        priceDeltaYuan: o.priceDelta,
      })),
    }))
  } catch {
    /* 已提示 */
  } finally {
    pageLoading.value = false
  }
}

function validateSpecs(): boolean {
  for (let gi = 0; gi < form.specGroups.length; gi++) {
    const g = form.specGroups[gi]
    if (!g.name.trim()) {
      ElMessage.warning(`第 ${gi + 1} 个规格组还没有名称`)
      return false
    }
    if (!g.options.length) {
      ElMessage.warning(`规格组「${g.name}」至少需要一个选项`)
      return false
    }
    const names = new Set<string>()
    for (const o of g.options) {
      if (!o.name.trim()) {
        ElMessage.warning(`规格组「${g.name}」存在未命名的选项`)
        return false
      }
      if (names.has(o.name.trim())) {
        ElMessage.warning(`规格组「${g.name}」中选项「${o.name}」重复`)
        return false
      }
      names.add(o.name.trim())
    }
  }
  return true
}

async function onSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!validateSpecs()) return

  // 组装 DrinkDTO：价格/加价直接按元提交（v2.1：后端 BigDecimal 单位元）；组内字段名 options 与 API-SPEC 一致（v2.0 不再叫 specOptions）
  const payload: DrinkDTO = {
    id: isNew.value ? undefined : id,
    name: form.name.trim(),
    categoryId: form.categoryId as number,
    price: form.priceYuan ?? 0,
    image: form.image,
    description: form.description.trim(),
    featured: form.featured,
    specGroups: form.specGroups.map((g, gi): SpecGroup => ({
      name: g.name.trim(),
      required: g.required,
      sort: gi + 1,
      options: g.options.map((o, oi) => ({
        name: o.name.trim(),
        priceDelta: o.priceDeltaYuan || 0,
        sort: oi + 1,
      })),
    })),
  }

  saveLoading.value = true
  try {
    if (isNew.value) {
      await addDrink(payload)
      ElMessage.success('饮品已创建')
    } else {
      await updateDrink(payload)
      ElMessage.success('饮品已保存')
    }
    router.push('/drink')
  } catch {
    /* 已提示 */
  } finally {
    saveLoading.value = false
  }
}

onMounted(async () => {
  try {
    categories.value = await getCategoryList(1)
  } catch {
    /* 已提示 */
  }
  loadDrink()
})
</script>

<style scoped>
.header-left {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.back-btn {
  margin-top: 2px;
  border-color: #e8dccd;
  color: #8b5e3c;
}

.edit-grid {
  display: grid;
  grid-template-columns: 400px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  padding-bottom: 86px;
}

.w-full {
  width: 100%;
}

.form-tip {
  font-size: 12px;
  color: #b3a08e;
  line-height: 1.6;
  margin-top: 2px;
}

.switch-tip {
  margin-left: 10px;
}

/* ---------- 图片上传 ---------- */
.image-box {
  width: 132px;
  height: 132px;
  border-radius: 14px;
  border: 1.5px dashed #d8c9b8;
  background: #fdfaf6;
  overflow: hidden;
  position: relative;
  cursor: pointer;
  transition: border-color 0.2s;
}

.image-box:hover {
  border-color: #8b5e3c;
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
  gap: 6px;
  color: #b3a08e;
  font-size: 12px;
}

.image-mask {
  position: absolute;
  inset: 0;
  background: rgba(63, 42, 26, 0.55);
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

/* ---------- 规格组编辑器 ---------- */
.spec-head {
  justify-content: space-between;
}

.help-icon {
  color: #b3a08e;
  cursor: help;
  margin-right: auto;
  margin-left: -2px;
  align-self: center;
}

.spec-head-right {
  display: flex;
  gap: 8px;
}

.tpl-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 12px;
  background: #faf5ee;
  border: 1px solid #f3ede3;
  border-radius: 12px;
  margin-bottom: 14px;
}

.tpl-label {
  font-size: 12px;
  color: #8c7a6b;
  font-weight: 600;
}

.group-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.spec-group {
  border: 1px solid #f0e6da;
  background: #fcfaf7;
  border-radius: 14px;
  padding: 14px 16px 12px;
  transition: box-shadow 0.2s;
}

.spec-group:hover {
  box-shadow: 0 4px 14px rgba(63, 42, 26, 0.07);
}

.sg-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sg-index {
  width: 22px;
  height: 22px;
  border-radius: 8px;
  background: linear-gradient(135deg, #c08552, #8b5e3c);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sg-name {
  width: 220px;
}

.sg-required {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #8c7a6b;
  margin-left: 4px;
}

.sg-actions {
  margin-left: auto;
  display: flex;
  gap: 6px;
}

/* 选项行 */
.opt-rows {
  margin-top: 12px;
  padding-left: 32px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.opt-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 8px;
  align-items: center;
  background: #ffffff;
  border: 1px solid #f3ede3;
  border-radius: 10px;
  padding: 8px 10px;
}

.opt-row:hover {
  border-color: #dccfc5;
}

.opt-name {
  min-width: 160px;
}

.opt-delta {
  display: flex;
  align-items: center;
  gap: 6px;
}

.opt-delta-label {
  font-size: 12px;
  color: #8c7a6b;
}

.opt-delta-input {
  width: 120px;
}

.opt-delta-unit {
  font-size: 12px;
  color: #8c7a6b;
}

.opt-actions {
  display: flex;
  gap: 2px;
}

.add-opt {
  width: 100%;
  border-style: dashed;
  color: #8b5e3c;
  --el-button-hover-bg-color: #fdfaf6;
}

/* 列表过渡 */
.sg-enter-active,
.sg-leave-active {
  transition: all 0.25s ease;
}
.sg-enter-from,
.sg-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* ---------- 底部保存条 ---------- */
.edit-footer {
  position: fixed;
  left: 224px;
  right: 0;
  bottom: 0;
  padding: 12px 22px 16px;
  background: linear-gradient(180deg, rgba(250, 246, 241, 0), rgba(250, 246, 241, 0.9) 40%);
  z-index: 15;
}

.footer-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  max-width: 980px;
}

.footer-summary {
  font-size: 13px;
  color: #8c7a6b;
}

.footer-btns {
  display: flex;
  gap: 10px;
}

@media (max-width: 1100px) {
  .edit-grid {
    grid-template-columns: 1fr;
  }
  .edit-footer {
    left: 0;
  }
}
</style>
