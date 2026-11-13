package com.shanyue.constant;

/**
 * 【本类知识点：常量类】
 * 1. 常量类用 public static final 修饰，命名全大写 + 下划线，构造器私有防止实例化更规范（课程里省略了）。
 * 2. "公共字段"指几乎每张表都有的 create_time / update_time / create_user / update_user。
 * 3. 这些字符串是实体类里 setter 的方法名，AutoFillAspect 会通过反射按名字找到方法再赋值，
 *    所以字符串必须与实体类字段名严格对应（改了字段名这里也要同步改）。
 */
public class AutoFillConstant {

    /** 实体类中的方法名称：填充创建时间 */
    public static final String SET_CREATE_TIME = "setCreateTime";

    /** 实体类中的方法名称：填充修改时间 */
    public static final String SET_UPDATE_TIME = "setUpdateTime";

    /** 实体类中的方法名称：填充创建人 */
    public static final String SET_CREATE_USER = "setCreateUser";

    /** 实体类中的方法名称：填充修改人 */
    public static final String SET_UPDATE_USER = "setUpdateUser";
}
