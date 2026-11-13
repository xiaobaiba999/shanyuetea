/**
 * utils/format.js —— 格式化工具
 *
 * 新手要点（金额防浮点误差）：
 * 后端 BigDecimal 序列化成 JS number（如 15.9），直接 15.9 + 5 会得到 20.799999…
 * 所以本项目所有金额运算一律先转「分」取整再回「元」：fenRound(15.9 + 5) === 20.9
 */

/** 金额运算：转分取整再回元，杜绝 0.1+0.2 之类浮点误差 */
function fenRound(n) {
  const num = Number(n);
  if (isNaN(num)) return 0;
  return Math.round(num * 100) / 100;
}

/** 金额显示：统一两位小数，如 15.9 -> "15.90" */
function fmtMoney(n) {
  return fenRound(n).toFixed(2);
}

/** 金额求和（可变参数或数组） */
function sumMoney(list) {
  let cents = 0;
  (list || []).forEach((n) => {
    cents += Math.round(Number(n || 0) * 100);
  });
  return cents / 100;
}

/** 时间显示：'yyyy-MM-ddTHH:mm:ss' 或 'yyyy-MM-dd HH:mm:ss' -> 'MM-dd HH:mm' */
function fmtTimeShort(s) {
  if (!s) return '';
  const str = String(s).replace('T', ' ');
  const m = str.match(/^(\d{4})-(\d{2})-(\d{2})[ ](\d{2}:\d{2})/);
  if (!m) return str.slice(0, 16);
  return m[2] + '-' + m[3] + ' ' + m[4];
}

/** 时间显示：完整到分钟 'yyyy-MM-dd HH:mm' */
function fmtTimeFull(s) {
  if (!s) return '';
  return String(s).replace('T', ' ').slice(0, 16);
}

/**
 * 订单状态 -> 文案 + 颜色（苍穹 6 状态不变）
 * 1 待付款 / 2 待接单 / 3 已接单·制作中 / 4 派送中(外送)·待取餐(自取) / 5 已完成 / 6 已取消
 */
const STATUS_MAP = {
  1: { text: '待付款', color: '#E6A23C' },
  2: { text: '待接单', color: '#3A7BD5' },
  3: { text: '已接单·制作中', color: '#3E8E5A' },
  4: { text: '待取餐', color: '#C77E32' }, // 文案会按 deliveryMode 动态切换
  5: { text: '已完成', color: '#2F5233' },
  6: { text: '已取消', color: '#9AA0A6' }
};

function statusText(status, deliveryMode) {
  if (Number(status) === 4) {
    return Number(deliveryMode) === 2 ? '派送中' : '待取餐';
  }
  const item = STATUS_MAP[Number(status)];
  return (item && item.text) || '未知状态';
}

function statusColor(status) {
  const item = STATUS_MAP[Number(status)];
  return (item && item.color) || '#9AA0A6';
}

/** 配送模式文案（山月补全契约：1 门店自取 | 2 外送） */
const DELIVERY_MODE_MAP = {
  1: '门店自取',
  2: '外送'
};

function deliveryModeText(mode) {
  return DELIVERY_MODE_MAP[Number(mode)] || '门店自取';
}

/** 支付状态文案（payStatus：0 未支付 1 已支付） */
function payStatusText(payStatus) {
  return Number(payStatus) === 1 ? '已支付' : '待支付';
}

/**
 * 解析规格字符串为可读文本。
 * 兼容两种格式：
 * - 山月 specSelected：'[{"groupName":"杯型","optionName":"大杯","priceDelta":3}]' -> "大杯"
 * - 苍穹 drinkFlavor：  '[{"name":"辣度","value":["微辣","中辣"]}]' -> "微辣/中辣"
 * 多组用「·」连接，解析失败原样返回。
 */
function parseSpecText(str) {
  if (!str) return '';
  let arr = null;
  try {
    arr = JSON.parse(str);
  } catch (e) {
    return String(str);
  }
  if (!Array.isArray(arr)) return String(str);
  return arr
    .map((g) => {
      if (g && g.optionName) return g.optionName;
      if (g && g.value) return Array.isArray(g.value) ? g.value.join('/') : String(g.value);
      return '';
    })
    .filter(Boolean)
    .join('·');
}

module.exports = {
  fenRound,
  fmtMoney,
  sumMoney,
  fmtTimeShort,
  fmtTimeFull,
  STATUS_MAP,
  statusText,
  statusColor,
  DELIVERY_MODE_MAP,
  deliveryModeText,
  payStatusText,
  parseSpecText
};
