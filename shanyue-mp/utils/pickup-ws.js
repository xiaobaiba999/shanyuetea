/**
 * utils/pickup-ws.js —— F8 取餐进度推送（WebSocket，双模式自动切换）
 *
 * 消息契约（复用苍穹 WebSocketServer 的 /ws/{sid} 端点，扩展 type=3 给 C 端）：
 *   { "type": 3, "orderId": 5, "status": 4, "content": "您的茶好了，请取餐" }
 *   type=1 来单提醒（管理端监工屏）/ type=2 催单提醒（管理端）/
 *   type=3 取餐进度推送（C 端小程序，按 orderId 过滤只处理自己的订单）
 * Java 练习点：管理端「接单/出餐」改订单状态时，调用 WebSocketServer.sendToAllClient
 *   广播 type=3 消息（见 websocket/WebSocketServer.java 与 docs/v2-features.md）。
 *
 * 双模式：
 *   - USE_MOCK = true ：模拟服务器推送。listen() 后 12 秒触发一次
 *     「制作完成 → 待取餐」（对应订单 status 3→4）；每次 listen 重新计时。
 *     页面 onHide / onUnload 必须调 close()，避免后台空跑计时器。
 *   - USE_MOCK = false：wx.connectSocket 连真实后端
 *     ws://BASE_URL替换http为ws + /ws/user_{userId}，断线自动重连（最多 3 次）。
 *
 * 用法（order-detail 页）：
 *   const pickupWs = require('../../utils/pickup-ws');
 *   pickupWs.listen({
 *     orderId: 5,
 *     onPush(msg) { ... }   // msg = {type, orderId, status, content}
 *   });
 *   // onHide / onUnload:
 *   pickupWs.close();
 */
const config = require('./config');
const auth = require('./auth');

/** 模拟模式：多少毫秒后模拟「茶好了」推送 */
const MOCK_PUSH_DELAY = 12 * 1000;
/** 真实模式：最大重连次数 */
const MAX_RETRY = 3;

let mockTimer = null;
let socketTask = null;
let retryCount = 0;
let listening = false;

/** BASE_URL(http) → ws 地址：http://localhost:8080 → ws://localhost:8080 */
function buildWsUrl() {
  const user = auth.getUser() || {};
  const sid = 'user_' + (user.userId || 0);
  const base = String(config.BASE_URL || '').replace(/^http/, 'ws');
  return base.replace(/\/$/, '') + '/ws/' + sid;
}

/** 关闭当前监听（mock 计时器或真实 socket），页面离场必调 */
function close() {
  listening = false;
  if (mockTimer) {
    clearTimeout(mockTimer);
    mockTimer = null;
  }
  if (socketTask) {
    try { socketTask.close({}); } catch (e) { /* 已断开则忽略 */ }
    socketTask = null;
  }
  retryCount = 0;
}

/**
 * 开始监听某个订单的取餐进度推送
 * @param {Object} options { orderId, onPush(msg), onError(err) }
 */
function listen(options) {
  const { orderId, onPush, onError } = options || {};
  if (!orderId) return;
  close(); // 重复 listen 以前一次为准
  listening = true;

  /* ===== Mock 模式：12 秒后模拟服务器推送「制作完成请取餐」 ===== */
  if (config.USE_MOCK) {
    mockTimer = setTimeout(() => {
      if (!listening) return;
      onPush && onPush({
        type: 3,
        orderId: Number(orderId),
        status: 4,
        content: '您的茶好了，请取餐'
      });
    }, MOCK_PUSH_DELAY);
    return;
  }

  /* ===== 真实模式：连后端 /ws/user_{userId} ===== */
  const url = buildWsUrl();
  socketTask = wx.connectSocket({ url });

  socketTask.onOpen(() => {
    retryCount = 0; // 连上即复位重连计数
  });

  socketTask.onMessage((res) => {
    if (!listening) return;
    let msg = null;
    try {
      msg = JSON.parse(res.data);
    } catch (e) {
      return; // 非 JSON（如心跳文本）忽略
    }
    // 只处理 type=3 且是本订单的推送
    if (msg && Number(msg.type) === 3 && Number(msg.orderId) === Number(orderId)) {
      onPush && onPush(msg);
    }
  });

  socketTask.onClose(() => {
    if (!listening) return;
    // 断线重连（指数退避：1s/2s/4s，最多 3 次）
    if (retryCount < MAX_RETRY) {
      retryCount += 1;
      setTimeout(() => {
        if (listening) listen(options);
      }, 1000 * Math.pow(2, retryCount - 1));
    } else if (onError) {
      onError(new Error('取餐推送连接已断开'));
    }
  });

  socketTask.onError((err) => {
    if (onError && listening) {
      onError(new Error('取餐推送连接异常：' + ((err && err.errMsg) || '')));
    }
  });
}

module.exports = {
  listen,
  close
};
