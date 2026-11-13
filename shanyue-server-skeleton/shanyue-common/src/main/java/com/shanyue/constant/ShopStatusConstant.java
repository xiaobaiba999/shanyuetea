package com.shanyue.constant;

/**
 * 【本类知识点：为什么营业状态存 Redis 而不是数据库？】
 * 营业状态（1 营业中 / 0 打烊）是一个"高频读、变更少、丢了也无所谓"的开关数据，
 * 放 Redis 可以避免每次 C 端进入小程序都查数据库；key 固定为 SHOP_STATUS，value 是 0/1。
 * 管理端 PUT /admin/shop/{status} 写入，C 端 GET /user/shop/status 读取。
 */
public class ShopStatusConstant {

    /** Redis 中存储店铺营业状态的 key */
    public static final String KEY = "SHOP_STATUS";
}
