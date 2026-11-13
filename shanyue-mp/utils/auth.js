/**
 * utils/auth.js —— 登录鉴权工具（苍穹模式：wx.login → code → 后端换 token）
 *
 * 登录流程（与课程完全一致）：
 *   1. wx.login() 拿到临时授权 code
 *   2. POST /user/user/login { code } （该接口免登录）
 *   3. 后端用 code 去微信换 openid，查/建用户，签发 JWT
 *   4. 返回 UserLoginVO{ id, openid, token, +山月补全: nickName, avatarUrl }
 *   5. 本地 storage 存 { token, userId, nickName, avatarUrl, openid }
 *
 * 新手要点：
 * - 「先登录后下单」：menu / submit / order / me 等页面 onShow 都会调 ensureLogin 守卫
 * - nickName / avatarUrl 后端可为空，前端统一显示默认「山月茶咖用户」
 */
const config = require('./config');
const { request } = require('./request');

/** 读取本地登录态 */
function getUser() {
  try {
    return wx.getStorageSync(config.USER_KEY) || null;
  } catch (e) {
    return null;
  }
}

function getToken() {
  const u = getUser();
  return (u && u.token) || '';
}

function isLoggedIn() {
  return !!getToken();
}

function saveUser(vo) {
  wx.setStorageSync(config.USER_KEY, {
    token: vo.token,
    userId: vo.id,
    openid: vo.openid,
    nickName: vo.nickName || '',
    avatarUrl: vo.avatarUrl || ''
  });
}

/** 显示用昵称（空则默认品牌昵称） */
function displayNickName() {
  const u = getUser();
  return (u && u.nickName) || '山月茶咖用户';
}

/** 退出登录：只清本地（后端 JWT 无状态，无需调接口） */
function logout() {
  try {
    wx.removeStorageSync(config.USER_KEY);
  } catch (e) { /* 忽略 */ }
}

/**
 * 完整登录流程：wx.login → POST /user/user/login → 存登录态
 * @param {Function} cb (err, user) 回调
 */
function loginFlow(cb) {
  wx.login({
    success(res) {
      if (!res.code) {
        cb && cb(new Error('wx.login 未获取到 code'));
        return;
      }
      request({
        url: '/user/user/login',
        method: 'POST',
        data: { code: res.code }
      })
        .then((vo) => {
          if (!vo || !vo.token) {
            cb && cb(new Error('登录接口未返回 token'));
            return;
          }
          saveUser(vo);
          cb && cb(null, getUser());
        })
        .catch((err) => cb && cb(err));
    },
    fail(err) {
      cb && cb(new Error('wx.login 调用失败：' + (err && err.errMsg)));
    }
  });
}

/**
 * 登录守卫：已登录 → cb(true)；未登录 → cb(false)（由页面决定跳登录页）
 * 注意：这里不静默登录，保证用户在登录页主动点「微信一键登录」（点击才调 loginFlow）
 */
function ensureLogin(cb) {
  const ok = isLoggedIn();
  if (typeof cb === 'function') cb(ok);
  return ok;
}

/** 未登录时统一跳登录页（reLaunch 在任何页面—including tab 页——都安全） */
function gotoLogin() {
  wx.reLaunch({ url: '/pages/login/login' });
}

module.exports = {
  getUser,
  getToken,
  isLoggedIn,
  saveUser,
  displayNickName,
  logout,
  loginFlow,
  ensureLogin,
  gotoLogin
};
