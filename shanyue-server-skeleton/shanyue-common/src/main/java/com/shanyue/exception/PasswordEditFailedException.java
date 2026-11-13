package com.shanyue.exception;

/**
 * 场景：修改密码失败（旧密码校验不通过、两次新密码不一致等）。
 */
public class PasswordEditFailedException extends BaseException {

    public PasswordEditFailedException(String msg) {
        super(msg);
    }
}
