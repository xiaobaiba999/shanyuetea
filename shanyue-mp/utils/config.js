/**
 * utils/config.js —— 全局配置（联调只需要改这一个文件）
 *
 * 新手要点：
 * 1. USE_MOCK = true  ：使用工程内置的演示数据（不需要启动后端，先看效果用）
 *    USE_MOCK = false ：请求真实后端接口（联调自己的 Java 工程时用）
 * 2. BASE_URL 是后端地址：
 *    - 微信开发者工具里模拟器跑，后端也在本机 → http://localhost:8080 可以直接用
 *    - 真机预览 → 必须改成「电脑在 WiFi 里的局域网 IP」，手机和电脑连同一个 WiFi
 *      Windows 查 IP：ipconfig 里的 IPv4 地址；macOS 查 IP：系统设置 → Wi-Fi → 详细信息
 * 3. DELIVERY_FEE 与后端「山月补全契约」一致：外送配送费 5 元
 */
const config = {
  BASE_URL: 'http://localhost:8080',
  // USE_MOCK = true  ：内置演示数据（含 v2 功能包 F1~F9，无需后端即可完整演示）
  // USE_MOCK = false ：走真实 Java 后端（v2 接口桩已返回与 mock 同构的样例数据，可渐进替换）
  USE_MOCK: true,
  DELIVERY_FEE: 5,
  // 配送模式编码（契约：1 门店自取 | 2 外送）
  MODE_PICKUP: 1,
  MODE_DELIVERY: 2,
  // 本地登录态 storage key（utils/auth.js 共用）
  USER_KEY: 'shanyue_user',
  // 记住上次选择的配送模式
  LAST_MODE_KEY: 'shanyue_last_mode'
};

module.exports = config;
