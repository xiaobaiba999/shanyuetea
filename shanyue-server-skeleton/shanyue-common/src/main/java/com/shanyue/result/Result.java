package com.shanyue.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 【本类知识点：统一响应封装 Result<T>】
 * 1. 前后端约定：code=1 成功、code=0 失败（msg 带提示语）；data 装业务数据。
 * 2. 为什么不用 HTTP 状态码表达业务失败？——HTTP 200 代表"请求送达且服务端处理了"，
 *    业务失败（如密码错误）也属于"处理了"，用 code 区分更直观；但"未登录"(401) 这类
 *    协议层语义仍用 HTTP 状态码（见 GlobalExceptionHandler）。
 * 3. 三个静态工厂方法：success() / success(data) / error(msg)，Controller 一行搞定返回。
 */
@Data
public class Result<T> implements Serializable {

    /** 编码：1 成功，0 和其它数字为失败 */
    private Integer code;

    /** 错误信息 */
    private String msg;

    /** 数据 */
    private T data;

    public static <T> Result<T> success() {
        Result<T> result = new Result<T>();
        result.code = 1;
        return result;
    }

    public static <T> Result<T> success(T object) {
        Result<T> result = new Result<T>();
        result.data = object;
        result.code = 1;
        return result;
    }

    public static <T> Result error(String msg) {
        Result result = new Result();
        result.msg = msg;
        result.code = 0;
        return result;
    }
}
