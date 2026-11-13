<template>
  <div class="login-page">
    <div class="blob blob-1" />
    <div class="blob blob-2" />

    <div class="login-card">
      <!-- 品牌区 -->
      <div class="brand">
        <div class="brand-seal">茶</div>
        <h1 class="brand-cn">山月茶咖</h1>
        <div class="brand-en">SHANYUE TEA &amp; COFFEE</div>
        <div class="brand-slogan">山月入杯，茶咖相宜</div>
      </div>

      <el-divider class="brand-divider" />

      <!-- 登录表单 -->
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onSubmit">
        <el-form-item prop="username">
          <el-input v-model.trim="form.username" placeholder="用户名" :prefix-icon="User" autocomplete="username" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model.trim="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button class="login-btn" type="primary" size="large" :loading="loading" @click="onSubmit">
          {{ loading ? '登录中…' : '登 录' }}
        </el-button>
      </el-form>

      <div class="login-tip">
        演示账号：admin / 123456（教师演示账号，mock 模式下直接校验）
      </div>
    </div>

    <div class="login-footer">山月茶咖 · 管理后台 · 配套苍穹外卖改造版后端</div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/employee'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const info = await login({ username: form.username, password: form.password })
    userStore.setLogin(info)
    ElMessage.success(`欢迎回来，${info.name}`)
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch {
    /* 业务错误已由拦截器 ElMessage.error 提示 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(1200px 600px at 85% -10%, rgba(192, 133, 82, 0.16), transparent 60%),
    radial-gradient(900px 500px at -10% 110%, rgba(139, 94, 60, 0.14), transparent 55%),
    #faf6f1;
}

/* 装饰光斑 */
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.5;
  pointer-events: none;
}
.blob-1 {
  width: 340px;
  height: 340px;
  background: rgba(192, 133, 82, 0.35);
  top: 8%;
  left: 12%;
}
.blob-2 {
  width: 300px;
  height: 300px;
  background: rgba(95, 141, 107, 0.22);
  bottom: 6%;
  right: 14%;
}

.login-card {
  width: 420px;
  background: #ffffff;
  border: 1px solid #f3ede3;
  border-radius: 20px;
  box-shadow: 0 12px 40px rgba(63, 42, 26, 0.1);
  padding: 40px 40px 28px;
  position: relative;
  z-index: 1;
}

/* 品牌区：书法感「山月」 */
.brand {
  text-align: center;
}

.brand-seal {
  width: 44px;
  height: 44px;
  margin: 0 auto 12px;
  border-radius: 12px;
  background: linear-gradient(135deg, #c08552, #8b5e3c);
  color: #faf6f1;
  font-family: 'Noto Serif SC', 'Songti SC', 'SimSun', serif;
  font-size: 24px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 16px rgba(139, 94, 60, 0.35);
}

.brand-cn {
  font-family: 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', 'STSong', 'SimSun', serif;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 10px;
  color: #3f2a1a;
  text-indent: 10px; /* 抵消最后一个字的字距，保持视觉居中 */
}

.brand-en {
  margin-top: 8px;
  font-size: 11px;
  letter-spacing: 4px;
  color: #8b5e3c;
  font-weight: 600;
}

.brand-slogan {
  margin-top: 6px;
  font-size: 12px;
  letter-spacing: 2px;
  color: #b3a08e;
}

.brand-divider {
  margin: 24px 0 22px;
}

.login-btn {
  width: 100%;
  margin-top: 4px;
  letter-spacing: 6px;
  height: 44px;
  font-size: 15px;
}

.login-tip {
  margin-top: 16px;
  text-align: center;
  font-size: 12px;
  color: #b3a08e;
}

.login-footer {
  position: absolute;
  bottom: 22px;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 12px;
  color: #b3a08e;
  letter-spacing: 1px;
}
</style>
