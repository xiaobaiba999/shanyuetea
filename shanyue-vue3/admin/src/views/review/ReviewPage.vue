<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">评价管理</h2>
        <p class="page-sub">C 端取餐顾客的订单评价；及时回复低分评价、隐藏不当内容，会同步影响 C 端评价墙与门店评分</p>
      </div>
    </div>

    <!-- 口碑概览小卡 -->
    <div class="stat-row">
      <div class="stat-chip">
        <span class="stat-num amber">{{ stats?.avgScore.toFixed(1) ?? '-' }}</span>
        <span class="stat-label">门店平均分</span>
      </div>
      <div class="stat-chip">
        <span class="stat-num">{{ stats?.total ?? 0 }}</span>
        <span class="stat-label">评价总数</span>
      </div>
      <div class="stat-chip">
        <span class="stat-num green">{{ stats ? stats.goodRate + '%' : '-' }}</span>
        <span class="stat-label">好评率（4/5 星）</span>
      </div>
      <div class="stat-chip" :class="{ attention: (stats?.pendingReply ?? 0) > 0 }">
        <span class="stat-num" :class="{ danger: (stats?.pendingReply ?? 0) > 0 }">{{ stats?.pendingReply ?? 0 }}</span>
        <span class="stat-label">待回复（{{ PENDING_SCORE_MAX }} 星及以下）</span>
      </div>
    </div>

    <!-- 星级分布 + 标签热度 -->
    <div class="insight-row">
      <div class="card card-pad insight-card">
        <div class="card-title-bar"><span class="card-title">星级分布</span></div>
        <div class="dist-rows">
          <div v-for="(n, i) in stats?.distribution ?? []" :key="i" class="dist-row">
            <span class="dr-label">{{ 5 - i }} 星</span>
            <div class="dr-track">
              <div class="dr-fill" :style="{ width: distPct(n) }" />
            </div>
            <span class="dr-num">{{ n }}</span>
          </div>
        </div>
      </div>
      <div class="card card-pad insight-card">
        <div class="card-title-bar"><span class="card-title">标签热度</span></div>
        <div v-if="stats?.tagStats.length" class="tag-cloud">
          <span v-for="t in stats.tagStats" :key="t.tag" class="tc-chip">
            {{ t.tag }} <i>{{ t.count }}</i>
          </span>
        </div>
        <div v-else class="insight-empty">暂无标签数据</div>
      </div>
    </div>

    <div class="card table-wrap">
      <!-- 搜索区 -->
      <div class="search-bar">
        <el-select v-model="query.score" placeholder="星级" clearable style="width: 130px" @change="onSearch">
          <el-option v-for="s in 5" :key="s" :label="`${s} 星`" :value="s" />
        </el-select>
        <el-select v-model="query.hidden" placeholder="展示状态" clearable style="width: 140px" @change="onSearch">
          <el-option label="正常展示" :value="0" />
          <el-option label="已隐藏" :value="1" />
        </el-select>
        <el-input
          v-model.trim="query.keyword"
          placeholder="评价内容 / 订单号 / 取餐号"
          clearable
          style="width: 230px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" plain @click="onReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column label="订单" width="128" align="center">
          <template #default="{ row }">
            <div class="order-cell">
              <span class="pickup">{{ row.pickUpCode }}</span>
              <span class="order-no">{{ row.orderNumber }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="128" align="center">
          <template #default="{ row }">
            <span class="stars" :class="`s${row.score}`">{{ '\u2605'.repeat(row.score) }}{{ '\u2606'.repeat(5 - row.score) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="280">
          <template #default="{ row }">
            <div class="review-cell" :class="{ hidden: row.visible === 0 }">
              <p class="review-content">{{ row.content || '（未填写文字）' }}</p>
              <div v-if="row.tags.length" class="review-tags">
                <span v-for="t in row.tags" :key="t" class="rtag">{{ t }}</span>
              </div>
              <p v-if="row.reply" class="review-reply"><b>商家回复：</b>{{ row.reply }}</p>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.visible === 1" type="success" effect="light" round>展示中</el-tag>
            <el-tag v-else type="info" effect="plain" round>已隐藏</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评价时间" width="170" align="center">
          <template #default="{ row }">
            <span class="text-secondary time-cell">{{ row.createTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openReply(row)">{{ row.reply ? '改回复' : '回复' }}</el-button>
              <el-button link :type="row.visible === 1 ? 'danger' : 'success'" @click="onToggleVisible(row)">
                {{ row.visible === 1 ? '隐藏' : '恢复' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <div class="table-empty"><el-empty description="暂无符合条件的评价" :image-size="72" /></div>
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

    <!-- 回复弹窗 -->
    <el-dialog v-model="replyVisible" title="回复评价" width="480px" destroy-on-close>
      <div v-if="replyRow" class="reply-target">
        <span class="stars" :class="`s${replyRow.score}`">{{ '\u2605'.repeat(replyRow.score) }}{{ '\u2606'.repeat(5 - replyRow.score) }}</span>
        <p class="reply-quote">{{ replyRow.content || '（未填写文字）' }}</p>
      </div>
      <el-input
        v-model="replyText"
        type="textarea"
        :rows="3"
        maxlength="50"
        show-word-limit
        placeholder="回复会展示在 C 端评价下方，50 字以内"
      />
      <template #footer>
        <el-button plain @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="replyLoading" @click="onSaveReply">发布回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { Refresh, Search } from '@element-plus/icons-vue'
import type { Review, ReviewStats } from '@/types'
import { getReviewPage, getReviewStats, replyReview, toggleReviewVisible } from '@/api/review'
import { PENDING_SCORE_MAX } from '@/mock/review'

/* ---------------- 列表查询 ---------------- */
const loading = ref(false)
const records = ref<Review[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  pageSize: 10,
  score: undefined as number | undefined,
  hidden: undefined as number | undefined,
  keyword: '',
})

async function fetchPage() {
  loading.value = true
  try {
    const res = await getReviewPage({ ...query, score: query.score ?? 0, hidden: query.hidden })
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
  query.score = undefined
  query.hidden = undefined
  query.keyword = ''
  onSearch()
}

function onSizeChange() {
  query.page = 1
  fetchPage()
}

/* ---------------- 统计 ---------------- */
const stats = ref<ReviewStats | null>(null)
const maxDist = ref(1)

async function fetchStats() {
  try {
    stats.value = await getReviewStats()
  } catch {
    /* 已提示 */
  }
}

function distPct(n: number): string {
  return `${Math.round((n / maxDist.value) * 100)}%`
}

function computeMaxDist() {
  maxDist.value = Math.max(1, ...(stats.value?.distribution ?? [1]))
}

function loadAll() {
  void fetchStats().then(computeMaxDist)
  void fetchPage()
}

/* ---------------- 回复 ---------------- */
const replyVisible = ref(false)
const replyLoading = ref(false)
const replyRow = ref<Review | null>(null)
const replyText = ref('')

function openReply(row: Review) {
  replyRow.value = row
  replyText.value = row.reply || ''
  replyVisible.value = true
}

async function onSaveReply() {
  const row = replyRow.value
  if (!row) return
  if (!replyText.value.trim()) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  replyLoading.value = true
  try {
    await replyReview(row.id, replyText.value)
    ElMessage.success('回复已发布，C 端评价下方同步展示')
    replyVisible.value = false
    loadAll()
  } catch {
    /* 已提示 */
  } finally {
    replyLoading.value = false
  }
}

/* ---------------- 隐藏 / 恢复 ---------------- */
async function onToggleVisible(row: Review) {
  const hiding = row.visible === 1
  try {
    await ElMessageBox.confirm(
      hiding
        ? '隐藏后该评价不在 C 端评价墙展示、不参与评分统计，可随时恢复。确定隐藏？'
        : '恢复后该评价重新在 C 端展示并计入评分。确定恢复？',
      hiding ? '隐藏评价' : '恢复评价',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await toggleReviewVisible(row.id, hiding ? 0 : 1)
    ElMessage.success(hiding ? '已隐藏' : '已恢复展示')
    loadAll()
  } catch {
    /* 已提示 */
  }
}

onMounted(loadAll)
</script>

<style scoped>
/* ---------- 口碑概览小卡 ---------- */
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
  background: var(--card);
  border: 1px solid var(--card-line);
  box-shadow: 0 1px 4px rgba(43, 38, 32, 0.06);
}

.stat-chip.attention {
  border-color: var(--el-color-warning-light-5);
  background: var(--el-color-warning-light-9);
}

.stat-num {
  font-family: var(--font-serif);
  font-size: 22px;
  font-weight: 700;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
}

.stat-num.amber {
  color: var(--gold);
}

.stat-num.green {
  color: var(--success);
}

.stat-num.danger {
  color: var(--danger);
}

.stat-label {
  font-size: 12px;
  color: #8c7a6b;
}

/* ---------- 星级分布 / 标签热度 ---------- */
.insight-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}

@media (max-width: 900px) {
  .insight-row {
    grid-template-columns: 1fr;
  }
}

.insight-card {
  min-height: 148px;
}

.dist-rows {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: 4px;
}

.dist-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: #8c7a6b;
}

.dr-label {
  flex-shrink: 0;
  width: 30px;
  text-align: right;
}

.dr-track {
  flex: 1;
  height: 8px;
  border-radius: 999px;
  background: var(--paper-deep);
  overflow: hidden;
}

.dr-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--gold-soft), var(--gold));
  transition: width 0.4s ease;
}

.dr-num {
  flex-shrink: 0;
  width: 22px;
  font-weight: 600;
  color: var(--ink-2);
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.tc-chip {
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-warning-dark-2);
  border-radius: 999px;
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-7);
}

.tc-chip i {
  font-style: normal;
  font-weight: 800;
  margin-left: 2px;
  color: var(--terracotta);
}

.insight-empty {
  padding: 26px 0;
  text-align: center;
  font-size: 12px;
  color: #8c7a6b;
}

/* ---------- 表格 ---------- */
.table-wrap {
  padding: 16px 18px 8px;
}

.search-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.order-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.pickup {
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 1px;
  color: var(--el-color-warning-dark-2);
  border-radius: 8px;
  background: var(--el-color-warning-light-8);
}

.order-no {
  font-size: 11px;
  color: #8c7a6b;
  font-family: 'SFMono-Regular', Consolas, monospace;
}

/* 星级：颜色随分值（茶金 → 暖陶） */
.stars {
  font-size: 15px;
  letter-spacing: 2px;
}

.stars.s5,
.stars.s4 {
  color: var(--gold);
}

.stars.s3 {
  color: var(--gold-soft);
}

.stars.s2 {
  color: var(--terracotta);
}

.stars.s1 {
  color: var(--danger);
}

.review-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.review-cell.hidden .review-content {
  text-decoration: line-through;
  color: #a99b8b;
}

.review-content {
  font-size: 13px;
  line-height: 1.6;
  color: var(--ink);
}

.review-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.rtag {
  padding: 2px 8px;
  font-size: 11px;
  color: var(--el-color-warning-dark-2);
  border-radius: 999px;
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-7);
}

/* 商家回复气泡：松雾底 + 墨绿标签 */
.review-reply {
  padding: 6px 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--ink-2);
  border-radius: 8px;
  background: var(--pine-mist);
}

.review-reply b {
  color: var(--pine);
}

.time-cell {
  font-size: 12px;
}

/* ---------- 回复弹窗 ---------- */
.reply-target {
  margin-bottom: 12px;
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--paper);
}

.reply-quote {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--ink-2);
}
</style>
