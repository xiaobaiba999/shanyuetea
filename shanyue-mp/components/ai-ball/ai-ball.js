/**
 * components/ai-ball/ai-ball.js —— 茶小月 AI 悬浮球组件（跨页通用）
 *
 * 行为契约（与 utils/ai-ball.js 帮助函数配合）：
 *   hide() —— 宿主页滚动时调用：球体下沉淡出；
 *   show() —— 滚动停止 2 秒后由帮助函数的 idle 定时器调用：球体上浮回归。
 * 实现要点：
 *   1. hide/show 内部都做了「值没变不 setData」防抖 —— onPageScroll 触发频率极高，
 *      每次都 setData 会浪费渲染（这是小程序性能优化的基本功）；
 *   2. 隐藏态只做透明 + 位移 + 禁点（pointer-events），不销毁节点，切换零成本；
 *   3. catchtap 防止误触穿透到球下面的页面元素。
 */
Component({
  data: {
    hidden: false
  },

  methods: {
    /** 滚动中：隐藏悬浮球（值没变不 setData） */
    hide() {
      if (!this.data.hidden) {
        this.setData({ hidden: true });
      }
    },

    /** 静置 2 秒后：重新出现 */
    show() {
      if (this.data.hidden) {
        this.setData({ hidden: false });
      }
    },

    /** 点击进入茶小月 AI 点单聊天页 */
    onTap() {
      wx.navigateTo({ url: '/pages/ai-order/ai-order' });
    }
  }
});
