package com.shanyue.constant;

/**
 * 【本类知识点：JWT 载荷（claims）的自定义字段名】
 * JWT 由三段组成：header.payload.signature，payload 里除标准字段（exp 过期时间等）外，
 * 可以放自定义业务字段。登录成功后我们把员工 id / 用户 id 塞进 payload，
 * 拦截器解析后再取出来放进 ThreadLocal（BaseContext），后续业务随时可取"当前登录人是谁"。
 */
public class JwtClaimsConstant {

    /** 管理端员工 id */
    public static final String EMP_ID = "empId";

    /** C 端用户 id */
    public static final String USER_ID = "userId";

    /** 手机号（预留） */
    public static final String PHONE = "phone";

    /** 用户名（预留） */
    public static final String USERNAME = "username";

    /** 姓名（预留） */
    public static final String NAME = "name";
}
