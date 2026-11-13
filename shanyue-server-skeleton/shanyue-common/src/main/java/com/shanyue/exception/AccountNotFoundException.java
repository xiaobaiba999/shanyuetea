package com.shanyue.exception;

/**
 * 场景：登录时用户名查不到（EmployeeServiceImpl.login）。
 * 继承 BaseException，被全局异常处理器兜底处理成 Result.error("账号不存在")。
 */
public class AccountNotFoundException extends BaseException {

    public AccountNotFoundException() {
    }

    public AccountNotFoundException(String msg) {
        super(msg);
    }
}
