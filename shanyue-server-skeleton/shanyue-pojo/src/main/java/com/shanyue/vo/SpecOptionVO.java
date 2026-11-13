package com.shanyue.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 规格选项 VO（山月 v2.0）。前端选中若干选项后，把 optionId/optionName/priceDelta
 * 组装成 JSON 快照传给购物车接口（specSelected）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 选项名称 */
    private String name;

    /** 加价金额 */
    private BigDecimal priceDelta;

    /** 排序 */
    private Integer sort;
}
