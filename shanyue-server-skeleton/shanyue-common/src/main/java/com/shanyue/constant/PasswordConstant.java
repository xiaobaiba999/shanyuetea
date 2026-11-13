package com.shanyue.constant;

/**
 * 【本类知识点：默认密码】
 * 新增员工时如果不单独设置密码，统一给默认密码 123456（Service 里会先 MD5 加密再入库）。
 * 把魔法值收进常量，将来改默认密码只改一行。
 */
public class PasswordConstant {

    public static final String DEFAULT_PASSWORD = "123456";
}
