package com.shanyue.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * C 端微信登录参数：wx.login() 拿到的临时授权 code。
 * code 一次性有效（5 分钟过期），服务端拿它去调微信 jscode2session 接口换 openid。
 * 登录失败大多出在这一步：appid/secret 不对（40013）、code 过期（40029）、IP 不在白名单（40164）。
 */
@Data
public class UserLoginDTO implements Serializable {

    private String code;
}
