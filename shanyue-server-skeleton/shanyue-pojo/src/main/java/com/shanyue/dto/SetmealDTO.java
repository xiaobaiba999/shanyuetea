package com.shanyue.dto;

import com.shanyue.entity.SetmealDrink;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 修改套餐的参数模型（管理端；新增时控制器直接用 SetmealVO，课程同款保留）。
 */
@Data
public class SetmealDTO implements Serializable {

    private Long id;

    /** 分类 id */
    private Long categoryId;

    /** 套餐名称 */
    private String name;

    /** 套餐价格 */
    private BigDecimal price;

    /** 状态 0:停售 1:起售 */
    private Integer status;

    /** 描述信息 */
    private String description;

    /** 图片 */
    private String image;

    /** 套餐-饮品关系列表 */
    private List<SetmealDrink> setmealDrinkes = new ArrayList<>();
}
