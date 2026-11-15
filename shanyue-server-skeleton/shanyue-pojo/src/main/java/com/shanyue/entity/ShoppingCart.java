package com.shanyue.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车实体。对应表 shopping_cart。
 * 山月 v2.0 新增 specSelected：所选规格的 JSON 快照字符串（与课程的 drinkFlavor 字符串同思路），
 * "同款商品 + 相同规格"合并数量、下单时留档都靠它。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingCart implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 商品名称（冗余，下单时直接复制进订单明细） */
    private String name;

    /** 用户 id */
    private Long userId;

    /** 饮品 id（与 setmealId 二选一） */
    private Long drinkId;

    /** 套餐 id */
    private Long setmealId;

    /** 口味（山月用 specSelected 规格快照） */
    private String drinkFlavor;

    /** 规格快照 JSON 数组字符串（山月 v2.0），例：
     *  [{"groupId":1,"groupName":"杯型","optionId":2,"optionName":"大杯","priceDelta":3}] */
    private String specSelected;

    /** 数量 */
    private Integer number;

    /** 单价（含规格加价，服务端重算） */
    private BigDecimal amount;

    /** 图片 */
    private String image;

    private LocalDateTime createTime;
}
