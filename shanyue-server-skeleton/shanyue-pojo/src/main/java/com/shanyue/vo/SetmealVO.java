package com.shanyue.vo;

import com.shanyue.entity.SetmealDrink;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 套餐 VO（管理端新增套餐时控制器直接用它接收，修改用 SetmealDTO，课程同款保留）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetmealVO implements Serializable {

    private static final long serialVersionUID = 1L;

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

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 分类名称 */
    private String categoryName;

    /** 套餐-饮品关联 */
    private List<SetmealDrink> setmealDrinkes = new ArrayList<>();
}
