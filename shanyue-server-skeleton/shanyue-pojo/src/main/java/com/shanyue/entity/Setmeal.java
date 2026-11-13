package com.shanyue.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 套餐实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Setmeal implements Serializable {

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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createUser;

    private Long updateUser;
}
