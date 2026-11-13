/**
 * utils/request.js —— 统一请求封装（全部页面只允许走这里发请求）
 *
 * 做了 4 件事（对照苍穹外卖小程序的请求模式）：
 * 1. 拼接 baseUrl，GET 请求支持 query 对象自动拼参数
 * 2. 自动携带登录态：请求头 token = 登录接口返回的 JWT（苍穹模式）
 * 3. 解包统一响应 Result{ code, msg, data }：code === 1 成功 → resolve(data)；
 *    否则 reject(Error(msg))，页面 catch 后 toast 提示即可
 * 4. HTTP 401（未登录/token 过期）→ 清空本地登录态 → 跳转登录页
 *    （说明：tab 页里 redirectTo 跳非 tab 页会打乱返回栈，这里统一用 reLaunch，
 *      效果与课程「回到登录页」一致且更稳）
 *
 * USE_MOCK = true 时：不走 wx.request，改走 utils/mock.js 的内置数据模拟。
 */
const config = require('./config');
const { mockRequest } = require('./mock');

/** 读取本地登录态 */
function getStoredUser() {
  try {
    return wx.getStorageSync(config.USER_KEY) || null;
  } catch (e) {
    return null;
  }
}

/** 清空登录态并回到登录页（401 统一出口） */
function clearAndGoLogin() {
  try {
    wx.removeStorageSync(config.USER_KEY);
  } catch (e) { /* 忽略 */ }
  wx.reLaunch({ url: '/pages/login/login' });
}

/** 把 query 对象拼成 a=1&b=xx（自动 encodeURIComponent） */
function buildQuery(query) {
  const parts = [];
  Object.keys(query || {}).forEach((key) => {
    const v = query[key];
    if (v === undefined || v === null || v === '') return; // 空参数不拼（如「全部」状态不传 status）
    parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(v));
  });
  return parts.join('&');
}

/**
 * 统一请求入口
 * @param {Object} options { url, method, data, query, header }
 * @returns {Promise<any>} 成功 resolve 后端 Result.data；失败 reject(Error)
 */
function request(options) {
  const { url, method = 'GET', data, query, header } = options || {};

  // ===== Mock 模式：内置演示数据 =====
  if (config.USE_MOCK) {
    return mockRequest({ url, method, data, query }).catch((err) => {
      if (err && err.httpStatus === 401) {
        clearAndGoLogin();
        throw new Error('请先登录');
      }
      throw err;
    });
  }

  // ===== 真实后端模式 =====
  let fullUrl = url.indexOf('http') === 0 ? url : config.BASE_URL + url;
  const qs = buildQuery(query);
  if (qs) fullUrl += (fullUrl.indexOf('?') > -1 ? '&' : '?') + qs;

  const h = Object.assign({ 'Content-Type': 'application/json' }, header || {});
  const user = getStoredUser();
  if (user && user.token) {
    h.token = user.token; // 苍穹模式：C 端 JWT 放在名为 token 的请求头里
  }

  return new Promise((resolve, reject) => {
    wx.request({
      url: fullUrl,
      method,
      data,
      header: h,
      timeout: 10000,
      success(res) {
        // 401：JWT 校验失败（苍穹拦截器直接回 401 空响应体）
        if (res.statusCode === 401) {
          clearAndGoLogin();
          reject(new Error('登录已过期，请重新登录'));
          return;
        }
        const body = res.data;
        // 苍穹统一响应：Result{code:1成功/0失败, msg, data}
        if (body && typeof body === 'object' && 'code' in body) {
          if (body.code === 1) {
            resolve(body.data);
          } else {
            reject(new Error(body.msg || '请求失败，请稍后再试'));
          }
          return;
        }
        // 非标准响应（理论上后端按课程写不会出现）
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(body);
        } else {
          reject(new Error('请求失败（HTTP ' + res.statusCode + '）'));
        }
      },
      fail(err) {
        const detail = (err && err.errMsg) || '';
        reject(new Error(
          '网络异常：' + detail +
          '。请检查：① 后端是否已启动 ② config.js 的 BASE_URL 是否正确 ③ 开发者工具是否勾选「不校验合法域名」'
        ));
      }
    });
  });
}

module.exports = {
  request,
  getStoredUser
};
