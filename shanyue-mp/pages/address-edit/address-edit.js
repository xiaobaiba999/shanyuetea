/**
 * pages/address-edit/address-edit.js —— 新增 / 编辑收货地址
 *
 * 本页职责：
 *   onLoad 带 id = 编辑态（GET /user/addressBook/{id} 回显表单）；不带 id = 新增态；
 *   表单：联系人 / 手机号（11 位校验）/ 性别（先生·女士 chip）/ 所在地区（微信原生省市区
 *   三级联动 picker mode="region"）/ 详细地址 / 标签（家·公司·学校 chip）/ 设为默认 switch；
 *   保存：新增 POST /user/addressBook，编辑 PUT /user/addressBook（body 含 id）；
 *   编辑态额外提供「删除该地址」；保存 / 删除成功后返回上一页。
 *
 * 涉及接口（苍穹地址簿契约，路径以骨架为准）：
 *   GET    /user/addressBook/{id}     → AddressBook（编辑回显）
 *   POST   /user/addressBook          → 新增（苍穹 save 不回传 id）
 *   PUT    /user/addressBook          → 编辑（body 含 id）
 *   DELETE /user/addressBook?id=xx    ← 注意：苍穹是 query 传 id，不是路径参数！
 *
 * 新手要点：
 *   1. AddressBook 的 sex 是字符串：'1' 男 / '0' 女（与苍穹一致，注意别传成 number）
 *   2. 省市区用微信原生 <picker mode="region">：系统自带全国省市区数据，零配置零文件；
 *      change 事件的 e.detail.value = ['广东省','广州市','天河区']（名称），
 *      e.detail.code = ['440000','440100','440106']（编号，基础库 2.13+ 才有）。
 *      address_book 表是「编号+名称」成对存的（14 列里占 6 列），保存时六字段全量提交，
 *      与教程 18.1 的口径对齐：前端地址选择器直接回传，后端不做反查。
 *   3. 保存成功后延迟 400ms 再 navigateBack，让「已保存」toast 先露出来
 */
const auth = require('../../utils/auth');
const { request } = require('../../utils/request');

/** 标签 chip 可选项（山月口径：家 / 公司 / 学校） */
const LABELS = ['家', '公司', '学校'];
/** 性别 chip：苍穹 AddressBook.sex '1' 男 / '0' 女 */
const SEXES = [
  { value: '1', text: '先生' },
  { value: '0', text: '女士' }
];
/** 手机号：11 位，1 开头（第 2 位 3-9） */
const PHONE_REG = /^1[3-9]\d{9}$/;

Page({
  data: {
    isEdit: false,
    id: null,
    form: {
      consignee: '',
      phone: '',
      sex: '1',
      provinceCode: '',
      provinceName: '',
      cityCode: '',
      cityName: '',
      districtCode: '',
      districtName: '',
      detail: '',
      label: '',
      isDefault: 0
    },
    // picker mode="region" 的回显定位值：[省名, 市名, 区名]，未选过为空数组
    regionValue: [],
    sexes: SEXES,
    labels: LABELS,
    saving: false
  },

  onLoad(options) {
    const isEdit = !!(options && options.id);
    this.setData({ isEdit, id: isEdit ? options.id : null });
    wx.setNavigationBarTitle({ title: isEdit ? '编辑收货地址' : '新增收货地址' });
    if (isEdit) {
      this.loadDetail(options.id);
    }
  },

  onShow() {
    // 与其他页面一致的登录守卫（从列表页进入时通常已登录，防御性兜底）
    auth.ensureLogin((ok) => {
      if (!ok) auth.gotoLogin();
    });
  },

  /** 编辑态：GET /user/addressBook/{id} 回显（省市区六字段一并回填，picker 靠 regionValue 定位） */
  loadDetail(id) {
    request({ url: '/user/addressBook/' + id })
      .then((addr) => {
        if (!addr) return;
        const region = [
          addr.provinceName || '',
          addr.cityName || '',
          addr.districtName || ''
        ];
        this.setData({
          form: {
            consignee: addr.consignee || '',
            phone: addr.phone || '',
            sex: addr.sex === '0' ? '0' : '1',
            provinceCode: addr.provinceCode || '',
            provinceName: addr.provinceName || '',
            cityCode: addr.cityCode || '',
            cityName: addr.cityName || '',
            districtCode: addr.districtCode || '',
            districtName: addr.districtName || '',
            detail: addr.detail || '',
            label: addr.label || '',
            isDefault: addr.isDefault === 1 ? 1 : 0
          },
          // 三个名称都齐才给 picker 定位；旧数据缺省市区则留空，用户重新选一次即可
          regionValue: region[0] && region[1] && region[2] ? region : []
        });
      })
      .catch((err) => wx.showToast({ title: err.message || '加载地址失败', icon: 'none' }));
  },

  /* ---------- 表单交互 ---------- */
  onInputField(e) {
    const field = e.currentTarget.dataset.field;
    const patch = {};
    patch['form.' + field] = e.detail.value;
    this.setData(patch);
  },

  onPickSex(e) {
    this.setData({ 'form.sex': e.currentTarget.dataset.value });
  },

  /** 标签单选：再次点击已选中的 chip 取消选择 */
  onPickLabel(e) {
    const value = e.currentTarget.dataset.value;
    this.setData({ 'form.label': this.data.form.label === value ? '' : value });
  },

  /**
   * 省市区选择回调（picker mode="region"）
   * e.detail.value = ['广东省','广州市','天河区']  ← 名称三连
   * e.detail.code  = ['440000','440100','440106'] ← 编号三连（旧基础库可能缺，兜底空串）
   */
  onRegionChange(e) {
    const names = e.detail.value || [];
    const codes = e.detail.code || ['', '', ''];
    this.setData({
      'form.provinceName': names[0] || '',
      'form.provinceCode': codes[0] || '',
      'form.cityName': names[1] || '',
      'form.cityCode': codes[1] || '',
      'form.districtName': names[2] || '',
      'form.districtCode': codes[2] || '',
      regionValue: names
    });
  },

  onDefaultChange(e) {
    this.setData({ 'form.isDefault': e.detail.value ? 1 : 0 });
  },

  /* ---------- 保存 / 删除 ---------- */
  onSave() {
    if (this.data.saving) return;
    const f = this.data.form;

    // 表单校验（与苍穹后端校验口径一致：四项必填 + 手机号格式）
    if (!f.consignee.trim()) {
      wx.showToast({ title: '请填写联系人', icon: 'none' });
      return;
    }
    if (!PHONE_REG.test(f.phone.trim())) {
      wx.showToast({ title: '请输入正确的 11 位手机号', icon: 'none' });
      return;
    }
    if (!f.provinceName || !f.cityName || !f.districtName) {
      wx.showToast({ title: '请选择所在地区', icon: 'none' });
      return;
    }
    if (!f.detail.trim()) {
      wx.showToast({ title: '请填写详细地址', icon: 'none' });
      return;
    }

    // 六个省市区字段（编号+名称成对）与联系人信息一起全量提交
    const body = {
      consignee: f.consignee.trim(),
      phone: f.phone.trim(),
      sex: f.sex,
      provinceCode: f.provinceCode,
      provinceName: f.provinceName,
      cityCode: f.cityCode,
      cityName: f.cityName,
      districtCode: f.districtCode,
      districtName: f.districtName,
      detail: f.detail.trim(),
      label: f.label,
      isDefault: f.isDefault
    };
    if (this.data.isEdit) {
      body.id = this.data.id; // 编辑必须带 id（苍穹 update 口径）
    }

    this.setData({ saving: true });
    wx.showLoading({ title: '保存中', mask: true });
    request({
      url: '/user/addressBook',
      method: this.data.isEdit ? 'PUT' : 'POST',
      data: body
    })
      .then(() => {
        wx.hideLoading();
        wx.showToast({ title: '已保存', icon: 'success' });
        // 列表页 onShow 会自动刷新，延迟返回让 toast 先露出
        setTimeout(() => wx.navigateBack(), 400);
      })
      .catch((err) => {
        wx.hideLoading();
        this.setData({ saving: false });
        wx.showToast({ title: err.message || '保存失败', icon: 'none' });
      });
  },

  /** 删除（编辑态）：确认弹窗 → DELETE /user/addressBook?id=xx（苍穹契约 query 传 id） */
  onDelete() {
    wx.showModal({
      title: '删除地址',
      content: '删除后不可恢复，确定删除这个地址吗？',
      confirmColor: '#D9534F',
      success: (res) => {
        if (!res.confirm) return;
        request({ url: '/user/addressBook', method: 'DELETE', query: { id: this.data.id } })
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' });
            setTimeout(() => wx.navigateBack(), 400);
          })
          .catch((err) => wx.showToast({ title: err.message || '删除失败', icon: 'none' }));
      }
    });
  }
});
