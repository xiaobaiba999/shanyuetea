package com.shanyue.service;

/**
 * 店铺服务接口（山月简版：营业状态开关，存 Redis）
 */
public interface ShopService {

    /**
     * 查询店铺营业状态
     *
     * @return 1 营业中 0 打烊（Redis 无值时默认 1）
     */
    Integer getStatus();

    /**
     * 设置店铺营业状态（管理端开关）
     *
     * @param status 1 营业中 0 打烊
     */
    void setStatus(Integer status);
}
