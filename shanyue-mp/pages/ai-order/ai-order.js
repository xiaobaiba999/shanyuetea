/**
 * pages/ai-order/ai-order.js —— 茶小月 AI 点单聊天页（功能包 F7）
 *
 * 本页职责：
 *   顶部茶小月人设卡（金描边头像 + 人设介绍 + 「重新开始」清空会话）；
 *   消息流：AI 消息居左（带小头像白气泡）、用户消息居右（墨绿气泡白字）；
 *   发送后先插「正在思考」呼吸气泡（三个点 CSS 动画），约 500ms 后调 AI 接口，
 *   返回后把思考气泡原位替换为正式回复；带 recommend 时紧随其后插入推荐卡（可加购）；
 *   快捷提问 chips（GET /user/ai/suggests）+ 底部输入条（confirm 发送、空文案禁用）。
 *
 * 涉及接口：
 *   GET  /user/ai/suggests       → string[]（快捷提问 chips）
 *   POST /user/ai/order           → { reply, recommend }（请求体 { message, history, lastRecommendId }）
 *   POST /user/shoppingCart/add   → null（推荐卡加购：{ drinkId } 或 { setmealId }，不带规格默认加购）
 *
 * 新手要点：
 *   1. history 只带最近 6 条纯文本 [{role:'user'|'assistant', content}]——真实后端把它
 *      拼进大模型 messages 上下文；截断是为了控制 token，mock 只用当前句也能跑通
 *   2. 「正在思考 → 正式回复」是同一个 id 的消息原位替换（type 从 thinking 换成 text），
 *      滚动锚点不跳；500ms 的 setTimeout 只是演示节奏，真实后端是慢接口/流式返回
 *   3. lastRecommendId 记的是「最近一张推荐卡」的 id，本轮没有新推荐时保持不变——
 *      用户说「换一个」时后端才知道该避开哪一杯（对应 AiOrderController 桩）
 *   4. WXML 不能调函数：价格 fmtMoney、「月售 xx」、建议规格整句都在 JS 里拼好存进 card
 *   5. 会话历史只存页面内存（重新进入页面即重置），不做持久化——毕设演示口径
 */
const auth = require('../../utils/auth');
const { request } = require('../../utils/request');
const F = require('../../utils/format');

/** 首屏欢迎语（本地写死；mock 的问候应答同文） */
const WELCOME = '小月参见～今日想喝点什么？说说口味、心情或预算，小月替您参谋。';
/** 会话上下文最多携带条数（真实后端拼大模型 messages 用） */
const HISTORY_KEEP = 6;
/** 「正在思考」最短展示时长：先让用户看到气泡再回结果（演示节奏） */
const THINKING_DELAY = 500;

Page({
  data: {
    /* 消息流：{ id, role:'user'|'assistant', type:'text'|'thinking'|'card', content, card } */
    messages: [],
    /* 快捷提问 chips（GET /user/ai/suggests） */
    suggests: [],
    /* 输入框草稿（空文案时发送按钮置灰） */
    draft: '',
    /* 思考中（发送按钮同步禁用，配合 _sending 防抖） */
    sending: false,
    /* scroll-into-view 锚点：始终指向最后一条消息的 id，自动滚到底 */
    anchorId: ''
  },

  onShow() {
    auth.ensureLogin((ok) => {
      if (!ok) {
        auth.gotoLogin();
        return;
      }
      if (!this._inited) {
        this._inited = true;
        this._initSession();
      }
      this.loadSuggests();
    });
  },

  onUnload() {
    // 拦截卸载后才回来的异步回调，避免对已销毁页面 setData（控制台告警）
    this._destroyed = true;
  },

  /* ============================== 会话 ============================== */

  /** 初始化/重置会话：回到欢迎语，清空上下文与最近推荐 */
  _initSession() {
    this._seq = 0;               // 消息 id 发号器（scroll-into-view 要求每条消息有唯一 id）
    this._history = [];          // 纯文本上下文 [{role, content}]
    this._lastRecommendId = null; // 最近一张推荐卡的 drinkId/setmealId
    const welcome = {
      id: this._nextId(),
      role: 'assistant',
      type: 'text',
      content: WELCOME
    };
    this.setData({ messages: [welcome], anchorId: '' });
  },

  /** 消息 id 发号器 */
  _nextId() {
    this._seq = (this._seq || 0) + 1;
    return 'm' + this._seq;
  },

  /** 拉快捷提问 chips（成功一次即不再拉；失败不置标记，下次 onShow 自动重试） */
  loadSuggests() {
    if (this._suggestsLoaded) return;
    request({ url: '/user/ai/suggests' })
      .then((list) => {
        this._suggestsLoaded = true;
        this.setData({ suggests: Array.isArray(list) ? list : [] });
      })
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '快捷提问加载失败', icon: 'none' });
      });
  },

  /* ============================== 输入与发送 ============================== */

  onInput(e) {
    this.setData({ draft: e.detail.value });
  },

  /** 发送按钮（键盘 confirm 走同一入口；空文案禁用） */
  onSend() {
    const text = String(this.data.draft || '').trim();
    if (!text || this._sending) return;
    this._send(text);
  },

  /** 点击快捷提问 chip：当作一条用户消息发出 */
  onChipTap(e) {
    if (this._sending) return;
    const text = String(((e.currentTarget && e.currentTarget.dataset) || {}).text || '').trim();
    if (!text) return;
    this._send(text);
  },

  /**
   * 发送一条用户消息：
   * 用户气泡 + 「正在思考」气泡上屏 → 500ms 后 POST /user/ai/order → 思考气泡原位替换
   */
  _send(text) {
    if (this._sending) return; // 防抖：思考期间不允许连发
    this._sending = true;

    const userMsg = { id: this._nextId(), role: 'user', type: 'text', content: text };
    const thinkMsg = { id: this._nextId(), role: 'assistant', type: 'thinking' };
    this.setData({
      messages: this.data.messages.concat([userMsg, thinkMsg]),
      draft: '',
      sending: true,
      anchorId: thinkMsg.id
    });
    this._history.push({ role: 'user', content: text });

    setTimeout(() => {
      request({
        url: '/user/ai/order',
        method: 'POST',
        data: {
          message: text,
          history: this._recentHistory(),
          lastRecommendId: this._lastRecommendId
        }
      })
        .then((vo) => this._applyReply(thinkMsg.id, vo))
        .catch((err) => this._failReply(thinkMsg.id, err))
        .finally(() => {
          this._sending = false;
          if (!this._destroyed) this.setData({ sending: false });
        });
    }, THINKING_DELAY);
  },

  /** 最近 6 条纯文本上下文（role/content） */
  _recentHistory() {
    return this._history.slice(-HISTORY_KEEP);
  },

  /** 接口成功：思考气泡原位换成正式回复；带 recommend 再追加一张推荐卡 */
  _applyReply(thinkId, vo) {
    if (this._destroyed) return;
    const reply = (vo && vo.reply) || '小月一时没听清，您再说一遍？';
    const messages = this.data.messages.slice();
    const idx = messages.findIndex((m) => m.id === thinkId);
    // 复用思考气泡的 id 原位替换 → 滚动位置不跳
    const replyMsg = { id: thinkId, role: 'assistant', type: 'text', content: reply };
    if (idx > -1) {
      messages[idx] = replyMsg;
    } else {
      messages.push(replyMsg);
    }
    this._history.push({ role: 'assistant', content: reply });
    if (this._history.length > HISTORY_KEEP) {
      this._history = this._history.slice(-HISTORY_KEEP);
    }

    let anchorId = thinkId;
    const rec = vo && vo.recommend;
    if (rec) {
      // 记住最近一次推荐的 id（下一轮「换一个」时后端避开它）
      const recId = this._pickRecommendId(rec);
      if (recId !== null) this._lastRecommendId = recId;
      const cardMsg = { id: this._nextId(), role: 'assistant', type: 'card', card: this._buildCard(rec) };
      messages.push(cardMsg);
      anchorId = cardMsg.id;
    }

    this.setData({ messages: messages, anchorId: anchorId });
  },

  /** 推荐卡 id：优先 drinkId，其次 setmealId，都没有返回 null */
  _pickRecommendId(rec) {
    if (rec.drinkId !== null && rec.drinkId !== undefined && rec.drinkId !== '') return rec.drinkId;
    if (rec.setmealId !== null && rec.setmealId !== undefined && rec.setmealId !== '') return rec.setmealId;
    return null;
  },

  /** 接口失败：撤掉思考气泡 + toast；用户消息留在原地，可直接重发 */
  _failReply(thinkId, err) {
    if (this._destroyed) return;
    const messages = this.data.messages.filter((m) => m.id !== thinkId);
    const anchorId = messages.length ? messages[messages.length - 1].id : '';
    this.setData({ messages: messages, anchorId: anchorId });
    wx.showToast({ title: (err && err.message) || '小月走神了，请再问一次', icon: 'none' });
  },

  /** recommend → 推荐卡展示模型（展示字符串全部预拼，WXML 不调函数） */
  _buildCard(rec) {
    return {
      kind: rec.kind === 'setmeal' ? 'setmeal' : 'drink',
      drinkId: rec.drinkId || null,
      setmealId: rec.setmealId || null,
      name: rec.name || '小月荐饮',
      priceText: F.fmtMoney(rec.price),
      monthSalesText: '月售 ' + (rec.monthSales || 0),
      reason: rec.reason || '',
      specText: '建议规格：' + (rec.specSuggestion || '按口味任选'),
      thumbChar: (rec.name || '茶').charAt(0),
      imgUrl: rec.image || '',
      added: false,
      btnText: '加入购物车'
    };
  },

  /* ============================== 推荐卡加购 ============================== */

  /** 加购：POST /user/shoppingCart/add（不带规格 = 默认加购）→ 按钮变「已加入 ✓」禁用态 */
  onCardAdd(e) {
    const idx = Number(e.currentTarget.dataset.index);
    const msg = this.data.messages[idx];
    if (!msg || msg.type !== 'card' || msg.card.added || this._cartting) return;
    this._cartting = true;
    const body = msg.card.kind === 'setmeal'
      ? { setmealId: msg.card.setmealId }
      : { drinkId: msg.card.drinkId };
    request({ url: '/user/shoppingCart/add', method: 'POST', data: body })
      .then(() => {
        if (this._destroyed) return;
        this.setData({
          ['messages[' + idx + '].card.added']: true,
          ['messages[' + idx + '].card.btnText']: '已加入 ✓'
        });
        wx.showToast({ title: '已加入购物车', icon: 'success' });
      })
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '加购失败', icon: 'none' });
      })
      .finally(() => {
        this._cartting = false;
      });
  },

  /** 推荐卡缩略图加载失败：清掉 imgUrl 露出首字兜底（与 menu 页同法） */
  onThumbError(e) {
    const idx = Number(e.currentTarget.dataset.index);
    const msg = this.data.messages[idx];
    if (!msg || msg.type !== 'card' || !msg.card.imgUrl) return;
    this.setData({ ['messages[' + idx + '].card.imgUrl']: '' });
  },

  /* ============================== 重新开始 ============================== */

  /** 顶部「重新开始」：清空会话回初始欢迎语（上下文、最近推荐一并重置） */
  onRestart() {
    if (this._sending) return; // 思考中不许重置，避免迟到回调把消息插进新会话
    this._initSession();
    wx.showToast({ title: '小月已重新听您吩咐', icon: 'none' });
  }
});
