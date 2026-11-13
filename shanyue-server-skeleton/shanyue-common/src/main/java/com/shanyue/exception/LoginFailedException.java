package com.shanyue.exception;

/**
 * 场景：C 端微信登录失败（code 换 openid 失败 / openid 为空）。
 */
public class LoginFailedException extends BaseException {

    public LoginFailedException(String msg) {
        super(msg);
    }
}
