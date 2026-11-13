<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">店铺设置</h2>
        <p class="page-sub">营业状态与店铺公告（/api/shop/*）；门店信息为品牌档案常量</p>
      </div>
    </div>

    <div class="shop-grid">
      <!-- 营业状态 -->
      <div class="card card-pad status-card" :class="{ closed: !shopStore.isOpen }">
        <div class="status-left">
          <div class="status-icon">
            <el-icon :size="26"><AlarmClock /></el-icon>
          </div>
          <div>
            <div class="status-title">
              营业状态
              <span class="status-pill" :class="shopStore.isOpen ? 'open' : 'shut'">
                {{ shopStore.isOpen ? '营业中' : '已打烊' }}
              </span>
            </div>
            <div class="status-desc">
              {{ shopStore.isOpen ? 'C 端顾客可以正常浏览菜单并下单' : '打烊期间 C 端将无法下单，历史订单不受影响' }}
            </div>
          </div>
        </div>
        <el-switch
          :model-value="shopStore.isOpen"
          size="large"
          :loading="shopStore.loading"
          @change="onToggleStatus"
        />
      </div>

      <!-- 公告 -->
      <div class="card card-pad notice-card">
        <div class="card-title-bar"><span class="card-title">店铺公告</span></div>
        <el-input
          v-model.trim="notice"
          type="textarea"
          :rows="4"
          maxlength="100"
          show-word-limit
          placeholder="如：今日桂花限定买一送一，欢迎品尝"
        />
        <div class="notice-actions">
          <span class="notice-tip">公告会展示在 C 端门店条与订单页</span>
          <el-button type="primary" :loading="noticeSaving" @click="onSaveNotice">保存公告</el-button>
        </div>
      </div>

      <!-- 门店信息（品牌档案常量，与 C 端门店信息页同源文案） -->
      <div class="card card-pad info-card">
        <div class="card-title-bar"><span class="card-title">门店信息</span><span class="readonly-tip">只读 · 品牌档案</span></div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="门店名称">{{ SHOP_PROFILE.name }}</el-descriptions-item>
          <el-descriptions-item label="营业时间">{{ SHOP_PROFILE.openHours }}</el-descriptions-item>
          <el-descriptions-item label="门店地址">{{ SHOP_PROFILE.address }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ SHOP_PROFILE.phone }}</el-descriptions-item>
          <el-descriptions-item label="品牌口号">{{ SHOP_PROFILE.slogan }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { AlarmClock } from '@element-plus/icons-vue'
import { useShopStore } from '@/stores/shop'
import { getShopNotice, updateShopNotice } from '@/api/shop'

const shopStore = useShopStore()
const notice = ref('')
const noticeSaving = ref(false)

/** 品牌档案常量（契约 /api/shop/* 目前只提供营业状态与公告，档案字段暂无接口） */
const SHOP_PROFILE = {
  name: '山月茶咖 · 科创园区店',
  openHours: '09:00 - 22:00',
  address: '杭州市西湖区科创园区 3 号楼 A 座 1F',
  phone: '0571-88886666',
  slogan: '山月入杯，茶咖相宜',
} as const

async function loadNotice() {
  try {
    notice.value = await getShopNotice()
  } catch {
    /* 已提示 */
  }
}

async function onToggleStatus() {
  try {
    await ElMessageBox.confirm(
      shopStore.isOpen
        ? '打烊后 C 端将无法下单，确定切换为「已打烊」吗？'
        : '确定切换为「营业中」并恢复 C 端下单吗？',
      '切换营业状态',
      { confirmButtonText: '确定切换', cancelButtonText: '再想想', type: 'warning' },
    )
  } catch {
    return
  }
  await shopStore.toggle()
}

async function onSaveNotice() {
  noticeSaving.value = true
  try {
    await updateShopNotice(notice.value)
    ElMessage.success('公告已保存')
  } catch {
    /* 已提示 */
  } finally {
    noticeSaving.value = false
  }
}

onMounted(async () => {
  loadNotice()
  if (shopStore.status === null) shopStore.fetchStatus()
})
</script>

<style scoped>
.shop-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.status-card {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  transition: all 0.25s;
}

.status-card.closed {
  background: linear-gradient(135deg, #fffdf9, #faf5ee);
}

.status-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.status-icon {
  width: 54px;
  height: 54px;
  border-radius: 14px;
  background: linear-gradient(135deg, #7fa88b, #5f8d6b);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.status-card.closed .status-icon {
  background: linear-gradient(135deg, #b3a08e, #8c7a6b);
}

.status-title {
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-pill {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 999px;
}

.status-pill.open {
  color: #4c7156;
  background: #eff4f0;
  border: 1px solid #d5e3d9;
}

.status-pill.shut {
  color: #8c7a6b;
  background: #f6f3f0;
  border: 1px solid #e4ddd4;
}

.status-desc {
  margin-top: 6px;
  font-size: 13px;
  color: #8c7a6b;
}

.notice-actions {
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.notice-tip {
  font-size: 12px;
  color: #b3a08e;
}

.readonly-tip {
  font-size: 12px;
  color: #b3a08e;
}

.info-card :deep(.el-descriptions__label) {
  width: 96px;
  color: #8c7a6b;
}

@media (max-width: 1000px) {
  .shop-grid {
    grid-template-columns: 1fr;
  }
}
</style>
