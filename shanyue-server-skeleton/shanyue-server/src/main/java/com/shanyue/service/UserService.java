package com.shanyue.service;

import com.shanyue.dto.UserLoginDTO;
import com.shanyue.entity.User;
import com.shanyue.vo.UserLoginVO;

/**
 * C 端用户服务接口（对照课程 UserService + 山月新增 profile）
 */
public interface UserService {

    /**
     * 微信登录（真实链路：code 调 jscode2session 换 openid）
     *
     * @param userLoginDTO 小程序 wx.login 拿到的 code
     * @return 用户信息 + JWT 令牌
     */
    UserLoginVO login(UserLoginDTO userLoginDTO);

    /**
     * 查询当前登录用户的资料（"我的"页面）
     * 山月 v2.0 新增：id 从 BaseContext（ThreadLocal）取出，不信任前端传参。
     *
     * @return 用户实体（不含敏感信息字段也可按需裁剪）
     */
    User profile();
}
