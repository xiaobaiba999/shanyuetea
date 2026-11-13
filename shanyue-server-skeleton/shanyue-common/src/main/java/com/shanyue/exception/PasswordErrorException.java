package com.shanyue.exception;

/**
 * 场景：登录时密码比对失败（MD5 后与库里不一致）。
 */
public class PasswordErrorException extends BaseException {

    public PasswordErrorException() {
    }

    public PasswordErrorException(String msg) {
        super(msg);
    }
}
