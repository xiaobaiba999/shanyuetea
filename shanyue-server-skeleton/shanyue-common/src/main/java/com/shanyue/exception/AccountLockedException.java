package com.shanyue.exception;

/**
 * 场景：登录时账号 status=0（被禁用），禁止登录。
 */
public class AccountLockedException extends BaseException {

    public AccountLockedException() {
    }

    public AccountLockedException(String msg) {
        super(msg);
    }
}
