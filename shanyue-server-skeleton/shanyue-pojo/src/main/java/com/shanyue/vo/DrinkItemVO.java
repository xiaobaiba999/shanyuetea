package com.shanyue.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 套餐内饮品条目 VO（C 端点开套餐弹窗展示"包含哪些饮品"，Drink 口径）。
 * 供 SetmealMapper.getDrinkItemById 联表查询映射。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrinkItemVO implements Serializable {

    /** 饮品名称 */
    private String name;

    /** 份数 */
    private Integer copies;

    /** 饮品图片 */
    private String image;

    /** 饮品描述 */
    private String description;
}
