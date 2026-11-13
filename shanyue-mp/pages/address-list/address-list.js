/**
 * pages/address-list/address-list.js —— 收货地址列表
 *
 * 本页职责：
 *   地址列表展示（默认徽标）、编辑、删除（确认弹窗）、设为默认、新增入口；
 *   「选择模式」（外送下单进入）：点击地址卡回传给上一页（submit）并返回。
 *
 * 涉及接口（苍穹地址簿契约）：
 *   GET    /user/addressBook/list            → AddressBook[]
 *   DELETE /user/addressBook?id=xx           ← 注意：苍穹是 query 传 id，不是路径参数！
 *   PUT    /user/addressBook/default         → body { id, isDefault }
 *
 * 新手要点：
 *   选择模式回传用 getCurrentPages() 拿上一页实例直接 setData —— 简单可靠的小程序跨页通信方式
 */
const auth = require('../../utils/auth');
const { request } = require('../../utils/request');

Page({
  data: {
    mode: 'normal',   // normal | select（外送下单选择地址）
    list: []
  },

  onLoad(options) {
    if (options && options.mode === 'select') {
      this.setData({ mode: 'select' });
    }
  },

  onShow() {
    auth.ensureLogin((ok) => {
      if (!ok) {
        auth.gotoLogin();
        return;
      }
      this.loadList();
    });
  },

  loadList() {
    request({ url: '/user/addressBook/list' })
      .then((raw) => {
        // isDefault 归一化：不管后端返回 1 / "1" / true / null，
        // 统一折算成数字 0/1，页面上的「默认」徽标和「设为默认」按钮显隐只认数字，
        // 不会再被类型差异坑到（后端正常返回 1 时这里原样放行，零副作用）
        const list = (raw || []).map((x) => ({ ...x, isDefault: Number(x.isDefault) || 0 }));
        this.setData({ list });
      })
      .catch((err) => wx.showToast({ title: err.message || '加载失败', icon: 'none' }));
  },

  /** 点击地址卡：选择模式回传；普通模式无动作 */
  onItemTap(e) {
    if (this.data.mode !== 'select') return;
    const item = this.data.list[Number(e.currentTarget.dataset.index)];
    if (!item) return;
    const pages = getCurrentPages();
    const prev = pages[pages.length - 2];
    if (prev && typeof prev.onAddressPicked === 'function') {
      prev.onAddressPicked(item);
    }
    wx.navigateBack();
  },

  onAdd() {
    wx.navigateTo({ url: '/pages/address-edit/address-edit' });
  },

  onEdit(e) {
    wx.navigateTo({ url: '/pages/address-edit/address-edit?id=' + e.currentTarget.dataset.id });
  },

  /** 删除（确认弹窗）：苍穹契约 DELETE /user/addressBook?id=xx */
  onDelete(e) {
    const id = e.currentTarget.dataset.id;
    const name = e.currentTarget.dataset.name || '该地址';
    wx.showModal({
      title: '删除地址',
      content: '确定删除「' + name + '」的这个地址吗？',
      confirmColor: '#D9534F',
      success: (res) => {
        if (!res.confirm) return;
        request({ url: '/user/addressBook', method: 'DELETE', query: { id } })
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' });
            this.loadList();
          })
          .catch((err) => wx.showToast({ title: err.message || '删除失败', icon: 'none' }));
      }
    });
  },

  /** 设为默认：PUT /user/addressBook/default，body 传 { id, isDefault }（苍穹契约） */
  onSetDefault(e) {
    const id = e.currentTarget.dataset.id;
    request({ url: '/user/addressBook/default', method: 'PUT', data: { id, isDefault: 1 } })
      .then(() => this.loadList())
      .then(() => {
        // 设置后复核：接口返回成功 ≠ 字段真的写进库。
        // 重新拉列表后若没有一行 isDefault=1，说明后端 update 动态 SQL 漏了
        // is_default 分支（或 setDefault 实现没写对）——把坑当场暴露出来
        const hit = this.data.list.some((x) => x.isDefault === 1);
        if (hit) {
          wx.showToast({ title: '已设为默认', icon: 'success' });
        } else {
          wx.showToast({
            title: '后端返回成功但默认未生效：请检查 update 语句的 is_default 分支',
            icon: 'none',
            duration: 3500
          });
        }
      })
      .catch((err) => wx.showToast({ title: err.message || '设置失败', icon: 'none' }));
  }
});
