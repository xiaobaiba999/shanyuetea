// app.js —— 小程序入口
// 职责：登录态不做全局强拉（由 menu 页 onShow 守卫 + 登录页引导，保证「先登录后下单」）；
//       globalData 存放门店静态信息，供 submit / me / order-detail 等页面复用。
App({
  globalData: {
    // 山月茶咖门店信息（静态演示值；真实项目可从后端店铺接口扩展返回）
    shop: {
      name: '山月茶咖·科创园区店',
      slogan: '山月入杯，茶咖相宜',
      businessHours: '09:00 - 22:00',
      address: '广州市天河区科创路 88 号 · 科创园区 A 座一层',
      phone: '020-8888-6688'
    }
  },
  onLaunch() {
    // 预热：可在此做版本更新检查等，暂保持最简
  }
});
