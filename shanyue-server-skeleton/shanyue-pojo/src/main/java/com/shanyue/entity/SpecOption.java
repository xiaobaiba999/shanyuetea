package com.shanyue.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 规格选项实体（山月核心 v2.0）。对应表 spec_option。
 * priceDelta（加价）是山月计价的核心：最终售价 = drink.price + Σ(所选项 price_delta)。
 * 例：拿铁 15.90 + 大杯 3.00 = 18.90。
 * 注意金额一律用 BigDecimal：float/double 有精度误差，金额计算绝不使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecOption implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 所属规格组 id */
    private Long groupId;

    /** 选项名称，如：大杯 / 少糖 */
    private String name;

    /** 加价金额（0 表示不加价） */
    private BigDecimal priceDelta;

    /** 排序 */
    private Integer sort;
}
