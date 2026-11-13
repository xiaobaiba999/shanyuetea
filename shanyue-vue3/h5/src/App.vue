<script setup lang="ts">
import { ConfigProvider } from 'vant'
import type { ConfigProviderThemeVars } from 'vant'

// UI 规范（API-SPEC 第 5 节）：主按钮焦糖 #C08552、深咖文字 #3F2A1A、奶油底 #FAF6F1
const themeVars: ConfigProviderThemeVars = {
  primaryColor: '#C08552',
  buttonPrimaryBackground: '#C08552',
  buttonPrimaryBorderColor: '#C08552',
  buttonRadius: '10px',
  fieldLabelColor: '#3F2A1A',
  stepsActiveColor: '#C08552',
  stepsFinishLineColor: '#C08552',
  tagPrimaryColor: '#C08552',
  toastBackground: 'rgba(63, 42, 26, 0.92)',
  toastTextColor: '#FAF6F1',
  dialogBorderRadius: '16px',
  stepperInputTextColor: '#3F2A1A',
  emptyDescriptionColor: '#A08B78',
}
</script>

<template>
  <ConfigProvider :theme-vars="themeVars">
    <div class="app-shell">
      <RouterView />
    </div>
  </ConfigProvider>
</template>
