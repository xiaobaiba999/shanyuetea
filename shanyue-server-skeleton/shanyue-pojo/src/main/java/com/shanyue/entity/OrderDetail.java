package com.shanyue.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单明细实体。对应表 order_detail（一单多行）。
 * 山月 v2.0 新增 specSelected：下单瞬间所选规格的 JSON 快照（含加价），历史订单可完整还原规格。
 * 月售统计（DrinkVO.monthSales）就是对本表 drink_id 分组 SUM(number)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 商品名称 */
    private String name;

    /** 订单 id */
    private Long orderId;

    /** 饮品 id */
    private Long drinkId;

    /** 套餐 id */
    private Long setmealId;

    /** 口味（课程同款字段保留） */
    private String drinkFlavor;

    /** 规格快照 JSON 数组字符串（山月 v2.0，与购物车 specSelected 同结构） */
    private String specSelected;

    /** 数量 */
    private Integer number;

    /** 单价（含规格加价） */
    private BigDecimal amount;

    /** 图片 */
    private String image;
}
