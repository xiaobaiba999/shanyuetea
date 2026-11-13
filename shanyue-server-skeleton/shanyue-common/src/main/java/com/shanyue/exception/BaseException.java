package com.shanyue.exception;

/**
 * 【本类知识点：业务异常基类】
 * 1. 继承 RuntimeException：业务异常多数"不该逼调用方写 try-catch"，
 *    受检异常（Exception）会强制每层方法声明 throws，侵入性太强，所以选运行时异常。
 * 2. 全局异常处理器（GlobalExceptionHandler）统一捕获 BaseException，
 *    把 message 包装成 Result.error(msg) 返回给前端 —— Service 层只管 throw，不用管响应。
 * 3. 其它具体异常（账号不存在/密码错误/订单业务异常...）都继承本类，共用同一个兜底 handler。
 */
public class BaseException extends RuntimeException {

    public BaseException() {
    }

    public BaseException(String msg) {
        super(msg);
    }
}
