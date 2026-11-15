package com.shanyue.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * C 端购物车操作参数（加购 / 减购共用）。
 * 【山月 v2.0】新增 specSelected：所选规格的 JSON 数组快照字符串，
 * 结构与前端规格弹窗一致，例如：
 *   [{"groupId":1,"groupName":"杯型","optionId":2,"optionName":"大杯","priceDelta":3}]
 * 合并规则：同一用户，drinkId 相同 且 specSelected 字符串完全相同 → 只加数量（number+1）；
 *          规格不同（哪怕只差一个选项）→ 视为不同行。
 * drinkFlavor 字段保留以对照课程代码，山月业务请传 specSelected。
 */
@Data
public class ShoppingCartDTO implements Serializable {

    /** 饮品 id（与 setmealId 二选一） */
    private Long drinkId;

    /** 套餐 id */
    private Long setmealId;

    /** 口味（山月不使用，规格走 specSelected） */
    private String drinkFlavor;

    /** 规格快照 JSON 数组字符串（山月 v2.0，空串/不传表示该商品无规格可选） */
    private String specSelected;
}
