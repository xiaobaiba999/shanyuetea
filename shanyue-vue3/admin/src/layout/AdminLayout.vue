<template>
  <div class="layout">
    <!-- 左侧深咖色固定侧边栏 -->
    <aside class="sidebar">
      <div class="logo" @click="router.push('/dashboard')">
        <div class="logo-cn">山月</div>
        <div class="logo-en">SHANYUE TEA &amp; COFFEE</div>
        <div class="logo-slogan">山月入杯 · 茶咖相宜</div>
      </div>

      <el-menu class="side-menu" :default-active="activeMenu" router>
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>今日数据</span>
        </el-menu-item>
        <el-menu-item index="/employee">
          <el-icon><User /></el-icon>
          <span>员工管理</span>
        </el-menu-item>
        <el-menu-item index="/category">
          <el-icon><Grid /></el-icon>
          <span>分类管理</span>
        </el-menu-item>
        <el-menu-item index="/drink">
          <el-icon><CoffeeCup /></el-icon>
          <span>饮品管理</span>
        </el-menu-item>
        <el-menu-item index="/setmeal">
          <el-icon><ForkSpoon /></el-icon>
          <span>套餐管理</span>
        </el-menu-item>
        <el-menu-item index="/order">
          <el-icon><Tickets /></el-icon>
          <span>订单管理</span>
        </el-menu-item>
        <el-menu-item index="/report">
          <el-icon><TrendCharts /></el-icon>
          <span>数据统计</span>
        </el-menu-item>
        <el-menu-item index="/coupon">
          <el-icon><Discount /></el-icon>
          <span>优惠券管理</span>
        </el-menu-item>
        <el-menu-item index="/review">
          <el-icon><Star /></el-icon>
          <span>评价管理</span>
        </el-menu-item>
        <el-menu-item index="/shop">
          <el-icon><Setting /></el-icon>
          <span>店铺设置</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <span>山月茶咖 · B端管理系统</span>
      </div>
    </aside>

    <!-- 右侧主区 -->
    <div class="main">
      <header class="topbar">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item>{{ route.meta?.title || '' }}</el-breadcrumb-item>
        </el-breadcrumb>

        <div class="topbar-right">
          <!-- 来单提醒模拟开关（mock 模式专属） -->
          <el-tooltip
            v-if="showSimToggle"
            :content="simOn ? '来单提醒模拟已开启（每 45 秒模拟一单，点击关闭）' : '来单提醒模拟已关闭，点击开启'"
            placement="bottom"
          >
            <button class="sim-badge" :class="{ on: simOn }" @click="onToggleSim">
              <el-icon><component :is="simOn ? BellFilled : Bell" /></el-icon>
              <span>{{ simOn ? '来单提醒 · 模拟中' : '来单提醒 · 已关' }}</span>
            </button>
          </el-tooltip>

          <!-- 营业状态徽标：点击切换 -->
          <el-tooltip content="点击切换营业状态" placement="bottom">
            <button class="shop-badge" :class="{ closed: !shopStore.isOpen }" :disabled="shopStore.loading" @click="onToggleShop">
              <span class="dot" />
              <span>{{ shopStore.isOpen ? '营业中' : '已打烊' }}</span>
            </button>
          </el-tooltip>

          <!-- 管理员下拉 -->
          <el-dropdown trigger="click" @command="onCommand">
            <div class="admin-chip">
              <span class="avatar">{{ userStore.name?.charAt(0) || '管' }}</span>
              <span class="admin-name">{{ userStore.name || '管理员' }}</span>
              <el-icon class="arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item v-if="USE_MOCK" command="reset">重置演示数据</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <!-- 修改密码弹窗（后端契约：PUT /admin/employee/editPassword，教程 9.5 节） -->
          <el-dialog
            v-model="pwdDialogVisible"
            title="修改密码"
            width="400px"
            :close-on-click-modal="false"
            @closed="resetPwdForm"
          >
            <el-form label-width="88px" @submit.prevent>
              <el-form-item label="旧密码" required>
                <el-input
                  v-model="pwdForm.oldPassword"
                  type="password"
                  show-password
                  placeholder="请输入当前密码"
                  maxlength="32"
                />
              </el-form-item>
              <el-form-item label="新密码" required>
                <el-input
                  v-model="pwdForm.newPassword"
                  type="password"
                  show-password
                  placeholder="至少 6 位"
                  maxlength="32"
                />
              </el-form-item>
              <el-form-item label="确认新密码" required>
                <el-input
                  v-model="pwdForm.confirmPassword"
                  type="password"
                  show-password
                  placeholder="再输一遍新密码"
                  maxlength="32"
                  @keyup.enter="onChangePassword"
                />
              </el-form-item>
            </el-form>
            <div class="pwd-tip">改密成功后会退出登录，请用新密码重新登录；后端以登录态为准，前端无法改他人密码。</div>
            <template #footer>
              <el-button @click="pwdDialogVisible = false">取消</el-button>
              <el-button type="primary" :loading="pwdSubmitting" @click="onChangePassword">确认修改</el-button>
            </template>
          </el-dialog>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { Bell, BellFilled, Odometer, User, Grid, CoffeeCup, ForkSpoon, Tickets, TrendCharts, Setting, Discount, Star, ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useShopStore } from '@/stores/shop'
import { editPassword as editPasswordApi } from '@/api/employee'
import { resetMockData } from '@/mock/db'
import { USE_MOCK } from '@/utils/env'
import { isSimEnabled, setSimEnabled, startSim, stopSim, exposeTestHook } from '@/mock/order-simulator'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const shopStore = useShopStore()

/** 饮品编辑页 /drink/edit/3 时也让「饮品管理」高亮 */
const activeMenu = computed(() => (route.path.startsWith('/drink') ? '/drink' : route.path))

/** 来单提醒模拟开关（仅 mock 模式显示；真实模式下由 WebSocket 推送取代） */
const simOn = ref(isSimEnabled())
const showSimToggle = USE_MOCK

function onToggleSim() {
  const next = !simOn.value
  setSimEnabled(next)
  simOn.value = next
  ElMessage.success(next ? '已开启来单提醒模拟（约 45s 一单）' : '已关闭来单提醒模拟')
}

onMounted(() => {
  shopStore.fetchStatus()
  if (USE_MOCK) {
    startSim(() => router.push('/order'))
    exposeTestHook()
  }
})

onUnmounted(() => {
  stopSim()
})

async function onToggleShop() {
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

async function onCommand(command: string) {
  if (command === 'password') {
    pwdDialogVisible.value = true
    return
  }
  if (command === 'logout') {
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
    return
  }
  if (command === 'reset') {
    try {
      await ElMessageBox.confirm(
        '将清空全部演示数据（员工/分类/饮品/套餐/订单/店铺）并恢复初始状态，页面会自动刷新。确定继续？',
        '重置演示数据',
        { confirmButtonText: '重置', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
    resetMockData()
    window.location.reload()
  }
}

/* ---------- 修改密码弹窗（教程 9.5 节契约：PUT /admin/employee/editPassword） ---------- */
const pwdDialogVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

/** 弹窗关闭后清空表单，避免下次打开残留上一次输入 */
function resetPwdForm() {
  pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  pwdSubmitting.value = false
}

async function onChangePassword() {
  const { oldPassword, newPassword, confirmPassword } = pwdForm.value
  if (!oldPassword || !newPassword || !confirmPassword) {
    ElMessage.warning('请把三个密码都填完整')
    return
  }
  if (newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (newPassword === oldPassword) {
    ElMessage.warning('新密码不能与旧密码相同')
    return
  }
  if (newPassword !== confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  pwdSubmitting.value = true
  try {
    // 后端契约：empId 以登录态为准（Controller 用 BaseContext 覆盖），前端只传两个密码
    await editPasswordApi({ oldPassword, newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    pwdDialogVisible.value = false
    // JWT 无状态：改密后旧 token 依然有效，强制重新登录更安全（教程 9.5 节有讲）
    userStore.logout()
    router.push('/login')
  } catch {
    // 失败提示已由 request/mock 统一 toast（如"旧密码不正确"），留在弹窗里让用户重试
  } finally {
    pwdSubmitting.value = false
  }
}
</script>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
}

/* ---------- 侧边栏 ---------- */
.sidebar {
  width: 224px;
  flex-shrink: 0;
  position: fixed;
  inset: 0 auto 0 0;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #46301e 0%, #3f2a1a 55%, #352313 100%);
  z-index: 20;
}

.logo {
  padding: 26px 20px 20px;
  cursor: pointer;
  border-bottom: 1px solid rgba(250, 246, 241, 0.08);
}

.logo-cn {
  font-family: 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', 'STSong', 'SimSun', serif;
  font-size: 40px;
  font-weight: 700;
  letter-spacing: 12px;
  line-height: 1.1;
  color: #faf6f1;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.35);
  margin-left: 2px;
}

.logo-en {
  margin-top: 8px;
  font-size: 10px;
  letter-spacing: 2.6px;
  color: rgba(250, 246, 241, 0.55);
  font-weight: 600;
}

.logo-slogan {
  margin-top: 6px;
  font-size: 11px;
  letter-spacing: 2px;
  color: #e9b968;
}

.side-menu {
  flex: 1;
  border-right: none;
  background: transparent;
  padding: 14px 0;
  overflow-y: auto;
}

.side-menu :deep(.el-menu-item) {
  height: 46px;
  margin: 4px 12px;
  border-radius: 10px;
  color: rgba(250, 246, 241, 0.68);
  transition: all 0.2s;
}

.side-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.07);
  color: #ffffff;
}

/* 菜单 active：琥珀色 */
.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(192, 133, 82, 0.34), rgba(192, 133, 82, 0.12));
  color: #e9b968;
  font-weight: 600;
}

.side-menu :deep(.el-menu-item .el-icon) {
  color: inherit;
}

.sidebar-footer {
  padding: 14px 20px;
  font-size: 11px;
  letter-spacing: 1px;
  color: rgba(250, 246, 241, 0.35);
  border-top: 1px solid rgba(250, 246, 241, 0.08);
}

/* ---------- 主区 ---------- */
.main {
  flex: 1;
  margin-left: 224px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: 60px;
  background: #fffdf9;
  border-bottom: 1px solid #f3ede3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 22px;
  position: sticky;
  top: 0;
  z-index: 10;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

/* 来单提醒模拟开关徽标 */
.sim-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px dashed #e3cba8;
  background: #fdf8ef;
  color: #a0763e;
  font-size: 12px;
  font-weight: 600;
  padding: 5px 12px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s;
}

.sim-badge.on {
  border-style: solid;
  background: #fbf1e2;
  color: #b0702e;
}

.sim-badge:hover {
  filter: brightness(0.97);
}

/* 营业状态徽标 */
.shop-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid #d9e8dc;
  background: #eff7f1;
  color: #4c7156;
  font-size: 12px;
  font-weight: 600;
  padding: 5px 12px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s;
}

.shop-badge .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #5f8d6b;
  box-shadow: 0 0 0 3px rgba(95, 141, 107, 0.18);
}

.shop-badge.closed {
  background: #f6f3f0;
  border-color: #e4ddd4;
  color: #8c7a6b;
}

.shop-badge.closed .dot {
  background: #b3a08e;
  box-shadow: 0 0 0 3px rgba(179, 160, 142, 0.18);
}

.shop-badge:hover {
  filter: brightness(0.97);
}

.shop-badge:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

/* 管理员 */
.admin-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 10px;
  transition: background 0.2s;
  outline: none;
}

.admin-chip:hover {
  background: #f8f2ea;
}

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, #c08552, #8b5e3c);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.admin-name {
  font-size: 13px;
  font-weight: 600;
  color: #3f2a1a;
}

.arrow {
  font-size: 12px;
  color: #8c7a6b;
}

/* 修改密码弹窗底部提示 */
.pwd-tip {
  margin: 4px 0 0;
  padding: 8px 12px;
  border-radius: 8px;
  background: #fdf8ef;
  border: 1px dashed #e3cba8;
  color: #a0763e;
  font-size: 12px;
  line-height: 1.6;
}

.content {
  flex: 1;
  min-width: 0;
}
</style>
