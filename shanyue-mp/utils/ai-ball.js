/**
 * utils/ai-ball.js —— AI 悬浮球显隐帮助函数（各宿主页统一接入）
 *
 * 用法（页面 js 两步接入）：
 *   const aiBall = require('相对路径/utils/ai-ball');
 *   Page({
 *     // ① 页面级滚动（普通页面）：
 *     onPageScroll() { aiBall.onPageScroll(this); }
 *     // ② 页面本体不滚、内部 scroll-view 滚的页面（如点单页）：
 *     //    在 wxml 的 scroll-view 上加 bindscroll="onGoodsScroll"，
 *     //    js 里写 onGoodsScroll() { aiBall.onPageScroll(this); }
 *   });
 *
 * 行为：滚动事件一来就隐藏悬浮球，并重置 2 秒空闲定时器；
 *       连续 2 秒无新滚动事件 → 定时器触发 → 球体重新出现。
 * （「滑动就隐藏，页面不动 2 秒后再出来」的产品诉求就是这一个定时器）
 */
const AI_IDLE_MS = 2000;

let idleTimer = null;

/**
 * 滚动驱动入口：找当前页上的 #ai-ball 组件实例
 * @param {Object} page 宿主页实例（this）
 */
function onPageScroll(page) {
  const ball = page && page.selectComponent && page.selectComponent('#ai-ball');
  if (!ball) return;

  ball.hide();
  if (idleTimer) clearTimeout(idleTimer);
  idleTimer = setTimeout(() => {
    ball.show();
    idleTimer = null;
  }, AI_IDLE_MS);
}

module.exports = { onPageScroll };
