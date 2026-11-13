package com.shanyue.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 规格选项参数模型（山月 v2.0）。
 * priceDelta 为加价金额：最终售价 = drink.price + Σ(所选项 priceDelta)。
 */
@Data
public class SpecOptionDTO implements Serializable {

    /** 选项 id（修改时已有选项可带 id，整体覆盖策略下可以不带） */
    private Long id;

    /** 所属规格组 id（服务端回填） */
    private Long groupId;

    /** 选项名称，如：大杯 */
    private String name;

    /** 加价金额（0 表示不加价） */
    private BigDecimal priceDelta;

    /** 排序 */
    private Integer sort;
}
