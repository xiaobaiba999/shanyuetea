package com.shanyue.constant;

/**
 * 【本类知识点：启用/禁用状态常量】
 * 员工账号、分类、饮品、套餐都有"启用/禁用（起售/停售）"的二态字段，
 * 统一用 0/1 表示，代码里不要出现裸写的 0 和 1（可读性差还容易写反）。
 */
public class StatusConstant {

    /** 启用（起售） */
    public static final Integer ENABLE = 1;

    /** 禁用（停售） */
    public static final Integer DISABLE = 0;
}
