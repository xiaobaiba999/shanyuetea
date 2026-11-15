package com.shanyue.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 套餐-饮品关联实体（多对多中间表 setmeal_drink，v1.2 起 drink 口径）。
 * name/price 是冗余字段：查询套餐包含哪些饮品时免联表，代价是饮品改名/改价后需要同步更新。
 * 字段与 setmeal_drink 表列一一对应（drink_id ↔ drinkId 驼峰自动映射）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetmealDrink implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 套餐 id */
    private Long setmealId;

    /** 饮品 id */
    private Long drinkId;

    /** 饮品名称（冗余字段） */
    private String name;

    /** 饮品原价（冗余字段） */
    private BigDecimal price;

    /** 份数 */
    private Integer copies;
}
