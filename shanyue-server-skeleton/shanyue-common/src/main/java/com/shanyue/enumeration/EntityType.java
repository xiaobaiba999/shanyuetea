package com.shanyue.enumeration;

/**
 * 【本类知识点：业务对象类型枚举（山月预留）】
 * 表示"被操作的对象是哪类业务"，例如后续做操作日志（谁在什么时候改了哪个饮品）、
 * 通知推送（来单提醒 / 催单提醒里标记业务类型）时可直接复用，避免到处写魔法字符串。
 */
public enum EntityType {

    /** 员工 */
    EMPLOYEE,

    /** 分类 */
    CATEGORY,

    /** 饮品（课程里对应菜品 Drink） */
    DRINK,

    /** 套餐 */
    SETMEAL,

    /** C 端用户 */
    USER,

    /** 订单 */
    ORDER
}
