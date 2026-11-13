package com.shanyue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 销量统计行（报表 TOP10 使用）：商品名 + 销量。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GoodsSalesDTO implements Serializable {

    /** 商品名称 */
    private String name;

    /** 销量 */
    private Integer number;
}
