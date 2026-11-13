package com.shanyue.service;

import com.shanyue.dto.ShoppingCartDTO;
import com.shanyue.entity.ShoppingCart;

import java.util.List;

/**
 * C 端购物车服务接口（课程叫 ShopCartService，山月按 POJO 命名习惯写作 ShoppingCartService）
 */
public interface ShoppingCartService {

    /**
     * 加入购物车（先查后插：同饮品+同规格快照 → 数量 +1，否则新增一行；单价由服务端重算）
     *
     * @param shoppingCartDTO drinkId/setmealId + specSelected（规格快照 JSON）
     */
    void add(ShoppingCartDTO shoppingCartDTO);

    /**
     * 查询当前用户购物车列表
     *
     * @return 购物车列表
     */
    List<ShoppingCart> showShoppingCart();

    /**
     * 清空当前用户购物车
     */
    void cleanShoppingCart();

    /**
     * 减少购物车中一个商品的数量（减到 0 删行；按 行id 精确删除）
     *
     * @param shoppingCartDTO drinkId/setmealId + specSelected
     */
    void sub(ShoppingCartDTO shoppingCartDTO);
}
