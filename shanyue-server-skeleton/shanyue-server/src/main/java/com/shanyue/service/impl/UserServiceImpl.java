package com.shanyue.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.shanyue.constant.JwtClaimsConstant;
import com.shanyue.constant.MessageConstant;
import com.shanyue.context.BaseContext;
import com.shanyue.dto.UserLoginDTO;
import com.shanyue.entity.User;
import com.shanyue.exception.LoginFailedException;
import com.shanyue.mapper.UserMapper;
import com.shanyue.properties.JwtProperties;
import com.shanyue.properties.WeChatProperties;
import com.shanyue.service.UserService;
import com.shanyue.utils.JwtUtil;
import com.shanyue.vo.UserLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 【本类知识点：C 端微信登录（真实链路：code 换 openid）】
 *
 * 一、微信登录流程（wx.login 的 code 换 openid）：
 *   1. 小程序端 wx.login() 得到临时凭证 code（5 分钟有效，一次性，用过即废）；
 *   2. 后端拿 code + appid + secret 请求微信官方接口 jscode2session：
 *      GET https://api.weixin.qq.com/sns/jscode2session
 *          ?appid=APPID&secret=SECRET&js_code=CODE&grant_type=authorization_code
 *      返回 JSON：{"openid":"...","session_key":"...","unionid":"..."} 或 {"errcode":40029,...}
 *      ——openid 就是用户在本小程序里的唯一身份证：只与「哪个微信号 + 哪个小程序」
 *      有关，同一个微信反复登录 openid 不变；session_key 本工程用不到，拿到即弃；
 *   3. 用 openid 查库：不存在 → 注册新用户；存在 → 老用户直接登录；
 *   4. 生成 JWT（claims 里放 userId）返回给前端，后续请求走 JwtTokenUserInterceptor。
 *   本工程用 Spring 的 RestTemplate 发 GET（课程用的是自己封装的 HttpClientUtil，效果一样）。
 *
 * 二、跑通真实登录的前置清单（缺一不可，实操步骤见教程第 15 章）：
 *   1. application-dev.yml 的 shanyue.wechat.appid/secret 填成你自己的
 *      （公众平台 → 开发管理 → 开发设置；AppSecret 只在生成时显示一次）；
 *   2. 微信开发者工具导入项目时填同一个 AppID（三方一致：公众平台/工具/yml）；
 *   3. 公众号后台「开发设置 → IP 白名单」加入你本机的公网出口 IP，
 *      否则微信直接拒绝请求（errcode 40164）；
 *   4. 真机扫码测试时，把自己的微信号加进「成员管理 → 体验成员」。
 *
 * 三、常见报错速判（详见附录 B）：
 *   40013 invalid appid → appid 填错或三方不一致；
 *   40029 invalid code  → code 过期或已被用过（code 一次性）；
 *   40164 IP 不在白名单 → 把本机公网 IP 加进后台白名单再试。
 *
 * 四、面试讲点：secret 绝不能下发到前端/小程序代码里，必须只存服务端
 * 配置（yml 或配置中心）；openid/session_key 只应出现在服务端与数据库。
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    /** 微信官方登录凭证校验接口 */
    private static final String WX_LOGIN_URL = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RestTemplate restTemplate;

    /**
     * 用户登录
     */
    @Override
    public UserLoginVO login(UserLoginDTO userLoginDTO) {
        String code = userLoginDTO.getCode();
        log.info("C 端用户登录，code={}", code);

        // -------- 第 1 步：调微信 jscode2session，用 code 换 openid --------
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("appid", weChatProperties.getAppid());
        paramMap.put("secret", weChatProperties.getSecret());
        paramMap.put("js_code", code);
        paramMap.put("grant_type", "authorization_code");
        // RestTemplate 发 GET 并把参数拼到 URL 上，返回体是 JSON 字符串
        String json = restTemplate.getForObject(WX_LOGIN_URL + "?appid={appid}&secret={secret}&js_code={js_code}&grant_type={grant_type}",
                String.class, paramMap);
        log.info("微信 jscode2session 返回：{}", json);

        // 解析 JSON，取 openid（code 无效/appid 不对/IP 不在白名单时 errcode 有值而 openid 为空）
        JSONObject jsonObject = JSON.parseObject(json);
        String openid = jsonObject.getString("openid");

        // openid 为空 = 登录失败（code 无效、appid/secret 不对或 IP 不在白名单）
        if (openid == null || openid.isEmpty()) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        // -------- 第 2 步：判断新用户/老用户 --------
        User user = userMapper.getByOpenId(openid);
        if (user == null) {
            // 新用户自动注册（openid 唯一索引兜底防并发重复注册）
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
            log.info("新用户注册成功，id={}", user.getId());
        }

        // -------- 第 3 步：生成 JWT（claims 里放 userId，供拦截器解析回 ThreadLocal） --------
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims);

        // -------- 第 4 步：封装返回 --------
        return UserLoginVO.builder()
                .id(user.getId())
                .openid(openid)
                .token(token)
                .nickName(user.getNickName())
                .avatarUrl(user.getAvatar())
                .build();
    }

    /**
     * 查询当前登录用户资料（"我的"页面）
     */
    @Override
    public User profile() {
        // 当前用户 id 由 JwtTokenUserInterceptor 解析 token 后放进 ThreadLocal，
        // 任何层都能取——这就是 BaseContext + ThreadLocal 的意义（不从前端传 id，防越权）
        Long userId = BaseContext.getCurrentId();
        log.info("查询用户资料，userId={}", userId);
        return userMapper.getById(userId);
    }
}
