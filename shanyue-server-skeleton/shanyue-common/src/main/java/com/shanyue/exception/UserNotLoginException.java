package com.shanyue.exception;

/**
 * 场景：C 端用户未登录 / token 过期或非法。
 * 由 JwtTokenUserInterceptor 在 token 解析失败时抛出，
 * 全局异常处理器用 @ResponseStatus(HttpStatus.UNAUTHORIZED) 把它响应为 401 状态码，
 * 前端请求库统一拦截 401 后跳转登录页。
 */
public class UserNotLoginException extends BaseException {

    public UserNotLoginException() {
    }

    public UserNotLoginException(String msg) {
        super(msg);
    }
}
