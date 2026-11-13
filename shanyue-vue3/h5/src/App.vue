<script setup lang="ts">
import { ConfigProvider } from 'vant'
import type { ConfigProviderThemeVars } from 'vant'

// UI 主题（新中式「宣纸 · 墨绿 · 茶金」）：主按钮墨绿 #2F5233、墨色文字 #2B2620、宣纸底 #F6F3EC
const themeVars: ConfigProviderThemeVars = {
  primaryColor: '#2F5233',
  buttonPrimaryBackground: 'linear-gradient(160deg, #3A6140, #2F5233 55%, #243E28)',
  buttonPrimaryBorderColor: '#2F5233',
  buttonRadius: '10px',
  fieldLabelColor: '#2B2620',
  stepsActiveColor: '#2F5233',
  stepsFinishLineColor: '#2F5233',
  tagPrimaryColor: '#2F5233',
  toastBackground: 'rgba(43, 38, 32, 0.92)',
  toastTextColor: '#F6F3EC',
  dialogBorderRadius: '14px',
  stepperInputTextColor: '#2B2620',
  emptyDescriptionColor: '#9C927F',
}
</script>

<template>
  <ConfigProvider :theme-vars="themeVars">
    <div class="app-shell">
      <RouterView />
    </div>
  </ConfigProvider>
</template>
