package com.shanyue.service;

/**
 * 店铺服务接口（山月简版：营业状态开关 + 店铺公告，均存 Redis）
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

    /**
     * 查询店铺公告（管理端「店铺设置」页展示）
     *
     * @return 公告文本；从未设置过时返回空串（前端拿空串直接展示「暂无公告」）
     */
    String getNotice();

    /**
     * 修改店铺公告（管理端「保存公告」按钮）
     *
     * @param notice 公告文本（校验长度由调用方 Controller 负责）
     */
    void setNotice(String notice);
}
